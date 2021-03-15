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
package org.andstatus.app.net.social.pumpio

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.actor.GroupType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpConnectionInterface
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Note
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.UriUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Pump.io specific
 * @author yvolk@yurivolkov.com
 */
internal class ActivitySender(val connection: ConnectionPumpio, val note: Note) {
    fun send(activityType: PActivityType): Try<AActivity> {
        return sendInternal(activityType)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jsoActivity: JSONObject? -> connection.activityFromJson(jsoActivity) }
    }

    private fun sendInternal(activityTypeIn: PActivityType): Try<HttpReadResult> {
        val activityType = if (isExisting()) if (activityTypeIn == PActivityType.POST) PActivityType.UPDATE else activityTypeIn else PActivityType.POST
        val msgLog = "Activity '" + activityType + "'" + if (isExisting()) " objectId:'" + note.oid + "'" else ""
        var activity = JSONObject()
        return try {
            activity = buildActivityToSend(activityType)
            val activityImm = activity
            val tryConu: Try<ConnectionAndUrl> = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPDATE_NOTE, getActor())
            val activityResponse = tryConu
                    .flatMap { conu: ConnectionAndUrl -> conu.execute(conu.newRequest().withPostParams(activityImm)) }
            if (activityResponse.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                            .getOrElseThrow { e: Throwable? -> ConnectionException.of(e) } == null) {
                return Try.failure(ConnectionException.hardConnectionException("$msgLog returned no data", null))
            }
            activityResponse.filter { r: HttpReadResult? -> MyLog.isVerboseEnabled() }
                    .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                    .map { jso: JSONObject -> msgLog + " " + jso.toString(2) }
                    .onSuccess { message: String? -> MyLog.v(this, message) }
            if (activityResponse
                            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                            .map { jso: JSONObject -> contentNotPosted(activityType, jso) }
                            .getOrElse(true)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update")
                }
                activity.put("verb", PActivityType.UPDATE.code)
                return tryConu.flatMap { conu: ConnectionAndUrl -> conu.execute(conu.newRequest().withPostParams(activityImm)) }
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

    @Throws(JSONException::class, ConnectionException::class)
    private fun buildActivityToSend(activityType: PActivityType): JSONObject {
        val activity = newActivityOfThisAccount(activityType)
        var obj = buildObject(activity)
        val attachment = note.attachments.getFirstToUpload()
        if (attachment.nonEmpty) {
            if (note.attachments.toUploadCount() > 1) {
                MyLog.w(this, "Sending only the first attachment: " + note.attachments) // TODO
            }
            val objectType: PObjectType = PObjectType.fromJson(obj)
            if (isExisting()
                    && (PObjectType.IMAGE != objectType || PObjectType.VIDEO != objectType)) {
                throw ConnectionException.hardConnectionException(
                        "Cannot update '" + objectType + "' to " + PObjectType.IMAGE, null)
            }
            val mediaObject = uploadMedia(attachment)
            val mediaObjectType: PObjectType = PObjectType.fromJson(mediaObject)
            if (isExisting() && mediaObjectType == objectType) {
                if (objectType === PObjectType.VIDEO) {
                    val video = mediaObject.optJSONObject(ConnectionPumpio.VIDEO_OBJECT)
                    if (video != null) {
                        // Replace the video in the existing object
                        obj.put(ConnectionPumpio.VIDEO_OBJECT, video)
                    }
                } else {
                    val image = mediaObject.optJSONObject(ConnectionPumpio.IMAGE_OBJECT)
                    if (image != null) {
                        // Replace an image in the existing object
                        obj.put(ConnectionPumpio.IMAGE_OBJECT, image)
                        val fullImage = mediaObject.optJSONObject(ConnectionPumpio.FULL_IMAGE_OBJECT)
                        if (fullImage != null) {
                            obj.put(ConnectionPumpio.FULL_IMAGE_OBJECT, fullImage)
                        }
                    }
                }
            } else {
                obj = mediaObject
            }
        }
        if (!note.getName().isEmpty()) {
            obj.put(ConnectionPumpio.NAME_PROPERTY, note.getName())
        }
        if (!note.content.isEmpty()) {
            obj.put(ConnectionPumpio.CONTENT_PROPERTY, note.getContentToPost())
        }
        if (StringUtil.nonEmptyNonTemp(note.getInReplyTo().getOid())) {
            val inReplyToObject = JSONObject()
            inReplyToObject.put("id", note.getInReplyTo().getOid())
            inReplyToObject.put("objectType", connection.oidToObjectType(note.getInReplyTo().getOid()))
            obj.put("inReplyTo", inReplyToObject)
        }
        activity.put("object", obj)
        return activity
    }

    private fun contentNotPosted(activityType: PActivityType, jsActivity: JSONObject): Boolean {
        val objPosted = jsActivity.optJSONObject("object")
        return PActivityType.POST == activityType && objPosted != null && (!note.content.isEmpty()
                && JsonUtils.optString(objPosted, ConnectionPumpio.CONTENT_PROPERTY).isEmpty()
                || !note.getName().isEmpty()
                && JsonUtils.optString(objPosted, ConnectionPumpio.NAME_PROPERTY).isEmpty())
    }

    @Throws(JSONException::class, ConnectionException::class)
    private fun newActivityOfThisAccount(activityType: PActivityType): JSONObject {
        val activity = JSONObject()
        activity.put("objectType", "activity")
        activity.put("verb", activityType.code)
        val generator = JSONObject()
        generator.put("id", ConnectionPumpio.APPLICATION_ID)
        generator.put("displayName", HttpConnectionInterface.USER_AGENT)
        generator.put("objectType", PObjectType.APPLICATION.id)
        activity.put("generator", generator)
        setAudience(activity, activityType)
        val author = JSONObject()
        author.put("id", getActor().oid)
        author.put("objectType", "person")
        activity.put("actor", author)
        return activity
    }

    @Throws(JSONException::class)
    private fun setAudience(activity: JSONObject, activityType: PActivityType) {
        note.audience().getRecipients().forEach { actor: Actor -> addToAudience(activity, "to", actor) }
        if (note.audience().noRecipients() && note.getInReplyTo().getOid().isEmpty()
                && (activityType == PActivityType.POST || activityType == PActivityType.UPDATE)) {
            addToAudience(activity, "to", Actor.PUBLIC)
        }
    }

    private fun addToAudience(activity: JSONObject, recipientField: String, recipient: Actor) {
        val recipientId: String?
        recipientId = if (recipient === Actor.PUBLIC) {
            ConnectionPumpio.PUBLIC_COLLECTION_ID
        } else if (recipient.groupType == GroupType.FOLLOWERS) {
            getActor().getEndpoint(ActorEndpointType.API_FOLLOWERS).orElse(Uri.EMPTY).toString()
        } else {
            recipient.getBestUri()
        }
        if (recipientId.isNullOrEmpty()) return
        val jsonRecipient = JSONObject()
        try {
            jsonRecipient.put("id", recipientId)
            jsonRecipient.put("objectType", connection.oidToObjectType(recipientId))
            val field = if (activity.has(recipientField)) activity.getJSONArray(recipientField) else JSONArray()
            field.put(jsonRecipient)
            activity.put(recipientField, field)
        } catch (e: JSONException) {
            MyLog.e(this, e)
        }
    }

    /** See as a working example of uploading image here:
     * org.macno.puma.provider.Pumpio.postImage(String, String, boolean, Location, String, byte[])
     * We simplified it a bit...
     */
    @Throws(ConnectionException::class)
    private fun uploadMedia(attachment: Attachment): JSONObject {
        var result: Try<HttpReadResult> = ConnectionAndUrl.fromActor(connection, ApiRoutineEnum.UPLOAD_MEDIA, getActor())
                .flatMap { conu: ConnectionAndUrl -> conu.execute(conu.newRequest().withAttachmentToPost(attachment)) }
        if (result.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                        .getOrElseThrow { e: Throwable -> ConnectionException.of(e) } == null) {
            result = Try.failure(ConnectionException(
                    "Error uploading '$attachment': null response returned"))
        }
        result.filter { r: HttpReadResult? -> MyLog.isVerboseEnabled() }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject -> jso.toString(2) }
                .onSuccess { message: String? -> MyLog.v(this, "uploaded '$attachment' $message") }
        return result.flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .getOrElseThrow { e: Throwable? -> ConnectionException.of(e) }
    }

    @Throws(JSONException::class)
    private fun buildObject(activity: JSONObject): JSONObject {
        val obj = JSONObject()
        if (isExisting()) {
            obj.put("id", note.oid)
            obj.put("objectType", connection.oidToObjectType(note.oid))
        } else {
            require(note.hasSomeContent()) { "Nothing to send" }
            obj.put("author", activity.getJSONObject("actor"))
            val objectType = if (note.getInReplyTo().getOid().isEmpty()) PObjectType.NOTE else PObjectType.COMMENT
            obj.put("objectType", objectType.id)
        }
        return obj
    }

    private fun isExisting(): Boolean {
        return UriUtils.isRealOid(note.oid)
    }

    companion object {
        fun fromId(connection: ConnectionPumpio, objectId: String?): ActivitySender {
            return ActivitySender(connection,
                    Note.fromOriginAndOid(connection.data.getOrigin(), objectId, DownloadStatus.UNKNOWN))
        }

        fun fromContent(connection: ConnectionPumpio, note: Note): ActivitySender {
            return ActivitySender(connection, note)
        }
    }
}