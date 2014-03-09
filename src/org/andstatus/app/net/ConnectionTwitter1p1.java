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

package org.andstatus.app.net;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1
 *
 */
public class ConnectionTwitter1p1 extends ConnectionTwitter {

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
}
