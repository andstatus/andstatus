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
import android.support.annotation.NonNull;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.TextMediaType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

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

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
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
        if (StringUtils.isEmpty(url)) {
            return super.getApiPath1(routine);
        } 
        return prependWithBasicPath(url);
    }

    @Override
    public List<String> getFriendsIds(String actorOid) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", actorOid);
        List<String> list = new ArrayList<>();
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        try {
            for (int index = 0; index < jArr.length(); index++) {
                list.add(jArr.getString(index));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing friendsIds", e, null);
        }
        return list;
    }

    @Override
    public List<String> getFollowersIds(String actorOid) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FOLLOWERS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", actorOid);
        List<String> list = new ArrayList<>();
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        try {
            for (int index = 0; index < jArr.length(); index++) {
                list.add(jArr.getString(index));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing followersIds", e, null);
        }
        return list;
    }

    @Override
    protected AActivity updateNote2(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", content);
            
            // This parameter was removed from Twitter API, but it still is in GNUsocial
            formParams.put("source", HttpConnection.USER_AGENT);
            
            if ( !StringUtils.isEmpty(inReplyToOid)) {
                formParams.put("in_reply_to_status_id", inReplyToOid);
            }
            if (!UriUtils.isEmpty(mediaUri)) {
                formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "media");
                formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.UPDATE_NOTE, formParams);
        return activityFromJson(jso);
    }
    
    @Override
    public OriginConfig getConfig() throws ConnectionException {
        JSONObject result = http.getRequest(getApiPath(ApiRoutineEnum.GET_CONFIG));
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
    }

    @Override
    public List<AActivity> getConversation(String conversationOid) throws ConnectionException {
        if (StringUtils.isEmpty(conversationOid)) {
            return new ArrayList<>();
        } else {
            String url = getApiPathWithNoteId(ApiRoutineEnum.GET_CONVERSATION, conversationOid);
            JSONArray jArr = http.getRequestAsArray(url);
            return jArrToTimeline(jArr, ApiRoutineEnum.GET_CONVERSATION, url);
        }
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
        note.url = jso.optString("external_url");
        note.setConversationOid(jso.optString(CONVERSATION_ID_FIELD_NAME));
        if (!jso.isNull(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    JSONObject jsonAttachment = (JSONObject) jArr.get(ind);
                    Uri uri = UriUtils.fromAlternativeTags(jsonAttachment, "url", "thumb_url");
                    Attachment attachment =  Attachment.fromUriAndMimeType(uri, jsonAttachment.optString("mimetype"));
                    if (attachment.isValid()) {
                        note.attachments.add(attachment);
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

    public static AActivity createLikeActivity(AActivity activityIn) {
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
        activity.setTimelinePosition(activityIn.getTimelinePosition().getPosition());
        activity.setActor(activityIn.getActor());
        activity.setUpdatedDate(activityIn.getUpdatedDate());
        activity.setActivity(favoritedActivity);
        return activity;
    }

    @Override
    @NonNull
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        return super.actorFromJson(jso).setProfileUrl(jso.optString("statusnet_profile_url"));
    }
    
    @Override
    public List<Server> getOpenInstances() throws ConnectionException {
        JSONObject result = http.getUnauthenticatedRequest(getApiPath(ApiRoutineEnum.GET_OPEN_INSTANCES));
        List<Server> origins = new ArrayList<>();
        StringBuilder logMessage = new StringBuilder(ApiRoutineEnum.GET_OPEN_INSTANCES.toString());
        boolean error = false;
        if (result == null) {
            MyStringBuilder.appendWithSpace(logMessage, "Response is null JSON");
            error = true;
        }
        if (!error && !result.optString("status").equals("OK")) {
            MyStringBuilder.appendWithSpace(logMessage, "gtools service returned the error: '" + result.optString("error") + "'");
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
                        origins.add(new Server(instance.optString("instance_name"),
                                instance.optString("instance_address"),
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
    }
    
}
