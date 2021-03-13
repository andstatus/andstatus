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
import io.vavr.control.Try
import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.actor.GroupType
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyContentType
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Connection
import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.ObjectOrId
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.function.Consumer
import java.util.function.UnaryOperator

/**
 * Implementation of pump.io API: [https://github.com/e14n/pump.io/blob/master/API.md](https://github.com/e14n/pump.io/blob/master/API.md)
 * @author yvolk@yurivolkov.com
 */
class ConnectionPumpio : Connection() {

    override fun setAccountConnectionData(connectionData: AccountConnectionData?): Connection? {
        val host = connectionData.getAccountActor().getConnectionHost()
        if (host.isNotEmpty()) {
            connectionData.setOriginUrl(UrlUtils.buildUrl(host, connectionData.isSsl()))
        }
        return super.setAccountConnectionData(connectionData)
    }

    override fun getApiPathFromOrigin(routine: ApiRoutineEnum): String {
        val url: String
        url = when (routine) {
            ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS -> "whoami"
            ApiRoutineEnum.GET_FOLLOWERS, ApiRoutineEnum.GET_FOLLOWERS_IDS -> "user/%username%/followers"
            ApiRoutineEnum.GET_FRIENDS, ApiRoutineEnum.GET_FRIENDS_IDS -> "user/%username%/following"
            ApiRoutineEnum.GET_ACTOR -> "user/%username%/profile"
            ApiRoutineEnum.HOME_TIMELINE -> "user/%username%/inbox"
            ApiRoutineEnum.LIKED_TIMELINE -> "user/%username%/favorites"
            ApiRoutineEnum.UPLOAD_MEDIA -> "user/%username%/uploads"
            ApiRoutineEnum.LIKE, ApiRoutineEnum.UNDO_LIKE, ApiRoutineEnum.FOLLOW, ApiRoutineEnum.UPDATE_PRIVATE_NOTE, ApiRoutineEnum.ANNOUNCE, ApiRoutineEnum.DELETE_NOTE, ApiRoutineEnum.UPDATE_NOTE, ApiRoutineEnum.ACTOR_TIMELINE -> "user/%username%/feed"
            else -> ""
        }
        return partialPathToApiPath(url)
    }

