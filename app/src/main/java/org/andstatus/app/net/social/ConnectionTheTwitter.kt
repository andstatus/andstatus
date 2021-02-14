/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.Build;

import androidx.annotation.NonNull;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.HttpRequest;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.vavr.control.Try;

import static org.andstatus.app.context.MyPreferences.BYTES_IN_MB;

/**
 * Implementation of current API of the twitter.com
 * https://dev.twitter.com/rest/public
 */
public class ConnectionTheTwitter extends ConnectionTwitterLike {
    private static final String ATTACHMENTS_FIELD_NAME = "media";
    private static final String SENSITIVE_PROPERTY = "possibly_sensitive";

    @NonNull
    @Override
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "application/rate_limit_status.json";
                break;
            case LIKE:
                url = "favorites/create.json?tweet_mode=extended";
                break;
            case UNDO_LIKE:
                url = "favorites/destroy.json?tweet_mode=extended";
                break;
            case PRIVATE_NOTES:
                url = "direct_messages.json?tweet_mode=extended";
                break;
            case LIKED_TIMELINE:
                // https://dev.twitter.com/rest/reference/get/favorites/list
                url = "favorites/list.json?tweet_mode=extended";
                break;
            case GET_FOLLOWERS:
                // https://dev.twitter.com/rest/reference/get/followers/list
                url = "followers/list.json";
                break;
            case GET_FRIENDS:
                // https://dev.twitter.com/docs/api/1.1/get/friends/list
                url = "friends/list.json";
                break;
            case GET_NOTE:
                url = "statuses/show.json" + "?id=%noteId%&tweet_mode=extended";
                break;
            case HOME_TIMELINE:
                url = "statuses/home_timeline.json?tweet_mode=extended";
                break;
            case NOTIFICATIONS_TIMELINE:
                // https://dev.twitter.com/docs/api/1.1/get/statuses/mentions_timeline
                url = "statuses/mentions_timeline.json?tweet_mode=extended";
                break;
            case UPDATE_PRIVATE_NOTE:
                url = "direct_messages/new.json?tweet_mode=extended";
                break;
            case UPDATE_NOTE:
                url = "statuses/update.json?tweet_mode=extended";
                break;
            case ANNOUNCE:
                url = "statuses/retweet/%noteId%.json?tweet_mode=extended";
                break;
            case UPLOAD_MEDIA:
                // Trying to allow setting alternative Twitter host...
                if (http.data.originUrl.getHost().equals("api.twitter.com")) {
                    url = "https://upload.twitter.com/1.1/media/upload.json";
                } else {
                    url = "media/upload.json";
                }
                break;
            case SEARCH_NOTES:
                // https://dev.twitter.com/docs/api/1.1/get/search/tweets
                url = "search/tweets.json?tweet_mode=extended";
                break;
            case SEARCH_ACTORS:
                url = "users/search.json?tweet_mode=extended";
                break;
            case ACTOR_TIMELINE:
                url = "statuses/user_timeline.json?tweet_mode=extended";
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

