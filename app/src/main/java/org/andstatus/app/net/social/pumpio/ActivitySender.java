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

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.vavr.control.Try;

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
    String summary = "";
    String content = "";
    Attachments attachments = Attachments.EMPTY;

    ActivitySender(ConnectionPumpio connection, String objectId, Audience audience) {
        this.connection = connection;
        this.objectId = objectId;
        this.audience = audience;
    }

    static ActivitySender fromId(ConnectionPumpio connection, String objectId) {
        return new ActivitySender(connection, objectId, Audience.EMPTY);
    }

    static ActivitySender fromContent(ConnectionPumpio connection, Note note) {
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
    
    ActivitySender setAttachments(Attachments attachments) {
        this.attachments = attachments;
        return this;
    }
    
    Try<AActivity> send(PActivityType activityType) {
        return sendInternal(activityType)
            .flatMap(HttpReadResult::getJsonObject)
            .map(connection::activityFromJson);
    }

    private Try<HttpReadResult> sendInternal(PActivityType activityTypeIn) {
        PActivityType activityType = isExisting()
                ? (activityTypeIn.equals(PActivityType.POST) ? PActivityType.UPDATE : activityTypeIn)
                : PActivityType.POST;
        String msgLog = "Activity '" + activityType + "'" + (isExisting() ? " objectId:'" + objectId + "'" : "");
        JSONObject activity = null;
        try {
            activity = buildActivityToSend(activityType);
            JSONObject activityImm = activity;
            Try<ConnectionAndUrl> tryConu = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPDATE_NOTE, getActor());
            Try<HttpReadResult> activityResponse = tryConu
                    .flatMap(conu -> conu.execute(conu.newRequest().withPostParams(activityImm)));
            if (activityResponse.flatMap(HttpReadResult::getJsonObject).getOrElseThrow(ConnectionException::of) == null) {
                return Try.failure(ConnectionException.hardConnectionException(msgLog + " returned no data", null));
            }
            activityResponse.filter(r -> MyLog.isVerboseEnabled())
                .flatMap(HttpReadResult::getJsonObject)
                .map(jso -> msgLog + " " + jso.toString(2))
                .onSuccess(message -> MyLog.v(this, message));
            if (activityResponse
                    .flatMap(HttpReadResult::getJsonObject)
                    .map(jso -> contentNotPosted(activityType, jso))
                    .getOrElse(true)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update");
                }
                activity.put("verb", PActivityType.UPDATE.code);
                return tryConu.flatMap(conu -> conu.execute(conu.newRequest().withPostParams(activityImm)));
            }
            return activityResponse;
        } catch (JSONException e) {
            return Try.failure(ConnectionException.loggedJsonException(this, msgLog, e, activity));
        } catch (Exception e) {
            return Try.failure(e);
        }
    }

    private Actor getActor() {
        return connection.getData().getAccountActor();
    }

    private JSONObject buildActivityToSend(PActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = newActivityOfThisAccount(activityType);
        JSONObject obj = buildObject(activity);
        Attachment attachment = attachments.getFirstToUpload();
        if (attachment.nonEmpty()) {
            if (attachments.toUploadCount() > 1) {
                MyLog.w(this, "Sending only the first attachment: " + attachments);  // TODO
            }
            PObjectType objectType = PObjectType.fromJson(obj);
            if (isExisting()
                    && (!PObjectType.IMAGE.equals(objectType) || !PObjectType.VIDEO.equals(objectType))
                    ) {
                throw ConnectionException.hardConnectionException(
                        "Cannot update '" + objectType + "' to " + PObjectType.IMAGE, null);
            }
            JSONObject mediaObject = uploadMedia(attachment);
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
        if (StringUtil.nonEmpty(name)) {
            obj.put(NAME_PROPERTY, name);
        }
        if (StringUtil.nonEmpty(content)) {
            obj.put(CONTENT_PROPERTY, content);
        }
        if (!StringUtil.isEmpty(inReplyToId)) {
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
                && (StringUtil.nonEmpty(content) && StringUtil.isEmpty(JsonUtils.optString(objPosted, CONTENT_PROPERTY))
                    || StringUtil.nonEmpty(name) && StringUtil.isEmpty(JsonUtils.optString(objPosted, NAME_PROPERTY)));
    }

    private JSONObject newActivityOfThisAccount(PActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = new JSONObject();
        activity.put("objectType", "activity");
        activity.put("verb", activityType.code);

        JSONObject generator = new JSONObject();
        generator.put("id", ConnectionPumpio.APPLICATION_ID);
        generator.put("displayName", HttpConnection.USER_AGENT);
        generator.put("objectType", PObjectType.APPLICATION.id);
        activity.put("generator", generator);

        setAudience(activity, activityType);

        JSONObject author = new JSONObject();
        author.put("id", getActor().oid);
        author.put("objectType", "person");

        activity.put("actor", author);
        return activity;
    }

    private void setAudience(JSONObject activity, PActivityType activityType) throws JSONException {
        audience.getRecipients().forEach(actor -> addToAudience(activity, "to", actor));
        if (audience.noRecipients() && StringUtil.isEmpty(inReplyToId)
                && (activityType.equals(PActivityType.POST) || activityType.equals(PActivityType.UPDATE))) {
            addToAudience(activity, "to", Actor.PUBLIC);
        }
    }

    private void addToAudience(JSONObject activity, String recipientField, Actor recipient) {
        String recipientId;
        if (recipient == Actor.PUBLIC) {
            recipientId = ConnectionPumpio.PUBLIC_COLLECTION_ID;
        } else if (recipient.groupType == GroupType.FOLLOWERS) {
            recipientId = getActor().getEndpoint(ActorEndpointType.API_FOLLOWERS).orElse(Uri.EMPTY).toString();
        } else {
            recipientId = recipient.getBestUri();
        }
        if (StringUtil.isEmpty(recipientId)) return;

        JSONObject jsonRecipient = new JSONObject();
        try {
            jsonRecipient.put("id", recipientId);
            jsonRecipient.put("objectType", connection.oidToObjectType(recipientId));
            JSONArray field = activity.has(recipientField) ? activity.getJSONArray(recipientField) : new JSONArray();
            field.put(jsonRecipient);
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
    private JSONObject uploadMedia(Attachment attachment) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, attachment.uri.toString());
            Try<HttpReadResult> result = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPLOAD_MEDIA, getActor())
                    .flatMap(conu -> conu.execute(conu.newRequest().withPostParams(formParams)));
            if (result.flatMap(HttpReadResult::getJsonObject).getOrElseThrow(ConnectionException::of) == null) {
                result = Try.failure(new ConnectionException(
                        "Error uploading '" + attachment + "': null response returned"));
            }
            result.filter(r -> MyLog.isVerboseEnabled())
                .flatMap(HttpReadResult::getJsonObject)
                .map(jso -> jso.toString(2))
                .onSuccess(message -> MyLog.v(this, "uploaded '" + attachment + "' " + message));
            return result.flatMap(HttpReadResult::getJsonObject).getOrElseThrow(ConnectionException::of);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this,
                    "Error uploading '" + attachment + "'", e, formParams);
        }
    }

    private JSONObject buildObject(JSONObject activity) throws JSONException {
        JSONObject obj = new JSONObject();
        if (isExisting()) {
            obj.put("id", objectId);
            obj.put("objectType", connection.oidToObjectType(objectId));
        } else {
            if (StringUtil.isEmpty(name) && StringUtil.isEmpty(content) && attachments.isEmpty()) {
                throw new IllegalArgumentException("Nothing to send");
            }
            obj.put("author", activity.getJSONObject("actor"));
            PObjectType objectType = StringUtil.isEmpty(inReplyToId) ? PObjectType.NOTE : PObjectType.COMMENT;
            obj.put("objectType", objectType.id);
        }
        return obj;
    }

    private boolean isExisting() {
        return UriUtils.isRealOid(objectId);
    }
}