    override fun verifyCredentials(whoAmI: Optional<Uri>): Try<Actor?> {
        return TryUtils.fromOptional(whoAmI)
                .filter { obj: Uri? -> UriUtils.isDownloadable() }
                .orElse { getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS) }
                .map(CheckedFunction<Uri?, HttpRequest?> { uri: Uri? -> HttpRequest.Companion.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri) })
                .flatMap { request: HttpRequest? -> execute(request) }
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jso: JSONObject? -> actorFromJson(jso) }
    }

    @Throws(ConnectionException::class)
    protected fun actorFromJson(jso: JSONObject?): Actor {
        val groupType: GroupType
        groupType = when (PObjectType.Companion.fromJson(jso)) {
            PObjectType.PERSON -> GroupType.NOT_A_GROUP
            PObjectType.COLLECTION -> GroupType.COLLECTION
            else -> return Actor.EMPTY
        }
        val oid = JsonUtils.optString(jso, "id")
        val actor: Actor = Actor.Companion.fromTwoIds(data.origin, groupType, 0, oid)
        val username = JsonUtils.optString(jso, "preferredUsername")
        actor.username = if (username.isNullOrEmpty()) actorOidToUsername(oid) else username
        actor.realName = JsonUtils.optString(jso, NAME_PROPERTY)
        actor.avatarUrl = JsonUtils.optStringInside(jso, "image", "url")
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY)
        actor.summary = JsonUtils.optString(jso, "summary")
        actor.homepage = JsonUtils.optString(jso, "url")
        actor.profileUrl = JsonUtils.optString(jso, "url")
        actor.updatedDate = dateFromJson(jso, "updated")
        val pumpIo = jso.optJSONObject("pump_io")
        if (pumpIo != null && !pumpIo.isNull("followed")) {
            actor.isMyFriend = TriState.Companion.fromBoolean(pumpIo.optBoolean("followed"))
        }
        val links = jso.optJSONObject("links")
        if (links != null) {
            actor.endpoints.add(ActorEndpointType.API_PROFILE, JsonUtils.optStringInside(links, "self", "href"))
                    .add(ActorEndpointType.API_INBOX, JsonUtils.optStringInside(links, "activity-inbox", "href"))
                    .add(ActorEndpointType.API_OUTBOX, JsonUtils.optStringInside(links, "activity-outbox", "href"))
        }
        actor.endpoints.add(ActorEndpointType.API_FOLLOWING, JsonUtils.optStringInside(jso, "following", "url"))
                .add(ActorEndpointType.API_FOLLOWERS, JsonUtils.optStringInside(jso, "followers", "url"))
                .add(ActorEndpointType.API_LIKED, JsonUtils.optStringInside(jso, "favorites", "url"))
        return actor.build()
    }

    private fun actorFromOid(id: String?): Actor? {
        return Actor.Companion.fromOid(data.origin, id)
    }

    override fun parseDate(stringDate: String?): Long {
        return parseIso8601Date(stringDate)
    }

    override fun undoLike(noteOid: String?): Try<AActivity> {
        return actOnNote(PActivityType.UNFAVORITE, noteOid)
    }

    override fun like(noteOid: String?): Try<AActivity> {
        return actOnNote(PActivityType.FAVORITE, noteOid)
    }

    override fun deleteNote(noteOid: String?): Try<Boolean> {
        return actOnNote(PActivityType.DELETE, noteOid).map(CheckedFunction { obj: AActivity? -> obj.nonEmpty })
    }

    private fun actOnNote(activityType: PActivityType?, noteId: String?): Try<AActivity> {
        return ActivitySender.Companion.fromId(this, noteId).send(activityType)
    }

    override fun getFollowers(actor: Actor?): Try<MutableList<Actor>>? {
        return getActors(actor, ApiRoutineEnum.GET_FOLLOWERS)
    }

    override fun getFriends(actor: Actor?): Try<MutableList<Actor>>? {
        return getActors(actor, ApiRoutineEnum.GET_FRIENDS)
    }

    private fun getActors(actor: Actor?, apiRoutine: ApiRoutineEnum?): Try<MutableList<Actor>> {
        val limit = 200
        return ConnectionAndUrl.Companion.fromActor(this, apiRoutine, actor)
                .map<ConnectionAndUrl?>(CheckedFunction { conu: ConnectionAndUrl? ->
                    val builder = conu.uri.buildUpon()
                    builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine))
                    val uri = builder.build()
                    conu.withUri(uri)
                })
                .flatMap<HttpReadResult?>(CheckedFunction<ConnectionAndUrl?, Try<out HttpReadResult>> { conu: ConnectionAndUrl? -> conu.execute(conu.newRequest()) })
                .flatMap<MutableList<Actor?>?>(CheckedFunction<HttpReadResult?, Try<out MutableList<Actor>>?> { result: HttpReadResult? ->
                    result.getJsonArray()
                            .map { jsonArray: JSONArray? -> jsonArrayToActors(apiRoutine, result.request.uri, jsonArray) }
                })
    }

    @Throws(ConnectionException::class)
    private fun jsonArrayToActors(apiRoutine: ApiRoutineEnum?, uri: Uri?, jArr: JSONArray?): MutableList<Actor?>? {
        val actors: MutableList<Actor?> = ArrayList()
        if (jArr != null) {
            for (index in 0 until jArr.length()) {
                try {
                    val jso = jArr.getJSONObject(index)
                    val item = actorFromJson(jso)
                    actors.add(item)
                } catch (e: JSONException) {
                    throw ConnectionException.Companion.loggedJsonException(this, "Parsing list of actors", e, null)
                }
            }
        }
        MyLog.d(TAG, apiRoutine.toString() + " '" + uri + "' " + actors.size + " actors")
        return actors
    }

    override fun getNote1(noteOid: String?): Try<AActivity> {
        return execute(HttpRequest.Companion.of(ApiRoutineEnum.GET_NOTE, UriUtils.fromString(noteOid)))
                .flatMap { obj: HttpReadResult? -> obj.getJsonObject() }
                .map { jsoActivity: JSONObject? -> activityFromJson(jsoActivity) }
    }

    override fun updateNote(note: Note?): Try<AActivity> {
        return ActivitySender.Companion.fromContent(this, note).send(PActivityType.POST)
    }

    fun oidToObjectType(oid: String?): String? {
        var objectType = ""
        if (oid.contains("/comment/")) {
            objectType = "comment"
        } else if (oid.startsWith(OriginPumpio.Companion.ACCOUNT_PREFIX)) {
            objectType = "person"
        } else if (oid.contains("/note/")) {
            objectType = "note"
        } else if (oid.contains("/notice/")) {
            objectType = "note"
        } else if (oid.contains("/person/")) {
            objectType = "person"
        } else if (oid.contains("/collection/") || oid.endsWith("/followers")) {
            objectType = "collection"
        } else if (oid.contains("/user/")) {
            objectType = "person"
        } else {
            val pattern = "/api/"
            val indStart = oid.indexOf(pattern)
            if (indStart >= 0) {
                val indEnd = oid.indexOf("/", indStart + pattern.length)
                if (indEnd > indStart) {
                    objectType = oid.substring(indStart + pattern.length, indEnd)
                }
            }
        }
        if (objectType.isNullOrEmpty()) {
            objectType = "unknown object type: $oid"
            MyLog.w(this, objectType)
        }
        return objectType
    }

    override fun announce(rebloggedNoteOid: String?): Try<AActivity> {
        return actOnNote(PActivityType.SHARE, rebloggedNoteOid)
    }

    override fun getTimeline(syncYounger: Boolean, apiRoutine: ApiRoutineEnum?,
                             youngestPosition: TimelinePosition?, oldestPosition: TimelinePosition?, limit: Int, actor: Actor?): Try<InputTimelinePage?> {
        val tryConu: Try<ConnectionAndUrl?> = ConnectionAndUrl.Companion.fromActor(this, apiRoutine, actor)
        if (tryConu.isFailure) return tryConu.map(CheckedFunction { any: ConnectionAndUrl? -> InputTimelinePage.Companion.EMPTY })
        val conu = tryConu.get()
        val builder = conu.uri.buildUpon()
        if (youngestPosition.nonEmpty) {
            // The "since" should point to the "Activity" on the timeline, not to the note
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("since", youngestPosition.getPosition())
        } else if (oldestPosition.nonEmpty) {
            builder.appendQueryParameter("before", oldestPosition.getPosition())
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine))
        return execute(HttpRequest.Companion.of(apiRoutine, builder.build()))
                .flatMap { obj: HttpReadResult? -> obj.getJsonArray() }
                .map(CheckedFunction { jArr: JSONArray? ->
                    val activities: MutableList<AActivity?> = ArrayList()
                    if (jArr != null) {
                        // Read the activities in the chronological order
                        for (index in jArr.length() - 1 downTo 0) {
                            try {
                                val jso = jArr.getJSONObject(index)
                                activities.add(activityFromJson(jso))
                            } catch (e: JSONException) {
                                throw ConnectionException.Companion.loggedJsonException(this, "Parsing timeline", e, null)
                            }
                        }
                    }
                    MyLog.d(TAG, "getTimeline '" + builder.build() + "' " + activities.size + " activities")
                    InputTimelinePage.Companion.of(activities)
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
        val verb: PActivityType = PActivityType.Companion.load(JsonUtils.optString(jsoActivity, "verb"))
        val activity: AActivity = AActivity.Companion.from(data.accountActor,
                if (verb == PActivityType.UNKNOWN) ActivityType.UPDATE else verb.activityType)
        return try {
            if (PObjectType.ACTIVITY.isTypeOf(jsoActivity)) {
                parseActivity(activity, jsoActivity)
            } else {
                parseObjectOfActivity(activity, jsoActivity)
            }
        } catch (e: JSONException) {
            throw ConnectionException.Companion.loggedJsonException(this, "Parsing activity", e, jsoActivity)
        }
    }

    @Throws(JSONException::class, ConnectionException::class)
    private fun parseActivity(activity: AActivity?, jsoActivity: JSONObject?): AActivity? {
        val oid = JsonUtils.optString(jsoActivity, "id")
        if (oid.isNullOrEmpty()) {
            MyLog.d(this, "Pumpio activity has no id:" + jsoActivity.toString(2))
            return AActivity.Companion.EMPTY
        }
        activity.setOid(oid)
        activity.setUpdatedDate(dateFromJson(jsoActivity, "updated"))
        if (jsoActivity.has("actor")) {
            activity.setActor(actorFromJson(jsoActivity.getJSONObject("actor")))
        }
        val objectOfActivity = jsoActivity.getJSONObject("object")
        if (PObjectType.ACTIVITY.isTypeOf(objectOfActivity)) {
            // Simplified dealing with nested activities
            val innerActivity = activityFromJson(objectOfActivity)
            activity.setObjActor(innerActivity.objActor)
            activity.setNote(innerActivity.note)
        } else {
            parseObjectOfActivity(activity, objectOfActivity)
        }
        if (activity.getObjectType() == AObjectType.NOTE) {
            setAudience(activity, jsoActivity)
            setVia(activity.getNote(), jsoActivity)
            if (activity.getAuthor().isEmpty) {
                activity.setAuthor(activity.getActor())
            }
        }
        return activity
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

    @Throws(ConnectionException::class)
    private fun parseObjectOfActivity(activity: AActivity?, objectOfActivity: JSONObject?): AActivity? {
        if (PObjectType.PERSON.isTypeOf(objectOfActivity)) {
            activity.setObjActor(actorFromJson(objectOfActivity))
        } else if (PObjectType.Companion.compatibleWith(objectOfActivity) === PObjectType.COMMENT) {
            noteFromJsonComment(activity, objectOfActivity)
        }
        return activity
    }

    @Throws(JSONException::class)
    private fun setVia(note: Note?, activity: JSONObject?) {
        if (note.via.isNullOrEmpty() && activity.has(Properties.GENERATOR.code)) {
            val generator = activity.getJSONObject(Properties.GENERATOR.code)
            if (generator.has(NAME_PROPERTY)) {
                note.via = generator.getString(NAME_PROPERTY)
            }
        }
    }

    @Throws(ConnectionException::class)
    private fun noteFromJsonComment(parentActivity: AActivity?, jso: JSONObject?) {
        try {
            val oid = JsonUtils.optString(jso, "id")
            if (oid.isNullOrEmpty()) {
                MyLog.d(TAG, "Pumpio object has no id:" + jso.toString(2))
                return
            }
            var updatedDate = dateFromJson(jso, "updated")
            if (updatedDate == 0L) {
                updatedDate = dateFromJson(jso, "published")
            }
            val noteActivity: AActivity = AActivity.Companion.newPartialNote(data.accountActor,
                    if (jso.has("author")) actorFromJson(jso.getJSONObject("author")) else Actor.EMPTY,
                    oid,
                    updatedDate, DownloadStatus.LOADED)
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
            note.setContentPosted(JsonUtils.optString(jso, CONTENT_PROPERTY))
            setVia(note, jso)
            note.url = JsonUtils.optString(jso, "url")

            // If the Msg is a Reply to other note
            if (jso.has("inReplyTo")) {
                note.setInReplyTo(activityFromJson(jso.getJSONObject("inReplyTo")))
            }
            if (jso.has("replies")) {
                val replies = jso.getJSONObject("replies")
                if (replies.has("items")) {
                    val jArr = replies.getJSONArray("items")
                    for (index in 0 until jArr.length()) {
                        try {
                            val item = activityFromJson(jArr.getJSONObject(index))
                            note.replies.add(item)
                        } catch (e: JSONException) {
                            throw ConnectionException.Companion.loggedJsonException(this,
                                    "Parsing list of replies", e, null)
                        }
                    }
                }
            }
            if (jso.has(VIDEO_OBJECT)) {
                val uri = UriUtils.fromJson(jso, VIDEO_OBJECT + "/url")
                val mbAttachment: Attachment = Attachment.Companion.fromUriAndMimeType(uri, MyContentType.VIDEO.generalMimeType)
                if (mbAttachment.isValid) {
                    activity.addAttachment(mbAttachment)
                } else {
                    MyLog.d(this, "Invalid video attachment; " + jso.toString())
                }
            }
            if (jso.has(FULL_IMAGE_OBJECT) || jso.has(IMAGE_OBJECT)) {
                val uri = UriUtils.fromAlternativeTags(jso, FULL_IMAGE_OBJECT + "/url", IMAGE_OBJECT + "/url")
                val mbAttachment: Attachment = Attachment.Companion.fromUriAndMimeType(uri, MyContentType.IMAGE.generalMimeType)
                if (mbAttachment.isValid) {
                    activity.addAttachment(mbAttachment)
                } else {
                    MyLog.d(this, "Invalid image attachment; " + jso.toString())
                }
            }
        } catch (e: JSONException) {
            throw ConnectionException.Companion.loggedJsonException(this, "Parsing comment", e, jso)
        }
    }

    /**
     * 2014-01-22 According to the crash reports, actorId may not have "acct:" prefix
     */
    fun actorOidToUsername(actorId: String?): String {
        return if (actorId.isNullOrEmpty()) ""
        else UriUtils.toOptional(actorId)
                .map { obj: Uri -> obj.getPath() ?: "" }
                .map(stripBefore("/api"))
                .map(stripBefore("/"))
                .orElse(Optional.of(actorId)
                        .map(stripBefore(":"))
                        .map(stripAfter("@"))
                        .orElse("")
                )
    }

    fun actorOidToHost(actorId: String?): String {
        if (actorId.isNullOrEmpty()) return ""
        val indexOfAt = actorId.indexOf('@')
        return if (indexOfAt < 0) "" else actorId.substring(indexOfAt + 1)
    }

    override fun follow(actorOid: String?, follow: Boolean): Try<AActivity> {
        return actOnActor(if (follow) PActivityType.FOLLOW else PActivityType.STOP_FOLLOWING, actorOid)
    }

    private fun actOnActor(activityType: PActivityType, actorId: String?): Try<AActivity> {
        return ActivitySender.Companion.fromId(this, actorId).send(activityType)
    }

    public override fun getActor2(actorIn: Actor): Try<Actor> {
        return ConnectionAndUrl.Companion.fromActor(this, ApiRoutineEnum.GET_ACTOR, actorIn)
                .flatMap { conu: ConnectionAndUrl -> conu.execute(conu.newRequest()) }
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject? -> actorFromJson(jso) }
    }

    companion object {
        private val TAG: String = ConnectionPumpio::class.java.simpleName
        val PUBLIC_COLLECTION_ID: String = "http://activityschema.org/collection/public"
        val APPLICATION_ID: String = "http://andstatus.org/andstatus"
        val NAME_PROPERTY: String = "displayName"
        val CONTENT_PROPERTY: String = "content"
        val VIDEO_OBJECT: String = "stream"
        val IMAGE_OBJECT: String = "image"
        val FULL_IMAGE_OBJECT: String = "fullImage"

        fun stripBefore(prefixEnd: String): (String?) -> String {
            return { value: String? ->
                if (value.isNullOrEmpty()) ""
                else {
                    val index = value.indexOf(prefixEnd)
                    if (index >= 0) value.substring(index + prefixEnd.length) else value
                }
            }
        }

        fun stripAfter(suffixStart: String): (String?) -> String {
            return { value: String? ->
                if (value.isNullOrEmpty())  ""
                else {
                    val index = value.indexOf(suffixStart)
                    if (index >= 0) value.substring(0, index) else value
                }
            }
        }
    }
}