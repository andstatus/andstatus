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
package org.andstatus.app.net.social

import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.social.AActivity.Companion.newPartialNote
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UriUtils.isRealOid
import org.andstatus.app.util.UriUtils.nonRealOid
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Twitter API implementations
 * https://dev.twitter.com/rest/public
 * @author yvolk@yurivolkov.com
 */
abstract class ConnectionTwitterLike : Connection() {
    /**
     * URL of the API. Not logged
     * @return URL or an empty string in a case the API routine is not supported
     */
    override fun getApiPathFromOrigin(routine: ApiRoutineEnum): String {
        val url: String = when (routine) {
            ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS -> "account/rate_limit_status.json"
            ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS -> "account/verify_credentials.json"
            ApiRoutineEnum.LIKE -> "favorites/create/%noteId%.json"
            ApiRoutineEnum.UNDO_LIKE -> "favorites/destroy/%noteId%.json"
            ApiRoutineEnum.DELETE_NOTE -> "statuses/destroy/%noteId%.json"
            ApiRoutineEnum.PRIVATE_NOTES -> "direct_messages.json"
            ApiRoutineEnum.LIKED_TIMELINE -> "favorites.json"
            ApiRoutineEnum.FOLLOW -> "friendships/create.json"
            ApiRoutineEnum.GET_FOLLOWERS_IDS -> "followers/ids.json"
            ApiRoutineEnum.GET_FRIENDS_IDS -> "friends/ids.json"
            ApiRoutineEnum.GET_NOTE -> "statuses/show.json" + "?id=%noteId%"
            ApiRoutineEnum.GET_ACTOR -> "users/show.json"
            ApiRoutineEnum.HOME_TIMELINE -> "statuses/home_timeline.json"
            ApiRoutineEnum.NOTIFICATIONS_TIMELINE -> "statuses/mentions.json"
            ApiRoutineEnum.UPDATE_PRIVATE_NOTE -> "direct_messages/new.json"
            ApiRoutineEnum.UPDATE_NOTE -> "statuses/update.json"
            ApiRoutineEnum.ANNOUNCE -> "statuses/retweet/%noteId%.json"
            ApiRoutineEnum.UNDO_FOLLOW -> "friendships/destroy.json"
            ApiRoutineEnum.ACTOR_TIMELINE -> "statuses/user_timeline.json"
            else -> ""
        }
        return partialPathToApiPath(url)
    }

