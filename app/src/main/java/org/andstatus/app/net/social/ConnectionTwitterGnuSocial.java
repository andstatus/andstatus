/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.net.social;

import android.net.Uri;

import androidx.annotation.NonNull;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.HttpRequest;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vavr.control.Try;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.andstatus.app.util.UriUtils.nonRealOid;

/**
 * Specific implementation of the Twitter API in GNU Social
 * @author yvolk@yurivolkov.com
 */
public class ConnectionTwitterGnuSocial extends ConnectionTwitterLike {
    private static final String ATTACHMENTS_FIELD_NAME = "attachments";
    private static final String CONVERSATION_ID_FIELD_NAME = "statusnet_conversation_id";
    private static final String HTML_BODY_FIELD_NAME = "statusnet_html";
    public static final Pattern GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN = Pattern.compile(
            "(?s)([^ ]+) favorited something by [^ ]+ (.+)");
    public static final Pattern GNU_SOCIAL_FAVOURITED_A_STATUS_BY_PATTERN = Pattern.compile(
            "(?s)([^ ]+) favourited (a status by [^ ]+)");

    @NonNull
    @Override
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case GET_CONFIG:
                url = "statusnet/config.json";
                break;
            case GET_CONVERSATION:
                url = "statusnet/conversation/%noteId%.json";
                break;
            case GET_OPEN_INSTANCES:
                url = "http://gstools.org/api/get_open_instances";
                break;
            case PUBLIC_TIMELINE:
                url = "statuses/public_timeline.json";
                break;
            case SEARCH_NOTES:
                url = "search.json";
                break;
            default:
                url = "";
                break;
        }
        if (StringUtil.isEmpty(url)) {
            return super.getApiPathFromOrigin(routine);
        } 
        return partialPathToApiPath(url);
    }

    @Override
    public Try<List<String>> getFriendsOrFollowersIds(ApiRoutineEnum apiRoutine, String actorOid) {
        return getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(builder -> builder.appendQueryParameter("user_id", actorOid))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonArray)
        .flatMap(jsonArray -> {
            List<String> list = new ArrayList<>();
            try {
                for (int index = 0; jsonArray != null && index < jsonArray.length(); index++) {
                    list.add(jsonArray.getString(index));
                }
            } catch (JSONException e) {
                return Try.failure(ConnectionException.loggedJsonException(this, apiRoutine.name(), e, jsonArray));
            }
            return Try.success(list);
        });
    }

    @Override
    public Try<RateLimitStatus> rateLimitStatus() {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS;
        return getApiPath(apiRoutine)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .flatMap(result -> {
            RateLimitStatus status = new RateLimitStatus();
            if (result != null) {
                status.remaining = result.optInt("remaining_hits");
                status.limit = result.optInt("hourly_limit");
            }
            return Try.success(status);
        });
    }

    @Override
    protected Try<AActivity> updateNote2(Note note, String inReplyToOid, Attachments attachments) {
        JSONObject formParams = new JSONObject();
        try {
            super.updateNoteSetFields(note, inReplyToOid, formParams);

            // This parameter was removed from Twitter API, but it still is in GNUsocial
            formParams.put("source", HttpConnection.USER_AGENT);

            if (attachments.toUploadCount() > 0) {
                formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "media");
                formParams.put(HttpConnection.KEY_MEDIA_PART_URI, attachments.getFirstToUpload().uri.toString());
            }
        } catch (JSONException e) {
            return Try.failure(e);
        }
        return postRequest(ApiRoutineEnum.UPDATE_NOTE, formParams)
            .flatMap(HttpReadResult::getJsonObject)
            .map(this::activityFromJson);
    }
    
    @Override
    public Try<OriginConfig> getConfig() {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.GET_CONFIG;
        return getApiPath(apiRoutine)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .map(result -> {
            OriginConfig config = OriginConfig.getEmpty();
            if (result != null) {
                JSONObject site = result.optJSONObject("site");
                if (site != null) {
                    int textLimit = site.optInt("textlimit");
                    int uploadLimit = 0;
                    JSONObject attachments = site.optJSONObject("attachments");
                    if (attachments != null && site.optBoolean("uploads")) {
                        uploadLimit = site.optInt("file_quota");
                    }
                    config = OriginConfig.fromTextLimit(textLimit, uploadLimit);
                    // "shorturllength" is not used
                }
            }
            return config;
        });
    }

    @Override
    public Try<List<AActivity>> getConversation(String conversationOid) {
        if (nonRealOid(conversationOid)) return TryUtils.emptyList();

        ApiRoutineEnum apiRoutine = ApiRoutineEnum.GET_CONVERSATION;
        return getApiPathWithNoteId(apiRoutine, conversationOid)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonArray)
        .flatMap(jsonArray -> jArrToTimeline(jsonArray, apiRoutine));
    }

    @Override
    protected void setNoteBodyFromJson(Note note, JSONObject jso) throws JSONException {
        if (data.getOrigin().isHtmlContentAllowed() && !jso.isNull(HTML_BODY_FIELD_NAME)) {
            note.setContent(jso.getString(HTML_BODY_FIELD_NAME), TextMediaType.HTML);
        } else if (jso.has("text")) {
            note.setContent(jso.getString("text"), TextMediaType.PLAIN);
        }
    }

    @Override
    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) return AActivity.EMPTY;
        final String method = "activityFromJson2";
        AActivity activity = super.activityFromJson2(jso);
        Note note = activity.getNote();
        note.url = JsonUtils.optString(jso, "external_url");
        note.setConversationOid(JsonUtils.optString(jso, CONVERSATION_ID_FIELD_NAME));
        if (!jso.isNull(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    JSONObject jsonAttachment = (JSONObject) jArr.get(ind);
                    Uri uri = UriUtils.fromAlternativeTags(jsonAttachment, "url", "thumb_url");
                    Attachment attachment =  Attachment.fromUriAndMimeType(uri, JsonUtils.optString(jsonAttachment, "mimetype"));
                    if (attachment.isValid()) {
                        activity.addAttachment(attachment);
                    } else {
                        MyLog.d(this, method + "; invalid attachment #" + ind + "; " + jArr.toString());
                    }
                }
            } catch (JSONException e) {
                MyLog.d(this, method, e);
            }
        }
        return createLikeActivity(activity);
    }

    static AActivity createLikeActivity(AActivity activityIn) {
        final Note noteIn = activityIn.getNote();
        Matcher matcher = GNU_SOCIAL_FAVORITED_SOMETHING_BY_PATTERN.matcher(noteIn.getContent());
        if (!matcher.matches()) {
            matcher = GNU_SOCIAL_FAVOURITED_A_STATUS_BY_PATTERN.matcher(noteIn.getContent());
        }
        if (!matcher.matches()) return activityIn;

        final AActivity inReplyTo = noteIn.getInReplyTo();
        final AActivity favoritedActivity;
        if (UriUtils.isRealOid(inReplyTo.getNote().oid)) {
            favoritedActivity = inReplyTo;
        } else {
            favoritedActivity = AActivity.from(activityIn.accountActor, ActivityType.UPDATE);
            favoritedActivity.setActor(activityIn.getActor());
            favoritedActivity.setNote(noteIn);
        }
        favoritedActivity.setUpdatedDate(SOME_TIME_AGO);

        Note note = favoritedActivity.getNote();
        note.setContent(matcher.replaceFirst("$2"), TextMediaType.HTML);
        note.setUpdatedDate(SOME_TIME_AGO);
        note.setStatus(DownloadStatus.LOADED);  // TODO: Maybe we need to invent some other status for partially loaded...
        note.setInReplyTo(AActivity.EMPTY);

        AActivity activity = AActivity.from(activityIn.accountActor, ActivityType.LIKE);
        activity.setOid(activityIn.getOid());
        activity.setActor(activityIn.getActor());
        activity.setUpdatedDate(activityIn.getUpdatedDate());
        activity.setActivity(favoritedActivity);
        return activity;
    }

    @Override
    @NonNull
    Actor actorBuilderFromJson(JSONObject jso) {
        if (jso == null) return Actor.EMPTY;
        return super.actorBuilderFromJson(jso)
                .setProfileUrl(JsonUtils.optString(jso, "statusnet_profile_url"));
    }
    
    @Override
    public Try<List<Server>> getOpenInstances() {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.GET_OPEN_INSTANCES;
        return getApiPath(apiRoutine)
        .map(path -> HttpRequest.of(apiRoutine, path)
                        .withAuthenticate(false))
        .flatMap(http::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .map(result -> {
            List<Server> origins = new ArrayList<>();
            StringBuilder logMessage = new StringBuilder(apiRoutine.toString());
            boolean error = false;
            if (result == null) {
                MyStringBuilder.appendWithSpace(logMessage, "Response is null JSON");
                error = true;
            }
            if (!error && !JsonUtils.optString(result, "status").equals("OK")) {
                MyStringBuilder.appendWithSpace(logMessage, "gtools service returned the error: '" +
                        JsonUtils.optString(result, "error") + "'");
                error = true;
            }
            if (!error) {
                JSONObject data = result.optJSONObject("data");
                if (data != null) {
                    try {
                        Iterator<String> iterator = data.keys();
                        while(iterator.hasNext()) {
                            String key = iterator.next();
                            JSONObject instance = data.getJSONObject(key);
                            origins.add(new Server(JsonUtils.optString(instance, "instance_name"),
                                    JsonUtils.optString(instance, "instance_address"),
                                    instance.optLong("users_count"),
                                    instance.optLong("notices_count")));
                        }
                    } catch (JSONException e) {
                        throw ConnectionException.loggedJsonException(this, logMessage.toString(), e, data);
                    }
                }
            }
            if (error) {
                throw new ConnectionException(logMessage.toString());
            }
            return origins;
        });
    }
    
}
