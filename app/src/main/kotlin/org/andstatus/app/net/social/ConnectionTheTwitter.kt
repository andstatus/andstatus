/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import android.net.Uri
import android.os.Build
import io.vavr.control.Try
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.stream.Collectors

/**
 * Implementation of current API of the twitter.com
 * https://dev.twitter.com/rest/public
 */
class ConnectionTheTwitter : ConnectionTwitterLike() {
    override fun getApiPathFromOrigin(routine: ApiRoutineEnum): String {
        val url: String = when (routine) {
            ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS -> "application/rate_limit_status.json"
            ApiRoutineEnum.LIKE -> "favorites/create.json?tweet_mode=extended"
            ApiRoutineEnum.UNDO_LIKE -> "favorites/destroy.json?tweet_mode=extended"
            ApiRoutineEnum.PRIVATE_NOTES -> "direct_messages.json?tweet_mode=extended"
            ApiRoutineEnum.LIKED_TIMELINE ->                 // https://dev.twitter.com/rest/reference/get/favorites/list
                "favorites/list.json?tweet_mode=extended"
            ApiRoutineEnum.GET_FOLLOWERS ->                 // https://dev.twitter.com/rest/reference/get/followers/list
                "followers/list.json"
            ApiRoutineEnum.GET_FRIENDS ->                 // https://dev.twitter.com/docs/api/1.1/get/friends/list
                "friends/list.json"
            ApiRoutineEnum.GET_NOTE -> "statuses/show.json" + "?id=%noteId%&tweet_mode=extended"
            ApiRoutineEnum.HOME_TIMELINE -> "statuses/home_timeline.json?tweet_mode=extended"
            ApiRoutineEnum.NOTIFICATIONS_TIMELINE ->                 // https://dev.twitter.com/docs/api/1.1/get/statuses/mentions_timeline
                "statuses/mentions_timeline.json?tweet_mode=extended"
            ApiRoutineEnum.UPDATE_PRIVATE_NOTE -> "direct_messages/new.json?tweet_mode=extended"
            ApiRoutineEnum.UPDATE_NOTE -> "statuses/update.json?tweet_mode=extended"
            ApiRoutineEnum.ANNOUNCE -> "statuses/retweet/%noteId%.json?tweet_mode=extended"
            ApiRoutineEnum.UPLOAD_MEDIA ->                 // Trying to allow setting alternative Twitter host...
                if (http.data.originUrl?.host == "api.twitter.com") {
                    "https://upload.twitter.com/1.1/media/upload.json"
                } else {
                    "media/upload.json"
                }
            ApiRoutineEnum.SEARCH_NOTES ->                 // https://dev.twitter.com/docs/api/1.1/get/search/tweets
                "search/tweets.json?tweet_mode=extended"
            ApiRoutineEnum.SEARCH_ACTORS -> "users/search.json?tweet_mode=extended"
            ApiRoutineEnum.ACTOR_TIMELINE -> "statuses/user_timeline.json?tweet_mode=extended"
            else -> ""
        }
        return if (url.isEmpty()) {
            super.getApiPathFromOrigin(routine)
        } else partialPathToApiPath(url)
    }

    /**
     * https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/post-statuses-update
     * @return
     */
    override fun updateNote2(note: Note): Try<AActivity> {
        val obj = JSONObject()
        try {
            super.updateNoteSetFields(note, obj)
            if (note.isSensitive()) {
                obj.put(SENSITIVE_PROPERTY, note.isSensitive())
            }
            val ids: MutableList<String?> = ArrayList()
            for (attachment in note.attachments.list) {
                if (UriUtils.isDownloadable(attachment.uri)) {
                    MyLog.i(this, "Skipped downloadable $attachment")
                } else {
                    // https://developer.twitter.com/en/docs/media/upload-media/api-reference/post-media-upload
                    val mediaObject = uploadMedia(attachment)
                    if (mediaObject != null && mediaObject.has("media_id_string")) {
                        ids.add(mediaObject["media_id_string"].toString())
                    }
                }
            }
            if (ids.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    obj.put("media_ids", java.lang.String.join(",", ids))
                } else {
                    obj.put("media_ids", ids.stream().collect(Collectors.joining(",")))
                }
            }
        } catch (e: JSONException) {
            return Try.failure(ConnectionException.hardConnectionException("Exception while preparing post params $note", e))
        } catch (e: Exception) {
            return Try.failure(e)
        }
        return postRequest(ApiRoutineEnum.UPDATE_NOTE, obj)
                .flatMap { obj1: HttpReadResult -> obj1.getJsonObject() }
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    private fun uploadMedia(attachment: Attachment): JSONObject? {
        return tryApiPath(data.getAccountActor(), ApiRoutineEnum.UPLOAD_MEDIA)
            .map { uri: Uri ->
                HttpRequest.of(ApiRoutineEnum.UPLOAD_MEDIA, uri)
                    .withMediaPartName("media")
                    .withAttachmentToPost(attachment)
            }
            .flatMap { request: HttpRequest -> request.executeMe(::execute) }
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .filter { obj: JSONObject? -> Objects.nonNull(obj) }
            .onSuccess { jso: JSONObject? -> MyLog.v(this) { "uploaded '" + attachment + "' " + jso.toString() } }
            .getOrElseThrow(ConnectionException::of)
    }

    override fun getConfig(): Try<OriginConfig> {
        // There is https://developer.twitter.com/en/docs/developer-utilities/configuration/api-reference/get-help-configuration
        // but it doesn't have this 280 chars limit...
        return Try.success(OriginConfig(280, 5L * MyPreferences.BYTES_IN_MB))
    }

