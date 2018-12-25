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
import androidx.annotation.NonNull;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio.ConnectionAndUrl;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static org.andstatus.app.net.social.pumpio.ConnectionPumpio.CONTENT_PROPERTY;
import static org.andstatus.app.net.social.pumpio.ConnectionPumpio.FULL_IMAGE_OBJECT;
import static org.andstatus.app.net.social.pumpio.ConnectionPumpio.NAME_PROPERTY;

/**
 * Pump.io specific
 * @author yvolk@yurivolkov.com
 */
class ActivitySender {
    final ConnectionPumpio connection;
    final String objectId;
    final Audience audience;
    String inReplyToId = "";
    String name = "";
    String content = "";
    Uri mMediaUri = null;

    ActivitySender(ConnectionPumpio connection, String objectId, Audience audience) {
        this.connection = connection;
        this.objectId = objectId;
        this.audience = audience;
    }

    static ActivitySender fromId(ConnectionPumpio connection, String objectId) {
        return new ActivitySender(connection, objectId, Audience.EMPTY);
    }
    
    static ActivitySender fromContent(ConnectionPumpio connection, String objectId, Audience audience, String name,
                                      String content) {
        ActivitySender sender = new ActivitySender(connection, objectId, audience);
        sender.name = name;
        sender.content = content;
        return sender;
    }

    ActivitySender setInReplyTo(String inReplyToId) {
        this.inReplyToId = inReplyToId;
        return this;
    }
    
    ActivitySender setMediaUri(Uri mediaUri) {
        mMediaUri = mediaUri;
        return this;
    }
    
    AActivity send(PActivityType activityType) throws ConnectionException {
        return connection.activityFromJson(sendInternal(activityType));
    }

    private JSONObject sendInternal(PActivityType activityTypeIn) throws ConnectionException {
        PActivityType activityType = isExisting()
                ? (activityTypeIn.equals(PActivityType.POST) ? PActivityType.UPDATE : activityTypeIn)
                : PActivityType.POST;
        String msgLog = "Activity '" + activityType + "'" + (isExisting() ? " objectId:'" + objectId + "'" : "");
        JSONObject activityResponse = null;
        JSONObject activity = null;
        try {
            activity = buildActivityToSend(activityType);
            ConnectionAndUrl conu = connection.getConnectionAndUrl(ApiRoutineEnum.UPDATE_NOTE,
                    connection.getData().getAccountActor().oid);
            activityResponse = connection.postRequest(conu.url, activity);
            if (activityResponse == null) {
                throw ConnectionException.hardConnectionException(msgLog + " returned no data", null);
            }
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, msgLog + " " + activityResponse.toString(2));
            }
            if (contentNotPosted(activityType, activityResponse)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update");
                }
                activity.put("verb", PActivityType.UPDATE.code);
                activityResponse = connection.postRequest(conu.url, activity);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, msgLog, e,
                    activityResponse == null ? activity : activityResponse);
        }
        return activityResponse;
    }

    private JSONObject buildActivityToSend(PActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = newActivityOfThisAccount(activityType);
        JSONObject obj = buildObject(activity);
        if (UriUtils.nonEmpty(mMediaUri)) {
            PObjectType objectType = PObjectType.fromJson(obj);
            if (isExisting()
                    && (!PObjectType.IMAGE.equals(objectType) || !PObjectType.VIDEO.equals(objectType))
                    ) {
                throw ConnectionException.hardConnectionException(
                        "Cannot update '" + objectType + "' to " + PObjectType.IMAGE, null);
            }
            JSONObject mediaObject = uploadMedia();
            PObjectType mediaObjectType = PObjectType.fromJson(mediaObject);
            if (isExisting() && mediaObjectType.equals(objectType)) {
                if (objectType == PObjectType.VIDEO) {
                    JSONObject video = mediaObject.optJSONObject(ConnectionPumpio.VIDEO_OBJECT);
                    if (video != null) {
                        // Replace the video in the existing object
                        obj.put(ConnectionPumpio.VIDEO_OBJECT, video);
                    }
                } else {
                    JSONObject image = mediaObject.optJSONObject(ConnectionPumpio.IMAGE_OBJECT);
                    if (image != null) {
                        // Replace an image in the existing object
                        obj.put(ConnectionPumpio.IMAGE_OBJECT, image);
                        JSONObject fullImage = mediaObject.optJSONObject(FULL_IMAGE_OBJECT);
                        if (fullImage != null) {
                            obj.put(FULL_IMAGE_OBJECT, fullImage);
                        }
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
        if (!StringUtils.isEmpty(inReplyToId)) {
            JSONObject inReplyToObject = new JSONObject();
            inReplyToObject.put("id", inReplyToId);
            inReplyToObject.put("objectType", connection.oidToObjectType(inReplyToId));
            obj.put("inReplyTo", inReplyToObject);
        }
        activity.put("object", obj);
        return activity;
    }

    private boolean contentNotPosted(PActivityType activityType, JSONObject jsActivity) {
        JSONObject objPosted = jsActivity.optJSONObject("object");
        return PActivityType.POST.equals(activityType) && objPosted != null
                && (StringUtils.nonEmpty(content) && StringUtils.isEmpty(objPosted.optString(CONTENT_PROPERTY))
                    || StringUtils.nonEmpty(name) && StringUtils.isEmpty(objPosted.optString(NAME_PROPERTY)));
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

        setAudience(activity, activityType);

        JSONObject author = new JSONObject();
        author.put("id", connection.getData().getAccountActor().oid);
        author.put("objectType", "person");

        activity.put("actor", author);
        return activity;
    }

    private void setAudience(JSONObject activity, PActivityType activityType) throws JSONException {
        audience.getActors().forEach(actor -> addToAudience(activity, "to", actor));
        if (audience.isEmpty() && StringUtils.isEmpty(inReplyToId)
                && (activityType.equals(PActivityType.POST) || activityType.equals(PActivityType.UPDATE))) {
            addToAudience(activity, "to", Actor.PUBLIC);
        }
    }

    private void addToAudience(JSONObject activity, String recipientField, Actor actor) {
        String recipientId = actor.equals(Actor.PUBLIC) ? ConnectionPumpio.PUBLIC_COLLECTION_ID : actor.oid;
        if (StringUtils.isEmpty(recipientId)) return;
        JSONObject recipient = new JSONObject();
        try {
            recipient.put("id", recipientId);
            recipient.put("objectType", connection.oidToObjectType(recipientId));
            JSONArray field = activity.has(recipientField) ? activity.getJSONArray(recipientField) : new JSONArray();
            field.put(recipient);
            activity.put(recipientField, field);
        } catch (JSONException e) {
            MyLog.e(this, e);
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
            if (StringUtils.isEmpty(name) && StringUtils.isEmpty(content) && UriUtils.isEmpty(mMediaUri)) {
                throw new IllegalArgumentException("Nothing to send");
            }
            obj.put("author", activity.getJSONObject("actor"));
            PObjectType objectType = StringUtils.isEmpty(inReplyToId) ? PObjectType.NOTE : PObjectType.COMMENT;
            obj.put("objectType", objectType.id());
        }
        return obj;
    }

    private boolean isExisting() {
        return UriUtils.isRealOid(objectId);
    }
}