    /**
     * https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/post-statuses-update
     * @return
     */
    @Override
    protected Try<AActivity> updateNote2(Note note) {
        JSONObject obj = new JSONObject();
        try {
            super.updateNoteSetFields(note, obj);
            if (note.isSensitive()) {
                obj.put(SENSITIVE_PROPERTY, note.isSensitive());
            }
            List<String> ids = new ArrayList<>();
            for (Attachment attachment : note.attachments.list) {
                if (UriUtils.isDownloadable(attachment.uri)) {
                    MyLog.i(this, "Skipped downloadable " + attachment);
                } else {
                    // https://developer.twitter.com/en/docs/media/upload-media/api-reference/post-media-upload
                    JSONObject mediaObject = uploadMedia(attachment);
                    if (mediaObject != null && mediaObject.has("media_id_string")) {
                        ids.add(mediaObject.get("media_id_string").toString());
                    }
                }
            };
            if (!ids.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    obj.put("media_ids", String.join(",", ids));
                } else {
                    obj.put("media_ids", ids.stream().collect(Collectors.joining(",")));
                }
            }
        } catch (JSONException e) {
            return Try.failure(ConnectionException.hardConnectionException("Exception while preparing post params " + note, e));
        } catch (Exception e) {
            return Try.failure(e);
        }
        return postRequest(ApiRoutineEnum.UPDATE_NOTE, obj)
                .flatMap(HttpReadResult::getJsonObject)
                .map(this::activityFromJson);
    }

    private JSONObject uploadMedia(Attachment attachment) throws ConnectionException {
        return tryApiPath(data.getAccountActor(), ApiRoutineEnum.UPLOAD_MEDIA)
        .map(uri -> HttpRequest.of(ApiRoutineEnum.UPLOAD_MEDIA, uri)
                        .withMediaPartName("media")
                        .withAttachmentToPost(attachment)
        )
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .filter(Objects::nonNull)
        .onSuccess(jso -> MyLog.v(this, () -> "uploaded '" + attachment + "' " + jso.toString()))
        .getOrElseThrow(ConnectionException::of);
    }

    @Override
    public Try<OriginConfig> getConfig() {
        // There is https://developer.twitter.com/en/docs/developer-utilities/configuration/api-reference/get-help-configuration
        // but it doesn't have this 280 chars limit...
        return Try.success(new OriginConfig(280, 5 * BYTES_IN_MB));
    }

    @Override
    public Try<AActivity> like(String noteOid) {
        JSONObject out = new JSONObject();
        try {
            out.put("id", noteOid);
        } catch (JSONException e) {
            return Try.failure(e);
        }
        return postRequest(ApiRoutineEnum.LIKE, out)
            .flatMap(HttpReadResult::getJsonObject)
            .map(this::activityFromJson);
    }

    @Override
    public Try<AActivity> undoLike(String noteOid) {
        JSONObject out = new JSONObject();
        try {
            out.put("id", noteOid);
        } catch (JSONException e) {
            return Try.failure(e);
        }
        return postRequest(ApiRoutineEnum.UNDO_LIKE, out)
            .flatMap(HttpReadResult::getJsonObject)
            .map(this::activityFromJson);
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> searchNotes(boolean syncYounger, TimelinePosition youngestPosition,
                                         TimelinePosition oldestPosition, int limit, String searchQuery) {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_NOTES;
        return getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(b -> StringUtil.isEmpty(searchQuery) ? b : b.appendQueryParameter("q", searchQuery))
        .map(builder -> appendPositionParameters(builder, youngestPosition, oldestPosition))
        .map(builder -> builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(result -> result.getJsonArrayInObject("statuses")
            .flatMap(jArr -> jArrToTimeline(jArr, apiRoutine)))
        .map(InputTimelinePage::of);
    }

    @NonNull
    @Override
    public Try<List<Actor>> searchActors(int limit, String searchQuery) {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_ACTORS;
        return getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(b -> StringUtil.isEmpty(searchQuery) ? b : b.appendQueryParameter("q", searchQuery))
        .map(b -> b.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(result -> result.getJsonArray()
            .flatMap(jsonArray -> jArrToActors(jsonArray, apiRoutine, result.request.uri)));
    }

    @Override
    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) return AActivity.EMPTY;

        AActivity activity = super.activityFromJson2(jso);
        Note note =  activity.getNote();
        note.setSensitive(jso.optBoolean(SENSITIVE_PROPERTY));
        note.setLikesCount(jso.optLong("favorite_count"));
        note.setReblogsCount(jso.optLong("retweet_count"));
        if (!addAttachmentsFromJson(jso, activity, "extended_entities")) {
            // See https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/entities-object
            addAttachmentsFromJson(jso, activity, "entities");
        }
        return activity;
    }

    private boolean addAttachmentsFromJson(JSONObject jso, AActivity activity, String sectionName) {
        JSONObject entities = jso.optJSONObject(sectionName);
        JSONArray jArr = entities == null ? null : entities.optJSONArray(ATTACHMENTS_FIELD_NAME);
        if (jArr != null && jArr.length() > 0) {
            for (int ind = 0; ind < jArr.length(); ind++) {
                JSONObject jsoAttachment = (JSONObject) jArr.opt(ind);
                jsonToAttachments(jsoAttachment).forEach(activity::addAttachment);
            }
            return true;
        }
        return false;
    }

    private List<Attachment> jsonToAttachments(JSONObject jsoAttachment) {
        final String method = "jsonToAttachments";
        List<Attachment> attachments = new ArrayList<>();
        try {
            JSONObject jsoVideo = jsoAttachment.optJSONObject("video_info");
            JSONArray jsoVariants = jsoVideo == null ? null : jsoVideo.optJSONArray("variants");
            JSONObject videoVariant = jsoVariants == null || jsoVariants.length() == 0
                    ? null
                    : jsoVariants.optJSONObject(0);
            Attachment video = videoVariant == null
                    ? Attachment.EMPTY
                    : Attachment.fromUriAndMimeType(videoVariant.optString("url"), videoVariant.optString("content_type"));
            if (video.isValid()) attachments.add(video);

            Attachment attachment = Attachment.fromUri(UriUtils.fromAlternativeTags(jsoAttachment,
                            "media_url_https", "media_url_http"));
            if (attachment.isValid()) {
                if (video.isValid()) attachment.setPreviewOf(video);
                attachments.add(attachment);
            } else {
                MyLog.w(this, method + "; invalid attachment: " + jsoAttachment);
            }
        } catch (Exception e) {
            MyLog.w(this, method, e);
        }
        return attachments;
    }

    @Override
    protected void setNoteBodyFromJson(Note note, JSONObject jso) throws JSONException {
        boolean bodyFound = false;
        if (!jso.isNull("full_text")) {
            note.setContentPosted(jso.getString("full_text"));
            bodyFound = true;
        }
        if (!bodyFound) {
            super.setNoteBodyFromJson(note, jso);
        }
    }

    @Override
    Try<List<Actor>> getActors(Actor actor, ApiRoutineEnum apiRoutine) {
        int limit = 200;
        return getApiPathWithActorId(apiRoutine, actor.oid)
        .map(Uri::buildUpon)
        .map(b -> StringUtil.isEmpty(actor.oid) ? b : b.appendQueryParameter("user_id", actor.oid))
        .map(b -> b.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(result -> result.getJsonArray()
            .flatMap(jsonArray -> jArrToActors(jsonArray, apiRoutine, result.request.uri)));
    }

}
