/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.activitypub;

import android.net.Uri;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import io.vavr.control.Try;

import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.CONTENT_PROPERTY;
import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.FULL_IMAGE_OBJECT;
import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.NAME_PROPERTY;
import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.SUMMARY_PROPERTY;

/**
 * ActivityPub specific
 * @author yvolk@yurivolkov.com
 */
class ActivitySender {
    final ConnectionActivityPub connection;
    final String objectId;
    final Audience audience;
    String inReplyToId = "";
    String name = "";
    String summary = "";
    String content = "";
    Uri mMediaUri = null;

    ActivitySender(ConnectionActivityPub connection, String objectId, Audience audience) {
        this.connection = connection;
        this.objectId = objectId;
        this.audience = audience;
    }

    static ActivitySender fromId(ConnectionActivityPub connection, String objectId) {
        return new ActivitySender(connection, objectId, Audience.EMPTY);
    }
    
    static ActivitySender fromContent(ConnectionActivityPub connection, Note note) {
        ActivitySender sender = new ActivitySender(connection, note.oid, note.audience());
        sender.name = note.getName();
        sender.summary = note.getSummary();
        sender.content = note.getContentToPost();
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
    
    AActivity send(ActivityType activityType) throws ConnectionException {
        return sendInternal(activityType)
            .map(HttpReadResult::getJsonObject)
            .map(connection::activityFromJson).getOrElseThrow(ConnectionException::of);
    }

    private Try<HttpReadResult> sendInternal(ActivityType activityTypeIn) throws ConnectionException {
        ActivityType activityType = isExisting()
                ? (activityTypeIn.equals(ActivityType.CREATE) ? ActivityType.UPDATE : activityTypeIn)
                : ActivityType.CREATE;
        String msgLog = "Activity '" + activityType + "'" + (isExisting() ? " objectId:'" + objectId + "'" : "");
        Try<HttpReadResult> activityResponse = null;
        JSONObject activity = null;
        try {
            activity = buildActivityToSend(activityType);
            ConnectionAndUrl conu = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPDATE_NOTE,
                    connection.getData().getAccountActor());
            activityResponse = connection.postRequest(conu.uri, activity);
            if (activityResponse.map(HttpReadResult::getJsonObject).getOrElseThrow(ConnectionException::of) == null) {
                return Try.failure(ConnectionException.hardConnectionException(msgLog + " returned no data", null));
            }
            activityResponse.filter(r -> MyLog.isVerboseEnabled()).map(HttpReadResult::getJsonObject)
                .map(jso -> msgLog + " " + jso.toString(2))
                .onSuccess(message -> MyLog.v(this, message));
            if (activityResponse.map(HttpReadResult::getJsonObject).map(jso -> contentNotPosted(activityType, jso))
                    .getOrElse(true)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update");
                }
                activity.put("type", ActivityType.UPDATE.activityPubValue);
                activityResponse = connection.postRequest(conu.uri, activity);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, msgLog, e, activity);
        }
        return activityResponse;
    }

    private JSONObject buildActivityToSend(ActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = newActivityOfThisAccount(activityType);
        JSONObject obj = buildObject(activity);
        if (UriUtils.nonEmpty(mMediaUri)) {
            ApObjectType objectType = ApObjectType.fromJson(obj);
            if (isExisting()
                    && (!ApObjectType.IMAGE.equals(objectType) || !ApObjectType.VIDEO.equals(objectType))
                    ) {
                throw ConnectionException.hardConnectionException(
                        "Cannot update '" + objectType + "' to " + ApObjectType.IMAGE, null);
            }
            JSONObject mediaObject = uploadMedia();
            ApObjectType mediaObjectType = ApObjectType.fromJson(mediaObject);
            if (isExisting() && mediaObjectType.equals(objectType)) {
                if (objectType == ApObjectType.VIDEO) {
                    JSONObject video = mediaObject.optJSONObject(ConnectionActivityPub.VIDEO_OBJECT);
                    if (video != null) {
                        // Replace the video in the existing object
                        obj.put(ConnectionActivityPub.VIDEO_OBJECT, video);
                    }
                } else {
                    JSONObject image = mediaObject.optJSONObject(ConnectionActivityPub.IMAGE_OBJECT);
                    if (image != null) {
                        // Replace an image in the existing object
                        obj.put(ConnectionActivityPub.IMAGE_OBJECT, image);
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
        if (StringUtils.nonEmpty(summary)) {
            obj.put(SUMMARY_PROPERTY, summary);
        }
        if (StringUtils.nonEmpty(content)) {
            obj.put(CONTENT_PROPERTY, content);
        }
        if (!StringUtils.isEmpty(inReplyToId)) {
            obj.put("inReplyTo", inReplyToId);
        }
        activity.put("object", obj);
        return activity;
    }

    private boolean contentNotPosted(ActivityType activityType, JSONObject jsActivity) {
        JSONObject objPosted = jsActivity.optJSONObject("object");
        return ActivityType.CREATE.equals(activityType) && objPosted != null &&
            (StringUtils.nonEmpty(content) && StringUtils.isEmpty(objPosted.optString(CONTENT_PROPERTY))
                || StringUtils.nonEmpty(name) && StringUtils.isEmpty(objPosted.optString(NAME_PROPERTY))
                || StringUtils.nonEmpty(summary) && StringUtils.isEmpty(objPosted.optString(SUMMARY_PROPERTY))
            );
    }

    private JSONObject newActivityOfThisAccount(ActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = new JSONObject();
        activity.put("@context", "https://www.w3.org/ns/activitystreams");
        activity.put("type", activityType.activityPubValue);

        setAudience(activity, activityType);

        activity.put("actor", connection.getData().getAccountActor().oid);
        return activity;
    }

    private void setAudience(JSONObject activity, ActivityType activityType) throws JSONException {
        audience.getActors().forEach(actor -> addToAudience(activity, "to", actor));
        if (audience.isEmpty()) {
            // "clients must be aware that the server will only forward new Activities
            //   to addressees in the to, bto, cc, bcc, and audience fields"
            addToAudience(activity, "to", Actor.PUBLIC);
        }
    }

    private void addToAudience(JSONObject activity, String recipientField, Actor actor) {
        String recipientId = actor.equals(Actor.PUBLIC)
                ? ConnectionActivityPub.PUBLIC_COLLECTION_ID
                : actor.getBestUri();
        if (StringUtils.isEmpty(recipientId)) return;
        try {
            JSONArray field = activity.has(recipientField) ? activity.getJSONArray(recipientField) : new JSONArray();
            field.put(recipientId);
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
        JSONObject formParams = new JSONObject();
        try {
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mMediaUri.toString());
            ConnectionAndUrl conu = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPDATE_NOTE_WITH_MEDIA,
                    connection.getData().getAccountActor());
            Try<HttpReadResult> result = connection.postRequest(conu.uri, formParams);
            if (result.map(HttpReadResult::getJsonObject).getOrElseThrow(ConnectionException::of) == null) {
                result = Try.failure(new ConnectionException(
                        "Error uploading '" + mMediaUri + "': null response returned"));
            }
            result.filter(r -> MyLog.isVerboseEnabled()).map(HttpReadResult::getJsonObject)
                .map(jso -> jso.toString(2))
                .onSuccess(message -> MyLog.v(this, "uploaded '" + mMediaUri + "' " + message));
            return result.map(HttpReadResult::getJsonObject).getOrElseThrow(ConnectionException::of);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this,
                    "Error uploading '" + mMediaUri + "'", e, formParams);
        }
    }

    private JSONObject buildObject(JSONObject activity) throws JSONException {
        JSONObject obj = new JSONObject();
        if (isExisting()) {
            obj.put("id", objectId);
            obj.put("type", ApObjectType.NOTE.id());
        } else {
            if (StringUtils.isEmpty(name) && StringUtils.isEmpty(content) && UriUtils.isEmpty(mMediaUri)) {
                throw new IllegalArgumentException("Nothing to send");
            }
            obj.put("attributedTo", connection.getData().getAccountActor().oid);
            obj.put("type", ApObjectType.NOTE.id());
        }
        obj.put("to", activity.getJSONArray("to"));
        return obj;
    }

    private boolean isExisting() {
        return UriUtils.isRealOid(objectId);
    }
}