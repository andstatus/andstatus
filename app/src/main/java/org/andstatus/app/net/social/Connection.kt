/*
 * Copyright (C) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.account.AccountConnectionData
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.net.http.HttpConnectionData
import org.andstatus.app.net.http.HttpReadResult
import org.andstatus.app.net.http.HttpRequest
import org.andstatus.app.net.http.OAuthService
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONObject
import java.net.URL
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.properties.Delegates

/**
 * Handles connection to the API of the Social Network (i.e. to the "Origin")
 * Authenticated User info (User account in the Social Network) and connection properties
 * are provided in the constructor.
 *
 * @author yvolk@yurivolkov.com
 */
abstract class Connection protected constructor() : IsEmpty {
    var http: HttpConnection by Delegates.notNull<HttpConnection>()
    var data: AccountConnectionData by Delegates.notNull<AccountConnectionData>()

    /**
     * @return an empty string in case the API routine is not supported
     */
    protected open fun getApiPathFromOrigin(routine: ApiRoutineEnum): String {
        return ""
    }

    /**
     * Full path of the API. Logged
     * Use [.tryApiPath]
     * @return URL or throws a ConnectionException in case the API routine is not supported
     */
    protected fun getApiPath(routine: ApiRoutineEnum): Try<Uri> {
        return tryApiPath(data.getAccountActor(), routine)
    }

    /**
     * Full path of the API. Logged
     */
    fun tryApiPath(endpointActor: Actor, routine: ApiRoutineEnum): Try<Uri> {
        return TryUtils.fromOptional(getApiUri(endpointActor, routine)) {
            ConnectionException(StatusCode.UNSUPPORTED_API, this.javaClass.simpleName +
                    ": " + "The API is not supported: '$routine'")
        }
                .onSuccess { uri: Uri -> MyLog.v(this.javaClass.simpleName) { "API '$routine' URI=$uri" } }
    }

    /**
     * Use this method to check the connection's (Account's) capability before attempting to use it
     * and even before presenting corresponding action to the User.
     * @return true if supported
     */
    fun hasApiEndpoint(routine: ApiRoutineEnum): Boolean {
        return getApiUri(data.getAccountActor(), routine).isPresent()
    }

    private fun getApiUri(endpointActor: Actor, routine: ApiRoutineEnum): Optional<Uri> {
        if (routine == ApiRoutineEnum.DUMMY_API) {
            return Optional.empty()
        }
        val fromActor = endpointActor.getEndpoint(ActorEndpointType.from(routine))
        return if (fromActor.isPresent) fromActor else Optional.of(getApiPathFromOrigin(routine)).flatMap { apiPath: String -> pathToUri(apiPath).toOptional() }
    }

    fun pathToUri(path: String): Try<Uri> {
        return Try.success(path)
                .filter { obj: String -> obj.isNotEmpty() }
                .flatMap { path2: String -> UrlUtils.pathToUrl(data.getOriginUrl(), path2) }
                .map { obj: URL -> obj.toExternalForm() }
                .map { obj: String -> UriUtils.fromString(obj) }
                .filter { obj: Uri -> UriUtils.isDownloadable(obj) }
    }

    /**
     * Check API requests status.
     */
    open fun rateLimitStatus(): Try<RateLimitStatus> {
        return Try.success(RateLimitStatus())
    }

    /**
     * Do we need password to be set?
     * By default password is not needed and is ignored
     */
    fun isPasswordNeeded(): Boolean {
        return http.isPasswordNeeded()
    }

    /**
     * Set Account's password if the Connection object needs it
     */
    fun setPassword(password: String?) {
        http.password = password ?: ""
    }

    fun getPassword(): String {
        return http.password
    }

    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    fun saveTo(dw: AccountDataWriter): Boolean {
        return http.saveTo(dw) == true
    }

    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    fun getCredentialsPresent(): Boolean {
        return http.credentialsPresent
    }

    abstract fun verifyCredentials(whoAmI: Optional<Uri>): Try<Actor?>
    abstract fun undoLike(noteOid: String?): Try<AActivity>

    /**
     * Favorites the status specified in the ID parameter as the authenticating account.
     * Returns the favorite status when successful.
     * @see [Twitter
     * REST API Method: favorites create](http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-favorites%C2%A0create)
     */
    abstract fun like(noteOid: String): Try<AActivity>
    open fun undoAnnounce(noteOid: String): Try<Boolean> {
        return deleteNote(noteOid)
    }

    /**
     * Destroys the status specified by the required ID parameter.
     * The authenticating account's actor must be the author of the specified status.
     * @see [Twitter
     * REST API Method: statuses/destroy](http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-statuses%C2%A0destroy)
     */
    abstract fun deleteNote(noteOid: String): Try<Boolean>

    open fun getFriendsOrFollowers(routineEnum: ApiRoutineEnum, position: TimelinePosition, actor: Actor): Try<InputActorPage> {
        return (if (routineEnum == ApiRoutineEnum.GET_FRIENDS) getFriends(actor) else getFollowers(actor))
                .map { actors: MutableList<Actor> -> InputActorPage.of(actors) }
    }

