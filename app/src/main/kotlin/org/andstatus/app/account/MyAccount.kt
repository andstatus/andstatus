/*
 * Copyright (C) 2010-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.account

import android.accounts.Account
import android.content.ContentResolver
import android.os.Bundle
import io.vavr.control.Try
import org.andstatus.app.IntentExtra
import org.andstatus.app.SearchObjects
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.OriginTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Connection
import org.andstatus.app.net.social.ConnectionEmpty
import org.andstatus.app.net.social.ConnectionFactory
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.Taggable
import org.andstatus.app.util.TaggedInstance
import org.andstatus.app.util.TriState
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Immutable class that holds "AndStatus account"-specific information including:
 * a Social network (twitter.com, identi.ca etc.),
 * Username in that system and [Connection] to it.
 *
 * @author yvolk@yurivolkov.com
 */
class MyAccount internal constructor(
    val data: AccountData,
    private val taggedInstance: TaggedInstance = TaggedInstance(MyAccount::class)
) : Comparable<MyAccount>, IsEmpty, Taggable by taggedInstance {
    val myContext: MyContext get() = data.myContext

    var actor: Actor = Actor.EMPTY
        get() = Actor.load(myContext, field.actorId, false) { field }

    @Volatile
    var connection: Connection = ConnectionEmpty.EMPTY
        private set

    /** Was this account authenticated last time _current_ credentials were verified?
     * CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    internal var credentialsVerified: CredentialsVerificationStatus = CredentialsVerificationStatus.NEVER

    /** Is this account authenticated with OAuth?  */
    private var isOAuth = true
    internal var syncFrequencySeconds: Long = 0
    var isSyncable = true
    internal var isSyncedAutomatically = true
    private val deleted: Boolean
    internal var order = 0

    internal constructor(accountName: AccountName) : this(AccountData.fromAccountName(accountName)) {}

    fun getValidOrCurrent(myContext: MyContext): MyAccount {
        return if (isValid) this else myContext.accounts.currentAccount
    }

    fun getOAccountName(): AccountName {
        return data.accountName
    }

    fun getWebFingerId(): String {
        return actor.getWebFingerId()
    }

    override val isEmpty: Boolean
        get() {
            return this === EMPTY
        }

    fun setConnection() {
        connection = ConnectionFactory.fromMyAccount(this, TriState.fromBoolean(isOAuth))
    }

    fun getNewOrExistingAndroidAccount(): Try<Account> {
        return AccountUtils.getExistingAndroidAccount(data.accountName).recoverWith(Exception::class.java)
        { notFound: Exception? ->
            if (isValidAndSucceeded()) AccountUtils.addEmptyAccount(data.accountName, getPassword())
            else Try.failure(notFound)
        }
    }

    fun getCredentialsPresent(): Boolean {
        return connection.getCredentialsPresent()
    }

    fun isValidAndSucceeded(): Boolean {
        return isValid && credentialsVerified == CredentialsVerificationStatus.SUCCEEDED
    }

    fun isPersistent(): Boolean {
        return data.isPersistent()
    }

    fun isFollowing(thatActor: Actor): Boolean {
        return myContext.users.friendsOfMyActors.entries.stream()
            .filter { entry: MutableMap.MutableEntry<Long, MutableSet<Long>> -> entry.key == thatActor.actorId }
            .anyMatch { entry: MutableMap.MutableEntry<Long, MutableSet<Long>> -> entry.value.contains(actor.actorId) }
    }

    fun getShortestUniqueAccountName(): String {
        var uniqueName = getAccountName()
        var found = false
        var possiblyUnique = actor.uniqueName
        for (persistentAccount in myContext.accounts.get()) {
            if (!persistentAccount.toString().equals(toString(), ignoreCase = true)
                && persistentAccount.actor.uniqueName.equals(possiblyUnique, ignoreCase = true)
            ) {
                found = true
                break
            }
        }
        if (!found) {
            uniqueName = possiblyUnique
        }
        if (!found) {
            possiblyUnique = username
            for (persistentAccount in myContext.accounts.get()) {
                if (!persistentAccount.toString().equals(toString(), ignoreCase = true)
                    && persistentAccount.username.equals(possiblyUnique, ignoreCase = true)
                ) {
                    found = true
                    break
                }
            }
            if (!found) {
                uniqueName = possiblyUnique
            }
        }
        if (!found) {
            var indAt = uniqueName.indexOf('@')
            if (indAt > 0) {
                possiblyUnique = uniqueName.substring(0, indAt)
                for (persistentAccount in myContext.accounts.get()) {
                    if (!persistentAccount.toString().equals(toString(), ignoreCase = true)) {
                        var toCompareWith = persistentAccount.username
                        indAt = toCompareWith.indexOf('@')
                        if (indAt > 0) {
                            toCompareWith = toCompareWith.substring(0, indAt)
                        }
                        if (toCompareWith.equals(possiblyUnique, ignoreCase = true)) {
                            found = true
                            break
                        }
                    }
                }
                if (!found) {
                    uniqueName = possiblyUnique
                }
            }
        }
        return uniqueName
    }

    val nonValid: Boolean get() = !isValid

    val isValid: Boolean
        get() {
            return (!deleted
                    && actor.actorId != 0L && connection.nonEmpty && data.accountName.isValid
                    && actor.oid.isNotEmpty())
        }

    fun setOAuth(isOAuthTriState: TriState) {
        val isOAuthBoolean: Boolean = if (isOAuthTriState == TriState.UNKNOWN) {
            data.getDataBoolean(KEY_OAUTH, origin.isOAuthDefault())
        } else {
            isOAuthTriState.toBoolean(origin.isOAuthDefault())
        }
        isOAuth = origin.originType.fixIsOAuth(isOAuthBoolean)
    }

    val username: String get() = actor.getUsername()

    /**
     * @return account name, unique for this application and suitable for android.accounts.AccountManager
     * The name is permanent and cannot be changed. This is why it may be used as Id
     */
    fun getAccountName(): String {
        return data.accountName.name
    }

    val actorId: Long get() = actor.actorId

    fun getActorOid(): String {
        return actor.oid
    }

    /**
     * @return The system in which the Account is defined, see [OriginTable]
     */
    val origin: Origin get() = data.accountName.origin
    val originId: Long get() = origin.id

    fun areClientKeysPresent(): Boolean {
        return connection.areOAuthClientKeysPresent()
    }

    fun charactersLeftForNote(html: String?): Int {
        return origin.charactersLeftForNote(html)
    }

    fun alternativeTermForResourceId(resId: Int): Int {
        return origin.alternativeTermForResourceId(resId)
    }

    fun isOAuth(): Boolean {
        return isOAuth
    }

    fun getPassword(): String {
        return connection.getPassword()
    }

    fun isUsernameValid(): Boolean {
        return actor.isUsernameValid()
    }

    fun isSearchSupported(searchObjects: SearchObjects?): Boolean {
        return connection.hasApiEndpoint(if (searchObjects == SearchObjects.NOTES) ApiRoutineEnum.SEARCH_NOTES else ApiRoutineEnum.SEARCH_ACTORS)
    }

    fun requestSync() {
        if (!isPersistent()) return
        AccountUtils.getExistingAndroidAccount(data.accountName)
            .onSuccess { a: Account? -> ContentResolver.requestSync(a, MatchedUri.AUTHORITY, Bundle()) }
    }

    fun getEffectiveSyncFrequencyMillis(): Long {
        var effectiveSyncFrequencySeconds = syncFrequencySeconds
        if (effectiveSyncFrequencySeconds <= 0) {
            effectiveSyncFrequencySeconds = MyPreferences.getSyncFrequencySeconds()
        }
        return TimeUnit.SECONDS.toMillis(effectiveSyncFrequencySeconds)
    }

    override fun toString(): String {
        if (EMPTY === this) {
            return MyStringBuilder.formatKeyValue(this, "EMPTY")
        }
        var members = (if (isValid) "" else "(invalid) ") + "accountName:" + data.accountName + ","
        try {
            if (actor.nonEmpty) {
                members += actor.toString() + ","
            }
            if (!isPersistent()) {
                members += "not persistent,"
            }
            if (isOAuth()) {
                members += "OAuth,"
            }
            if (credentialsVerified != CredentialsVerificationStatus.SUCCEEDED) {
                members += "verified:" + credentialsVerified.name + ","
            }
            if (getCredentialsPresent()) {
                members += "credentialsPresent,"
            }
            if (connection.isEmpty) {
                members += "connection:empty,"
            }
            if (syncFrequencySeconds > 0) {
                members += "syncFrequency:$syncFrequencySeconds,"
            }
            if (isSyncable) {
                members += "syncable,"
            }
            if (isSyncedAutomatically) {
                members += "syncauto,"
            }
            if (deleted) {
                members += "deleted,"
            }
        } catch (e: Exception) {
            MyLog.v(this, members, e)
        }
        return MyStringBuilder.formatKeyValue(this, members)
    }

    fun toJson(): JSONObject {
        return data.updateFrom(this).toJSon()
    }

    fun toAccountButtonText(): String {
        var accountButtonText = getShortestUniqueAccountName()
        if (!isValidAndSucceeded()) {
            accountButtonText = "($accountButtonText)"
        }
        return accountButtonText
    }

    override fun compareTo(other: MyAccount): Int {
        if (this === other) return 0
        if (isValid != other.isValid) {
            return if (isValid) -1 else 1
        }
        return if (order > other.order) 1
        else if (order < other.order) -1
        else getAccountName().compareTo(other.getAccountName())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MyAccount) return false

        return if (data.accountName != other.data.accountName) false
        else StringUtil.equalsNotEmpty(actor.oid, other.actor.oid)
    }

    override fun hashCode(): Int {
        var result = data.accountName.hashCode()
        if (!actor.oid.isEmpty()) {
            result = 31 * result + actor.oid.hashCode()
        }
        return result
    }

    fun shouldBeSyncedAutomatically(): Boolean {
        return isSyncedAutomatically && isValidAndSucceeded() && getEffectiveSyncFrequencyMillis() > 0
    }

    fun getLastSyncSucceededDate(): Long {
        return if (isValid && isPersistent()) myContext.timelines
            .filter(false, TriState.UNKNOWN, TimelineType.UNKNOWN, actor, Origin.EMPTY)
            .map { obj: Timeline -> obj.getSyncSucceededDate() }
            .max { obj: Long, anotherLong: Long -> obj.compareTo(anotherLong) }
            .orElse(0L) else 0L
    }

    fun hasAnyTimelines(): Boolean {
        for (timeline in myContext.timelines.values()) {
            if (timeline.myAccountToSync == this) {
                return true
            }
        }
        MyLog.v(this) { getAccountName() + " doesn't have any timeline" }
        return false
    }

    companion object {
        val KEY_ACCOUNT_NAME: String = "account_name"

        /** Username for the account  */
        val KEY_USERNAME: String = "username"

        /** A name that is unique for an origin  */
        val KEY_UNIQUE_NAME: String = "unique_name"

        /** [ActorTable._ID] in our System.  */
        val KEY_ACTOR_ID: String = "user_id"

        /** [ActorTable.ACTOR_OID] in Microblogging System.  */
        val KEY_ACTOR_OID: String = "user_oid"

        /** Is OAuth on for this MyAccount  */
        val KEY_OAUTH: String = "oauth"

        /** This account is in the process of deletion and should be ignored...  */
        val KEY_DELETED: String = "deleted"

        /** @see android.content.ContentResolver.getIsSyncable
         */
        val KEY_IS_SYNCABLE: String = "is_syncable"

        /** This corresponds to turning syncing on/off in Android Accounts
         * @see android.content.ContentResolver.getSyncAutomatically
         */
        val KEY_IS_SYNCED_AUTOMATICALLY: String = "sync_automatically"
        val KEY_ORDER: String = "order"

        val EMPTY: MyAccount by lazy {
            MyAccount(AccountName.EMPTY)
        }

        fun fromBundle(myContext: MyContext, bundle: Bundle?): MyAccount {
            return if (bundle == null) EMPTY else myContext.accounts.fromAccountName(bundle.getString(IntentExtra.ACCOUNT_NAME.key))
        }
    }

    init {
        actor = Actor.load(myContext, data.getDataLong(KEY_ACTOR_ID, 0L), false) {
            Actor.fromOid(data.accountName.origin, data.getDataString(KEY_ACTOR_OID))
                .withUniqueName(data.accountName.getUniqueName())
                .lookupUser()
        }
        deleted = data.getDataBoolean(KEY_DELETED, false)
        syncFrequencySeconds = data.getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0L)
        isSyncable = data.getDataBoolean(KEY_IS_SYNCABLE, true)
        isSyncedAutomatically = data.getDataBoolean(KEY_IS_SYNCED_AUTOMATICALLY, true)
        setOAuth(TriState.fromBoolean(data.getDataBoolean(KEY_OAUTH, origin.isOAuthDefault())))
        setConnection()
        credentialsVerified = CredentialsVerificationStatus.load(data)
        order = data.getDataInt(KEY_ORDER, 1)
    }
}
