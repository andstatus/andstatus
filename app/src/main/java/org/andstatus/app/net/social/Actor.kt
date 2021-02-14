/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import io.vavr.control.Try
import org.andstatus.app.R
import org.andstatus.app.account.AccountName
import org.andstatus.app.actor.Group
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.ActorInTimeline
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.ActorSql
import org.andstatus.app.data.AvatarFile
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.origin.OriginType
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.user.User
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.LazyVal
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.NullUtil
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils
import org.junit.Assert
import java.net.URL
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 */
class Actor private constructor(// In our system
        val origin: Origin, val groupType: GroupType?, actorId: Long, actorOid: String?) : Comparable<Actor?>, IsEmpty {
    val oid: String
    private var parentActorId: Long = 0
    private var parentActor: LazyVal<Actor?>? = LazyVal.Companion.of<Actor?>(Supplier { EMPTY })
    private var username: String? = ""
    private var webFingerId: String? = ""
    private var isWebFingerIdValid = false
    private var realName: String? = ""
    private var summary: String? = ""
    var location: String? = ""
    private var profileUri = Uri.EMPTY
    private var homepage: String? = ""
    private var avatarUri = Uri.EMPTY
    val endpoints: ActorEndpoints?
    private val connectionHost: LazyVal<String?>? = LazyVal.Companion.of<String?>(Supplier { evalConnectionHost() })
    private val idHost: LazyVal<String?>? = LazyVal.Companion.of<String?>(Supplier { evalIdHost() })
    var notesCount: Long = 0
    var favoritesCount: Long = 0
    var followingCount: Long = 0
    var followersCount: Long = 0
    private var createdDate = RelativeTime.DATETIME_MILLIS_NEVER
    private var updatedDate = RelativeTime.DATETIME_MILLIS_NEVER
    private var latestActivity: AActivity? = null

    // Hack for Twitter-like origins...
    var isMyFriend: TriState? = TriState.UNKNOWN

    @Volatile
    var actorId = 0L
    var avatarFile: AvatarFile? = AvatarFile.Companion.EMPTY

    @Volatile
    var user: User? = User.Companion.EMPTY

    @Volatile
    private var isFullyDefined: TriState? = TriState.UNKNOWN
    fun getDefaultMyAccountTimelineTypes(): MutableList<TimelineType?>? {
        return if (origin.originType.isPrivatePostsSupported) TimelineType.Companion.getDefaultMyAccountTimelineTypes() else TimelineType.Companion.getDefaultMyAccountTimelineTypes().stream()
                .filter(Predicate { t: TimelineType? -> t != TimelineType.PRIVATE })
                .collect(Collectors.toList())
    }

    private fun betterToCache(other: Actor?): Actor? {
        return if (isBetterToCacheThan(other)) this else other
    }

    /** this Actor is MyAccount and the Actor updates objActor  */
    fun update(objActor: Actor?): AActivity {
        return update(this, objActor)
    }

    /** this actor updates objActor  */
    fun update(accountActor: Actor?, objActor: Actor): AActivity {
        return if (objActor === EMPTY) AActivity.Companion.EMPTY else act(accountActor, ActivityType.UPDATE, objActor)
    }

    /** this actor acts on objActor  */
    fun act(accountActor: Actor?, activityType: ActivityType, objActor: Actor): AActivity {
        if (this === EMPTY || accountActor === EMPTY || objActor === EMPTY) {
            return AActivity.Companion.EMPTY
        }
        val mbActivity: AActivity = AActivity.Companion.from(accountActor, activityType)
        mbActivity.setActor(this)
        mbActivity.setObjActor(objActor)
        return mbActivity
    }

    fun isConstant(): Boolean {
        return this === EMPTY || this === PUBLIC || this === FOLLOWERS
    }

    override fun isEmpty(): Boolean {
        if (this === EMPTY) return true
        return if (isConstant()) false else !origin.isValid ||
                actorId == 0L && UriUtils.nonRealOid(oid) && !isWebFingerIdValid() && !isUsernameValid()
    }

    fun dontStore(): Boolean {
        return isConstant()
    }

    fun isFullyDefined(): Boolean {
        if (isFullyDefined.unknown) {
            isFullyDefined = calcIsFullyDefined()
        }
        return isFullyDefined.isTrue
    }

    private fun calcIsFullyDefined(): TriState? {
        if (isEmpty || UriUtils.nonRealOid(oid)) return TriState.FALSE
        return if (groupType.isGroupLike) TriState.TRUE else TriState.Companion.fromBoolean(isWebFingerIdValid() && isUsernameValid())
    }

    fun isNotFullyDefined(): Boolean {
        return !isFullyDefined()
    }

    fun isBetterToCacheThan(other: Actor?): Boolean {
        if (this === other) return false
        if (other == null || other === EMPTY ||
                isFullyDefined() && other.isNotFullyDefined()) return true
        if (this === EMPTY || isNotFullyDefined() && other.isFullyDefined()) return false
        return if (isFullyDefined()) {
            if (getUpdatedDate() != other.getUpdatedDate()) {
                return getUpdatedDate() > other.getUpdatedDate()
            }
            if (avatarFile.downloadedDate != other.avatarFile.downloadedDate) {
                avatarFile.downloadedDate > other.avatarFile.downloadedDate
            } else notesCount > other.notesCount
        } else {
            if (isOidReal() && !other.isOidReal()) return true
            if (!isOidReal() && other.isOidReal()) return false
            if (isUsernameValid() && !other.isUsernameValid()) return true
            if (!isUsernameValid() && other.isUsernameValid()) return false
            if (isWebFingerIdValid() && !other.isWebFingerIdValid()) return true
            if (!isWebFingerIdValid() && other.isWebFingerIdValid()) return false
            if (UriUtils.nonEmpty(profileUri) && UriUtils.isEmpty(other.profileUri)) return true
            if (UriUtils.isEmpty(profileUri) && UriUtils.nonEmpty(other.profileUri)) false else getUpdatedDate() > other.getUpdatedDate()
        }
    }

    fun isIdentified(): Boolean {
        return actorId != 0L && isOidReal()
    }

    fun isOidReal(): Boolean {
        return UriUtils.isRealOid(oid)
    }

    fun canGetActor(): Boolean {
        return (isOidReal() || isWebFingerIdValid()) && StringUtil.nonEmpty(getConnectionHost())
    }

    override fun toString(): String {
        if (this === EMPTY) {
            return "Actor:EMPTY"
        }
        val members: MyStringBuilder = MyStringBuilder.Companion.of("origin:" + origin.name)
                .withComma("id", actorId)
                .withComma("oid", oid)
                .withComma(if (isWebFingerIdValid()) "webFingerId" else "", if (StringUtil.isEmpty(webFingerId)) "" else if (isWebFingerIdValid()) webFingerId else "(invalid webFingerId)")
                .withComma("username", username)
                .withComma("realName", realName)
                .withComma("groupType", if (groupType == GroupType.UNKNOWN) "" else groupType)
                .withComma("", user, Supplier { user.nonEmpty() })
                .withComma<Uri?>("profileUri", profileUri, Predicate { obj: Uri? -> UriUtils.nonEmpty() })
                .withComma<Uri?>("avatar", avatarUri, Predicate { obj: Uri? -> UriUtils.nonEmpty() })
                .withComma<AvatarFile?>("avatarFile", avatarFile, Predicate { obj: AvatarFile? -> obj.nonEmpty() })
                .withComma("banner", endpoints.findFirst(ActorEndpointType.BANNER).orElse(null))
                .withComma("", "latest note present", Supplier { hasLatestNote() })
        if (parentActor.isEvaluated() && parentActor.get().nonEmpty()) {
            members.withComma("parent", parentActor.get())
        } else if (parentActorId != 0L) {
            members.withComma("parentId", parentActorId)
        }
        return MyStringBuilder.Companion.formatKeyValue(this, members)
    }

    fun getUsername(): String? {
        return username
    }

    fun getUniqueNameWithOrigin(): String? {
        return getUniqueName() + AccountName.Companion.ORIGIN_SEPARATOR + origin.name
    }

    fun getUniqueName(): String? {
        if (StringUtil.nonEmptyNonTemp(username)) return username + getOptAtHostForUniqueName()
        if (StringUtil.nonEmptyNonTemp(realName)) return realName + getOptAtHostForUniqueName()
        if (isWebFingerIdValid()) return webFingerId
        return if (StringUtil.nonEmptyNonTemp(oid)) oid else "id:" + actorId + getOptAtHostForUniqueName()
    }

    private fun getOptAtHostForUniqueName(): String? {
        return if (origin.originType.uniqueNameHasHost()) if (StringUtil.isEmpty(getIdHost())) "" else "@" + getIdHost() else ""
    }

    fun withUniqueName(uniqueName: String?): Actor? {
        uniqueNameToUsername(origin, uniqueName).ifPresent(Consumer { username: String? -> setUsername(username) })
        uniqueNameToWebFingerId(origin, uniqueName).ifPresent(Consumer { webFingerIdIn: String? -> setWebFingerId(webFingerIdIn) })
        return this
    }

    fun setUsername(username: String?): Actor? {
        check(!(this === EMPTY)) { "Cannot set username of EMPTY Actor" }
        this.username = if (StringUtil.isEmpty(username)) "" else username.trim { it <= ' ' }
        return this
    }

    fun getProfileUrl(): String? {
        return profileUri.toString()
    }

    fun setProfileUrl(url: String?): Actor? {
        profileUri = UriUtils.fromString(url)
        return this
    }

    fun setProfileUrlToOriginUrl(originUrl: URL?) {
        profileUri = UriUtils.fromUrl(originUrl)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        return if (o !is Actor) false else isSame(o as Actor?, true)
    }

    override fun hashCode(): Int {
        var result = origin.hashCode()
        if (actorId != 0L) {
            return 31 * result + java.lang.Long.hashCode(actorId)
        }
        if (UriUtils.isRealOid(oid)) {
            return 31 * result + oid.hashCode()
        } else if (isWebFingerIdValid) {
            return 31 * result + getWebFingerId().hashCode()
        } else if (isUsernameValid()) {
            result = 31 * result + getUsername().hashCode()
        }
        return result
    }

    /** Doesn't take origin into account  */
    fun isSame(that: Actor?): Boolean {
        return isSame(that, false)
    }

    fun isSame(other: Actor?, sameOriginOnly: Boolean): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (actorId != 0L) {
            if (actorId == other.actorId) return true
        }
        if (origin == other.origin) {
            if (UriUtils.isRealOid(oid) && oid == other.oid) {
                return true
            }
        } else if (sameOriginOnly) {
            return false
        }
        if (isWebFingerIdValid()) {
            if (webFingerId == other.webFingerId) return true
            if (other.isWebFingerIdValid) return false
        }
        return if (!groupType.isSameActor(other.groupType)) false else isUsernameValid() && other.isUsernameValid() && username.equals(other.username, ignoreCase = true)
    }

    fun notSameUser(other: Actor): Boolean {
        return !isSameUser(other)
    }

    fun isSameUser(other: Actor): Boolean {
        return if (user.actorIds.isEmpty() || other.actorId == 0L) if (other.user.actorIds.isEmpty() || actorId == 0L) isSame(other) else other.user.actorIds.contains(actorId) else user.actorIds.contains(other.actorId)
    }

    fun build(): Actor? {
        if (this === EMPTY) return this
        connectionHost.reset()
        if (StringUtil.isEmpty(username) || isWebFingerIdValid) return this
        if (username.contains("@")) {
            setWebFingerId(username)
        } else if (!UriUtils.isEmpty(profileUri)) {
            if (origin.isValid) {
                setWebFingerId(username + "@" + origin.fixUriForPermalink(profileUri).host)
            } else {
                setWebFingerId(username + "@" + profileUri.host)
            }
            // Don't infer "id host" from the Origin's host
        }
        return this
    }

    fun setWebFingerId(webFingerIdIn: String?): Actor? {
        if (StringUtil.isEmpty(webFingerIdIn) || !webFingerIdIn.contains("@")) return this
        var potentialUsername = webFingerIdIn
        val nameBeforeTheLastAt = webFingerIdIn.substring(0, webFingerIdIn.lastIndexOf("@"))
        if (isWebFingerIdValid(webFingerIdIn)) {
            webFingerId = webFingerIdIn.toLowerCase()
            isWebFingerIdValid = true
            potentialUsername = nameBeforeTheLastAt
        } else {
            val lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@")
            if (lastButOneIndex > -1) {
                val potentialWebFingerId = webFingerIdIn.substring(lastButOneIndex + 1)
                if (isWebFingerIdValid(potentialWebFingerId)) {
                    webFingerId = potentialWebFingerId.toLowerCase()
                    isWebFingerIdValid = true
                    potentialUsername = webFingerIdIn.substring(0, lastButOneIndex)
                }
            }
        }
        if (!isUsernameValid() && origin.isUsernameValid(potentialUsername)) {
            username = potentialUsername
        }
        return this
    }

    fun getWebFingerId(): String? {
        return webFingerId
    }

    fun isWebFingerIdValid(): Boolean {
        return isWebFingerIdValid
    }

    fun getBestUri(): String? {
        if (!StringUtil.isEmptyOrTemp(oid)) {
            return oid
        }
        if (isUsernameValid() && StringUtil.nonEmpty(getIdHost())) {
            return OriginPumpio.Companion.ACCOUNT_PREFIX + getUsername() + "@" + getIdHost()
        }
        return if (isWebFingerIdValid()) {
            OriginPumpio.Companion.ACCOUNT_PREFIX + getWebFingerId()
        } else ""
    }

    /** Lookup the application's id from other IDs  */
    fun lookupActorId() {
        if (isConstant()) return
        if (actorId == 0L && isOidReal()) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.id, oid)
        }
        if (actorId == 0L && isWebFingerIdValid()) {
            actorId = MyQuery.webFingerIdToId(origin.myContext, origin.id, webFingerId, true)
        }
        if (actorId == 0L && StringUtil.nonEmpty(username)) {
            val actorId2 = origin.usernameToId(username)
            if (actorId2 != 0L) {
                var skip2 = false
                if (isWebFingerIdValid()) {
                    val webFingerId2 = MyQuery.actorIdToWebfingerId(origin.myContext, actorId2)
                    if (isWebFingerIdValid(webFingerId2)) {
                        skip2 = !webFingerId.equals(webFingerId2, ignoreCase = true)
                        if (!skip2) actorId = actorId2
                    }
                }
                if (actorId == 0L && !skip2 && isOidReal()) {
                    val oid2 = MyQuery.idToOid(origin.myContext, OidEnum.ACTOR_OID, actorId2, 0)
                    if (UriUtils.isRealOid(oid2)) skip2 = !oid.equals(oid2, ignoreCase = true)
                }
                if (actorId == 0L && !skip2 && groupType != GroupType.UNKNOWN) {
                    val groupTypeStored = origin.myContext.users().idToGroupType(actorId2)
                    if (Group.groupTypeNeedsCorrection(groupTypeStored, groupType)) {
                        val updatedDateStored = MyQuery.actorIdToLongColumnValue(ActorTable.UPDATED_DATE, actorId)
                        if (getUpdatedDate() <= updatedDateStored) {
                            setUpdatedDate(Math.max(updatedDateStored + 1, RelativeTime.SOME_TIME_AGO + 1))
                        }
                    }
                }
                if (actorId == 0L && !skip2) {
                    actorId = actorId2
                }
            }
        }
        if (actorId == 0L) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.id, toTempOid())
        }
        if (actorId == 0L && hasAltTempOid()) {
            actorId = MyQuery.oidToId(origin.myContext, OidEnum.ACTOR_OID, origin.id, toAltTempOid())
        }
    }

    fun hasAltTempOid(): Boolean {
        return toTempOid() != toAltTempOid() && StringUtil.nonEmpty(username)
    }

    fun hasLatestNote(): Boolean {
        return latestActivity != null && !latestActivity.isEmpty()
    }

    fun toAltTempOid(): String? {
        return toTempOid("", username)
    }

    /** Tries to find this actor in his home origin (the same host...).
     * Returns the same Actor, if not found  */
    fun toHomeOrigin(): Actor? {
        return if (origin.host == getIdHost()) this else user.actorIds.stream().map { id: Long? -> NullUtil.getOrDefault(origin.myContext.users().actors, id, EMPTY) }
                .filter { a: Actor? -> a.nonEmpty() && a.origin.host == getIdHost() }
                .findAny().orElse(this)
    }

    fun extractActorsFromContent(text: String?, inReplyToActorIn: Actor?): MutableList<Actor?>? {
        return _extractActorsFromContent(MyHtml.htmlToCompactPlainText(text), 0, ArrayList(),
                inReplyToActorIn.withValidUsernameAndWebfingerId())
    }

    private fun _extractActorsFromContent(text: String?, textStart: Int, actors: MutableList<Actor?>?, inReplyToActor: Actor?): MutableList<Actor?>? {
        val actorReference = origin.getActorReference(text, textStart)
        if (actorReference.index < textStart) return actors
        var validUsername: String? = ""
        var validWebFingerId: String? = ""
        var ind = actorReference.index
        while (ind < text.length) {
            if (Patterns.WEBFINGER_ID_CHARS.indexOf(text.get(ind)) < 0) {
                break
            }
            val username = text.substring(actorReference.index, ind + 1)
            if (origin.isUsernameValid(username)) {
                validUsername = username
            }
            if (isWebFingerIdValid(username)) {
                validWebFingerId = username
            }
            ind++
        }
        if (StringUtil.nonEmpty(validWebFingerId) || StringUtil.nonEmpty(validUsername)) {
            addExtractedActor(actors, validWebFingerId, validUsername, actorReference.groupType, inReplyToActor)
        }
        return _extractActorsFromContent(text, ind + 1, actors, inReplyToActor)
    }

    private fun withValidUsernameAndWebfingerId(): Actor? {
        return if (isWebFingerIdValid && isUsernameValid() || actorId == 0L) this else load(origin.myContext, actorId)
    }

    fun isUsernameValid(): Boolean {
        return StringUtil.nonEmptyNonTemp(username) && origin.isUsernameValid(username)
    }

    private fun addExtractedActor(actors: MutableList<Actor?>?, webFingerId: String?, validUsername: String?, groupType: GroupType?,
                                  inReplyToActor: Actor?) {
        var actor = newUnknown(origin, groupType)
        if (isWebFingerIdValid(webFingerId)) {
            actor.setWebFingerId(webFingerId)
            actor.setUsername(validUsername)
        } else {
            // Is this a reply to Actor?
            if (validUsername.equals(inReplyToActor.getUsername(), ignoreCase = true)) {
                actor = inReplyToActor
            } else if (validUsername.equals(getUsername(), ignoreCase = true)) {
                actor = this
            } else {
                // Don't infer "id host", if it wasn't explicitly provided
                actor.setUsername(validUsername)
            }
        }
        actor.build()
        actors.add(actor)
    }

    fun getConnectionHost(): String? {
        return connectionHost.get()
    }

    private fun evalConnectionHost(): String? {
        if (origin.shouldHaveUrl()) {
            return origin.host
        }
        return if (StringUtil.nonEmpty(profileUri.host)) {
            profileUri.host
        } else UrlUtils.getHost(oid).orElseGet {
            if (isWebFingerIdValid) {
                val pos = getWebFingerId().indexOf('@')
                if (pos >= 0) {
                    return@orElseGet getWebFingerId().substring(pos + 1)
                }
            }
            ""
        }
    }

    fun getIdHost(): String? {
        return idHost.get()
    }

    private fun evalIdHost(): String? {
        return UrlUtils.getHost(oid).orElseGet {
            if (isWebFingerIdValid) {
                val pos = getWebFingerId().indexOf('@')
                if (pos >= 0) {
                    return@orElseGet getWebFingerId().substring(pos + 1)
                }
            }
            if (StringUtil.nonEmpty(profileUri.host)) {
                return@orElseGet profileUri.host
            }
            ""
        }
    }

    fun getSummary(): String? {
        return summary
    }

    fun setSummary(summary: String?): Actor? {
        if (!isEmpty && !SharedPreferencesUtil.isEmpty(summary)) {
            this.summary = summary
        }
        return this
    }

    fun getHomepage(): String? {
        return homepage
    }

    fun setHomepage(homepage: String?) {
        if (!SharedPreferencesUtil.isEmpty(homepage)) {
            this.homepage = homepage
        }
    }

    fun getRecipientName(): String? {
        return if (groupType == GroupType.FOLLOWERS) {
            origin.myContext.context().getText(R.string.followers).toString()
        } else getUniqueName()
    }

    fun getActorNameInTimelineWithOrigin(): String? {
        return if (MyPreferences.getShowOrigin() && nonEmpty()) {
            val name = getActorNameInTimeline() + " / " + origin.name
            if (origin.originType === OriginType.GNUSOCIAL && MyPreferences.isShowDebuggingInfoInUi()
                    && StringUtil.nonEmpty(oid)) {
                "$name oid:$oid"
            } else name
        } else getActorNameInTimeline()
    }

    fun getActorNameInTimeline(): String? {
        val name1 = getActorNameInTimeline1()
        return if (StringUtil.nonEmpty(name1)) name1 else getUniqueNameWithOrigin()
    }

    private fun getActorNameInTimeline1(): String? {
        return when (MyPreferences.getActorInTimeline()) {
            ActorInTimeline.AT_USERNAME -> if (StringUtil.isEmpty(username)) "" else "@$username"
            ActorInTimeline.WEBFINGER_ID -> if (isWebFingerIdValid) webFingerId else ""
            ActorInTimeline.REAL_NAME -> realName
            ActorInTimeline.REAL_NAME_AT_USERNAME -> if (StringUtil.nonEmpty(realName) && StringUtil.nonEmpty(username)) "$realName @$username" else username
            ActorInTimeline.REAL_NAME_AT_WEBFINGER_ID -> if (StringUtil.nonEmpty(realName) && StringUtil.nonEmpty(webFingerId)) "$realName @$webFingerId" else webFingerId
            else -> username
        }
    }

    fun getRealName(): String? {
        return realName
    }

    fun setRealName(realName: String?) {
        if (!SharedPreferencesUtil.isEmpty(realName)) {
            this.realName = realName
        }
    }

    fun getCreatedDate(): Long {
        return createdDate
    }

    fun setCreatedDate(createdDate: Long) {
        this.createdDate = if (createdDate < RelativeTime.SOME_TIME_AGO) RelativeTime.SOME_TIME_AGO else createdDate
    }

    fun getUpdatedDate(): Long {
        return updatedDate
    }

    fun setUpdatedDate(updatedDate: Long) {
        if (this.updatedDate >= updatedDate) return
        this.updatedDate = if (updatedDate < RelativeTime.SOME_TIME_AGO) RelativeTime.SOME_TIME_AGO else updatedDate
    }

    override operator fun compareTo(another: Actor): Int {
        if (actorId != 0L && another.actorId != 0L) {
            if (actorId == another.actorId) {
                return 0
            }
            return if (origin.id > another.origin.id) 1 else -1
        }
        return if (origin.id != another.origin.id) {
            if (origin.id > another.origin.id) 1 else -1
        } else oid.compareTo(another.oid)
    }

    fun getLatestActivity(): AActivity? {
        return latestActivity
    }

    fun setLatestActivity(latestActivity: AActivity) {
        this.latestActivity = latestActivity
        if (this.latestActivity.getAuthor().isEmpty) {
            this.latestActivity.setAuthor(this)
        }
    }

    fun toActorTitle(): String? {
        val builder = StringBuilder()
        val uniqueName = getUniqueName()
        if (StringUtil.nonEmpty(uniqueName)) {
            builder.append("@$uniqueName")
        }
        if (StringUtil.nonEmpty(getRealName())) {
            MyStringBuilder.Companion.appendWithSpace(builder, "(" + getRealName() + ")")
        }
        return builder.toString()
    }

    fun lookupUser(): Actor? {
        return origin.myContext.users().lookupUser(this)
    }

    fun saveUser() {
        if (user.isMyUser().unknown && origin.myContext.users().isMe(this)) {
            user.setIsMyUser(TriState.TRUE)
        }
        if (user.userId == 0L) user.setKnownAs(getUniqueName())
        user.save(origin.myContext)
    }

    fun hasAvatar(): Boolean {
        return UriUtils.nonEmpty(avatarUri)
    }

    fun hasAvatarFile(): Boolean {
        return AvatarFile.Companion.EMPTY !== avatarFile
    }

    fun requestDownload(isManuallyLaunched: Boolean) {
        if (canGetActor()) {
            MyLog.v(this) { "Actor $this will be loaded from the Internet" }
            val command: CommandData = CommandData.Companion.newActorCommandAtOrigin(
                    CommandEnum.GET_ACTOR, this, getUsername(), origin)
                    .setManuallyLaunched(isManuallyLaunched)
            MyServiceManager.Companion.sendForegroundCommand(command)
        } else {
            MyLog.v(this) { "Cannot get Actor $this" }
        }
    }

    fun isPublic(): Boolean {
        return groupType == GroupType.PUBLIC
    }

    fun isFollowers(): Boolean {
        return groupType == GroupType.FOLLOWERS
    }

    fun nonPublic(): Boolean {
        return !isPublic()
    }

    fun getAvatarUri(): Uri? {
        return avatarUri
    }

    fun getAvatarUrl(): String? {
        return avatarUri.toString()
    }

    fun setAvatarUrl(avatarUrl: String?) {
        setAvatarUri(UriUtils.fromString(avatarUrl))
    }

    fun setAvatarUri(avatarUri: Uri?) {
        this.avatarUri = UriUtils.notNull(avatarUri)
        if (hasAvatar() && avatarFile.isEmpty()) {
            avatarFile = AvatarFile.Companion.fromActorOnly(this)
        }
    }

    fun requestAvatarDownload() {
        if (hasAvatar() && MyPreferences.getShowAvatars() && avatarFile.downloadStatus != DownloadStatus.LOADED) {
            avatarFile.requestDownload()
        }
    }

    fun getEndpoint(type: ActorEndpointType?): Optional<Uri?>? {
        val uri = endpoints.findFirst(type)
        return if (uri.isPresent) uri else if (type == ActorEndpointType.API_PROFILE) UriUtils.toDownloadableOptional(oid) else Optional.empty()
    }

    fun assertContext() {
        if (isConstant()) return
        try {
            origin.assertContext()
        } catch (e: Throwable) {
            Assert.fail("""
    Failed on $this
    ${e.message}
    """.trimIndent())
        }
    }

    fun setParentActorId(myContext: MyContext?, parentActorId: Long): Actor? {
        if (this.parentActorId != parentActorId) {
            this.parentActorId = parentActorId
            parentActor = if (parentActorId == 0L) LazyVal.Companion.of<Actor?>(Supplier { EMPTY }) else LazyVal.Companion.of<Actor?>(Supplier { load(myContext, parentActorId) })
        }
        return this
    }

    fun getParentActorId(): Long {
        return parentActorId
    }

    fun getParent(): Actor? {
        return parentActor.get()
    }

    companion object {
        val EMPTY = newUnknown(Origin.Companion.EMPTY, GroupType.UNKNOWN).setUsername("Empty")
        val TRY_EMPTY = Try.success(EMPTY)
        val PUBLIC = fromTwoIds(Origin.Companion.EMPTY, GroupType.PUBLIC, 0,
                "https://www.w3.org/ns/activitystreams#Public").setUsername("Public")
        val FOLLOWERS = fromTwoIds(Origin.Companion.EMPTY, GroupType.FOLLOWERS, 0,
                "org.andstatus.app.net.social.Actor#Followers").setUsername("Followers")

        fun getEmpty(): Actor {
            return EMPTY
        }

        fun load(myContext: MyContext, actorId: Long): Actor {
            return load(myContext, actorId, false) { getEmpty() }
        }

        fun load(myContext: MyContext, actorId: Long, reloadFirst: Boolean, supplier: Supplier<Actor?>?): Actor? {
            if (actorId == 0L) return supplier.get()
            val cached = myContext.users().actors.getOrDefault(actorId, EMPTY)
            return if (MyAsyncTask.Companion.nonUiThread() && (reloadFirst || cached.isNotFullyDefined)) loadFromDatabase(myContext, actorId, supplier, true).betterToCache(cached) else cached
        }

        fun loadFromDatabase(myContext: MyContext, actorId: Long, supplier: Supplier<Actor?>?,
                             useCache: Boolean): Actor? {
            val sql = ("SELECT " + ActorSql.selectFullProjection()
                    + " FROM " + ActorSql.allTables()
                    + " WHERE " + ActorTable.TABLE_NAME + "." + BaseColumns._ID + "=" + actorId)
            val function = Function { cursor: Cursor? -> fromCursor(myContext, cursor, useCache) }
            return MyQuery.get(myContext, sql, function).stream().findFirst().orElseGet(supplier)
        }

        /** Updates cache on load  */
        fun fromCursor(myContext: MyContext?, cursor: Cursor?, useCache: Boolean): Actor {
            val updatedDate = DbUtils.getLong(cursor, ActorTable.UPDATED_DATE)
            val actor = fromTwoIds(
                    myContext.origins().fromId(DbUtils.getLong(cursor, ActorTable.ORIGIN_ID)),
                    GroupType.Companion.fromId(DbUtils.getLong(cursor, ActorTable.GROUP_TYPE)),
                    DbUtils.getLong(cursor, ActorTable.ACTOR_ID),
                    DbUtils.getString(cursor, ActorTable.ACTOR_OID))
            actor.setParentActorId(myContext, DbUtils.getLong(cursor, ActorTable.PARENT_ACTOR_ID))
            actor.setRealName(DbUtils.getString(cursor, ActorTable.REAL_NAME))
            actor.setUsername(DbUtils.getString(cursor, ActorTable.USERNAME))
            actor.setWebFingerId(DbUtils.getString(cursor, ActorTable.WEBFINGER_ID))
            actor.setSummary(DbUtils.getString(cursor, ActorTable.SUMMARY))
            actor.location = DbUtils.getString(cursor, ActorTable.LOCATION)
            actor.setProfileUrl(DbUtils.getString(cursor, ActorTable.PROFILE_PAGE))
            actor.setHomepage(DbUtils.getString(cursor, ActorTable.HOMEPAGE))
            actor.setAvatarUrl(DbUtils.getString(cursor, ActorTable.AVATAR_URL))
            actor.notesCount = DbUtils.getLong(cursor, ActorTable.NOTES_COUNT)
            actor.favoritesCount = DbUtils.getLong(cursor, ActorTable.FAVORITES_COUNT)
            actor.followingCount = DbUtils.getLong(cursor, ActorTable.FOLLOWING_COUNT)
            actor.followersCount = DbUtils.getLong(cursor, ActorTable.FOLLOWERS_COUNT)
            actor.setCreatedDate(DbUtils.getLong(cursor, ActorTable.CREATED_DATE))
            actor.setUpdatedDate(updatedDate)
            actor.user = User.Companion.fromCursor(myContext, cursor, useCache)
            actor.avatarFile = AvatarFile.Companion.fromCursor(actor, cursor)
            return if (useCache) {
                val cachedActor = myContext.users().actors.getOrDefault(actor.actorId, EMPTY)
                if (actor.isBetterToCacheThan(cachedActor)) {
                    myContext.users().updateCache(actor)
                    return actor
                }
                cachedActor
            } else {
                actor
            }
        }

        fun newUnknown(origin: Origin, groupType: GroupType?): Actor? {
            return fromTwoIds(origin, groupType, 0, "")
        }

        fun fromId(origin: Origin, actorId: Long): Actor? {
            return fromTwoIds(origin, GroupType.UNKNOWN, actorId, "")
        }

        fun fromOid(origin: Origin, actorOid: String?): Actor? {
            return fromTwoIds(origin, GroupType.UNKNOWN, 0, actorOid)
        }

        fun fromTwoIds(origin: Origin, groupType: GroupType?, actorId: Long, actorOid: String?): Actor? {
            return Actor(origin, groupType, actorId, actorOid)
        }

        fun uniqueNameToUsername(origin: Origin?, uniqueName: String?): Optional<String?>? {
            if (StringUtil.nonEmpty(uniqueName)) {
                if (uniqueName.contains("@")) {
                    val nameBeforeTheLastAt = uniqueName.substring(0, uniqueName.lastIndexOf("@"))
                    if (isWebFingerIdValid(uniqueName)) {
                        if (origin.isUsernameValid(nameBeforeTheLastAt)) return Optional.of(nameBeforeTheLastAt)
                    } else {
                        val lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@")
                        if (lastButOneIndex > -1) {
                            // A case when a Username contains "@"
                            val potentialWebFingerId = uniqueName.substring(lastButOneIndex + 1)
                            if (isWebFingerIdValid(potentialWebFingerId)) {
                                val nameBeforeLastButOneAt = uniqueName.substring(0, lastButOneIndex)
                                if (origin.isUsernameValid(nameBeforeLastButOneAt)) return Optional.of(nameBeforeLastButOneAt)
                            }
                        }
                    }
                }
                if (origin.isUsernameValid(uniqueName)) return Optional.of(uniqueName)
            }
            return Optional.empty()
        }

        fun uniqueNameToWebFingerId(origin: Origin?, uniqueName: String?): Optional<String?>? {
            if (StringUtil.nonEmpty(uniqueName)) {
                if (uniqueName.contains("@")) {
                    val nameBeforeTheLastAt = uniqueName.substring(0, uniqueName.lastIndexOf("@"))
                    if (isWebFingerIdValid(uniqueName)) {
                        return Optional.of(uniqueName.toLowerCase())
                    } else {
                        val lastButOneIndex = nameBeforeTheLastAt.lastIndexOf("@")
                        if (lastButOneIndex > -1) {
                            val potentialWebFingerId = uniqueName.substring(lastButOneIndex + 1)
                            if (isWebFingerIdValid(potentialWebFingerId)) {
                                return Optional.of(potentialWebFingerId.toLowerCase())
                            }
                        }
                    }
                } else {
                    return Optional.of(uniqueName.toLowerCase() + "@" +
                            origin.fixUriForPermalink(UriUtils.fromUrl(origin.getUrl())).host)
                }
            }
            return Optional.empty()
        }

        fun isWebFingerIdValid(webFingerId: String?): Boolean {
            return StringUtil.nonEmpty(webFingerId) && Patterns.WEBFINGER_ID_REGEX_PATTERN.matcher(webFingerId).matches()
        }

        @JvmOverloads
        fun toTempOid(webFingerId: String? = webFingerId, validUserName: String? = username): String? {
            return StringUtil.toTempOid(if (isWebFingerIdValid(webFingerId)) webFingerId else validUserName)
        }
    }

    init {
        this.actorId = actorId
        oid = if (StringUtil.isEmpty(actorOid)) "" else actorOid
        endpoints = ActorEndpoints.Companion.from(origin.myContext, actorId)
    }
}