    override fun like(noteOid: String): Try<AActivity> {
        val out = JSONObject()
        try {
            out.put("id", noteOid)
        } catch (e: JSONException) {
            return Try.failure(e)
        }
        return postRequest(ApiRoutineEnum.LIKE, out)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    override fun undoLike(noteOid: String): Try<AActivity> {
        val out = JSONObject()
        try {
            out.put("id", noteOid)
        } catch (e: JSONException) {
            return Try.failure(e)
        }
        return postRequest(ApiRoutineEnum.UNDO_LIKE, out)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    override fun searchNotes(syncYounger: Boolean, youngestPosition: TimelinePosition,
                             oldestPosition: TimelinePosition, limit: Int, searchQuery: String): Try<InputTimelinePage> {
        val apiRoutine = ApiRoutineEnum.SEARCH_NOTES
        return getApiPath(apiRoutine)
                .map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder -> if (searchQuery.isEmpty()) b else b.appendQueryParameter("q", searchQuery) }
                .map { builder: Uri.Builder -> appendPositionParameters(builder, youngestPosition, oldestPosition) }
                .map { builder: Uri.Builder -> builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)) }
                .map { it.build() }
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> request.executeMe(::execute) }
                .flatMap { result: HttpReadResult ->
                    result.getJsonArrayInObject("statuses")
                            .flatMap { jArr: JSONArray? -> jArrToTimeline(jArr, apiRoutine) }
                }
                .map { activities: MutableList<AActivity> -> InputTimelinePage.of(activities) }
    }

    override fun searchActors(limit: Int, searchQuery: String): Try<List<Actor>> {
        val apiRoutine = ApiRoutineEnum.SEARCH_ACTORS
        return getApiPath(apiRoutine)
            .map { obj: Uri -> obj.buildUpon() }
            .map { b: Uri.Builder -> if (searchQuery.isEmpty()) b else b.appendQueryParameter("q", searchQuery) }
            .map { b: Uri.Builder -> b.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)) }
            .map { it.build() }
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap { request: HttpRequest -> request.executeMe(::execute) }
            .flatMap { result: HttpReadResult ->
                result.getJsonArray()
                    .flatMap { jsonArray: JSONArray? -> jArrToActors(jsonArray, apiRoutine, result.request.uri) }
            }
    }

    override fun activityFromJson2(jso: JSONObject?): AActivity {
        if (jso == null) return AActivity.EMPTY
        val activity = super.activityFromJson2(jso)
        val note = activity.getNote()
        note.setSensitive(jso.optBoolean(SENSITIVE_PROPERTY))
        note.setLikesCount(jso.optLong("favorite_count"))
        note.setReblogsCount(jso.optLong("retweet_count"))
        if (!addAttachmentsFromJson(jso, activity, "extended_entities")) {
            // See https://developer.twitter.com/en/docs/tweets/data-dictionary/overview/entities-object
            addAttachmentsFromJson(jso, activity, "entities")
        }
        return activity
    }

    private fun addAttachmentsFromJson(jso: JSONObject, activity: AActivity, sectionName: String?): Boolean {
        val entities = jso.optJSONObject(sectionName)
        val jArr = entities?.optJSONArray(ATTACHMENTS_FIELD_NAME)
        if (jArr != null && jArr.length() > 0) {
            for (ind in 0 until jArr.length()) {
                val jsoAttachment = jArr.opt(ind) as JSONObject
                jsonToAttachments(jsoAttachment)
                        .forEach { attachment: Attachment -> activity.addAttachment(attachment) }
            }
            return true
        }
        return false
    }

    private fun jsonToAttachments(jsoAttachment: JSONObject): MutableList<Attachment> {
        val method = "jsonToAttachments"
        val attachments: MutableList<Attachment> = ArrayList()
        try {
            val jsoVideo = jsoAttachment.optJSONObject("video_info")
            val jsoVariants = jsoVideo?.optJSONArray("variants")
            val videoVariant = if (jsoVariants == null || jsoVariants.length() == 0) null else jsoVariants.optJSONObject(0)
            val video: Attachment = if (videoVariant == null) Attachment.EMPTY
            else Attachment.fromUriAndMimeType(videoVariant.optString("url"), videoVariant.optString("content_type"))
            if (video.isValid()) attachments.add(video)
            val attachment: Attachment = Attachment.fromUri(UriUtils.fromAlternativeTags(jsoAttachment,
                    "media_url_https", "media_url_http"))
            if (attachment.isValid()) {
                if (video.isValid()) attachment.setPreviewOf(video)
                attachments.add(attachment)
            } else {
                MyLog.w(this, "$method; invalid attachment: $jsoAttachment")
            }
        } catch (e: Exception) {
            MyLog.w(this, method, e)
        }
        return attachments
    }

    override fun setNoteBodyFromJson(note: Note, jso: JSONObject) {
        var bodyFound = false
        if (!jso.isNull("full_text")) {
            note.setContentPosted(jso.getString("full_text"))
            bodyFound = true
        }
        if (!bodyFound) {
            super.setNoteBodyFromJson(note, jso)
        }
    }

    override fun getActors(actor: Actor, apiRoutine: ApiRoutineEnum): Try<List<Actor>> {
        val limit = 200
        return getApiPathWithActorId(apiRoutine, actor.oid)
                .map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder -> if (actor.oid.isEmpty()) b else b.appendQueryParameter("user_id", actor.oid) }
                .map { b: Uri.Builder -> b.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)) }
                .map { it.build() }
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> request.executeMe(::execute) }
                .flatMap { result: HttpReadResult ->
                    result.getJsonArray()
                            .flatMap { jsonArray: JSONArray? -> jArrToActors(jsonArray, apiRoutine, result.request.uri) }
                }
    }

    companion object {
        private val ATTACHMENTS_FIELD_NAME: String = "media"
        private val SENSITIVE_PROPERTY: String = "possibly_sensitive"
    }
}
