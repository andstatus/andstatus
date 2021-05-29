/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.MyContentType
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.AActivity.Companion.newPartialNote
import org.andstatus.app.note.KeywordsFilter
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.ObjectOrId
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * See [API](https://source.joinmastodon.org/mastodon/docs)
 */
class ConnectionMastodon : ConnectionTwitterLike() {

    override fun getApiPathFromOrigin(routine: ApiRoutineEnum): String {
        return partialPathToApiPath(partialPath(routine))
    }

    override fun getTimelineUriBuilder(apiRoutine: ApiRoutineEnum, limit: Int, actor: Actor): Try<Uri.Builder> {
        return getApiPathWithActorId(apiRoutine, actor.oid)
                .map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder -> b.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)) }
    }

    @Throws(ConnectionException::class)
    override fun activityFromTwitterLikeJson(jso: JSONObject?): AActivity {
        return when {
            jso == null -> return AActivity.EMPTY
            isNotification(jso) -> {
                val activity: AActivity = AActivity.from(data.getAccountActor(), getType(jso))
                activity.setOid(JsonUtils.optString(jso, "id"))
                activity.setUpdatedDate(dateFromJson(jso, "created_at"))
                activity.setActor(actorFromJson(jso.optJSONObject("account")))
                val noteActivity = activityFromJson2(jso.optJSONObject("status"))
                when (activity.type) {
                    ActivityType.LIKE,
                    ActivityType.UNDO_LIKE,
                    ActivityType.ANNOUNCE,
                    ActivityType.UNDO_ANNOUNCE -> activity.setActivity(noteActivity)
                    ActivityType.FOLLOW,
                    ActivityType.UNDO_FOLLOW -> activity.setObjActor(data.getAccountActor())
                    else -> activity.setNote(noteActivity.getNote())
                }
                activity
            }
            isConversation(jso) -> {
                // https://docs.joinmastodon.org/entities/conversation/
                val noteActivity = activityFromJson2(jso.optJSONObject("last_status"))
                if (noteActivity.nonEmpty) {
                    noteActivity.getNote().setConversationOid(jso.optString("id"))
                }
                noteActivity
            }
            else -> super.activityFromTwitterLikeJson(jso)
        }
    }

    private fun getType(timelineItem: JSONObject?): ActivityType {
        return if (timelineItem != null && isNotification(timelineItem)) {
            when (JsonUtils.optString(timelineItem, "type")) {
                "favourite" -> ActivityType.LIKE
                "reblog" -> ActivityType.ANNOUNCE
                "follow" -> ActivityType.FOLLOW
                "mention" -> ActivityType.UPDATE
                else -> ActivityType.EMPTY
            }
        } else ActivityType.UPDATE
    }

    /** https://docs.joinmastodon.org/entities/conversation/  */
    private fun isConversation(activity: JSONObject?): Boolean {
        return activity != null && activity.has("last_status")
    }

    private fun isNotification(activity: JSONObject?): Boolean {
        return activity != null && activity.has("type")
    }

    override fun searchNotes(syncYounger: Boolean, youngestPosition: TimelinePosition,
                             oldestPosition: TimelinePosition, limit: Int, searchQuery: String): Try<InputTimelinePage> {
        val tag = KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword()
        if (tag.isEmpty()) {
            return InputTimelinePage.TRY_EMPTY
        }
        val apiRoutine = ApiRoutineEnum.TAG_TIMELINE
        return getApiPathWithTag(apiRoutine, tag)
                .map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder -> appendPositionParameters(b, youngestPosition, oldestPosition) }
                .map { b: Uri.Builder -> b.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)) }
                .map { it.build() }
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { result: HttpReadResult ->
                    result.getJsonArray()
                            .flatMap { jsonArray: JSONArray? -> jArrToTimeline(jsonArray, apiRoutine) }
                }
                .map { activities -> InputTimelinePage.of(activities) }
    }

    override fun searchActors(limit: Int, searchQuery: String): Try<List<Actor>> {
        val tag = KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword()
        if (tag.isEmpty()) {
            return TryUtils.emptyList()
        }
        val apiRoutine = ApiRoutineEnum.SEARCH_ACTORS
        return getApiPath(apiRoutine).map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder ->
                    b.appendQueryParameter("q", searchQuery)
                            .appendQueryParameter("resolve", "true")
                            .appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine))
                }
                .map { it.build() }
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { result: HttpReadResult ->
                    result.getJsonArray()
                            .flatMap { jsonArray: JSONArray? -> jArrToActors(jsonArray, apiRoutine, result.request.uri) }
                }
    }

    // TODO: Delete ?
    protected fun getApiPathWithTag(routineEnum: ApiRoutineEnum, tag: String): Try<Uri> {
        return getApiPath(routineEnum).map { uri: Uri -> UriUtils.map(uri) { s: String? -> s?.replace("%tag%", tag) } }
    }

    override fun getConversation(conversationOid: String): Try<List<AActivity>> {
        return noteAction(ApiRoutineEnum.GET_CONVERSATION, conversationOid)
                .map { jsonObject: JSONObject -> getConversationActivities(jsonObject, conversationOid) }
    }

    @Throws(ConnectionException::class)
    private fun getConversationActivities(mastodonContext: JSONObject, conversationOid: String): List<AActivity> {
        if (JsonUtils.isEmpty(mastodonContext)) return emptyList()
        val timeline: MutableList<AActivity> = ArrayList()
        try {
            val ancestors = "ancestors"
            if (mastodonContext.has(ancestors)) {
                jArrToTimeline(mastodonContext.getJSONArray(ancestors), ApiRoutineEnum.GET_CONVERSATION)
                        .onSuccess { c: MutableList<AActivity> -> timeline.addAll(c) }
            }
            val descendants = "descendants"
            if (mastodonContext.has(descendants)) {
                jArrToTimeline(mastodonContext.getJSONArray(descendants), ApiRoutineEnum.GET_CONVERSATION)
                        .onSuccess { c: MutableList<AActivity> -> timeline.addAll(c) }
            }
        } catch (e: JSONException) {
            throw ConnectionException.loggedJsonException(this, "Error getting conversation '$conversationOid'",
                    e, mastodonContext)
        }
        return timeline
    }

    override fun updateNote(note: Note): Try<AActivity> {
        return updateNote2(note)
    }

    override fun updateNote2(note: Note): Try<AActivity> {
        val obj = JSONObject()
        try {
            obj.put(SUMMARY_PROPERTY, note.summary)
            obj.put(VISIBILITY_PROPERTY, getVisibility(note))
            obj.put(SENSITIVE_PROPERTY, note.isSensitive())
            obj.put(CONTENT_PROPERTY_UPDATE, note.getContentToPost())
            if (StringUtil.nonEmptyNonTemp(note.getInReplyTo().getOid())) {
                obj.put("in_reply_to_id", note.getInReplyTo().getOid())
            }
            val ids: MutableList<String?> = ArrayList()
            for (attachment in note.attachments.list) {
                if (UriUtils.isDownloadable(attachment.uri)) {
                    // TODO
                    MyLog.i(this, "Skipped downloadable $attachment")
                } else {
                    val uploaded = uploadMedia(attachment)
                            .map { mediaObject: JSONObject? ->
                                if (mediaObject != null && mediaObject.has("id")) {
                                    ids.add(mediaObject["id"].toString())
                                }
                                AActivity.EMPTY
                            }
                    if (uploaded.isFailure) return uploaded
                }
            }
            if (!ids.isEmpty()) {
                obj.put("media_ids[]", ids)
            }
        } catch (e: JSONException) {
            return Try.failure(ConnectionException.loggedJsonException(this, "Error updating note", e, obj))
        }
        return postRequest(ApiRoutineEnum.UPDATE_NOTE, obj)
                .flatMap { result: HttpReadResult -> result.getJsonObject() }
                .map { jso: JSONObject -> activityFromJson(jso) }
    }

    private fun getVisibility(note: Note): String {
        return when (note.audience().visibility) {
            Visibility.UNKNOWN, Visibility.PUBLIC_AND_TO_FOLLOWERS, Visibility.PUBLIC -> VISIBILITY_PUBLIC
            Visibility.NOT_PUBLIC_NEEDS_CLARIFICATION, Visibility.TO_FOLLOWERS -> VISIBILITY_PRIVATE
            Visibility.PRIVATE -> VISIBILITY_DIRECT
        }
    }

    private fun uploadMedia(attachment: Attachment): Try<JSONObject> {
        return tryApiPath(data.getAccountActor(), ApiRoutineEnum.UPLOAD_MEDIA)
                .map { uri: Uri ->
                    HttpRequest.of(ApiRoutineEnum.UPLOAD_MEDIA, uri)
                            .withMediaPartName("file")
                            .withAttachmentToPost(attachment)
                }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .filter { obj: JSONObject? -> Objects.nonNull(obj) }
                .onSuccess { jso: JSONObject -> MyLog.v(this) { "uploaded '" + attachment + "' " + jso.toString() } }
    }

    @Throws(ConnectionException::class)
    override fun actorFromJson(jso: JSONObject?): Actor {
        if (jso == null) {
            return Actor.EMPTY
        }
        val oid = JsonUtils.optString(jso, "id")
        val username = JsonUtils.optString(jso, "username")
        if (oid.isEmpty() || username.isEmpty()) {
            throw ConnectionException.loggedJsonException(this, "Id or username is empty", null, jso)
        }
        val actor: Actor = Actor.fromOid(data.getOrigin(), oid)
        actor.setUsername(username)
        actor.setRealName(JsonUtils.optString(jso, "display_name"))
        actor.setWebFingerId(JsonUtils.optString(jso, "acct"))
        if (!SharedPreferencesUtil.isEmpty(actor.getRealName())) {
            actor.setProfileUrlToOriginUrl(data.getOriginUrl())
        }
        actor.setAvatarUri(UriUtils.fromJson(jso, "avatar"))
        actor.endpoints.add(ActorEndpointType.BANNER, UriUtils.fromJson(jso, "header"))
        actor.setSummary(extractSummary(jso))
        actor.setProfileUrl(JsonUtils.optString(jso, "url"))
        actor.notesCount = jso.optLong("statuses_count")
        actor.followingCount = jso.optLong("following_count")
        actor.followersCount = jso.optLong("followers_count")
        actor.setCreatedDate(dateFromJson(jso, "created_at"))
        return actor.build()
    }

    private fun extractSummary(jso: JSONObject): String {
        val builder = MyStringBuilder()
        builder.append(JsonUtils.optString(jso, "note"))
        val fields = jso.optJSONArray("fields")
        if (fields != null) {
            for (ind in 0 until fields.length()) {
                val field = fields.optJSONObject(ind)
                if (field != null) {
                    builder.append(JsonUtils.optString(field, "name"), JsonUtils.optString(field, "value"),
                            "\n<br>", false)
                }
            }
        }
        return builder.toString()
    }

    @Throws(ConnectionException::class)
    override fun activityFromJson2(jso: JSONObject?): AActivity {
        if (jso == null) {
            return AActivity.EMPTY
        }
        val method = "activityFromJson2"
        val oid = JsonUtils.optString(jso, "id")
        val activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"))
        try {
            val actor: JSONObject?
            if (jso.has("account")) {
                actor = jso.getJSONObject("account")
                activity.setActor(actorFromJson(actor))
            }
            val note = activity.getNote()
            note.setSummary(JsonUtils.optString(jso, SUMMARY_PROPERTY))
            note.setSensitive(jso.optBoolean(SENSITIVE_PROPERTY))
            note.setContentPosted(JsonUtils.optString(jso, CONTENT_PROPERTY))
            note.url = JsonUtils.optString(jso, "url")
            note.setLikesCount(jso.optLong("favourites_count"))
            note.setReblogsCount(jso.optLong("reblogs_count"))
            note.setRepliesCount(jso.optLong("replies_count"))
            note.audience().visibility = parseVisibility(jso)
            if (jso.has("recipient")) {
                val recipient = jso.getJSONObject("recipient")
                note.audience().add(actorFromJson(recipient))
            }
            ObjectOrId.of(jso, "mentions")
                    .mapAll<Actor>({ jso1: JSONObject? -> actorFromJson(jso1) }, { oid1: String? -> Actor.EMPTY })
                    .forEach { o: Actor -> note.audience().add(o) }
            if (!jso.isNull("application")) {
                val application = jso.getJSONObject("application")
                note.via = JsonUtils.optString(application, "name")
            }
            if (!jso.isNull("favourited")) {
                note.addFavoriteBy(data.getAccountActor(),
                        TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favourited"))))
            }

            // If the Msg is a Reply to other note
            var inReplyToActorOid: String? = ""
            if (jso.has("in_reply_to_account_id")) {
                inReplyToActorOid = jso.getString("in_reply_to_account_id")
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                inReplyToActorOid = ""
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                var inReplyToNoteOid: String? = ""
                if (jso.has("in_reply_to_id")) {
                    inReplyToNoteOid = jso.getString("in_reply_to_id")
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToNoteOid)) {
                    // Construct Related note from available info
                    val inReplyTo: AActivity = newPartialNote(data.getAccountActor(),
                            Actor.fromOid(data.getOrigin(), inReplyToActorOid), inReplyToNoteOid)
                    note.setInReplyTo(inReplyTo)
                }
            }
            ObjectOrId.of(jso, ATTACHMENTS_FIELD_NAME).mapObjects<List<Attachment>>(jsonToAttachments(method))
                    .forEach { attachments: List<Attachment> -> attachments
                            .forEach { attachment: Attachment -> activity.addAttachment(attachment) } }
        } catch (e: JSONException) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso)
        } catch (e: Exception) {
            MyLog.w(this, method, e)
            return AActivity.EMPTY
        }
        return activity
    }

    private fun parseVisibility(jso: JSONObject): Visibility {
        return when (JsonUtils.optString(jso, VISIBILITY_PROPERTY)) {
            VISIBILITY_PUBLIC, VISIBILITY_UNLISTED -> Visibility.PUBLIC_AND_TO_FOLLOWERS
            VISIBILITY_PRIVATE -> Visibility.TO_FOLLOWERS
            VISIBILITY_DIRECT -> Visibility.PRIVATE
            else -> Visibility.UNKNOWN
        }
    }

    private fun jsonToAttachments(method: String): CheckedFunction<JSONObject, List<Attachment>> {
        return CheckedFunction { jsoAttachment: JSONObject ->
            var type = StringUtil.notEmpty(JsonUtils.optString(jsoAttachment, "type"), "unknown")
            val attachments: MutableList<Attachment> = if ("unknown" == type) {
                // When the type is "unknown", it is likely only remote_url is available and local url is missing
                val remoteUri = UriUtils.fromJson(jsoAttachment, "remote_url")
                val attachment: Attachment = Attachment.fromUri(remoteUri)
                if (attachment.isValid()) {
                    mutableListOf(attachment)
                } else {
                    type = ""
                    ArrayList()
                }
            } else ArrayList()
            if (attachments.isEmpty()) {
                val uri = UriUtils.fromJson(jsoAttachment, "url")
                val attachment: Attachment = Attachment.fromUriAndMimeType(uri, type)
                if (attachment.isValid()) {
                    attachments.add(attachment)
                    val preview: Attachment = Attachment.fromUriAndMimeType(
                            UriUtils.fromJson(jsoAttachment, "preview_url"),
                            MyContentType.IMAGE.generalMimeType)
                            .setPreviewOf(attachment)
                    attachments.add(preview)
                } else {
                    MyLog.d(this, "$method; invalid attachment $jsoAttachment")
                }
            }
            attachments
        }
    }

    @Throws(ConnectionException::class)
    override fun rebloggedNoteFromJson(jso: JSONObject): AActivity {
        return activityFromJson2(jso.optJSONObject("reblog"))
    }

    override fun parseDate(stringDate: String?): Long {
        return parseIso8601Date(stringDate)
    }

    override fun getActor2(actorIn: Actor): Try<Actor> {
        val apiRoutine = ApiRoutineEnum.GET_ACTOR
        return getApiPathWithActorId(apiRoutine,
                if (UriUtils.isRealOid(actorIn.oid)) actorIn.oid else actorIn.getUsername())
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject? -> actorFromJson(jso) }
    }

    override fun follow(actorOid: String, follow: Boolean): Try<AActivity> {
        val apiRoutine = if (follow) ApiRoutineEnum.FOLLOW else ApiRoutineEnum.UNDO_FOLLOW
        val tryRelationship = getApiPathWithActorId(apiRoutine, actorOid)
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri).asPost() }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
        return tryRelationship.map { relationship: JSONObject? ->
            if (relationship == null || relationship.isNull("following")) {
                AActivity.EMPTY
            } else {
                val following: TriState = TriState.fromBoolean(relationship.optBoolean("following"))
                data.getAccountActor().act(
                        data.getAccountActor(),
                        if (following.toBoolean(!follow) == follow) if (follow) ActivityType.FOLLOW else ActivityType.UNDO_FOLLOW else ActivityType.UPDATE,
                        Actor.fromOid(data.getOrigin(), actorOid)
                )
            }
        }
    }

    override fun undoAnnounce(noteOid: String): Try<Boolean> {
        return postNoteAction(ApiRoutineEnum.UNDO_ANNOUNCE, noteOid)
                .filter { obj: JSONObject? -> Objects.nonNull(obj) }
                .map { any: JSONObject? -> true }
    }

    override fun getActors(actor: Actor, apiRoutine: ApiRoutineEnum): Try<List<Actor>> {
        val limit = 400
        return getApiPathWithActorId(apiRoutine, actor.oid)
                .map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder -> b.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)) }
                .map { it.build() }
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { result: HttpReadResult ->
                    result.getJsonArray()
                            .flatMap { jsonArray: JSONArray? -> jArrToActors(jsonArray, apiRoutine, result.request.uri) }
                }
    }

    override fun getConfig(): Try<OriginConfig> {
        val apiRoutine = ApiRoutineEnum.GET_CONFIG
        return getApiPath(apiRoutine)
                .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
                .flatMap { request: HttpRequest -> execute(request) }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map({ result: JSONObject? ->
                    // Hardcoded in https://github.com/tootsuite/mastodon/blob/master/spec/validators/status_length_validator_spec.rb
                    val textLimit = if (result == null || result.optInt(TEXT_LIMIT_KEY) < 1)
                        OriginConfig.MASTODON_TEXT_LIMIT_DEFAULT
                    else result.optInt(TEXT_LIMIT_KEY)
                    OriginConfig.fromTextLimit(textLimit, (10 * MyPreferences.BYTES_IN_MB).toLong())
                })
    }

    companion object {
        private val ATTACHMENTS_FIELD_NAME: String = "media_attachments"
        private val VISIBILITY_PROPERTY: String = "visibility"
        private val VISIBILITY_PUBLIC: String = "public"
        private val VISIBILITY_UNLISTED: String = "unlisted"
        private val VISIBILITY_PRIVATE: String = "private"
        private val VISIBILITY_DIRECT: String = "direct"
        private val SENSITIVE_PROPERTY: String = "sensitive"
        private val SUMMARY_PROPERTY: String = "spoiler_text"
        private val CONTENT_PROPERTY_UPDATE: String = "status"
        private val CONTENT_PROPERTY: String = "content"

        /** Only Pleroma has this, see https://github.com/tootsuite/mastodon/issues/4915  */
        private val TEXT_LIMIT_KEY: String = "max_toot_chars"
        fun partialPath(routine: ApiRoutineEnum): String {
            return when (routine) {
                ApiRoutineEnum.GET_CONFIG -> "v1/instance" // https://docs.joinmastodon.org/api/rest/instances/
                ApiRoutineEnum.HOME_TIMELINE -> "v1/timelines/home"
                ApiRoutineEnum.NOTIFICATIONS_TIMELINE -> "v1/notifications"
                ApiRoutineEnum.LIKED_TIMELINE -> "v1/favourites"
                ApiRoutineEnum.PUBLIC_TIMELINE -> "v1/timelines/public"
                ApiRoutineEnum.TAG_TIMELINE -> "v1/timelines/tag/%tag%"
                ApiRoutineEnum.ACTOR_TIMELINE -> "v1/accounts/%actorId%/statuses"
                ApiRoutineEnum.PRIVATE_NOTES -> "v1/conversations"
                ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS -> "v1/accounts/verify_credentials"
                ApiRoutineEnum.UPDATE_NOTE, ApiRoutineEnum.UPDATE_PRIVATE_NOTE -> "v1/statuses"
                ApiRoutineEnum.UPLOAD_MEDIA -> "v1/media"
                ApiRoutineEnum.GET_NOTE -> "v1/statuses/%noteId%"
                ApiRoutineEnum.SEARCH_NOTES -> "v1/search" /* actually, this is a complex search "for content" */
                ApiRoutineEnum.SEARCH_ACTORS -> "v1/accounts/search"
                ApiRoutineEnum.GET_CONVERSATION -> "v1/statuses/%noteId%/context"
                ApiRoutineEnum.LIKE -> "v1/statuses/%noteId%/favourite"
                ApiRoutineEnum.UNDO_LIKE -> "v1/statuses/%noteId%/unfavourite"
                ApiRoutineEnum.FOLLOW -> "v1/accounts/%actorId%/follow"
                ApiRoutineEnum.UNDO_FOLLOW -> "v1/accounts/%actorId%/unfollow"
                ApiRoutineEnum.GET_FOLLOWERS -> "v1/accounts/%actorId%/followers"
                ApiRoutineEnum.GET_FRIENDS -> "v1/accounts/%actorId%/following"
                ApiRoutineEnum.GET_ACTOR -> "v1/accounts/%actorId%"
                ApiRoutineEnum.ANNOUNCE -> "v1/statuses/%noteId%/reblog"
                ApiRoutineEnum.UNDO_ANNOUNCE -> "v1/statuses/%noteId%/unreblog"
                else -> ""
            }
        }
    }
}
