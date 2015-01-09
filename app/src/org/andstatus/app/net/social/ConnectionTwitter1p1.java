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
import android.text.TextUtils;

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.List;

/**
 * Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1
 *
 */
public class ConnectionTwitter1p1 extends ConnectionTwitter {

    @Override
    public MbMessage updateStatus(String message, String inReplyToId, Uri mediaUri)
            throws ConnectionException {
        if (UriUtils.isEmpty(mediaUri)) {
            return super.updateStatus(message, inReplyToId, mediaUri);
        }
        return updateWithMedia(message, inReplyToId, mediaUri);
    }

    private MbMessage updateWithMedia(String message, String inReplyToId, Uri mediaUri) throws ConnectionException {
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
        return messageFromJson(jso);
    }

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "application/rate_limit_status" + EXTENSION;
                break;
            case CREATE_FAVORITE:
                url = "favorites/create" + EXTENSION;
                break;
            case GET_FRIENDS:
                // TODO: see https://dev.twitter.com/docs/api/1.1/get/friends/list
                //   url will be: "friends/list" + EXTENSION
                url = ""; 
                break;
            case DESTROY_FAVORITE:
                url = "favorites/destroy" + EXTENSION;
                break;
            case POST_WITH_MEDIA:
                url = "statuses/update_with_media" + EXTENSION;
                break;
            case SEARCH_MESSAGES:
                // https://dev.twitter.com/docs/api/1.1/get/search/tweets
                url = "search/tweets" + EXTENSION;
                break;
            case STATUSES_MENTIONS_TIMELINE:
                // https://dev.twitter.com/docs/api/1.1/get/statuses/mentions_timeline
                url = "statuses/mentions_timeline" + EXTENSION;
                break;
            default:
                url = "";
                break;
        }
        if (TextUtils.isEmpty(url)) {
            url = super.getApiPath1(routine);
        } else {
            url = http.data.basicPath + "/" + url;
        }
        return url;
    }

    @Override
    public MbMessage createFavorite(String statusId) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", statusId);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.CREATE_FAVORITE, out);
        return messageFromJson(jso);
    }

    @Override
    public MbMessage destroyFavorite(String statusId) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("id", statusId);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.DESTROY_FAVORITE, out);
        return messageFromJson(jso);
    }

    @Override
    public List<MbTimelineItem> search(String searchQuery, int limit)
            throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_MESSAGES;
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (fixedDownloadLimitForApiRoutine(limit, apiRoutine) > 0) {
            builder.appendQueryParameter("count", String.valueOf(fixedDownloadLimitForApiRoutine(limit, apiRoutine)));
        }
        if (!TextUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        JSONArray jArr = getRequestArrayInObject(builder.build().toString(), "statuses");
        return jArrToTimeline(jArr, apiRoutine, url);
    }
    
    private static final String ATTACHMENTS_FIELD_NAME = "media";
    @Override
    protected MbMessage messageFromJson(JSONObject jso) throws ConnectionException {
        final String method = "messageFromJson";
        MbMessage message = super.messageFromJson(jso);
        // See https://dev.twitter.com/docs/entities
        JSONObject entities = jso.optJSONObject("entities");
        if (entities != null && entities.has(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = entities.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    JSONObject attachment = (JSONObject) jArr.get(ind);
                    URL url = UrlUtils.fromJson(attachment, "media_url_https");
                    if (url == null) {
                        url = UrlUtils.fromJson(attachment, "media_url_http");
                    }
                    MbAttachment mbAttachment =  MbAttachment.fromUrlAndContentType(url, MyContentType.IMAGE);
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
    
}
