/*
 * Copyright (C) 2022 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.TryUtils.getOrRecover
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UriUtils.isRealOid
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * ActivityPub specific
 * @author yvolk@yurivolkov.com
 */
internal class ActivitySender(val connection: ConnectionActivityPub, activityTypeIn: ActivityType,
                              val audience: Audience,  val note: Note, val objActor: Actor) {
    val activityType: ActivityType = calcActivityType(activityTypeIn)
    private val isNote: Boolean get() = note != Note.EMPTY
    private val actor: Actor get() = connection.data.getAccountActor()
    private val objExists: Boolean get() = (if (isNote) note.oid else objActor.oid).isRealOid
    private val strContext = "Activity " + activityType + " " +
            (if (isNote) "Note" else "Actor") +
            (if (objExists) " objectId:'" + note.oid + "'" else " (new)")

    private fun calcActivityType(activityTypeIn: ActivityType): ActivityType {
        val activityType = if (objExists) {
            if (activityTypeIn == ActivityType.CREATE) ActivityType.UPDATE else activityTypeIn
        } else ActivityType.CREATE
        return activityType
    }

    fun send(): Try<AActivity> {
        return sendInternal()
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jsoActivity: JSONObject -> connection.activityFromJson(jsoActivity) }
    }

    private fun sendInternal(): Try<HttpReadResult> {
        val activity = Try.of { buildActivityToSend(activityType) }
            .getOrRecover { return TryUtils.failure(strContext, it) }

        val activityResponse: HttpReadResult = ConnectionAndUrl.fromActor(
            connection, ApiRoutineEnum.UPDATE_NOTE, TimelinePosition.EMPTY, actor
        )
            .flatMap { conu: ConnectionAndUrl ->
                conu.newRequest()
                    .withPostParams(activity)
                    .let(conu::execute)
            }
            .getOrRecover { return TryUtils.failure(strContext, it) }

        val jsonObject: JSONObject = activityResponse
            .getJsonObject()
            .getOrRecover { return TryUtils.failure(strContext, it) }

        if (MyLog.isVerboseEnabled()) {
            Try.of { strContext + " " + jsonObject.toString(2) }
                .onSuccess { message: String? -> MyLog.v(this, message) }
        }

        if (isNote && contentNotPosted(activityType, jsonObject)) {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(
                    this,
                    "$strContext Server bug: " +
                            "content is not sent, when an image object is posted. Sending an update"
                )
            }
            activity.put("type", ActivityType.UPDATE.activityPubValue)
            return ConnectionAndUrl.fromActor(
                connection, ApiRoutineEnum.UPDATE_NOTE, TimelinePosition.EMPTY, actor
            )
                .flatMap { conu: ConnectionAndUrl ->
                    conu.newRequest()
                        .withPostParams(activity)
                        .let(conu::execute)
                }
                .mapFailure { ConnectionException.of(it, strContext) }
        }
        return Try.success(activityResponse)
    }

    private fun buildActivityToSend(activityType: ActivityType): JSONObject =
        newActivityOfThisActor(activityType).apply {
            if (isNote) {
                put("object", buildNoteObject(this))
            } else {
                put("object", objActor.oid)
            }
        }

    private fun buildNoteObject(activity: JSONObject): JSONObject {
        val obj = JSONObject()
        if (objExists) {
            obj.put("id", note.oid)
            obj.put("type", ApObjectType.NOTE.id())
        } else {
            require(note.hasSomeContent()) { "Nothing to send" }
            obj.put("attributedTo", actor.oid)
            obj.put("type", ApObjectType.NOTE.id())
        }
        obj.put("to", activity.getJSONArray("to"))
        addAttachments(obj)
        if (note.getName().isNotEmpty()) {
            obj.put(ConnectionActivityPub.NAME_PROPERTY, note.getName())
        }
        if (note.summary.isNotEmpty()) {
            obj.put(ConnectionActivityPub.SUMMARY_PROPERTY, note.summary)
        }
        if (note.isSensitive()) {
            obj.put(ConnectionActivityPub.SENSITIVE_PROPERTY, note.isSensitive())
        }
        if (note.content.isNotEmpty()) {
            obj.put(ConnectionActivityPub.CONTENT_PROPERTY, note.getContentToPost())
        }
        if (note.inReplyTo.getOid().isNotEmpty()) {
            obj.put("inReplyTo", note.inReplyTo.getOid())
        }
        return obj
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

    private fun newActivityOfThisActor(activityType: ActivityType): JSONObject {
        val activity = JSONObject()
        activity.put("@context", "https://www.w3.org/ns/activitystreams")
        activity.put("type", activityType.activityPubValue)
        setAudience(activity)
        activity.put("actor", actor.oid)
        return activity
    }

    private fun setAudience(activity: JSONObject) {
        audience.getRecipients().forEach { recipient: Actor -> addToAudience(activity, "to", recipient) }
        if (audience.noRecipients()) {
            // "clients must be aware that the server will only forward new Activities
            //   to addressees in the to, bto, cc, bcc, and audience fields"
            addToAudience(activity, "to", Actor.PUBLIC)
        }
    }

    private fun addToAudience(activity: JSONObject, recipientField: String, recipient: Actor) {
        val recipientId: String = if (recipient === Actor.PUBLIC) {
            ConnectionActivityPub.PUBLIC_COLLECTION_ID
        } else if (recipient.groupType == GroupType.FOLLOWERS) {
            actor.getEndpoint(ActorEndpointType.API_FOLLOWERS).orElse(Uri.EMPTY).toString()
        } else {
            recipient.getBestUri()
        }
        if (recipientId.isEmpty()) return

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
            TimelinePosition.EMPTY, actor
        )
            .flatMap { conu: ConnectionAndUrl ->
                conu.newRequest()
                    .withMediaPartName("file")
                    .withAttachmentToPost(attachment)
                    .let(conu::execute)
            }
        if (result.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .getOrElseThrow(ConnectionException::of) == null) {
            result = Try.failure(
                ConnectionException(
                    "Error uploading '$attachment': null response returned"
                )
            )
        }
        result.filter { r: HttpReadResult? -> MyLog.isVerboseEnabled() }
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jso: JSONObject -> jso.toString(2) }
            .onSuccess { message: String? -> MyLog.v(this, "uploaded '$attachment' $message") }
        return result.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .getOrElseThrow(ConnectionException::of)
    }

    companion object {
        fun sendNote(connection: ConnectionActivityPub, activityType: ActivityType, note: Note): Try<AActivity> {
            return ActivitySender(connection, activityType, note.audience(), note, Actor.EMPTY).send()
        }

        fun sendActor(connection: ConnectionActivityPub, activityType: ActivityType, objActor: Actor): Try<AActivity> {
            return ActivitySender(connection, activityType, Audience.EMPTY, Note.EMPTY, objActor).send()
        }
    }
}
