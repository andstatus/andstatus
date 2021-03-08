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
import io.vavr.control.CheckedFunction
import io.vavr.control.CheckedPredicate
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
import java.util.function.Consumer
import java.util.function.Function

/**
 * Pump.io specific
 * @author yvolk@yurivolkov.com
 */
internal class ActivitySender(val connection: ConnectionPumpio?, val note: Note?) {
    fun send(activityType: PActivityType?): Try<AActivity> {
        return sendInternal(activityType)
                .flatMap(CheckedFunction<HttpReadResult?, Try<out JSONObject>> { obj: HttpReadResult? -> obj.getJsonObject() })
                .map { jsoActivity: JSONObject? -> connection.activityFromJson(jsoActivity) }
    }

    private fun sendInternal(activityTypeIn: PActivityType?): Try<HttpReadResult> {
        val activityType = if (isExisting()) if (activityTypeIn == PActivityType.POST) PActivityType.UPDATE else activityTypeIn else PActivityType.POST
        val msgLog = "Activity '" + activityType + "'" + if (isExisting()) " objectId:'" + note.oid + "'" else ""
        var activity: JSONObject? = null
        return try {
            activity = buildActivityToSend(activityType)
            val activityImm = activity
            val tryConu: Try<ConnectionAndUrl?> = ConnectionAndUrl.Companion.fromActor(connection, ApiRoutineEnum.UPDATE_NOTE, getActor())
            val activityResponse = tryConu
                    .flatMap { conu: ConnectionAndUrl? -> conu.execute(conu.newRequest().withPostParams(activityImm)) }
            if (activityResponse.flatMap { obj: HttpReadResult? -> obj.getJsonObject() }.getOrElseThrow(Function<Throwable?, ConnectionException?> { e: Throwable? -> ConnectionException.Companion.of(e) }) == null) {
                return Try.failure(ConnectionException.Companion.hardConnectionException("$msgLog returned no data", null))
            }
            activityResponse.filter { r: HttpReadResult? -> MyLog.isVerboseEnabled() }
                    .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                    .map { jso: JSONObject? -> msgLog + " " + jso.toString(2) }
                    .onSuccess { message: String? -> MyLog.v(this, message) }
            if (activityResponse
                            .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                            .map { jso: JSONObject? -> contentNotPosted(activityType, jso) }
                            .getOrElse(true)) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog + " Pump.io bug: content is not sent, " +
                            "when an image object is posted. Sending an update")
                }
                activity.put("verb", PActivityType.UPDATE.code)
                return tryConu.flatMap { conu: ConnectionAndUrl? -> conu.execute(conu.newRequest().withPostParams(activityImm)) }
            }
            activityResponse
        } catch (e: JSONException) {
            Try.failure(ConnectionException.Companion.loggedJsonException(this, msgLog, e, activity))
        } catch (e: Exception) {
            Try.failure(e)
        }
    }

    private fun getActor(): Actor? {
        return connection.getData().accountActor
    }

    @Throws(JSONException::class, ConnectionException::class)
    private fun buildActivityToSend(activityType: PActivityType?): JSONObject? {
        val activity = newActivityOfThisAccount(activityType)
        var obj = buildObject(activity)
        val attachment = note.attachments.firstToUpload
        if (attachment.nonEmpty()) {
            if (note.attachments.toUploadCount() > 1) {
                MyLog.w(this, "Sending only the first attachment: " + note.attachments) // TODO
            }
            val objectType: PObjectType = PObjectType.Companion.fromJson(obj)
            if (isExisting()
                    && (PObjectType.IMAGE != objectType || PObjectType.VIDEO != objectType)) {
                throw ConnectionException.Companion.hardConnectionException(
                        "Cannot update '" + objectType + "' to " + PObjectType.IMAGE, null)
            }
            val mediaObject = uploadMedia(attachment)
            val mediaObjectType: PObjectType = PObjectType.Companion.fromJson(mediaObject)
            if (isExisting() && mediaObjectType == objectType) {
                if (objectType === PObjectType.VIDEO) {
                    val video = mediaObject.optJSONObject(ConnectionPumpio.Companion.VIDEO_OBJECT)
                    if (video != null) {
                        // Replace the video in the existing object
                        obj.put(ConnectionPumpio.Companion.VIDEO_OBJECT, video)
                    }
                } else {
                    val image = mediaObject.optJSONObject(ConnectionPumpio.Companion.IMAGE_OBJECT)
                    if (image != null) {
                        // Replace an image in the existing object
                        obj.put(ConnectionPumpio.Companion.IMAGE_OBJECT, image)
                        val fullImage = mediaObject.optJSONObject(ConnectionPumpio.Companion.FULL_IMAGE_OBJECT)
                        if (fullImage != null) {
                            obj.put(ConnectionPumpio.Companion.FULL_IMAGE_OBJECT, fullImage)
                        }
                    }
                }
            } else {
                obj = mediaObject
            }
        }
        if (!note.getName().isNullOrEmpty()) {
            obj.put(ConnectionPumpio.Companion.NAME_PROPERTY, note.getName())
        }
        if (!note.getContent().isNullOrEmpty()) {
            obj.put(ConnectionPumpio.Companion.CONTENT_PROPERTY, note.getContentToPost())
        }
        if (StringUtil.nonEmptyNonTemp(note.getInReplyTo().oid)) {
            val inReplyToObject = JSONObject()
            inReplyToObject.put("id", note.getInReplyTo().oid)
            inReplyToObject.put("objectType", connection.oidToObjectType(note.getInReplyTo().oid))
            obj.put("inReplyTo", inReplyToObject)
        }
        activity.put("object", obj)
        return activity
    }

    private fun contentNotPosted(activityType: PActivityType?, jsActivity: JSONObject?): Boolean {
        val objPosted = jsActivity.optJSONObject("object")
        return PActivityType.POST == activityType && objPosted != null && (!note.getContent().isNullOrEmpty()
                && JsonUtils.optString(objPosted, ConnectionPumpio.Companion.CONTENT_PROPERTY).isNullOrEmpty()
                || !note.getName().isNullOrEmpty()
                && JsonUtils.optString(objPosted, ConnectionPumpio.Companion.NAME_PROPERTY).isNullOrEmpty())
    }

    @Throws(JSONException::class, ConnectionException::class)
    private fun newActivityOfThisAccount(activityType: PActivityType?): JSONObject? {
        val activity = JSONObject()
        activity.put("objectType", "activity")
        activity.put("verb", activityType.code)
        val generator = JSONObject()
        generator.put("id", ConnectionPumpio.Companion.APPLICATION_ID)
        generator.put("displayName", HttpConnectionInterface.Companion.USER_AGENT)
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
    private fun setAudience(activity: JSONObject?, activityType: PActivityType?) {
        note.audience().recipients.forEach(Consumer { actor: Actor? -> addToAudience(activity, "to", actor) })
        if (note.audience().noRecipients() && note.getInReplyTo().oid.isNullOrEmpty()
                && (activityType == PActivityType.POST || activityType == PActivityType.UPDATE)) {
            addToAudience(activity, "to", Actor.Companion.PUBLIC)
        }
    }

    private fun addToAudience(activity: JSONObject?, recipientField: String?, recipient: Actor?) {
        val recipientId: String?
        recipientId = if (recipient === Actor.Companion.PUBLIC) {
            ConnectionPumpio.Companion.PUBLIC_COLLECTION_ID
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
    private fun uploadMedia(attachment: Attachment?): JSONObject {
        var result: Try<HttpReadResult> = ConnectionAndUrl.Companion.fromActor(connection, ApiRoutineEnum.UPLOAD_MEDIA, getActor())
                .flatMap<HttpReadResult?>(CheckedFunction<ConnectionAndUrl?, Try<out HttpReadResult>> { conu: ConnectionAndUrl? -> conu.execute(conu.newRequest().withAttachmentToPost(attachment)) })
        if (result.flatMap(CheckedFunction<HttpReadResult?, Try<out JSONObject>> { obj: HttpReadResult? -> obj.getJsonObject() }).getOrElseThrow(Function<Throwable?, ConnectionException?> { e: Throwable? -> ConnectionException.Companion.of(e) }) == null) {
            result = Try.failure(ConnectionException(
                    "Error uploading '$attachment': null response returned"))
        }
        result.filter(CheckedPredicate { r: HttpReadResult? -> MyLog.isVerboseEnabled() })
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jso: JSONObject? -> jso.toString(2) }
                .onSuccess { message: String? -> MyLog.v(this, "uploaded '$attachment' $message") }
        return result.flatMap(CheckedFunction<HttpReadResult?, Try<out JSONObject>> { obj: HttpReadResult? -> obj.getJsonObject() }).getOrElseThrow(Function<Throwable?, ConnectionException?> { e: Throwable? -> ConnectionException.Companion.of(e) })
    }

    @Throws(JSONException::class)
    private fun buildObject(activity: JSONObject?): JSONObject? {
        val obj = JSONObject()
        if (isExisting()) {
            obj.put("id", note.oid)
            obj.put("objectType", connection.oidToObjectType(note.oid))
        } else {
            require(note.hasSomeContent()) { "Nothing to send" }
            obj.put("author", activity.getJSONObject("actor"))
            val objectType = if (note.getInReplyTo().oid.isNullOrEmpty()) PObjectType.NOTE else PObjectType.COMMENT
            obj.put("objectType", objectType.id)
        }
        return obj
    }

    private fun isExisting(): Boolean {
        return UriUtils.isRealOid(note.oid)
    }

    companion object {
        fun fromId(connection: ConnectionPumpio?, objectId: String?): ActivitySender? {
            return ActivitySender(connection,
                    Note.Companion.fromOriginAndOid(connection.getData().origin, objectId, DownloadStatus.UNKNOWN))
        }

        fun fromContent(connection: ConnectionPumpio?, note: Note?): ActivitySender? {
            return ActivitySender(connection, note)
        }
    }
}