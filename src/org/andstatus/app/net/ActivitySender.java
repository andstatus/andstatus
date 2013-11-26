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

package org.andstatus.app.net;

import android.text.TextUtils;

import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.net.ConnectionPumpio.ConnectionAndUrl;
import org.andstatus.app.util.MyLog;
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
            if (!TextUtils.isEmpty(inReplyToId)) {
                JSONObject inReplyToObject = new JSONObject();
                inReplyToObject.put("id", inReplyToId);
                inReplyToObject.put("objectType", connection.oidToObjectType(inReplyToId));
                obj.put("inReplyTo", inReplyToObject);
            }
            activity.put("object", obj);

            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.STATUSES_UPDATE, connection.data.accountUserOid);
            jso = conu.httpConnection.postRequest(conu.url, activity);
            if (jso != null && MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
                MyLog.v(this, "verb '" + verb + "' object id='" + objectId + "' " + jso.toString(2));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, e, jso, "Error '" + verb + "' object id='" + objectId + "'");
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

        JSONObject recipient = new JSONObject();
        if (TextUtils.isEmpty(recipientId)) {
            recipient.put("id", "http://activityschema.org/collection/public");
            recipient.put("objectType", "collection");
        } else {
            recipient.put("id", recipientId);
            recipient.put("objectType", connection.oidToObjectType(recipientId));
        }
        JSONArray to = new JSONArray();
        to.put(recipient);
        activity.put("to", to);
        
        JSONObject author = new JSONObject();
        author.put("id", connection.data.accountUserOid);
        author.put("objectType", "person");

        activity.put("actor", author);
        return activity;
    }
}