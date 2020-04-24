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

import androidx.annotation.NonNull;

import org.andstatus.app.actor.GroupType;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.vavr.control.Try;

import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.CONTENT_PROPERTY;
import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.NAME_PROPERTY;
import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.SENSITIVE_PROPERTY;
import static org.andstatus.app.net.social.activitypub.ConnectionActivityPub.SUMMARY_PROPERTY;

/**
 * ActivityPub specific
 * @author yvolk@yurivolkov.com
 */
class ActivitySender {
    final ConnectionActivityPub connection;
    final Note note;
    final Audience audience;
    String inReplyToId = "";
    Attachments attachments = Attachments.EMPTY;

    ActivitySender(ConnectionActivityPub connection, Note note, Audience audience) {
        this.connection = connection;
        this.note = note;
        this.audience = audience;
    }

    static ActivitySender fromId(ConnectionActivityPub connection, String objectId) {
        return new ActivitySender(connection,
                Note.fromOriginAndOid(connection.getData().getOrigin(), objectId, DownloadStatus.UNKNOWN), Audience.EMPTY);
    }
    
    static ActivitySender fromContent(ConnectionActivityPub connection, Note note) {
        return new ActivitySender(connection, note, note.audience());
    }

    ActivitySender setInReplyTo(String inReplyToId) {
        this.inReplyToId = inReplyToId;
        return this;
    }
    
    ActivitySender setAttachments(Attachments attachments) {
        this.attachments = attachments;
        return this;
    }
    
    Try<AActivity> send(ActivityType activityType) {
        return sendInternal(activityType)
            .flatMap(HttpReadResult::getJsonObject)
            .map(connection::activityFromJson);
    }

