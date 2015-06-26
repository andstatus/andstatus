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
import android.text.TextUtils;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.ConnectionPumpio.ConnectionAndUrl;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pump.io specific
 * @author yvolk@yurivolkov.com
 */
class ActivitySender {
    private static final String TAG = ActivitySender.class.getSimpleName();
    ConnectionPumpio connection;
    String objectId = "";
    String inReplyToId = "";
    String recipientId = "";
    String content = "";
    Uri mMediaUri = null;
    
    static ActivitySender fromId(ConnectionPumpio connection, String objectId) {
        ActivitySender sender = new ActivitySender();
        sender.connection = connection;
        sender.objectId = objectId;
        return sender;
    }
    
    static ActivitySender fromContent(ConnectionPumpio connection, String content) {
        ActivitySender sender = new ActivitySender();
        sender.connection = connection;
        sender.content = content;
        return sender;
    }

    ActivitySender setInReplyTo(String inReplyToId) {
        this.inReplyToId = inReplyToId;
        return this;
    }
    
    ActivitySender setRecipient(String recipientId) {
        this.recipientId = recipientId;
        return this;
    }

    ActivitySender setMediaUri(Uri mediaUri) {
        mMediaUri = mediaUri;
        return this;
    }
    
    MbMessage sendMessage(String verb) throws ConnectionException {
        return connection.messageFromJson(sendMe(verb));
    }

    MbUser sendUser(String verb) throws ConnectionException {
        return connection.userFromJsonActivity(sendMe(verb));
    }

    JSONObject sendMe(String verb) throws ConnectionException {
        JSONObject jso = null;
        try {
            JSONObject activity = newActivityOfThisAccount(verb);
            JSONObject obj = UriUtils.isEmpty(mMediaUri) ? newTextObject(activity) : uploadMedia();
            if (!TextUtils.isEmpty(inReplyToId)) {
                JSONObject inReplyToObject = new JSONObject();
                inReplyToObject.put("id", inReplyToId);
                inReplyToObject.put("objectType", connection.oidToObjectType(inReplyToId));
                obj.put("inReplyTo", inReplyToObject);
            }
            activity.put("object", obj);

            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.POST_MESSAGE, connection.data.getAccountUserOid());
            jso = conu.httpConnection.postRequest(conu.url, activity);
            if (jso != null) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, "verb '" + verb + "' object id='" + objectId + "' " + jso.toString(2));
                }
                if ("post".equals(verb) && !TextUtils.isEmpty(objectId)) {
                    activity.put("verb", "update");
                    jso = conu.httpConnection.postRequest(conu.url, activity);
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error '" + verb + "' object id='" + objectId + "'", e, jso);
        }
        return jso;
    }

    private JSONObject newActivityOfThisAccount(String verb) throws JSONException {
        JSONObject activity = new JSONObject();
        activity.put("objectType", "activity");
        activity.put("verb", verb);

        JSONObject generator = new JSONObject();
        generator.put("id", ConnectionPumpio.APPLICATION_ID);
        generator.put("displayName", HttpConnection.USER_AGENT);
        generator.put("objectType", "application");
        activity.put("generator", generator);

        addRecipient(activity);
        
        JSONObject author = new JSONObject();
        author.put("id", connection.data.getAccountUserOid());
        author.put("objectType", "person");

        activity.put("actor", author);
        return activity;
    }

    private void addRecipient(JSONObject activity) throws JSONException {
        boolean skip = false;
        JSONObject recipient = new JSONObject();
        if (!TextUtils.isEmpty(recipientId)) {
            recipient.put("id", recipientId);
            recipient.put("objectType", connection.oidToObjectType(recipientId));
        } else if (!TextUtils.isEmpty(inReplyToId)) {
            skip = true;
        } else {
            recipient.put("id", "http://activityschema.org/collection/public");
            recipient.put("objectType", "collection");
        }
        if (!skip) {
            JSONArray to = new JSONArray();
            to.put(recipient);
            activity.put("to", to);
        }
    }

    /** See as a working example of uploading image here: 
     *  org.macno.puma.provider.Pumpio.postImage(String, String, boolean, Location, String, byte[])
     *  We simplified it a bit...
     */
    private JSONObject uploadMedia() throws ConnectionException {
        JSONObject obj1 = null;
        try {
            JSONObject formParams = new JSONObject();
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mMediaUri.toString());
            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.POST_WITH_MEDIA, connection.data.getAccountUserOid());
            obj1 = conu.httpConnection.postRequest(conu.url, formParams);
            if (obj1 != null) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, "uploaded '" + mMediaUri.toString() + "' " + obj1.toString(2));
                }
                objectId = obj1.optString("id");
                obj1.put("content", content);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error uploading '" + mMediaUri.toString() + "'", e, obj1);
        }
        return obj1;
    }

    private JSONObject newTextObject(JSONObject activity) throws JSONException {
        JSONObject obj = new JSONObject();
        if (TextUtils.isEmpty(objectId)) {
            if (TextUtils.isEmpty(content)) {
                throw new IllegalArgumentException("Nothing to send");
            }
            obj.put("content", content);
            PumpioObjectType objectType = TextUtils.isEmpty(inReplyToId) ? PumpioObjectType.NOTE : PumpioObjectType.COMMENT;
            obj.put("objectType", objectType.id());
            obj.put("author", activity.getJSONObject("actor"));
        } else {
            obj.put("id", objectId);
            obj.put("objectType", connection.oidToObjectType(objectId));
        }
        return obj;
    }
}