    override fun deleteNote(noteOid: String): Try<Boolean> {
        return postNoteAction(ApiRoutineEnum.DELETE_NOTE, noteOid)
                .map { jso: JSONObject? ->
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(TAG, "deleteNote response: " + jso.toString())
                    }
                    true
                }
    }

    /**
     * @see [POST friendships/create](https://dev.twitter.com/docs/api/1.1/post/friendships/create)
     *
     * @see [POST friendships/destroy](https://dev.twitter.com/docs/api/1.1/post/friendships/destroy)
     *
     * @return
     */
    override fun follow(actorOid: String, follow: Boolean): Try<AActivity> {
        val out = JSONObject()
        try {
            out.put("user_id", actorOid)
        } catch (e: JSONException) {
            MyLog.w(this, "follow $actorOid", e)
        }
        return postRequest(if (follow) ApiRoutineEnum.FOLLOW else ApiRoutineEnum.UNDO_FOLLOW, out)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject? -> actorFromJson(jso) }
                .map { friend: Actor ->
                    data.getAccountActor().act(
                            data.getAccountActor(),
                            if (follow) ActivityType.FOLLOW else ActivityType.UNDO_FOLLOW,
                            friend)
                }
    }

    /**
     * Returns an array of numeric IDs for every actor the specified actor is following.
     * Current implementation is restricted to 5000 IDs (no paged cursors are used...)
     * @see [GET friends/ids](https://dev.twitter.com/docs/api/1.1/get/friends/ids)
     *
     * Returns a cursored collection of actor IDs for every actor following the specified actor.
     *
     * @see [GET followers/ids](https://dev.twitter.com/rest/reference/get/followers/ids)
     */
    override fun getFriendsOrFollowersIds(apiRoutine: ApiRoutineEnum, actorOid: String): Try<List<String>> {
        return getApiPath(apiRoutine)
            .map { obj: Uri -> obj.buildUpon() }
            .map { builder: Uri.Builder -> builder.appendQueryParameter("user_id", actorOid) }
            .map { it.build() }
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap(::execute)
            .flatMap { result: HttpReadResult -> result.getJsonArrayInObject("ids") }
            .flatMap { jsonArray: JSONArray? ->
                val list: MutableList<String> = ArrayList()
                try {
                    var index = 0
                    while (jsonArray != null && index < jsonArray.length()) {
                        list.add(jsonArray.getString(index))
                        index++
                    }
                    Try.success(list)
                } catch (e: JSONException) {
                    Try.failure<MutableList<String>>(
                        ConnectionException.loggedJsonException(
                            this,
                            apiRoutine.name,
                            e,
                            jsonArray
                        )
                    )
                }
            }
    }

    /**
     * Returns a single status, specified by the id parameter below.
     * The status's author will be returned inline.
     * @see [Twitter
     * REST API Method: statuses/destroy](https://dev.twitter.com/docs/api/1/get/statuses/show/%3Aid)
     */
    public override fun getNote1(noteOid: String): Try<AActivity> {
        return noteAction(ApiRoutineEnum.GET_NOTE, noteOid)
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    override fun getTimeline(syncYounger: Boolean, apiRoutine: ApiRoutineEnum,
                             youngestPosition: TimelinePosition, oldestPosition: TimelinePosition,
                             limit: Int, actor: Actor): Try<InputTimelinePage> {
        return getTimelineUriBuilder(apiRoutine, limit, actor)
            .map { builder: Uri.Builder -> appendPositionParameters(builder, youngestPosition, oldestPosition) }
            .map { it.build() }
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap(::execute)
            .flatMap { result: HttpReadResult ->
                result.getJsonArray()
                    .flatMap { jsonArray: JSONArray? -> jArrToTimeline(jsonArray, apiRoutine) }
            }
            .map { activities -> InputTimelinePage.of(activities) }
    }

    protected open fun getTimelineUriBuilder(apiRoutine: ApiRoutineEnum, limit: Int, actor: Actor): Try<Uri.Builder> {
        return getApiPath(apiRoutine)
                .map { obj: Uri -> obj.buildUpon() }
                .map { b: Uri.Builder -> b.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)) }
                .map { b: Uri.Builder -> if (actor.oid.isEmpty()) b else b.appendQueryParameter("user_id", actor.oid) }
    }

    protected open fun activityFromTwitterLikeJson(jso: JSONObject?): AActivity {
        return activityFromJson(jso)
    }

    fun activityFromJson(jso: JSONObject?): AActivity {
        if (jso == null) {
            return AActivity.EMPTY
        }
        val mainActivity = activityFromJson2(jso)
        val rebloggedActivity = rebloggedNoteFromJson(jso)
        return if (rebloggedActivity.isEmpty) {
            mainActivity
        } else {
            makeReblog(data.getAccountActor(), mainActivity, rebloggedActivity)
        }
    }

    private fun makeReblog(accountActor: Actor, mainActivity: AActivity,
                           rebloggedActivity: AActivity): AActivity {
        val reblog: AActivity = AActivity.from(accountActor, ActivityType.ANNOUNCE)
        reblog.setOid(mainActivity.getNote().oid)
        reblog.setUpdatedDate(mainActivity.getUpdatedDate())
        reblog.setActor(mainActivity.getActor())
        reblog.setActivity(rebloggedActivity)
        return reblog
    }

    fun newLoadedUpdateActivity(oid: String?, updatedDate: Long): AActivity {
        return newPartialNote(data.getAccountActor(), Actor.EMPTY, oid, updatedDate,
                DownloadStatus.LOADED).setOid(oid)
    }

    open fun rebloggedNoteFromJson(jso: JSONObject): AActivity {
        return activityFromJson2(jso.optJSONObject("retweeted_status"))
    }

    open fun activityFromJson2(jso: JSONObject?): AActivity {
        if (jso == null) {
            return AActivity.EMPTY
        }
        val activity: AActivity
        try {
            var oid = JsonUtils.optString(jso, "id_str")
            if (oid.isEmpty()) {
                // This is for the Status.net
                oid = JsonUtils.optString(jso, "id")
            }
            activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"))
            activity.setActor(authorFromJson(jso))
            val note = activity.getNote()
            setNoteBodyFromJson(note, jso)
            if (jso.has("recipient")) {
                val recipient = jso.getJSONObject("recipient")
                note.audience().add(actorFromJson(recipient))
            }
            // Tweets are public by default, see https://help.twitter.com/en/safety-and-security/public-and-protected-tweets
            note.audience().visibility = Visibility.PUBLIC_AND_TO_FOLLOWERS
            if (jso.has("source")) {
                note.via = jso.getString("source")
            }

            // If the Msg is a Reply to other note
            var inReplyToActorOid: String? = ""
            if (jso.has("in_reply_to_user_id_str")) {
                inReplyToActorOid = jso.getString("in_reply_to_user_id_str")
            } else if (jso.has("in_reply_to_user_id")) {
                // This is for Status.net
                inReplyToActorOid = jso.getString("in_reply_to_user_id")
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                inReplyToActorOid = ""
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                var inReplyToNoteOid: String? = ""
                if (jso.has("in_reply_to_status_id_str")) {
                    inReplyToNoteOid = jso.getString("in_reply_to_status_id_str")
                } else if (jso.has("in_reply_to_status_id")) {
                    // This is for StatusNet
                    inReplyToNoteOid = jso.getString("in_reply_to_status_id")
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToNoteOid)) {
                    // Construct Related note from available info
                    val inReplyToActor: Actor = Actor.fromOid(data.getOrigin(), inReplyToActorOid)
                    if (jso.has("in_reply_to_screen_name")) {
                        inReplyToActor.setUsername(jso.getString("in_reply_to_screen_name"))
                    }
                    val inReplyTo: AActivity = newPartialNote(data.getAccountActor(), inReplyToActor,
                            inReplyToNoteOid)
                    note.setInReplyTo(inReplyTo)
                }
            }
            activity.getNote().audience().addActorsFromContent(activity.getNote().content,
                    activity.getAuthor(), activity.getNote().inReplyTo.getActor())
            if (!jso.isNull("favorited")) {
                note.addFavoriteBy(data.getAccountActor(),
                        TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favorited"))))
            }
        } catch (e: JSONException) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso)
        } catch (e: Exception) {
            MyLog.w(this, "activityFromJson2", e)
            return AActivity.EMPTY
        }
        return activity
    }

    private fun authorFromJson(jso: JSONObject): Actor {
        var author: Actor = Actor.EMPTY
        if (jso.has("sender")) {
            author = actorFromJson(jso.getJSONObject("sender"))
        } else if (jso.has("user")) {
            author = actorFromJson(jso.getJSONObject("user"))
        } else if (jso.has("from_user")) {
            // This is in the search results,
            // see https://dev.twitter.com/docs/api/1/get/search
            val senderName = jso.getString("from_user")
            var senderOid = JsonUtils.optString(jso, "from_user_id_str")
            if (SharedPreferencesUtil.isEmpty(senderOid)) {
                senderOid = JsonUtils.optString(jso, "from_user_id")
            }
            if (!SharedPreferencesUtil.isEmpty(senderOid)) {
                author = Actor.fromOid(data.getOrigin(), senderOid)
                author.setUsername(senderName)
                author.build()
            }
        }
        return author
    }

    protected open fun setNoteBodyFromJson(note: Note, jso: JSONObject) {
        if (jso.has("text")) {
            note.setContentPosted(jso.getString("text"))
        }
    }

    protected open fun actorFromJson(jso: JSONObject?): Actor {
        val actor = actorBuilderFromJson(jso).build()
        if (jso != null && !jso.isNull("status")) {
            try {
                val activity = activityFromJson(jso.getJSONObject("status"))
                activity.setActor(actor)
                actor.setLatestActivity(activity)
            } catch (e: JSONException) {
                throw ConnectionException.loggedJsonException(this, "getting status from actor", e, jso)
            }
        }
        return actor
    }

    open fun actorBuilderFromJson(jso: JSONObject?): Actor {
        if (jso == null) return Actor.EMPTY
        var oid = ""
        if (jso.has("id_str")) {
            oid = JsonUtils.optString(jso, "id_str")
        } else if (jso.has("id")) {
            oid = JsonUtils.optString(jso, "id")
        }
        if (SharedPreferencesUtil.isEmpty(oid)) {
            oid = ""
        }
        var username = ""
        if (jso.has("screen_name")) {
            username = JsonUtils.optString(jso, "screen_name")
            if (SharedPreferencesUtil.isEmpty(username)) {
                username = ""
            }
        }
        val actor: Actor = Actor.fromOid(data.getOrigin(), oid)
        actor.setUsername(username)
        actor.setRealName(JsonUtils.optString(jso, "name"))
        if (!SharedPreferencesUtil.isEmpty(actor.getRealName())) {
            actor.setProfileUrlToOriginUrl(data.getOriginUrl())
        }
        actor.location = JsonUtils.optString(jso, "location")
        actor.setAvatarUri(UriUtils.fromAlternativeTags(jso, "profile_image_url_https", "profile_image_url"))
        actor.endpoints.add(ActorEndpointType.BANNER, UriUtils.fromJson(jso, "profile_banner_url"))
        actor.setSummary(JsonUtils.optString(jso, "description"))
        actor.setHomepage(JsonUtils.optString(jso, "url"))
        // Hack for twitter.com
        actor.setProfileUrl(http.pathToUrlString("/").replace("/api.", "/") + username)
        actor.notesCount = jso.optLong("statuses_count")
        actor.favoritesCount = jso.optLong("favourites_count")
        actor.followingCount = jso.optLong("friends_count")
        actor.followersCount = jso.optLong("followers_count")
        actor.setCreatedDate(dateFromJson(jso, "created_at"))
        if (!jso.isNull("following")) {
            actor.isMyFriend = TriState.fromBoolean(jso.optBoolean("following"))
        }
        return actor
    }

    override fun searchNotes(syncYounger: Boolean, youngestPosition: TimelinePosition,
                             oldestPosition: TimelinePosition, limit: Int, searchQuery: String): Try<InputTimelinePage> {
        val apiRoutine = ApiRoutineEnum.SEARCH_NOTES
        return getApiPath(apiRoutine)
            .map { obj: Uri -> obj.buildUpon() }
            .map { b: Uri.Builder -> if (searchQuery.isEmpty()) b else b.appendQueryParameter("q", searchQuery) }
            .map { builder: Uri.Builder -> appendPositionParameters(builder, youngestPosition, oldestPosition) }
            .map { builder: Uri.Builder ->
                builder.appendQueryParameter(
                    "count",
                    strFixedDownloadLimit(limit, apiRoutine)
                )
            }
            .map { it.build() }
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap(::execute)
            .flatMap { result: HttpReadResult ->
                result.getJsonArray()
                    .flatMap { jsonArray: JSONArray? -> jArrToTimeline(jsonArray, apiRoutine) }
            }
            .map { activities -> InputTimelinePage.of(activities) }
    }

    fun appendPositionParameters(builder: Uri.Builder, youngest: TimelinePosition, oldest: TimelinePosition): Uri.Builder {
        if (youngest.nonEmpty) {
            builder.appendQueryParameter("since_id", youngest.getPosition())
        } else if (oldest.nonEmpty) {
            var maxIdString = oldest.getPosition()
            try {
                // Subtract 1, as advised at https://dev.twitter.com/rest/public/timelines
                val maxId = maxIdString.toLong()
                maxIdString = (maxId - 1).toString()
            } catch (e: NumberFormatException) {
                MyLog.i(this, "Is not long number: '$maxIdString'")
            }
            builder.appendQueryParameter("max_id", maxIdString)
        }
        return builder
    }

    fun jArrToTimeline(jArr: JSONArray?, apiRoutine: ApiRoutineEnum): Try<MutableList<AActivity>> {
        val timeline: MutableList<AActivity> = ArrayList()
        if (jArr != null) {
            // Read the activities in chronological order
            for (index in jArr.length() - 1 downTo 0) {
                try {
                    val item = activityFromTwitterLikeJson(jArr.getJSONObject(index))
                    timeline.add(item)
                } catch (e: JSONException) {
                    return Try.failure(ConnectionException.loggedJsonException(this, "Parsing $apiRoutine", e, null))
                } catch (e: Exception) {
                    return Try.failure(e)
                }
            }
        }
        if (apiRoutine.isNotePrivate()) {
            setNotesPrivate(timeline)
        }
        return Try.success(timeline)
    }

    private fun setNotesPrivate(timeline: MutableList<AActivity>) {
        for (item in timeline) {
            if (item.getObjectType() == AObjectType.NOTE) {
                item.getNote().audience().visibility = Visibility.PRIVATE
            }
        }
    }

    fun jArrToActors(jArr: JSONArray?, apiRoutine: ApiRoutineEnum, uri: Uri?): Try<MutableList<Actor>> {
        val actors: MutableList<Actor> = ArrayList()
        if (jArr != null) {
            for (index in 0 until jArr.length()) {
                try {
                    val jso = jArr.getJSONObject(index)
                    val item = actorFromJson(jso)
                    actors.add(item)
                } catch (e: JSONException) {
                    return Try.failure(ConnectionException.loggedJsonException(this, "Parsing $apiRoutine", e, null))
                } catch (e: Exception) {
                    return Try.failure(e)
                }
            }
        }
        MyLog.d(this, apiRoutine.toString() + " '" + uri + "' " + actors.size + " items")
        return Try.success(actors)
    }

    /**
     * @see [GET users/show](https://dev.twitter.com/docs/api/1.1/get/users/show)
     *
     * @return
     */
    public override fun getActor2(actorIn: Actor): Try<Actor> {
        val apiRoutine = ApiRoutineEnum.GET_ACTOR
        return getApiPath(apiRoutine)
            .map { obj: Uri -> obj.buildUpon() }
            .map { builder: Uri.Builder ->
                if (actorIn.oid.isRealOid)
                    builder.appendQueryParameter("user_id", actorIn.oid)
                else builder.appendQueryParameter("screen_name", actorIn.getUsername())
            }
            .map { it.build() }
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap(::execute)
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jso: JSONObject? -> actorFromJson(jso) }
    }

    override fun announce(rebloggedNoteOid: String): Try<AActivity> {
        return postNoteAction(ApiRoutineEnum.ANNOUNCE, rebloggedNoteOid)
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    /**
     * Check API requests status.
     *
     * Returns the remaining number of API requests available to the requesting
     * account before the API limit is reached for the current hour. Calls to
     * rate_limit_status do not count against the rate limit.  If authentication
     * credentials are provided, the rate limit status for the authenticating
     * account is returned.  Otherwise, the rate limit status for the requester's
     * IP address is returned.
     * @see [GET
     * account/rate_limit_status](https://dev.twitter.com/docs/api/1/get/account/rate_limit_status)
     */
    override fun rateLimitStatus(): Try<RateLimitStatus> {
        val apiRoutine = ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS
        return getApiPath(apiRoutine)
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap(::execute)
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .flatMap { result: JSONObject? ->
                val status = RateLimitStatus()
                if (result != null) {
                    var resources: JSONObject? = null
                    try {
                        resources = result.getJSONObject("resources")
                        val limitObject = resources.getJSONObject("statuses").getJSONObject("/statuses/home_timeline")
                        status.remaining = limitObject.optInt("remaining")
                        status.limit = limitObject.optInt("limit")
                    } catch (e: JSONException) {
                        return@flatMap Try.failure<RateLimitStatus?>(
                            ConnectionException.loggedJsonException(
                                this,
                                "getting rate limits",
                                e,
                                resources
                            )
                        )
                    }
                }
                Try.success(status)
            }
    }

    override fun updateNote(note: Note): Try<AActivity> {
        return if (note.audience().hasNonSpecial() && note.audience().visibility.isPrivate) {
            updatePrivateNote(note, note.audience().getFirstNonSpecial().oid)
        } else updateNote2(note)
    }

    abstract fun updateNote2(note: Note): Try<AActivity>

    fun updateNoteSetFields(note: Note, formParams: JSONObject) {
        if (note.getContentToPost().isNotEmpty()) {
            formParams.put("status", note.getContentToPost())
        }
        if (StringUtil.nonEmptyNonTemp(note.inReplyTo.getOid())) {
            formParams.put("in_reply_to_status_id", note.inReplyTo.getOid())
        }
    }

    private fun updatePrivateNote(note: Note, recipientOid: String): Try<AActivity> {
        val formParams = JSONObject()
        try {
            formParams.put("text", note.getContentToPost())
            if (recipientOid.isNotEmpty()) {
                formParams.put("user_id", recipientOid)
            }
        } catch (e: JSONException) {
            return Try.failure(e)
        }
        return postRequest(ApiRoutineEnum.UPDATE_PRIVATE_NOTE, formParams)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    /**
     * @see [Twitter
     * REST API Method: account verify_credentials](http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials)
     */
    override fun verifyCredentials(whoAmI: Optional<Uri>): Try<Actor> {
        val apiRoutine = ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS
        return getApiPath(apiRoutine)
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri) }
            .flatMap(::execute)
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jso: JSONObject? -> actorFromJson(jso) }
    }

    protected fun postRequest(apiRoutine: ApiRoutineEnum, formParams: JSONObject): Try<HttpReadResult> {
        return tryApiPath(data.getAccountActor(), apiRoutine)
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri).withPostParams(formParams) }
            .flatMap(::execute)
    }

    fun getApiPathWithNoteId(routineEnum: ApiRoutineEnum, noteId: String): Try<Uri> {
        return getApiPath(routineEnum).map { uri: Uri ->
            UriUtils.map(uri)
            { s: String? -> s?.replace("%noteId%", noteId) }
        }
    }

    fun getApiPathWithActorId(routineEnum: ApiRoutineEnum, actorId: String): Try<Uri> {
        return getApiPath(routineEnum).map { uri: Uri -> UriUtils.map(uri) { s: String? -> s?.replace("%actorId%", actorId) } }
    }

    override fun getFollowers(actor: Actor): Try<List<Actor>> {
        return getActors(actor, ApiRoutineEnum.GET_FOLLOWERS)
    }

    override fun getFriends(actor: Actor): Try<List<Actor>> {
        return getActors(actor, ApiRoutineEnum.GET_FRIENDS)
    }

    open fun getActors(actor: Actor, apiRoutine: ApiRoutineEnum): Try<List<Actor>> {
        return TryUtils.emptyList()
    }

    override fun like(noteOid: String): Try<AActivity> {
        return postNoteAction(ApiRoutineEnum.LIKE, noteOid)
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    override fun undoLike(noteOid: String): Try<AActivity> {
        return postNoteAction(ApiRoutineEnum.UNDO_LIKE, noteOid)
                .map { jso: JSONObject? -> activityFromJson(jso) }
    }

    fun noteAction(apiRoutine: ApiRoutineEnum, noteOid: String): Try<JSONObject> {
        return noteAction(apiRoutine, noteOid, false)
    }

    fun postNoteAction(apiRoutine: ApiRoutineEnum, noteOid: String): Try<JSONObject> {
        return noteAction(apiRoutine, noteOid, true)
    }

    private fun noteAction(apiRoutine: ApiRoutineEnum, noteOid: String, asPost: Boolean): Try<JSONObject> {
        return if (noteOid.nonRealOid) Try.success(JsonUtils.EMPTY) else getApiPathWithNoteId(
            apiRoutine,
            noteOid
        )
            .map { uri: Uri -> HttpRequest.of(apiRoutine, uri).asPost(asPost) }
            .flatMap(::execute)
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
    }

    companion object {
        private val TAG: String = ConnectionTwitterLike::class.simpleName!!
    }
}