    private Try<HttpReadResult> sendInternal(ActivityType activityTypeIn) {
        ActivityType activityType = isExisting()
                ? (activityTypeIn.equals(ActivityType.CREATE) ? ActivityType.UPDATE : activityTypeIn)
                : ActivityType.CREATE;
        String msgLog = "Activity '" + activityType + "'" + (isExisting() ? " objectId:'" + note.oid + "'" : "");
        JSONObject activity = null;
        try {
            activity = buildActivityToSend(activityType);
            JSONObject activityImm = activity;
            Try<HttpReadResult> activityResponse = ConnectionAndUrl.fromActor(connection,
                    ApiRoutineEnum.UPDATE_NOTE, TimelinePosition.EMPTY, getActor())
                .flatMap(conu -> conu.execute(conu.newRequest().withPostParams(activityImm)));

            Try<JSONObject> jsonObject = activityResponse
                    .flatMap(HttpReadResult::getJsonObject)
                    .flatMap(jso -> jso == null
                    ? Try.failure(ConnectionException.hardConnectionException(msgLog + " returned no data", null))
                    : Try.success(jso)
            );
            if (jsonObject.isFailure()) return jsonObject.flatMap(j -> activityResponse);

            activityResponse.filter(r -> MyLog.isVerboseEnabled())
                .flatMap(HttpReadResult::getJsonObject)
                .map(jso -> msgLog + " " + jso.toString(2))
                .onSuccess(message -> MyLog.v(this, message));
            if (jsonObject.map(jso -> contentNotPosted(activityType, jso)).getOrElse(true)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update");
                }
                activity.put("type", ActivityType.UPDATE.activityPubValue);
                return ConnectionAndUrl.fromActor(connection,
                        ApiRoutineEnum.UPDATE_NOTE, TimelinePosition.EMPTY, getActor())
                    .flatMap(conu -> conu.execute(conu.newRequest().withPostParams(activityImm)));
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

    private JSONObject buildActivityToSend(ActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = newActivityOfThisAccount(activityType);
        JSONObject obj = buildObject(activity);
        addAttachments(obj);
        if (StringUtil.nonEmpty(note.getName())) {
            obj.put(NAME_PROPERTY, note.getName());
        }
        if (StringUtil.nonEmpty(note.getSummary())) {
            obj.put(SUMMARY_PROPERTY, note.getSummary());
        }
        if (note.isSensitive()) {
            obj.put(SENSITIVE_PROPERTY, note.isSensitive());
        }
        if (StringUtil.nonEmpty(note.getContent())) {
            obj.put(CONTENT_PROPERTY, note.getContentToPost());
        }
        if (!StringUtil.isEmpty(inReplyToId)) {
            obj.put("inReplyTo", inReplyToId);
        }
        activity.put("object", obj);
        return activity;
    }

    private void addAttachments(JSONObject obj) throws ConnectionException, JSONException {
        if (attachments.isEmpty()) return;

        JSONArray jsoAttachments = new JSONArray();
        for (Attachment attachment: attachments.list) {
            if (UriUtils.isDownloadable(attachment.uri)) {
                // TODO
                MyLog.i(this, "Skipped downloadable " + attachment);
            } else {
                JSONObject mediaObject = uploadMedia(attachment);
                jsoAttachments.put(mediaObject);
            }
        }
        obj.put("attachment", jsoAttachments);
    }

    private boolean contentNotPosted(ActivityType activityType, JSONObject jsActivity) {
        JSONObject objPosted = jsActivity.optJSONObject("object");
        return ActivityType.CREATE.equals(activityType) && objPosted != null &&
            (StringUtil.nonEmpty(note.getContent()) && StringUtil.isEmpty(JsonUtils.optString(objPosted, CONTENT_PROPERTY))
                || StringUtil.nonEmpty(note.getName()) && StringUtil.isEmpty(JsonUtils.optString(objPosted, NAME_PROPERTY))
                || StringUtil.nonEmpty(note.getSummary()) && StringUtil.isEmpty(JsonUtils.optString(objPosted, SUMMARY_PROPERTY))
            );
    }

    private JSONObject newActivityOfThisAccount(ActivityType activityType) throws JSONException, ConnectionException {
        JSONObject activity = new JSONObject();
        activity.put("@context", "https://www.w3.org/ns/activitystreams");
        activity.put("type", activityType.activityPubValue);

        setAudience(activity, activityType);

        activity.put("actor", getActor().oid);
        return activity;
    }

    private void setAudience(JSONObject activity, ActivityType activityType) throws JSONException {
        audience.getRecipients().forEach(recipient -> addToAudience(activity, "to", recipient));
        if (audience.noRecipients()) {
            // "clients must be aware that the server will only forward new Activities
            //   to addressees in the to, bto, cc, bcc, and audience fields"
            addToAudience(activity, "to", Actor.PUBLIC);
        }
    }

    private void addToAudience(JSONObject activity, String recipientField, Actor recipient) {
        String recipientId;
        if (recipient == Actor.PUBLIC) {
            recipientId = ConnectionActivityPub.PUBLIC_COLLECTION_ID;
        } else if (recipient.groupType == GroupType.FOLLOWERS) {
            recipientId = getActor().getEndpoint(ActorEndpointType.API_FOLLOWERS).orElse(Uri.EMPTY).toString();
        } else {
            recipientId = recipient.getBestUri();
        }
        if (StringUtil.isEmpty(recipientId)) return;

        try {
            JSONArray field = activity.has(recipientField) ? activity.getJSONArray(recipientField) : new JSONArray();
            field.put(recipientId);
            activity.put(recipientField, field);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
    }

    @NonNull
    private JSONObject uploadMedia(Attachment attachment) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "file");
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, attachment.uri.toString());
            Try<HttpReadResult> result = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPLOAD_MEDIA,
                    TimelinePosition.EMPTY, getActor())
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
            obj.put("id", note.oid);
            obj.put("type", ApObjectType.NOTE.id());
        } else {
            if (!note.hasSomeContent() && attachments.isEmpty()) {
                throw new IllegalArgumentException("Nothing to send");
            }
            obj.put("attributedTo", getActor().oid);
            obj.put("type", ApObjectType.NOTE.id());
        }
        obj.put("to", activity.getJSONArray("to"));
        return obj;
    }

    private boolean isExisting() {
        return UriUtils.isRealOid(note.oid);
    }
}