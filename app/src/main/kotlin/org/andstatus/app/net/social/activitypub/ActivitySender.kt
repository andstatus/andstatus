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
package org.andstatus.app.net.social.activitypub

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.actor.GroupType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * ActivityPub specific
 * @author yvolk@yurivolkov.com
 */
internal class ActivitySender(val connection: ConnectionActivityPub, val note: Note) {

    fun send(activityType: ActivityType): Try<AActivity> {
        return sendInternal(activityType)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jsoActivity: JSONObject -> connection.activityFromJson(jsoActivity) }
    }

    private fun sendInternal(activityTypeIn: ActivityType): Try<HttpReadResult> {
        val activityType = if (isExisting()) if (activityTypeIn == ActivityType.CREATE) ActivityType.UPDATE else activityTypeIn else ActivityType.CREATE
        val msgLog = "Activity '" + activityType + "'" + if (isExisting()) " objectId:'" + note.oid + "'" else ""
        var activity = JSONObject()
        return try {
            activity = buildActivityToSend(activityType)
            val activityImm = activity
            val activityResponse: Try<HttpReadResult> = ConnectionAndUrl.fromActor(connection,
                    ApiRoutineEnum.UPDATE_NOTE, TimelinePosition.EMPTY, getActor())
                .flatMap { conu: ConnectionAndUrl ->
                    conu.newRequest()
                        .withPostParams(activityImm)
                        .let(conu::execute)
                }
            val jsonObject = activityResponse
                    .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                    .flatMap { jso: JSONObject? ->
                        if (jso == null) Try.failure(ConnectionException.hardConnectionException("$msgLog returned no data", null))
                        else Try.success(jso) }
            if (jsonObject.isFailure) return jsonObject.flatMap { j: JSONObject? -> activityResponse }
            activityResponse.filter { r: HttpReadResult? -> MyLog.isVerboseEnabled() }
                    .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                    .map { jso: JSONObject -> msgLog + " " + jso.toString(2) }
                    .onSuccess { message: String? -> MyLog.v(this, message) }
            if (jsonObject.map { jso: JSONObject -> contentNotPosted(activityType, jso) }.getOrElse(true)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update")
                }
                activity.put("type", ActivityType.UPDATE.activityPubValue)
                return ConnectionAndUrl.fromActor(
                    connection, ApiRoutineEnum.UPDATE_NOTE, TimelinePosition.EMPTY, getActor()
                )
                    .flatMap { conu: ConnectionAndUrl ->
                        conu.newRequest()
                            .withPostParams(activityImm)
                            .let(conu::execute)
                    }
            }
            activityResponse
        } catch (e: JSONException) {
            Try.failure(ConnectionException.loggedJsonException(this, msgLog, e, activity))
        } catch (e: Exception) {
            Try.failure(e)
        }
    }

    private fun getActor(): Actor {
        return connection.data.getAccountActor()
    }

    private fun buildActivityToSend(activityType: ActivityType): JSONObject {
        val activity = newActivityOfThisAccount(activityType)
        val obj = buildObject(activity)
        addAttachments(obj)
        if (!note.getName().isEmpty()) {
            obj.put(ConnectionActivityPub.NAME_PROPERTY, note.getName())
        }
        if (!note.summary.isEmpty()) {
            obj.put(ConnectionActivityPub.SUMMARY_PROPERTY, note.summary)
        }
        if (note.isSensitive()) {
            obj.put(ConnectionActivityPub.SENSITIVE_PROPERTY, note.isSensitive())
        }
        if (!note.content.isEmpty()) {
            obj.put(ConnectionActivityPub.CONTENT_PROPERTY, note.getContentToPost())
        }
        if (!note.inReplyTo.getOid().isEmpty()) {
            obj.put("inReplyTo", note.inReplyTo.getOid())
        }
        activity.put("object", obj)
        return activity
    }

    private fun addAttachments(obj: JSONObject) {
        if (note.attachments.isEmpty) return
        val jsoAttachments = JSONArray()
        for (attachment in note.attachments.list) {
            if (UriUtils.isDownloadable(attachment.uri)) {
                // TODO
                MyLog.i(this, "Skipped downloadable $attachment")
            } else {
                val mediaObject = uploadMedia(attachment)
                jsoAttachments.put(mediaObject)
            }
        }
        obj.put("attachment", jsoAttachments)
    }

    private fun contentNotPosted(activityType: ActivityType, jsActivity: JSONObject): Boolean {
        val objPosted = jsActivity.optJSONObject("object")
        return ActivityType.CREATE == activityType && objPosted != null &&
                (!note.content.isEmpty() &&
                        JsonUtils.optString(objPosted, ConnectionActivityPub.CONTENT_PROPERTY).isEmpty() ||
                        !note.getName().isEmpty() &&
                        JsonUtils.optString(objPosted, ConnectionActivityPub.NAME_PROPERTY).isEmpty() ||
                        !note.summary.isEmpty() &&
                        JsonUtils.optString(objPosted, ConnectionActivityPub.SUMMARY_PROPERTY).isEmpty())
    }

    private fun newActivityOfThisAccount(activityType: ActivityType): JSONObject {
        val activity = JSONObject()
        activity.put("@context", "https://www.w3.org/ns/activitystreams")
        activity.put("type", activityType.activityPubValue)
        setAudience(activity)
        activity.put("actor", getActor().oid)
        return activity
    }

    private fun setAudience(activity: JSONObject) {
        note.audience().getRecipients().forEach { recipient: Actor -> addToAudience(activity, "to", recipient) }
        if (note.audience().noRecipients()) {
            // "clients must be aware that the server will only forward new Activities
            //   to addressees in the to, bto, cc, bcc, and audience fields"
            addToAudience(activity, "to", Actor.PUBLIC)
        }
    }

    private fun addToAudience(activity: JSONObject, recipientField: String, recipient: Actor) {
        val recipientId: String?
        recipientId = if (recipient === Actor.PUBLIC) {
            ConnectionActivityPub.PUBLIC_COLLECTION_ID
        } else if (recipient.groupType == GroupType.FOLLOWERS) {
            getActor().getEndpoint(ActorEndpointType.API_FOLLOWERS).orElse(Uri.EMPTY).toString()
        } else {
            recipient.getBestUri()
        }
        if (recipientId.isNullOrEmpty()) return
        try {
            val field = if (activity.has(recipientField)) activity.getJSONArray(recipientField) else JSONArray()
            field.put(recipientId)
            activity.put(recipientField, field)
        } catch (e: JSONException) {
            MyLog.w(this, "JSON: $activity", e)
        }
    }

    private fun uploadMedia(attachment: Attachment): JSONObject {
        var result: Try<HttpReadResult> = ConnectionAndUrl.fromActor(
            connection, ApiRoutineEnum.UPLOAD_MEDIA,
            TimelinePosition.EMPTY, getActor()
        )
            .flatMap { conu: ConnectionAndUrl ->
                conu.newRequest()
                    .withMediaPartName("file")
                    .withAttachmentToPost(attachment)
                    .let(conu::execute)
            }
        if (result.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .getOrElseThrow(ConnectionException::of) == null) {
            result = Try.failure(ConnectionException(
                    "Error uploading '$attachment': null response returned"))
        }
        result.filter { r: HttpReadResult? -> MyLog.isVerboseEnabled() }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject -> jso.toString(2) }
                .onSuccess { message: String? -> MyLog.v(this, "uploaded '$attachment' $message") }
        return result.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .getOrElseThrow(ConnectionException::of)
    }

    private fun buildObject(activity: JSONObject): JSONObject {
        val obj = JSONObject()
        if (isExisting()) {
            obj.put("id", note.oid)
            obj.put("type", ApObjectType.NOTE.id())
        } else {
            require(note.hasSomeContent()) { "Nothing to send" }
            obj.put("attributedTo", getActor().oid)
            obj.put("type", ApObjectType.NOTE.id())
        }
        obj.put("to", activity.getJSONArray("to"))
        return obj
    }

    private fun isExisting(): Boolean {
        return UriUtils.isRealOid(note.oid)
    }

    companion object {
        fun fromId(connection: ConnectionActivityPub, objectId: String): ActivitySender {
            return ActivitySender(connection,
                    Note.fromOriginAndOid(connection.data.getOrigin(), objectId, DownloadStatus.UNKNOWN))
        }

        fun fromContent(connection: ConnectionActivityPub, note: Note): ActivitySender {
            return ActivitySender(connection, note)
        }
    }
}
