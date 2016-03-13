/**
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
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Specific implementation of the Twitter API in GNU Social
 * @author yvolk@yurivolkov.com
 */
public class ConnectionTwitterGnuSocial extends ConnectionTwitter1p0 {

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case GET_CONFIG:
                url = "statusnet/config" + EXTENSION;
                break;
            case GET_OPEN_INSTANCES:
                url = "http://gstools.org/api/get_open_instances";
                break;
            case PUBLIC_TIMELINE:
                url = "statuses/public_timeline" + EXTENSION;
                break;
            case SEARCH_MESSAGES:
                url = "search" + EXTENSION;
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
    public List<String> getIdsOfUsersFollowedBy(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
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
    public List<String> getIdsOfUsersFollowing(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FOLLOWERS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
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
    public MbMessage updateStatus(String message, String inReplyToId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", message);
            
            // This parameter was removed from Twitter API, but it still is in GNUsocial
            formParams.put("source", HttpConnection.USER_AGENT);
            
            if ( !TextUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_status_id", inReplyToId);
            }
            if (!UriUtils.isEmpty(mediaUri)) {
                formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "media");
                formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_MESSAGE, formParams);
        return messageFromJson(jso);
    }
    
    @Override
    public MbConfig getConfig() throws ConnectionException {
        JSONObject result = http.getRequest(getApiPath(ApiRoutineEnum.GET_CONFIG));
        MbConfig config = MbConfig.getEmpty();
        if (result != null) {
            JSONObject site = result.optJSONObject("site");
            if (site != null) {
                int textLimit = site.optInt("textlimit");
                int uploadLimit = 0;
                JSONObject attachments = site.optJSONObject("attachments");
                if (attachments != null && site.optBoolean("uploads")) {
                    uploadLimit = site.optInt("file_quota");
                }
                config = MbConfig.fromTextLimit(textLimit, uploadLimit);
                // "shorturllength" is not used
            }
        }
        return config;
    }

    private static final String HTML_BODY_FIELD_NAME = "statusnet_html";
    @Override
    protected void setMessageBodyFromJson(MbMessage message, JSONObject jso) throws JSONException {
        boolean bodyFound = false;
        if (MyContextHolder.get().persistentOrigins().isHtmlContentAllowed(data.getOriginId())
                && jso.has(HTML_BODY_FIELD_NAME)) {
            message.setBody(jso.getString(HTML_BODY_FIELD_NAME));
            bodyFound = true;
        }
        if (!bodyFound) {
            super.setMessageBodyFromJson(message, jso);
        }
    }

    private static final String ATTACHMENTS_FIELD_NAME = "attachments";
    @Override
    protected MbMessage messageFromJson(JSONObject jso) throws ConnectionException {
        final String method = "messageFromJson";
        MbMessage message = super.messageFromJson(jso);
        if (jso != null && jso.has(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    JSONObject attachment = (JSONObject) jArr.get(ind);
                    URL url = UrlUtils.fromJson(attachment, "url");
                    if (url == null) {
                        url = UrlUtils.fromJson(attachment, "thumb_url");
                    }
                    MbAttachment mbAttachment =  MbAttachment.fromUrlAndContentType(url, MyContentType.fromUrl(url, attachment.optString("mimetype")));
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
    protected MbUser userFromJson(JSONObject jso) throws ConnectionException {
        MbUser mbUser = super.userFromJson(jso);
        if (jso != null) {
            mbUser.setProfileUrl(jso.optString("statusnet_profile_url"));
        }
        return mbUser;
    }
    
    @Override
    public List<MbOrigin> getOpenInstances() throws ConnectionException {
        JSONObject result = http.getUnauthenticatedRequest(getApiPath(ApiRoutineEnum.GET_OPEN_INSTANCES));
        List<MbOrigin> origins = new ArrayList<MbOrigin>();
        StringBuilder logMessage = new StringBuilder(ApiRoutineEnum.GET_OPEN_INSTANCES.toString());
        boolean error = false;
        if (result == null) {
            I18n.appendWithSpace(logMessage, "Response is null JSON");
            error = true;
        }
        if (!error && !result.optString("status").equals("OK")) {
            I18n.appendWithSpace(logMessage, "gtools service returned the error: '" + result.optString("error") + "'");
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
                        origins.add(new MbOrigin(instance.optString("instance_name"),
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
