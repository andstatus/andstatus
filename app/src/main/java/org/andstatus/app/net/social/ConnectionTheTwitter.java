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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Implementation of current API of the twitter.com
 * https://dev.twitter.com/rest/public
 */
public class ConnectionTheTwitter extends ConnectionTwitterLike {

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "application/rate_limit_status.json";
                break;
            case CREATE_FAVORITE:
                url = "favorites/create.json?tweet_mode=extended";
                break;
            case DESTROY_FAVORITE:
                url = "favorites/destroy.json?tweet_mode=extended";
                break;
            case DIRECT_MESSAGES:
                url = "direct_messages.json?tweet_mode=extended";
                break;
            case FAVORITES_TIMELINE:
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
            case GET_MESSAGE:
                url = "statuses/show.json" + "?id=%messageId%&tweet_mode=extended";
                break;
            case HOME_TIMELINE:
                url = "statuses/home_timeline.json?tweet_mode=extended";
                break;
            case MENTIONS_TIMELINE:
                // https://dev.twitter.com/docs/api/1.1/get/statuses/mentions_timeline
                url = "statuses/mentions_timeline.json?tweet_mode=extended";
                break;
            case POST_DIRECT_MESSAGE:
                url = "direct_messages/new.json?tweet_mode=extended";
                break;
            case POST_MESSAGE:
                url = "statuses/update.json?tweet_mode=extended";
                break;
            case POST_REBLOG:
                url = "statuses/retweet/%messageId%.json?tweet_mode=extended";
                break;
            case POST_WITH_MEDIA:
                url = "statuses/update_with_media.json?tweet_mode=extended";
                break;
            case SEARCH_MESSAGES:
                // https://dev.twitter.com/docs/api/1.1/get/search/tweets
                url = "search/tweets.json?tweet_mode=extended";
                break;
            case USER_TIMELINE:
                url = "statuses/user_timeline.json?tweet_mode=extended";
                break;
            default:
                url = "";
                break;
        }
        if (TextUtils.isEmpty(url)) {
            return super.getApiPath1(routine);
        }
        return prependWithBasicPath(url);
    }

    @Override
    public MbActivity updateStatus(String message, String statusId, String inReplyToId, Uri mediaUri)
            throws ConnectionException {
        if (UriUtils.isEmpty(mediaUri)) {
            return super.updateStatus(message, statusId, inReplyToId, mediaUri);
        }
        return updateWithMedia(message, inReplyToId, mediaUri);
    }

    private MbActivity updateWithMedia(String message, String inReplyToId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", message);
            if (!TextUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_status_id", inReplyToId);
            }
            if (!UriUtils.isEmpty(mediaUri)) {
                formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "media[]");
                formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_WITH_MEDIA, formParams);
        return activityFromJson(jso);
    }

    @Override
    public MbActivity createFavorite(String statusId) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", statusId);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.CREATE_FAVORITE, out);
        return activityFromJson(jso);
    }

    @Override
    public MbActivity destroyFavorite(String statusId) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", statusId);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.DESTROY_FAVORITE, out);
        return activityFromJson(jso);
    }

    @NonNull
    @Override
    public List<MbActivity> searchMessages(TimelinePosition youngestPosition,
                                           TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_MESSAGES;
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!TextUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = getRequestArrayInObject(builder.build().toString(), "statuses");
        return jArrToTimeline(jArr, apiRoutine, url);
    }
    
    private static final String ATTACHMENTS_FIELD_NAME = "media";
    @Override
    @NonNull
    MbMessage messageFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return MbMessage.EMPTY;
        }
        final String method = "messageFromJson";
        MbMessage message = super.messageFromJson2(jso);
        // See https://dev.twitter.com/docs/entities
        JSONObject entities = jso.optJSONObject("entities");
        if (entities != null && entities.has(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = entities.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    JSONObject attachment = (JSONObject) jArr.get(ind);
                    Uri uri = UriUtils.fromAlternativeTags(attachment, "media_url_https", "media_url_http");
                    MbAttachment mbAttachment =  MbAttachment.fromUriAndContentType(uri, MyContentType.IMAGE);
                    if (mbAttachment.isValid()) {
                        message.attachments.add(mbAttachment);
                    } else {
                        MyLog.d(this, method + "; invalid attachment #" + ind + "; " + jArr.toString());
                    }
                }
            } catch (JSONException e) {
                MyLog.d(this, method, e);
            }
        }
        return message;
    }

    @Override
    protected void setMessageBodyFromJson(MbMessage message, JSONObject jso) throws JSONException {
        boolean bodyFound = false;
        if (!jso.isNull("full_text")) {
            message.setBody(jso.getString("full_text"));
            bodyFound = true;
        }
        if (!bodyFound) {
            super.setMessageBodyFromJson(message, jso);
        }
    }

    List<MbUser> getMbUsers(String userId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        int limit = 200;
        if (!TextUtils.isEmpty(userId)) {
            builder.appendQueryParameter("user_id", userId);
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        return jArrToUsers(http.getRequestAsArray(builder.build().toString()), apiRoutine, url);
    }

}
