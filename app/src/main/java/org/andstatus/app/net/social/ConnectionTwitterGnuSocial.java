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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.origin.OriginConfig;
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
public class ConnectionTwitterGnuSocial extends ConnectionTwitterLike {
    private static final String ATTACHMENTS_FIELD_NAME = "attachments";
    private static final String CONVERSATION_ID_FIELD_NAME = "statusnet_conversation_id";
    private static final String HTML_BODY_FIELD_NAME = "statusnet_html";

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
        if (TextUtils.isEmpty(url)) {
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
    public AActivity updateNote(String message, String noteOid, String inReplyToOid, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", message);
            
            // This parameter was removed from Twitter API, but it still is in GNUsocial
            formParams.put("source", HttpConnection.USER_AGENT);
            
            if ( !TextUtils.isEmpty(inReplyToOid)) {
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
        if (TextUtils.isEmpty(conversationOid)) {
            return new ArrayList<>();
        } else {
            String url = getApiPathWithMessageId(ApiRoutineEnum.GET_CONVERSATION, conversationOid);
            JSONArray jArr = http.getRequestAsArray(url);
            return jArrToTimeline(jArr, ApiRoutineEnum.GET_CONVERSATION, url);
        }
    }

    @Override
    protected void setMessageBodyFromJson(Note message, JSONObject jso) throws JSONException {
        boolean bodyFound = false;
        if (data.getOrigin().isHtmlContentAllowed() && !jso.isNull(HTML_BODY_FIELD_NAME)) {
            message.setBody(jso.getString(HTML_BODY_FIELD_NAME));
            bodyFound = true;
        }
        if (!bodyFound) {
            super.setMessageBodyFromJson(message, jso);
        }
    }

    @Override
    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        final String method = "activityFromJson2";
        AActivity activity = super.activityFromJson2(jso);
        Note message = activity.getMessage();
        message.url = jso.optString("external_url");
        message.setConversationOid(jso.optString(CONVERSATION_ID_FIELD_NAME));
        if (!jso.isNull(ATTACHMENTS_FIELD_NAME)) {
            try {
                JSONArray jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME);
                for (int ind = 0; ind < jArr.length(); ind++) {
                    JSONObject attachment = (JSONObject) jArr.get(ind);
                    URL url = UrlUtils.fromJson(attachment, "url");
                    if (url == null) {
                        url = UrlUtils.fromJson(attachment, "thumb_url");
                    }
                    Attachment mbAttachment =  Attachment.fromUrlAndContentType(url, MyContentType.fromUrl(url, attachment.optString("mimetype")));
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
        return activity;
    }

    @Override
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        Actor actor = super.actorFromJson(jso);
        if (jso != null) {
            actor.setProfileUrl(jso.optString("statusnet_profile_url"));
        }
        return actor;
    }
    
    @Override
    public List<Server> getOpenInstances() throws ConnectionException {
        JSONObject result = http.getUnauthenticatedRequest(getApiPath(ApiRoutineEnum.GET_OPEN_INSTANCES));
        List<Server> origins = new ArrayList<>();
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
