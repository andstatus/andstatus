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
import io.vavr.control.CheckedConsumer
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.actor.GroupType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.AActivity
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
import java.util.concurrent.Callable
import java.util.function.Consumer

class ConnectionActivityPub : Connection() {
    override fun setAccountConnectionData(connectionData: AccountConnectionData?): Connection? {
        val host = connectionData.getAccountActor().connectionHost
        if (StringUtil.nonEmpty(host)) {
            connectionData.setOriginUrl(UrlUtils.buildUrl(host, connectionData.isSsl()))
        }
        return super.setAccountConnectionData(connectionData)
    }

    override fun getApiPathFromOrigin(routine: ApiRoutineEnum?): String {
        val url: String
        url = when (routine) {
            ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS -> "whoami"
            else -> ""
        }
        return partialPathToApiPath(url)
    }

    override fun verifyCredentials(whoAmI: Optional<Uri?>?): Try<Actor?> {
        return TryUtils.fromOptional(whoAmI)
                .filter { obj: Uri? -> UriUtils.isDownloadable() }
                .orElse { getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS) }
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .recoverWith(Exception::class.java) { originalException: Exception? -> mastodonsHackToVerifyCredentials(originalException) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jso: JSONObject? -> actorFromJson(jso) }
    }

    /** @return  original error, if this Mastodon's hack didn't work
     */
    private fun mastodonsHackToVerifyCredentials(originalException: Exception?): Try<HttpReadResult?>? {
        // Get Username first by partially parsing Mastodon's non-ActivityPub response
        val userNameInMastodon = Try.success(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS)
                .map(CheckedFunction<ApiRoutineEnum?, String?> { routine: ApiRoutineEnum? -> ConnectionMastodon.Companion.partialPath(routine) })
                .map { partialPath: String? -> OriginType.MASTODON.partialPathToApiPath(partialPath) }
                .flatMap { path: String? -> pathToUri(path) }
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jso: JSONObject? -> JsonUtils.optString(jso, "username") }
                .filter { obj: String? -> StringUtil.nonEmpty() }

        // Now build the Actor's Uri artificially using Mastodon's known URL pattern
        return userNameInMastodon.map { username: String? -> "users/$username" }
                .flatMap { path: String? -> pathToUri(path) }
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(ApiRoutineEnum.GET_ACTOR, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .recoverWith(Exception::class.java) { newException: Exception? -> Try.failure(originalException) }
    }

    private fun actorFromJson(jso: JSONObject?): Actor {
        return when (ApObjectType.Companion.fromJson(jso)) {
            ApObjectType.PERSON -> actorFromPersonTypeJson(jso)
            ApObjectType.COLLECTION, ApObjectType.ORDERED_COLLECTION -> actorFromCollectionTypeJson(jso)
            ApObjectType.UNKNOWN -> Actor.Companion.EMPTY
            else -> {
                MyLog.w(TAG, """
     Unexpected object type for Actor: ${ApObjectType.Companion.fromJson(jso)}, JSON:
     $jso
     """.trimIndent())
                Actor.Companion.EMPTY
            }
        }
    }

    private fun actorFromCollectionTypeJson(jso: JSONObject?): Actor {
        val actor: Actor = Actor.Companion.fromTwoIds(data.origin, GroupType.COLLECTION, 0, JsonUtils.optString(jso, "id"))
        return actor.build()
    }

    private fun actorFromPersonTypeJson(jso: JSONObject?): Actor {
        val actor = actorFromOid(JsonUtils.optString(jso, "id"))
        actor.setUsername(JsonUtils.optString(jso, "preferredUsername"))
        actor.setRealName(JsonUtils.optString(jso, NAME_PROPERTY))
        actor.setAvatarUrl(JsonUtils.optStringInside(jso, "icon", "url"))
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY)
        actor.setSummary(JsonUtils.optString(jso, "summary"))
        actor.setHomepage(JsonUtils.optString(jso, "url"))
        actor.setProfileUrl(JsonUtils.optString(jso, "url"))
        actor.setUpdatedDate(dateFromJson(jso, "updated"))
        actor.endpoints
                .add(ActorEndpointType.API_PROFILE, JsonUtils.optString(jso, "id"))
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

    override fun undoLike(noteOid: String?): Try<AActivity?>? {
        return actOnNote(ActivityType.UNDO_LIKE, noteOid)
    }

    override fun like(noteOid: String?): Try<AActivity?>? {
        return actOnNote(ActivityType.LIKE, noteOid)
    }

    override fun deleteNote(noteOid: String?): Try<Boolean?>? {
        return actOnNote(ActivityType.DELETE, noteOid).map(CheckedFunction { obj: AActivity? -> obj.isEmpty() })
    }

    private fun actOnNote(activityType: ActivityType?, noteId: String?): Try<AActivity?>? {
        return ActivitySender.Companion.fromId(this, noteId).send(activityType)
    }

    override fun getFriendsOrFollowers(routineEnum: ApiRoutineEnum?, position: TimelinePosition?, actor: Actor?): Try<InputActorPage?>? {
        return getActors(routineEnum, position, actor)
    }

    private fun getActors(apiRoutine: ApiRoutineEnum?, position: TimelinePosition?, actor: Actor?): Try<InputActorPage?> {
        return ConnectionAndUrl.Companion.fromActor(this, apiRoutine, position, actor)
                .flatMap<InputActorPage?>(CheckedFunction<ConnectionAndUrl?, Try<out InputActorPage?>?> { conu: ConnectionAndUrl? ->
                    conu.execute(conu.newRequest())
                            .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                            .map(CheckedFunction { jsonObject: JSONObject? ->
                                val jsonCollection: AJsonCollection = of(jsonObject)
                                val actors = jsonCollection.mapAll({ jso: JSONObject? -> actorFromJson(jso) }) { id: String? -> actorFromOid(id) }
                                MyLog.v(TAG) { apiRoutine.toString() + " '" + conu.uri + "' " + actors.size + " actors" }
                                InputActorPage.Companion.of(jsonCollection, actors)
                            })
                }
                )
    }

    override fun getNote1(noteOid: String?): Try<AActivity?>? {
        return execute(HttpRequest.Companion.of(ApiRoutineEnum.GET_NOTE, UriUtils.fromString(noteOid)))
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jsoActivity: JSONObject? -> this.activityFromJson(jsoActivity) }
    }

    override fun updateNote(note: Note?): Try<AActivity?>? {
        return ActivitySender.Companion.fromContent(this, note).send(ActivityType.CREATE)
    }

    override fun announce(rebloggedNoteOid: String?): Try<AActivity?>? {
        return actOnNote(ActivityType.ANNOUNCE, rebloggedNoteOid)
    }

    override fun canGetConversation(conversationOid: String?): Boolean {
        val uri = UriUtils.fromString(conversationOid)
        return UriUtils.isDownloadable(uri)
    }

    override fun getConversation(conversationOid: String?): Try<MutableList<AActivity?>?>? {
        val uri = UriUtils.fromString(conversationOid)
        return if (UriUtils.isDownloadable(uri)) {
            ConnectionAndUrl.Companion.fromUriActor(uri, this, ApiRoutineEnum.GET_CONVERSATION, data.accountActor)
                    .flatMap<InputTimelinePage?>(CheckedFunction<ConnectionAndUrl?, Try<out InputTimelinePage?>?> { conu: ConnectionAndUrl? -> getActivities(conu) })
                    .map<MutableList<AActivity?>?>(CheckedFunction { p: InputTimelinePage? -> p.items })
        } else {
            super.getConversation(conversationOid)
        }
    }

    override fun getTimeline(syncYounger: Boolean, apiRoutine: ApiRoutineEnum?,
                             youngestPosition: TimelinePosition?, oldestPosition: TimelinePosition?, limit: Int, actor: Actor?): Try<InputTimelinePage?> {
        val requestedPosition = if (syncYounger) youngestPosition else oldestPosition

        // TODO: See https://github.com/andstatus/andstatus/issues/499#issuecomment-475881413
        return ConnectionAndUrl.Companion.fromActor(this, apiRoutine, requestedPosition, actor)
                .map<ConnectionAndUrl?>(CheckedFunction { conu: ConnectionAndUrl? -> conu.withSyncDirection(syncYounger) })
                .flatMap<InputTimelinePage?>(CheckedFunction<ConnectionAndUrl?, Try<out InputTimelinePage?>?> { conu: ConnectionAndUrl? -> getActivities(conu) })
    }

    private fun getActivities(conu: ConnectionAndUrl?): Try<InputTimelinePage?>? {
        return conu.execute(conu.newRequest())
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map(CheckedFunction { root: JSONObject? ->
                    val jsonCollection: AJsonCollection = of(root)
                    val activities = jsonCollection.mapObjects(CheckedFunction<JSONObject?, AActivity?> { item: JSONObject? ->
                        activityFromJson(ObjectOrId.Companion.of(item))
                                .setTimelinePositions(jsonCollection.prevId, jsonCollection.nextId)
                    }
                    )
                    MyLog.d(TAG, "getTimeline " + conu.apiRoutine + " '" + conu.uri + "' " + activities.size + " activities")
                    InputTimelinePage.Companion.of(jsonCollection, activities)
                })
    }

    override fun fixedDownloadLimit(limit: Int, apiRoutine: ApiRoutineEnum?): Int {
        val maxLimit = if (apiRoutine == ApiRoutineEnum.GET_FRIENDS) 200 else 20
        var out = super.fixedDownloadLimit(limit, apiRoutine)
        if (out > maxLimit) {
            out = maxLimit
        }
        return out
    }

    @Throws(ConnectionException::class)
    fun activityFromJson(jsoActivity: JSONObject?): AActivity {
        if (jsoActivity == null) return AActivity.Companion.EMPTY
        val activityType: ActivityType = ActivityType.Companion.from(JsonUtils.optString(jsoActivity, "type"))
        val activity: AActivity = AActivity.Companion.from(data.accountActor,
                if (activityType == ActivityType.EMPTY) ActivityType.UPDATE else activityType)
        return try {
            if (ApObjectType.ACTIVITY.isTypeOf(jsoActivity)) {
                parseActivity(activity, jsoActivity)
            } else {
                parseObjectOfActivity(activity, jsoActivity)
            }
        } catch (e: JSONException) {
            throw ConnectionException.Companion.loggedJsonException(this, "Parsing activity", e, jsoActivity)
        }
    }

    fun activityFromOid(oid: String?): AActivity {
        return if (StringUtil.isEmptyOrTemp(oid)) AActivity.Companion.EMPTY else AActivity.Companion.from(data.accountActor, ActivityType.UPDATE)
    }

    @Throws(ConnectionException::class)
    private fun activityFromJson(objectOrId: ObjectOrId?): AActivity {
        return if (objectOrId.id.isPresent) {
            newPartialNote(data.accountActor, Actor.Companion.EMPTY, objectOrId.id.get())
                    .setOid(objectOrId.id.get())
        } else if (objectOrId.`object`.isPresent) {
            activityFromJson(objectOrId.`object`.get())
        } else {
            AActivity.Companion.EMPTY
        }
    }

    @Throws(JSONException::class, ConnectionException::class)
    private fun parseActivity(activity: AActivity?, jsoActivity: JSONObject?): AActivity? {
        val oid = JsonUtils.optString(jsoActivity, "id")
        if (StringUtil.isEmpty(oid)) {
            MyLog.d(this, "Activity has no id:" + jsoActivity.toString(2))
            return AActivity.Companion.EMPTY
        }
        activity.setOid(oid).updatedDate = updatedOrCreatedDate(jsoActivity)
        actorFromProperty(jsoActivity, "actor").onSuccess(Consumer { actor: Actor? -> activity.setActor(actor) })
        val `object`: ObjectOrId = ObjectOrId.Companion.of(jsoActivity, "object")
                .ifId(CheckedConsumer { id: String? ->
                    when (ApObjectType.Companion.fromId(activity.type, id)) {
                        ApObjectType.PERSON -> activity.setObjActor(actorFromOid(id))
                        ApObjectType.NOTE -> activity.setNote(Note.Companion.fromOriginAndOid(data.origin, id, DownloadStatus.UNKNOWN))
                        else -> MyLog.w(this, "Unknown type of id:$id")
                    }
                }).ifObject(CheckedConsumer { objectOfActivity: JSONObject? ->
                    if (ApObjectType.ACTIVITY.isTypeOf(objectOfActivity)) {
                        // Simplified dealing with nested activities
                        val innerActivity = activityFromJson(objectOfActivity)
                        activity.setObjActor(innerActivity.objActor)
                        activity.setNote(innerActivity.note)
                    } else {
                        parseObjectOfActivity(activity, objectOfActivity)
                    }
                })
        if (`object`.error.isPresent) throw `object`.error.get()
        if (activity.getObjectType() == AObjectType.NOTE) {
            setAudience(activity, jsoActivity)
            if (activity.getAuthor().isEmpty) {
                activity.setAuthor(activity.getActor())
            }
        }
        return activity
    }

    private fun actorFromProperty(parentObject: JSONObject?, propertyName: String?): Try<Actor?>? {
        return ObjectOrId.Companion.of(parentObject, propertyName).mapOne<Actor?>(CheckedFunction { jso: JSONObject? -> actorFromJson(jso) }, CheckedFunction { id: String? -> actorFromOid(id) })
    }

    private fun setAudience(activity: AActivity?, jso: JSONObject?) {
        val audience = Audience(data.origin)
        ObjectOrId.Companion.of(jso, "to")
                .mapAll<Actor?>(CheckedFunction { jso: JSONObject? -> actorFromJson(jso) }, CheckedFunction { id: String? -> actorFromOid(id) })
                .forEach(Consumer { o: Actor? -> addRecipient(o, audience) })
        ObjectOrId.Companion.of(jso, "cc")
                .mapAll<Actor?>(CheckedFunction { jso: JSONObject? -> actorFromJson(jso) }, CheckedFunction { id: String? -> actorFromOid(id) })
                .forEach(Consumer { o: Actor? -> addRecipient(o, audience) })
        if (audience.hasNonSpecial()) {
            audience.addVisibility(Visibility.PRIVATE)
        }
        activity.getNote().setAudience(audience)
    }

    private fun addRecipient(recipient: Actor?, audience: Audience?) {
        audience.add(
                if (PUBLIC_COLLECTION_ID == recipient.oid) Actor.Companion.PUBLIC else recipient)
    }

    private fun actorFromOid(id: String?): Actor? {
        return Actor.Companion.fromOid(data.origin, id)
    }

    fun updatedOrCreatedDate(jso: JSONObject?): Long {
        val dateUpdated = dateFromJson(jso, "updated")
        return if (dateUpdated > RelativeTime.SOME_TIME_AGO) dateUpdated else dateFromJson(jso, "published")
    }

    @Throws(ConnectionException::class)
    private fun parseObjectOfActivity(activity: AActivity?, objectOfActivity: JSONObject?): AActivity? {
        if (ApObjectType.PERSON.isTypeOf(objectOfActivity)) {
            activity.setObjActor(actorFromJson(objectOfActivity))
            if (activity.getOid().isEmpty()) {
                activity.setOid(activity.getObjActor().oid)
            }
        } else if (ApObjectType.Companion.compatibleWith(objectOfActivity) === ApObjectType.NOTE) {
            noteFromJson(activity, objectOfActivity)
            if (activity.getOid().isEmpty()) {
                activity.setOid(activity.getNote().oid)
            }
        }
        return activity
    }

    @Throws(ConnectionException::class)
    private fun noteFromJson(parentActivity: AActivity?, jso: JSONObject?) {
        try {
            val oid = JsonUtils.optString(jso, "id")
            if (StringUtil.isEmpty(oid)) {
                MyLog.d(TAG, "ActivityPub object has no id:" + jso.toString(2))
                return
            }
            val author = actorFromProperty(jso, "attributedTo")
                    .orElse(Callable<Try<out Actor?>?> { actorFromProperty(jso, "author") }).getOrElse(Actor.Companion.EMPTY)
            val noteActivity: AActivity = AActivity.Companion.newPartialNote(
                    data.accountActor,
                    author,
                    oid,
                    updatedOrCreatedDate(jso),
                    DownloadStatus.LOADED)
            val activity: AActivity?
            when (parentActivity.type) {
                ActivityType.UPDATE, ActivityType.CREATE, ActivityType.DELETE -> {
                    activity = parentActivity
                    activity.setNote(noteActivity.note)
                    if (activity.getActor().isEmpty) {
                        MyLog.d(this, "No Actor in outer activity $activity")
                        activity.setActor(noteActivity.actor)
                    }
                }
                else -> {
                    activity = noteActivity
                    parentActivity.setActivity(noteActivity)
                }
            }
            val note = activity.getNote()
            note.name = JsonUtils.optString(jso, NAME_PROPERTY)
            note.summary = JsonUtils.optString(jso, SUMMARY_PROPERTY)
            note.setContentPosted(JsonUtils.optString(jso, CONTENT_PROPERTY))
            note.isSensitive = jso.optBoolean(SENSITIVE_PROPERTY)
            note.likesCount = jso.optLong("likesCount") // I didn't see this field yet
            note.reblogsCount = jso.optLong("reblogsCount") // I didn't see this field yet
            note.repliesCount = jso.optLong("repliesCount")
            note.setConversationOid(StringUtil.optNotEmpty(JsonUtils.optString(jso, "conversation"))
                    .orElseGet { JsonUtils.optString(jso, "context") })
            setAudience(activity, jso)

            // If the Note is a Reply to the other note
            ObjectOrId.Companion.of(jso, "inReplyTo")
                    .mapOne<AActivity?>(CheckedFunction { jsoActivity: JSONObject? -> this.activityFromJson(jsoActivity) }, CheckedFunction { oid: String? -> activityFromOid(oid) })
                    .onSuccess(Consumer { activity: AActivity? -> note.setInReplyTo(activity) })
            if (jso.has("replies")) {
                val replies = jso.getJSONObject("replies")
                if (replies.has("items")) {
                    val jArr = replies.getJSONArray("items")
                    for (index in 0 until jArr.length()) {
                        val item: AActivity = activityFromJson(ObjectOrId.Companion.of(jArr, index))
                        if (item !== AActivity.Companion.EMPTY) {
                            note.replies.add(item)
                        }
                    }
                }
            }
            ObjectOrId.Companion.of(jso, "attachment")
                    .mapAll<Attachment?>(CheckedFunction { jso: JSONObject? -> attachmentFromJson(jso) }, CheckedFunction<String?, Attachment?> { uriString: String? -> Attachment.Companion.fromUri(uriString) })
                    .forEach(Consumer { attachment: Attachment? -> activity.addAttachment(attachment) })
        } catch (e: JSONException) {
            throw ConnectionException.Companion.loggedJsonException(this, "Parsing note", e, jso)
        }
    }

    override fun follow(actorOid: String?, follow: Boolean?): Try<AActivity?>? {
        return actOnActor(if (follow) ActivityType.FOLLOW else ActivityType.UNDO_FOLLOW, actorOid)
    }

    private fun actOnActor(activityType: ActivityType?, actorId: String?): Try<AActivity?>? {
        return ActivitySender.Companion.fromId(this, actorId).send(activityType)
    }

    public override fun getActor2(actorIn: Actor?): Try<Actor?>? {
        return ConnectionAndUrl.Companion.fromActor(this, ApiRoutineEnum.GET_ACTOR, TimelinePosition.Companion.EMPTY, actorIn)
                .flatMap<HttpReadResult?>(CheckedFunction<ConnectionAndUrl?, Try<out HttpReadResult?>?> { connectionAndUrl: ConnectionAndUrl? -> connectionAndUrl.execute(connectionAndUrl.newRequest()) })
                .flatMap<JSONObject?>(CheckedFunction<HttpReadResult?, Try<out JSONObject?>?> { obj: HttpReadResult? -> obj.getJsonObject() })
                .map<Actor?>(CheckedFunction { jso: JSONObject? -> actorFromJson(jso) })
    }

    companion object {
        private val TAG: String? = ConnectionActivityPub::class.java.simpleName
        val PUBLIC_COLLECTION_ID: String? = "https://www.w3.org/ns/activitystreams#Public"
        val APPLICATION_ID: String? = "http://andstatus.org/andstatus"
        val NAME_PROPERTY: String? = "name"
        val SUMMARY_PROPERTY: String? = "summary"
        val SENSITIVE_PROPERTY: String? = "sensitive"
        val CONTENT_PROPERTY: String? = "content"
        val VIDEO_OBJECT: String? = "stream"
        val IMAGE_OBJECT: String? = "image"
        val FULL_IMAGE_OBJECT: String? = "fullImage"
        private fun attachmentFromJson(jso: JSONObject?): Attachment {
            return ObjectOrId.Companion.of(jso, "url")
                    .mapAll<Attachment?>(CheckedFunction { jso: JSONObject? -> attachmentFromUrlObject(jso) },
                            CheckedFunction<String?, Attachment?> { url: String? -> Attachment.Companion.fromUriAndMimeType(url, JsonUtils.optString(jso, "mediaType")) })
                    .stream().findFirst().orElse(Attachment.Companion.EMPTY)
        }

        private fun attachmentFromUrlObject(jso: JSONObject?): Attachment {
            return Attachment.Companion.fromUriAndMimeType(JsonUtils.optString(jso, "href"), JsonUtils.optString(jso, "mediaType"))
        }
    }
}