    open fun getFriendsOrFollowersIds(routineEnum: ApiRoutineEnum, actorOid: String): Try<MutableList<String>> {
        return if (routineEnum == ApiRoutineEnum.GET_FRIENDS_IDS) getFriendsIds(actorOid) else getFollowersIds(actorOid)
    }

    /**
     * Returns a list of actors the specified actor is following.
     */
    open fun getFriends(actor: Actor): Try<MutableList<Actor>> {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFriends for actor:" + actor.getUniqueNameWithOrigin()))
    }

    /**
     * Returns a list of IDs for every actor the specified actor is following.
     */
    fun getFriendsIds(actorOid: String): Try<MutableList<String>> {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFriendsIds for actorOid=$actorOid"))
    }

    fun getFollowersIds(actorOid: String): Try<MutableList<String>> {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFollowersIds for actorOid=$actorOid"))
    }

    open fun getFollowers(actor: Actor): Try<MutableList<Actor>> {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFollowers for actor:" + actor.getUniqueNameWithOrigin()))
    }

    /**
     * Requests a single note (status), specified by the id parameter.
     * More than one activity may be returned (as replies) to reflect Favoriting and Reblogging of the "status"
     */
    fun getNote(noteOid: String?): Try<AActivity> {
        return getNote1(noteOid)
    }

    /** See [.getNote]  */
    protected abstract fun getNote1(noteOid: String?): Try<AActivity>
    open fun canGetConversation(conversationOid: String?): Boolean {
        return UriUtils.isRealOid(conversationOid) && hasApiEndpoint(ApiRoutineEnum.GET_CONVERSATION)
    }

    open fun getConversation(conversationOid: String?): Try<MutableList<AActivity>>? {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getConversation oid=$conversationOid"))
    }

    /**
     * Create or update Note
     */
    abstract fun updateNote(note: Note?): Try<AActivity>

    /**
     * Post Reblog ("retweet")
     * @see [POST statuses/retweet/:id](https://dev.twitter.com/docs/api/1/post/statuses/retweet/%3Aid)
     *
     *
     * @param rebloggedNoteOid id of the Reblogged note
     */
    abstract fun announce(rebloggedNoteOid: String?): Try<AActivity>

    /**
     * Universal method for several Timeline Types...
     * @param syncYounger
     * @param actor For the [ApiRoutineEnum.ACTOR_TIMELINE], null for the other timelines
     */
    abstract fun getTimeline(syncYounger: Boolean, apiRoutine: ApiRoutineEnum,
                             youngestPosition: TimelinePosition, oldestPosition: TimelinePosition, limit: Int, actor: Actor): Try<InputTimelinePage>

    open fun searchNotes(syncYounger: Boolean, youngestPosition: TimelinePosition,
                         oldestPosition: TimelinePosition, limit: Int, searchQuery: String): Try<InputTimelinePage> {
        return InputTimelinePage.TRY_EMPTY
    }

    open fun searchActors(limit: Int, searchQuery: String): Try<List<Actor>> {
        return TryUtils.emptyList()
    }

    /**
     * Allows this Account to follow (or stop following) an actor specified in the actorOid parameter
     * @param follow true - Follow, false - Stop following
     */
    abstract fun follow(actorOid: String, follow: Boolean): Try<AActivity>

    /** Get information about the specified Actor  */
    fun getActor(actorIn: Actor): Try<Actor> {
        return getActor2(actorIn).map { actor: Actor ->
            if (actor.isFullyDefined() && actor.getUpdatedDate() <= RelativeTime.SOME_TIME_AGO) {
                actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
            }
            MyLog.v(this) { "getActor oid='" + actorIn.oid + "' -> " + actor.uniqueName }
            actor
        }
    }

    protected abstract fun getActor2(actorIn: Actor): Try<Actor>
    protected fun strFixedDownloadLimit(limit: Int, apiRoutine: ApiRoutineEnum?): String {
        return fixedDownloadLimit(limit, apiRoutine).toString()
    }

    /**
     * Restrict the limit to 1 - 400
     */
    open fun fixedDownloadLimit(limit: Int, apiRoutine: ApiRoutineEnum?): Int {
        var out = 400
        if (limit > 0 && limit < 400) {
            out = limit
        }
        return out
    }

    fun clearAuthInformation() {
        http.clearAuthInformation()
    }

    fun setUserTokenWithSecret(token: String?, secret: String?) {
        http.setUserTokenWithSecret(token, secret)
    }

    fun getOAuthService(): OAuthService? {
        return if (http is OAuthService) http as OAuthService? else null
    }

    fun registerClientForAccount(): Try<Void> {
        return http.registerClient()
    }

    fun clearClientKeys() {
        http.clearClientKeys()
    }

    fun areOAuthClientKeysPresent(): Boolean {
        return http.data.areOAuthClientKeysPresent()
    }

    open fun setAccountConnectionData(connectionData: AccountConnectionData): Connection {
        data = connectionData
        http = connectionData.newHttpConnection()
        http.setHttpConnectionData(HttpConnectionData.fromAccountConnectionData(connectionData))
        return this
    }

