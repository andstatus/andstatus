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

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * Implementation of current API of the twitter.com
 * https://dev.twitter.com/rest/public
 */
public class ConnectionTheTwitter extends ConnectionTwitterLike {

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
            case UPDATE_NOTE_WITH_MEDIA:
                url = "statuses/update_with_media.json?tweet_mode=extended";
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
        if (StringUtils.isEmpty(url)) {
            return super.getApiPathFromOrigin(routine);
        }
        return prependWithBasicPath(url);
    }

    @Override
    protected AActivity updateNote2(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        if (UriUtils.isEmpty(mediaUri)) {
            return super.updateNote2(name, content, noteOid, audience, inReplyToOid, mediaUri);
        }
        return updateWithMedia(content, inReplyToOid, mediaUri);
    }

    private AActivity updateWithMedia(String note, String inReplyToId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", note);
            if (!StringUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_status_id", inReplyToId);
            }
            if (!UriUtils.isEmpty(mediaUri)) {
                formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "media[]");
                formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.UPDATE_NOTE_WITH_MEDIA, formParams);
        return activityFromJson(jso);
    }

    @Override
    public AActivity like(String noteOid) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", noteOid);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.LIKE, out);
        return activityFromJson(jso);
    }

    @Override
    public AActivity undoLike(String noteOid) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", noteOid);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.UNDO_LIKE, out);
        return activityFromJson(jso);
    }

    @NonNull
    @Override
    public List<AActivity> searchNotes(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_NOTES;
        Uri.Builder builder = getApiPath(apiRoutine).buildUpon();
        if (!StringUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = getRequestArrayInObject(builder.build(), "statuses");
        return jArrToTimeline("", jArr, apiRoutine, builder.build());
    }

    @NonNull
    @Override
    public List<Actor> searchActors(int limit, String searchQuery) throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_ACTORS;
        Uri.Builder builder = getApiPath(apiRoutine).buildUpon();
        if (!StringUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        return jArrToActors(http.getRequestAsArray(builder.build()), apiRoutine, builder.build());
    }

    private static final String ATTACHMENTS_FIELD_NAME = "media";
    @Override
    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        final String method = "activityFromJson2";
        AActivity activity = super.activityFromJson2(jso);
        // See https://dev.twitter.com/docs/entities
        JSONObject entities = jso.optJSONObject("entities");
        if (entities != null && entities.has(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = entities.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    Attachment attachment = Attachment.fromUri(
                            UriUtils.fromAlternativeTags((JSONObject) jArr.get(ind),
                                    "media_url_https", "media_url_http"));
                    if (attachment.isValid()) {
                        activity.getNote().attachments.add(attachment);
                    } else {
                        MyLog.d(this, method + "; invalid attachment #" + ind + "; " + jArr.toString());
                    }
                }
            } catch (JSONException e) {
                MyLog.d(this, method, e);
            }
        }
        return activity;
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

    List<Actor> getActors(String actorId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        Uri.Builder builder = getApiPath(apiRoutine).buildUpon();
        int limit = 200;
        if (!StringUtils.isEmpty(actorId)) {
            builder.appendQueryParameter("user_id", actorId);
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        return jArrToActors(http.getRequestAsArray(builder.build()), apiRoutine, builder.build());
    }

}
