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
import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.actor.GroupType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.AActivity.Companion.newPartialNote
import org.andstatus.app.net.social.AJsonCollection
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Connection
import org.andstatus.app.net.social.ConnectionMastodon
import org.andstatus.app.net.social.InputActorPage
import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.ObjectOrId
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.function.Consumer

class ConnectionActivityPub : Connection() {
    override fun updateConnectionData(connectionData: AccountConnectionData): AccountConnectionData {
        val host = connectionData.getAccountActor().getConnectionHost()
        if (host.isNotEmpty()) {
            connectionData.setOriginUrl(UrlUtils.buildUrl(host, connectionData.isSsl()))
        }
        return connectionData
    }

    override fun getApiPathFromOrigin(routine: ApiRoutineEnum): String {
        val url: String
        url = when (routine) {
            ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS -> "whoami"
            else -> ""
        }
        return partialPathToApiPath(url)
    }

    override fun verifyCredentials(whoAmI: Optional<Uri>): Try<Actor> {
        return TryUtils.fromOptional(whoAmI)
            .filter { obj: Uri -> UriUtils.isDownloadable(obj) }
            .orElse { getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS) }
            .map { uri: Uri -> HttpRequest.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri) }
            .flatMap(::execute)
            .recoverWith(Exception::class.java) { originalException: Exception ->
                mastodonsHackToVerifyCredentials(
                    originalException
                )
            }
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jso: JSONObject -> actorFromJson(jso) }
    }

    /** @return  original error, if this Mastodon's hack didn't work
     */
    private fun mastodonsHackToVerifyCredentials(originalException: Exception?): Try<HttpReadResult> {
        // Get Username first by partially parsing Mastodon's non-ActivityPub response
        val userNameInMastodon = Try.success(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS)
            .map { routine: ApiRoutineEnum -> ConnectionMastodon.partialPath(routine) }
            .map { partialPath: String -> OriginType.MASTODON.partialPathToApiPath(partialPath) }
            .flatMap { path: String -> pathToUri(path) }
            .map { uri: Uri -> HttpRequest.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri) }
            .flatMap(::execute)
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jso: JSONObject -> JsonUtils.optString(jso, "username") }
            .filter { obj: String -> obj.isNotEmpty() }

        // Now build the Actor's Uri artificially using Mastodon's known URL pattern
        return userNameInMastodon.map { username: String -> "users/$username" }
            .flatMap { path: String -> pathToUri(path) }
            .map { uri: Uri -> HttpRequest.of(ApiRoutineEnum.GET_ACTOR, uri) }
            .flatMap(::execute)
            .recoverWith(Exception::class.java) { newException: Exception? -> Try.failure(originalException) }
    }

    private fun actorFromJson(jso: JSONObject): Actor {
        return when (ApObjectType.fromJson(jso)) {
            ApObjectType.PERSON -> actorFromPersonTypeJson(jso)
            ApObjectType.COLLECTION, ApObjectType.ORDERED_COLLECTION -> actorFromCollectionTypeJson(jso)
            ApObjectType.UNKNOWN -> Actor.EMPTY
            else -> {
                MyLog.w(TAG, "Unexpected object type for Actor: ${ApObjectType.fromJson(jso)}, JSON:\n$jso")
                Actor.EMPTY
            }
        }
    }

    private fun actorFromCollectionTypeJson(jso: JSONObject): Actor {
        val actor: Actor = Actor.fromTwoIds(data.getOrigin(), GroupType.COLLECTION, 0, JsonUtils.optString(jso, ID))
        return actor.build()
    }

    private fun actorFromPersonTypeJson(jso: JSONObject): Actor {
        val actor = actorFromOid(JsonUtils.optString(jso, ID))
        actor.setUsername(JsonUtils.optString(jso, "preferredUsername"))
        actor.setRealName(JsonUtils.optString(jso, NAME_PROPERTY))
        actor.setAvatarUrl(JsonUtils.optStringInside(jso, "icon", "url"))
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY)
        actor.setSummary(JsonUtils.optString(jso, "summary"))
        actor.setHomepage(JsonUtils.optString(jso, "url"))
        actor.setProfileUrl(JsonUtils.optString(jso, "url"))
        actor.setUpdatedDate(dateFromJson(jso, "updated"))
        actor.endpoints
            .add(ActorEndpointType.API_PROFILE, JsonUtils.optString(jso, ID))
            .add(ActorEndpointType.API_INBOX, JsonUtils.optString(jso, "inbox"))
            .add(ActorEndpointType.API_OUTBOX, JsonUtils.optString(jso, "outbox"))
            .add(ActorEndpointType.API_FOLLOWING, JsonUtils.optString(jso, "following"))
            .add(ActorEndpointType.API_FOLLOWERS, JsonUtils.optString(jso, "followers"))
            .add(ActorEndpointType.BANNER, JsonUtils.optStringInside(jso, "image", "url"))
            .add(ActorEndpointType.API_SHARED_INBOX, JsonUtils.optStringInside(jso, "endpoints", "sharedInbox"))
            .add(ActorEndpointType.API_UPLOAD_MEDIA, JsonUtils.optStringInside(jso, "endpoints", "uploadMedia"))
        return actor.build()
    }

    override fun parseDate(stringDate: String?): Long {
        return parseIso8601Date(stringDate)
    }

    override fun undoLike(noteOid: String): Try<AActivity> {
        return actOnNote(ActivityType.UNDO_LIKE, noteOid)
    }

    override fun like(noteOid: String): Try<AActivity> {
        return actOnNote(ActivityType.LIKE, noteOid)
    }

    override fun deleteNote(noteOid: String): Try<Boolean> {
        return actOnNote(ActivityType.DELETE, noteOid).map { obj: AActivity -> obj.isEmpty }
    }

    private fun actOnNote(activityType: ActivityType, noteId: String): Try<AActivity> {
        return ActivitySender.sendNote(
            this, ActivityType.CREATE,
            Note.fromOriginAndOid(data.getOrigin(), noteId, DownloadStatus.UNKNOWN)
        )
    }

    override fun getFriendsOrFollowers(
        routineEnum: ApiRoutineEnum,
        position: TimelinePosition,
        actor: Actor
    ): Try<InputActorPage> {
        return getActors(routineEnum, position, actor)
    }

    private fun getActors(apiRoutine: ApiRoutineEnum, position: TimelinePosition, actor: Actor): Try<InputActorPage> =
        ConnectionAndUrl.fromActor(this, apiRoutine, position, actor)
            .flatMap { conu: ConnectionAndUrl ->
                conu.newRequest()
                    .let(conu::execute)
                    .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                    .map { jsonObject: JSONObject ->
                        val jsonCollection: AJsonCollection = AJsonCollection.of(jsonObject)
                        val actors = jsonCollection.mapAll({ jso: JSONObject -> actorFromJson(jso) }) { id: String? ->
                            actorFromOid(id)
                        }
                        MyLog.v(TAG) { apiRoutine.toString() + " '" + conu.uri + "' " + actors.size + " actors" }
                        InputActorPage.of(jsonCollection, actors)
                    }
            }

    override fun getActivity(activityOid: String): Try<AActivity> = HttpRequest
        .of(ApiRoutineEnum.GET_ACTIVITY, UriUtils.fromString(activityOid))
        .let(::execute)
        .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
        .map { jsoActivity: JSONObject? -> this.activityFromJson(jsoActivity) }

    /** TODO: Some special code may be needed here, not the same as for Activity */
    override fun getNote1(noteOid: String): Try<AActivity> = HttpRequest
        .of(ApiRoutineEnum.GET_NOTE, UriUtils.fromString(noteOid))
        .let(::execute)
        .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
        .map { jsoActivity: JSONObject? -> this.activityFromJson(jsoActivity) }

    override fun updateNote(note: Note): Try<AActivity> {
        return ActivitySender.sendNote(this, ActivityType.CREATE, note)
    }

    override fun announce(rebloggedNoteOid: String): Try<AActivity> {
        return actOnNote(ActivityType.ANNOUNCE, rebloggedNoteOid)
    }

    override fun canGetConversation(conversationOid: String?): Boolean {
        val uri = UriUtils.fromString(conversationOid)
        return UriUtils.isDownloadable(uri)
    }

    override fun getConversation(conversationOid: String): Try<List<AActivity>> {
        val uri = UriUtils.fromString(conversationOid)
        return if (UriUtils.isDownloadable(uri)) {
            ConnectionAndUrl.fromUriActor(uri, this, ApiRoutineEnum.GET_CONVERSATION, data.getAccountActor())
                .flatMap { conu: ConnectionAndUrl -> getActivities(conu) }
                .map { p: InputTimelinePage -> p.items }
        } else {
            super.getConversation(conversationOid)
        }
    }

    override fun getTimeline(
        syncYounger: Boolean, apiRoutine: ApiRoutineEnum,
        youngestPosition: TimelinePosition, oldestPosition: TimelinePosition, limit: Int, actor: Actor
    ): Try<InputTimelinePage> {
        val requestedPosition = if (syncYounger) youngestPosition else oldestPosition

        // TODO: See https://github.com/andstatus/andstatus/issues/499#issuecomment-475881413
        return ConnectionAndUrl.fromActor(this, apiRoutine, requestedPosition, actor)
            .map { conu: ConnectionAndUrl -> conu.withSyncDirection(syncYounger) }
            .flatMap { conu: ConnectionAndUrl -> getActivities(conu) }
    }

    private fun getActivities(conu: ConnectionAndUrl): Try<InputTimelinePage> = conu.newRequest()
        .let(conu::execute)
        .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
        .map { root: JSONObject ->
            val jsonCollection: AJsonCollection = AJsonCollection.of(root)
            val activities = jsonCollection.mapObjects { item: JSONObject? ->
                activityFromJson(ObjectOrId.of(item))
                    .setTimelinePositions(jsonCollection.getPrevId(), jsonCollection.getNextId())
            }
            MyLog.d(
                TAG,
                "getTimeline " + conu.apiRoutine + " '" + conu.uri + "' " + activities.size + " activities"
            )
            InputTimelinePage.of(jsonCollection, activities)
        }

    override fun fixedDownloadLimit(limit: Int, apiRoutine: ApiRoutineEnum?): Int {
        val maxLimit = if (apiRoutine == ApiRoutineEnum.GET_FRIENDS) 200 else 20
        var out = super.fixedDownloadLimit(limit, apiRoutine)
        if (out > maxLimit) {
            out = maxLimit
        }
        return out
    }

    fun activityFromJson(jsoActivity: JSONObject?): AActivity {
        if (jsoActivity == null || jsoActivity.length() == 0) return AActivity.EMPTY

        val oid = jsoActivity.optString(ID)
        if (jsoActivity.length() == 1 && oid.isNotBlank()) {
            val activity: AActivity = AActivity.from(data.getAccountActor(), ActivityType.UNKNOWN)
            return activity.setOid(oid)
        }

        val activityType: ActivityType = ActivityType.from(JsonUtils.optString(jsoActivity, "type"))
        val activity: AActivity = AActivity.from(
            data.getAccountActor(),
            if (activityType == ActivityType.EMPTY) ActivityType.UPDATE else activityType
        )
        return try {
            if (ApObjectType.ACTIVITY.isTypeOf(jsoActivity)) {
                parseActivity(activity, jsoActivity)
            } else {
                parseObjectOfActivity(activity, jsoActivity)
            }
        } catch (e: JSONException) {
            throw ConnectionException.loggedJsonException(this, "Parsing activity", e, jsoActivity)
        }
    }

    fun activityFromOid(oid: String?): AActivity {
        return if (StringUtil.isEmptyOrTemp(oid)) AActivity.EMPTY
        else AActivity.from(data.getAccountActor(), ActivityType.UPDATE)
    }

    private fun activityFromJson(objectOrId: ObjectOrId): AActivity {
        return if (objectOrId.id.isPresent) {
            newPartialNote(data.getAccountActor(), Actor.EMPTY, objectOrId.id.get())
                .setOid(objectOrId.id.get())
        } else if (objectOrId.optObj.isPresent) {
            activityFromJson(objectOrId.optObj.get())
        } else {
            AActivity.EMPTY
        }
    }

    private fun parseActivity(activity: AActivity, jsoActivity: JSONObject): AActivity {
        val oid = JsonUtils.optString(jsoActivity, ID)
        if (oid.isEmpty()) {
            MyLog.d(this, "Activity has no id:" + jsoActivity.toString(2))
            return AActivity.EMPTY
        }
        activity.setOid(oid).setUpdatedDate(updatedOrCreatedDate(jsoActivity))
        actorFromProperty(jsoActivity, "actor").onSuccess { actor: Actor -> activity.setActor(actor) }
        val `object`: ObjectOrId = ObjectOrId.of(jsoActivity, "object")
            .ifId { id: String ->
                when (ApObjectType.fromId(activity.type, id)) {
                    ApObjectType.PERSON -> activity.setObjActor(actorFromOid(id))
                    ApObjectType.NOTE -> activity.setNote(
                        Note.fromOriginAndOid(
                            data.getOrigin(),
                            id,
                            DownloadStatus.UNKNOWN
                        )
                    )
                    else -> MyLog.w(this, "Unknown type of id:$id")
                }
            }.ifObject { objectOfActivity: JSONObject ->
                if (ApObjectType.ACTIVITY.isTypeOf(objectOfActivity)) {
                    // Simplified dealing with nested activities
                    val innerActivity = activityFromJson(objectOfActivity)
                    activity.setObjActor(innerActivity.getObjActor())
                    activity.setNote(innerActivity.getNote())
                } else {
                    parseObjectOfActivity(activity, objectOfActivity)
                }
            }
        if (`object`.error.isPresent) throw `object`.error.get()
        if (activity.getObjectType() == AObjectType.NOTE) {
            setAudience(activity, jsoActivity)
            if (activity.getAuthor().isEmpty) {
                activity.setAuthor(activity.getActor())
            }
        }
        return activity
    }

    private fun actorFromProperty(parentObject: JSONObject, propertyName: String): Try<Actor> {
        return ObjectOrId.of(parentObject, propertyName)
            .mapOne({ jso: JSONObject -> actorFromJson(jso) }, { id: String -> actorFromOid(id) })
    }

    private fun setAudience(activity: AActivity, jso: JSONObject) {
        val audience = Audience(data.getOrigin())
        ObjectOrId.of(jso, "to")
            .mapAll<Actor>({ jso1: JSONObject -> actorFromJson(jso1) }, { id: String? -> actorFromOid(id) })
            .forEach(Consumer { o: Actor -> addRecipient(o, audience) })
        ObjectOrId.of(jso, "cc")
            .mapAll<Actor>({ jso1: JSONObject -> actorFromJson(jso1) }, { id: String? -> actorFromOid(id) })
            .forEach(Consumer { o: Actor -> addRecipient(o, audience) })
        if (audience.hasNonSpecial()) {
            audience.addVisibility(Visibility.PRIVATE)
        }
        activity.getNote().setAudience(audience)
    }

    private fun addRecipient(recipient: Actor, audience: Audience) {
        audience.add(
            if (PUBLIC_COLLECTION_ID == recipient.oid) Actor.PUBLIC else recipient
        )
    }

    private fun actorFromOid(id: String?): Actor {
        return Actor.fromOid(data.getOrigin(), id)
    }

    fun updatedOrCreatedDate(jso: JSONObject?): Long {
        val dateUpdated = dateFromJson(jso, "updated")
        return if (dateUpdated > RelativeTime.SOME_TIME_AGO) dateUpdated else dateFromJson(jso, "published")
    }

    private fun parseObjectOfActivity(activity: AActivity, objectOfActivity: JSONObject): AActivity {
        if (ApObjectType.PERSON.isTypeOf(objectOfActivity)) {
            activity.setObjActor(actorFromJson(objectOfActivity))
            if (activity.getOid().isEmpty()) {
                activity.setOid(activity.getObjActor().oid)
            }
        } else if (ApObjectType.compatibleWith(objectOfActivity) === ApObjectType.NOTE) {
            noteFromJson(activity, objectOfActivity)
            if (activity.getOid().isEmpty()) {
                activity.setOid(activity.getNote().oid)
            }
        }
        return activity
    }

    private fun noteFromJson(parentActivity: AActivity, jso: JSONObject) {
        try {
            val oid = JsonUtils.optString(jso, ID)
            if (oid.isEmpty()) {
                MyLog.d(TAG, "ActivityPub object has no id:" + jso.toString(2))
                return
            }
            val author = actorFromProperty(jso, "attributedTo")
                .orElse { actorFromProperty(jso, "author") }.getOrElse(Actor.EMPTY)
            val noteActivity: AActivity = newPartialNote(
                data.getAccountActor(),
                author,
                oid,
                updatedOrCreatedDate(jso),
                DownloadStatus.LOADED
            )
            val activity: AActivity?
            when (parentActivity.type) {
                ActivityType.UPDATE, ActivityType.CREATE, ActivityType.DELETE -> {
                    activity = parentActivity
                    activity.setNote(noteActivity.getNote())
                    if (activity.getActor().isEmpty) {
                        MyLog.d(this, "No Actor in outer activity $activity")
                        activity.setActor(noteActivity.getActor())
                    }
                }
                else -> {
                    activity = noteActivity
                    parentActivity.setActivity(noteActivity)
                }
            }
            val note = activity.getNote()
            note.setName(JsonUtils.optString(jso, NAME_PROPERTY))
            note.setSummary(JsonUtils.optString(jso, SUMMARY_PROPERTY))
            note.setContentPosted(JsonUtils.optString(jso, CONTENT_PROPERTY))
            note.setSensitive(jso.optBoolean(SENSITIVE_PROPERTY))
            note.setLikesCount(jso.optLong("likesCount")) // I didn't see this field yet
            note.setReblogsCount(jso.optLong("reblogsCount")) // I didn't see this field yet
            note.setRepliesCount(jso.optLong("repliesCount"))
            note.setConversationOid(StringUtil.optNotEmpty(JsonUtils.optString(jso, "conversation"))
                .orElseGet { JsonUtils.optString(jso, "context") })
            setAudience(activity, jso)

            // If the Note is a Reply to the other note
            ObjectOrId.of(jso, "inReplyTo")
                .mapOne<AActivity?>(
                    { jsoActivity: JSONObject? -> this.activityFromJson(jsoActivity) },
                    { oid1: String? -> activityFromOid(oid1) })
                .onSuccess { activity1: AActivity? -> note.setInReplyTo(activity1) }
            if (jso.has("replies")) {
                val replies = jso.getJSONObject("replies")
                if (replies.has("items")) {
                    val jArr = replies.getJSONArray("items")
                    for (index in 0 until jArr.length()) {
                        val item: AActivity = activityFromJson(ObjectOrId.of(jArr, index))
                        if (item !== AActivity.EMPTY) {
                            note.replies.add(item)
                        }
                    }
                }
            }
            ObjectOrId.of(jso, "attachment")
                .mapAll({ jso1: JSONObject -> attachmentFromJson(jso1) },
                    { uriString: String? -> Attachment.fromUri(uriString) })
                .forEach(Consumer { attachment: Attachment -> activity.addAttachment(attachment) })
        } catch (e: JSONException) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso)
        }
    }

    override fun follow(actorOid: String, follow: Boolean): Try<AActivity> {
        return actOnActor(if (follow) ActivityType.FOLLOW else ActivityType.UNDO_FOLLOW, actorOid)
    }

    private fun actOnActor(activityType: ActivityType, actorId: String): Try<AActivity> {
        return ActivitySender.sendActor(this, activityType, Actor.fromOid(data.getOrigin(), actorId))
    }

    public override fun getActor2(actorIn: Actor): Try<Actor> = ConnectionAndUrl
        .fromActor(this, ApiRoutineEnum.GET_ACTOR, TimelinePosition.EMPTY, actorIn)
        .flatMap { conu: ConnectionAndUrl -> conu.newRequest().let(conu::execute) }
        .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
        .map { jso: JSONObject -> actorFromJson(jso) }

    companion object {
        private val TAG: String = ConnectionActivityPub::class.simpleName!!
        const val PUBLIC_COLLECTION_ID: String = "https://www.w3.org/ns/activitystreams#Public"
        const val NAME_PROPERTY: String = "name"
        const val SUMMARY_PROPERTY: String = "summary"
        const val SENSITIVE_PROPERTY: String = "sensitive"
        const val CONTENT_PROPERTY: String = "content"
        const val ID: String = "id"

        private fun attachmentFromJson(jso: JSONObject): Attachment {
            return ObjectOrId.of(jso, "url")
                .mapAll<Attachment>({ jso1: JSONObject -> attachmentFromUrlObject(jso1) },
                    { url: String? -> Attachment.fromUriAndMimeType(url, JsonUtils.optString(jso, "mediaType")) })
                .stream().findFirst().orElse(Attachment.EMPTY)
        }

        private fun attachmentFromUrlObject(jso: JSONObject): Attachment {
            return Attachment.fromUriAndMimeType(
                JsonUtils.optString(jso, "href"),
                JsonUtils.optString(jso, "mediaType")
            )
        }
    }
}
