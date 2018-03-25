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

package org.andstatus.app.net.social.pumpio;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio.ConnectionAndUrl;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static org.andstatus.app.net.social.pumpio.ConnectionPumpio.CONTENT_PROPERTY;
import static org.andstatus.app.net.social.pumpio.ConnectionPumpio.NAME_PROPERTY;

/**
 * Pump.io specific
 * @author yvolk@yurivolkov.com
 */
class ActivitySender {
    static final String PUBLIC_COLLECTION_ID = "http://activityschema.org/collection/public";
    ConnectionPumpio connection;
    String objectId = "";
    String inReplyToId = "";
    String recipientId = "";
    String name = "";
    String content = "";
    Uri mMediaUri = null;
    
    static ActivitySender fromId(ConnectionPumpio connection, String objectId) {
        ActivitySender sender = new ActivitySender();
        sender.connection = connection;
        sender.objectId = objectId;
        return sender;
    }
    
    static ActivitySender fromContent(ConnectionPumpio connection, String objectId, String name, String content) {
        ActivitySender sender = new ActivitySender();
        sender.connection = connection;
        sender.objectId = objectId;
        sender.name = name;
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
    
    AActivity sendNote(PActivityType activityType) throws ConnectionException {
        return connection.activityFromJson(sendMe(activityType));
    }

    AActivity sendActor(PActivityType activityType) throws ConnectionException {
        return connection.activityFromJson(sendMe(activityType));
    }

    JSONObject sendMe(PActivityType activityTypeIn) throws ConnectionException {
        PActivityType activityType = isExisting() ?
                (activityTypeIn.equals(PActivityType.POST) ? PActivityType.UPDATE : activityTypeIn) :
                PActivityType.POST;
        String msgLog = "Activity '" + activityType + "'" +
                (isExisting() ? " objectId:'" + objectId + "'" : "");
        JSONObject jso = null;
        try {
            JSONObject activity = newActivityOfThisAccount(activityType);
            JSONObject obj = buildObject(activity);
            if (!UriUtils.isEmpty(mMediaUri)) {
                PObjectType objectType = PObjectType.fromJson(obj);
                if (isExisting() && !PObjectType.IMAGE.equals(objectType)) {
                    throw ConnectionException.hardConnectionException(
                            "Cannot update '" + objectType + "' to " + PObjectType.IMAGE, null);
                }
                JSONObject mediaObject = uploadMedia();
                if (isExisting()) {
                    JSONObject image = mediaObject.optJSONObject("image");
                    if (image != null) {
                        // Replace an image in the existing object
                        obj.put("image", image);
                        image = mediaObject.optJSONObject("fullImage");
                        if (image != null) {
                            obj.put("fullImage", image);
                        }
                    }
                } else {
                    obj = mediaObject;
                }
            }
            if (StringUtils.nonEmpty(name)) {
                obj.put(NAME_PROPERTY, name);
            }
            if (StringUtils.nonEmpty(content)) {
                obj.put(CONTENT_PROPERTY, content);
            }
            if (!TextUtils.isEmpty(inReplyToId)) {
                JSONObject inReplyToObject = new JSONObject();
                inReplyToObject.put("id", inReplyToId);
                inReplyToObject.put("objectType", connection.oidToObjectType(inReplyToId));
                obj.put("inReplyTo", inReplyToObject);
            }
            activity.put("object", obj);

            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.UPDATE_NOTE,
                    connection.getData().getAccountActor().oid);
            jso = connection.postRequest(conu.url, activity);
            if (jso == null) {
                throw ConnectionException.hardConnectionException(msgLog + " returned no data", null);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, msgLog + " " + jso.toString(2));
            }
            JSONObject objPosted = jso.optJSONObject("object");
            if (PActivityType.POST.equals(activityType)
                    && objPosted != null
                    && (
                       StringUtils.nonEmpty(content) && TextUtils.isEmpty(objPosted.optString(CONTENT_PROPERTY))
                    || StringUtils.nonEmpty(name) && TextUtils.isEmpty(objPosted.optString(NAME_PROPERTY))
                    )
                    ) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update");
                }
                activity.put("verb", PActivityType.UPDATE.code);
                jso = connection.postRequest(conu.url, activity);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, msgLog, e, jso);
        }
        return jso;
    }

    private JSONObject newActivityOfThisAccount(PActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = new JSONObject();
        activity.put("objectType", "activity");
        activity.put("verb", activityType.code);

        JSONObject generator = new JSONObject();
        generator.put("id", ConnectionPumpio.APPLICATION_ID);
        generator.put("displayName", HttpConnection.USER_AGENT);
        generator.put("objectType", PObjectType.APPLICATION.id());
        activity.put("generator", generator);

        addMainRecipient(activity, activityType);

        JSONObject author = new JSONObject();
        author.put("id", connection.getData().getAccountActor().oid);
        author.put("objectType", "person");

        activity.put("actor", author);
        return activity;
    }

    private String getFollowersCollectionId() throws ConnectionException {
        ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.GET_FOLLOWERS,
                connection.getData().getAccountActor().oid);
        return conu.httpConnection.pathToUrlString(conu.url);
    }

    private void addMainRecipient(JSONObject activity, PActivityType activityType) throws JSONException {
        String id = recipientId;
        if (TextUtils.isEmpty(id) && TextUtils.isEmpty(inReplyToId) && activityType.equals(PActivityType.POST)) {
            id = PUBLIC_COLLECTION_ID;
        }
        addRecipient(activity, "to", id);
    }

    private void addRecipient(JSONObject activity, String recipientField, String recipientId) throws JSONException {
        if (!TextUtils.isEmpty(recipientId)) {
            JSONObject recipient = new JSONObject();
            recipient.put("id", recipientId);
            recipient.put("objectType", connection.oidToObjectType(recipientId));

            JSONArray field = activity.has(recipientField) ? activity.getJSONArray(recipientField) :
                    new JSONArray();
            field.put(recipient);
            activity.put(recipientField, field);
        }
    }

    /** See as a working example of uploading image here: 
     *  org.macno.puma.provider.Pumpio.postImage(String, String, boolean, Location, String, byte[])
     *  We simplified it a bit...
     */
    @NonNull
    private JSONObject uploadMedia() throws ConnectionException {
        JSONObject obj1 = null;
        try {
            JSONObject formParams = new JSONObject();
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mMediaUri.toString());
            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.UPDATE_NOTE_WITH_MEDIA,
                    connection.getData().getAccountActor().oid);
            obj1 = connection.postRequest(conu.url, formParams);
            if (obj1 == null) {
                throw new ConnectionException("Error uploading '" + mMediaUri.toString() + "': null response returned");
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "uploaded '" + mMediaUri.toString() + "' " + obj1.toString(2));
            }
            return obj1;
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error uploading '" + mMediaUri.toString() + "'", e, obj1);
        }
    }

    private JSONObject buildObject(JSONObject activity) throws JSONException {
        JSONObject obj = new JSONObject();
        if (isExisting()) {
            obj.put("id", objectId);
            obj.put("objectType", connection.oidToObjectType(objectId));
        } else {
            if (TextUtils.isEmpty(name) && TextUtils.isEmpty(content) && UriUtils.isEmpty(mMediaUri)) {
                throw new IllegalArgumentException("Nothing to send");
            }
            obj.put("author", activity.getJSONObject("actor"));
            PObjectType objectType = TextUtils.isEmpty(inReplyToId) ? PObjectType.NOTE : PObjectType.COMMENT;
            obj.put("objectType", objectType.id());
        }
        return obj;
    }

    private boolean isExisting() {
        return UriUtils.isRealOid(objectId);
    }
}