    open fun getConfig(): Try<OriginConfig> {
        return Try.success(OriginConfig.getEmpty())
    }

    open fun getOpenInstances(): Try<MutableList<Server>>? {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                MyStringBuilder.objToTag(this)))
    }

    /**
     * @return Unix time. Returns 0 in a case of an error or absence of such a field
     */
    fun dateFromJson(jso: JSONObject?, fieldName: String): Long {
        var date: Long = 0
        if (jso != null && jso.has(fieldName)) {
            val updated = JsonUtils.optString(jso, fieldName)
            if (updated.length > 0) {
                date = parseDate(updated)
            }
        }
        return date
    }

    /**
     * @return Unix time. Returns 0 in a case of an error
     */
    open fun parseDate(stringDate: String?): Long {
        if (stringDate.isNullOrEmpty()) {
            return 0
        }
        var unixDate: Long = 0
        val formats = arrayOf<String?>("", "E MMM d HH:mm:ss Z yyyy", "E, d MMM yyyy HH:mm:ss Z")
        for (format in formats) {
            if (format.isNullOrEmpty()) {
                try {
                    unixDate = Date.parse(stringDate)
                } catch (e: IllegalArgumentException) {
                    MyLog.ignored(this, e)
                }
            } else {
                val dateFormat1: DateFormat = SimpleDateFormat(format, Locale.ENGLISH)
                try {
                    unixDate = dateFormat1.parse(stringDate)?.time ?: 0L
                } catch (e: ParseException) {
                    MyLog.ignored(this, e)
                }
            }
            if (unixDate != 0L) {
                break
            }
        }
        if (unixDate == 0L) {
            MyLog.d(this, "Failed to parse the date: '$stringDate'")
        }
        return unixDate
    }

    /**
     * Simple solution based on:
     * http://stackoverflow.com/questions/2201925/converting-iso8601-compliant-string-to-java-util-date
     * @return Unix time. Returns 0 in a case of an error
     */
    protected fun parseIso8601Date(stringDate: String?): Long {
        var unixDate: Long = 0
        if (stringDate != null) {
            val datePrepared: String
            datePrepared = if (stringDate.lastIndexOf('Z') == stringDate.length - 1) {
                stringDate.substring(0, stringDate.length - 1) + "+0000"
            } else {
                stringDate.replace("\\+0([0-9]):00".toRegex(), "+0$100")
            }
            val formatString = if (stringDate.contains(".")) if (stringDate.length - stringDate.lastIndexOf(".") > 4) "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ" else "yyyy-MM-dd'T'HH:mm:ss.SSSZ" else "yyyy-MM-dd'T'HH:mm:ssZ"
            val iso8601DateFormatSec: DateFormat = SimpleDateFormat(formatString, Locale.GERMANY)
            try {
                unixDate = iso8601DateFormatSec.parse(datePrepared)?.time ?: 0L
            } catch (e: ParseException) {
                MyLog.w(this, "Failed to parse the date: '$stringDate' using '$formatString'", e)
            }
        }
        return unixDate
    }

    fun myContext(): MyContext {
        return http.data.myContext()
    }

    fun execute(request: HttpRequest): Try<HttpReadResult> {
        return http.execute(request)
    }

    fun partialPathToApiPath(partialPath: String): String {
        return http.data.getAccountName().origin.originType.partialPathToApiPath(partialPath)
    }

    override fun toString(): String {
        return ((if (data == null) "(empty data)" else data.toString())
                + ", http: " + if (http == null) "(empty" else http.toString())
    }

    override val isEmpty: Boolean
        get() {
            return this === ConnectionEmpty.EMPTY || data == null || http == null
        }

    companion object {
        val KEY_PASSWORD: String = "password"

        fun fromMyAccount(myAccount: MyAccount, isOAuth: TriState): Connection {
            if (!myAccount.origin.isValid()) return ConnectionEmpty.EMPTY
            val connectionData: AccountConnectionData = AccountConnectionData.fromMyAccount(myAccount, isOAuth)
            return try {
                (myAccount.origin.originType.getConnectionClass().newInstance() as Connection)
                        .setAccountConnectionData(connectionData)
            } catch (e: InstantiationException) {
                MyLog.e("Failed to instantiate connection for $myAccount", e)
                ConnectionEmpty.EMPTY
            } catch (e: IllegalAccessException) {
                MyLog.e("Failed to instantiate connection for $myAccount", e)
                ConnectionEmpty.EMPTY
            }
        }

        fun fromOrigin(origin: Origin, isOAuth: TriState): Connection {
            if (!origin.isValid()) return ConnectionEmpty.EMPTY
            val connectionData: AccountConnectionData = AccountConnectionData.fromOrigin(origin, isOAuth)
            return try {
                (origin.originType.getConnectionClass().newInstance() as Connection)
                        .setAccountConnectionData(connectionData)
            } catch (e: InstantiationException) {
                MyLog.e("Failed to instantiate connection for $origin", e)
                ConnectionEmpty.EMPTY
            } catch (e: IllegalAccessException) {
                MyLog.e("Failed to instantiate connection for $origin", e)
                ConnectionEmpty.EMPTY
            }
        }
    }
}