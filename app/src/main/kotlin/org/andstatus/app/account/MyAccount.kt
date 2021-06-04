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
import org.andstatus.app.R
import org.andstatus.app.SearchObjects
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.OriginTable
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.http.OAuthService
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Connection
import org.andstatus.app.net.social.ConnectionEmpty
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineSaver
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TaggedClass
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
class MyAccount internal constructor(val data: AccountData) : Comparable<MyAccount>, IsEmpty, TaggedClass {
    val myContext: MyContext get() = data.myContext

    var actor: Actor = Actor.EMPTY
        get() = Actor.load(myContext, field.actorId, false) { field }
        private set

    @Volatile
    var connection: Connection = ConnectionEmpty.EMPTY
        private set

    /** Was this account authenticated last time _current_ credentials were verified?
     * CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    private var credentialsVerified: CredentialsVerificationStatus = CredentialsVerificationStatus.NEVER

    /** Is this account authenticated with OAuth?  */
    private var isOAuth = true
    private var syncFrequencySeconds: Long = 0
    var isSyncable = true
    private var isSyncedAutomatically = true
    private val deleted: Boolean
    private var order = 0

    internal constructor(accountName: AccountName) : this(AccountData.fromAccountName(accountName)) {}

    fun getValidOrCurrent(myContext: MyContext): MyAccount {
        return if (isValid) this else myContext.accounts().currentAccount
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

    fun setConnection(): Connection {
        return Connection.fromMyAccount(this, TriState.fromBoolean(isOAuth)).also {
            connection = it
        }
    }

    private fun getNewOrExistingAndroidAccount(): Try<Account> {
        return AccountUtils.getExistingAndroidAccount(data.accountName).recoverWith(Exception::class.java)
        { notFound: Exception? ->
            if (isValidAndSucceeded()) AccountUtils.addEmptyAccount(data.accountName, getPassword())
            else Try.failure(notFound)
        }
    }

    fun getCredentialsPresent(): Boolean {
        return connection.getCredentialsPresent()
    }

    fun getCredentialsVerified(): CredentialsVerificationStatus {
        return credentialsVerified
    }

    fun isValidAndSucceeded(): Boolean {
        return isValid && getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED
    }

    private fun isPersistent(): Boolean {
        return data.isPersistent()
    }

    fun isFollowing(thatActor: Actor): Boolean {
        return myContext.users().friendsOfMyActors.entries.stream()
                .filter { entry: MutableMap.MutableEntry<Long, MutableSet<Long>> -> entry.key == thatActor.actorId }
                .anyMatch { entry: MutableMap.MutableEntry<Long, MutableSet<Long>> -> entry.value.contains(actor.actorId) }
    }

    fun getShortestUniqueAccountName(): String {
        var uniqueName = getAccountName()
        var found = false
        var possiblyUnique = actor.uniqueName
        for (persistentAccount in myContext.accounts().get()) {
            if (!persistentAccount.toString().equals(toString(), ignoreCase = true)
                    && persistentAccount.actor.uniqueName.equals(possiblyUnique, ignoreCase = true)) {
                found = true
                break
            }
        }
        if (!found) {
            uniqueName = possiblyUnique
        }
        if (!found) {
            possiblyUnique = username
            for (persistentAccount in myContext.accounts().get()) {
                if (!persistentAccount.toString().equals(toString(), ignoreCase = true)
                        && persistentAccount.username.equals(possiblyUnique, ignoreCase = true)) {
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
                for (persistentAccount in myContext.accounts().get()) {
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

    private fun setOAuth(isOAuthTriState: TriState) {
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

    fun getOAuthService(): OAuthService? {
        return connection.getOAuthService()
    }

    fun getOrder(): Int {
        return order
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

    fun isUsernameNeededToStartAddingNewAccount(): Boolean {
        return origin.originType.isUsernameNeededToStartAddingNewAccount(isOAuth())
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

    fun getSyncFrequencySeconds(): Long {
        return syncFrequencySeconds
    }

    fun getEffectiveSyncFrequencyMillis(): Long {
        var effectiveSyncFrequencySeconds = getSyncFrequencySeconds()
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
            if (getCredentialsVerified() != CredentialsVerificationStatus.SUCCEEDED) {
                members += "verified:" + getCredentialsVerified().name + ","
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
        return isSyncedAutomatically() && isValidAndSucceeded() && getEffectiveSyncFrequencyMillis() > 0
    }

    fun isSyncedAutomatically(): Boolean {
        return isSyncedAutomatically
    }

    fun getLastSyncSucceededDate(): Long {
        return if (isValid && isPersistent()) myContext.timelines()
                .filter(false, TriState.UNKNOWN, TimelineType.UNKNOWN, actor,  Origin.EMPTY)
                .map { obj: Timeline -> obj.getSyncSucceededDate() }
                .max { obj: Long, anotherLong: Long -> obj.compareTo(anotherLong) }
                .orElse(0L) else 0L
    }

    fun hasAnyTimelines(): Boolean {
        for (timeline in myContext.timelines().values()) {
            if (timeline.myAccountToSync == this) {
                return true
            }
        }
        MyLog.v(this) { getAccountName() + " doesn't have any timeline" }
        return false
    }

    override fun classTag(): String {
        return TAG
    }

    /** Companion class used to load/create/change/delete [MyAccount]'s data  */
    class Builder private constructor(myAccountIn: MyAccount) : TaggedClass {
        var myAccount = myAccountIn
            private set

        private fun fixInconsistenciesWithChangedEnvironmentSilently() {
            var changed = false
            if (isPersistent() && myAccount.actor.actorId == 0L) {
                changed = true
                assignActorId()
                MyLog.i(this, "MyAccount '" + myAccount.getAccountName()
                        + "' was not connected to the Actor table. actorId=" + myAccount.actor.actorId)
            }
            if (!myAccount.getCredentialsPresent()
                    && myAccount.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED) {
                MyLog.i(this, "Account's credentials were lost?! Fixing...")
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER)
                changed = true
            }
            if (changed && isPersistent()) {
                saveSilently()
            }
        }

        private fun logLoadResult(method: String?) {
            if (myAccount.isValid) {
                MyLog.v(this) { "$method Loaded $this" }
            } else {
                MyLog.i(this, "$method Load failed: Invalid account; $this ${MyLog.getStackTrace(Exception())}")
            }
        }

        fun setOrigin(origin: Origin) {
            rebuildMyAccount(origin, getUniqueName())
        }

        fun setUniqueName(uniqueName: String) {
            rebuildMyAccount(myAccount.origin, uniqueName)
        }

        fun setOAuth(isOauthBoolean: Boolean): Builder {
            val isOauth = if (isOauthBoolean == myAccount.origin.isOAuthDefault()) TriState.UNKNOWN
                else TriState.fromBoolean(isOauthBoolean)
            myAccount.setOAuth(isOauth)
            return this
        }

        fun rebuildMyAccount(myContext: MyContext) {
            rebuildMyAccount(myContext.origins().fromName(myAccount.origin.name), getUniqueName())
        }

        private fun rebuildMyAccount(origin: Origin, uniqueName: String) {
            rebuildMyAccount(AccountName.fromOriginAndUniqueName(origin, uniqueName))
        }

        fun rebuildMyAccount(accountName: AccountName) {
            val ma = myAccount.myContext.accounts().fromAccountName(accountName.name)
            myAccount = if (ma.isValid) ma else MyAccount(myAccount.data.withAccountName(accountName))
        }

        fun getOrigin(): Origin {
            return myAccount.origin
        }

        fun getUniqueName(): String {
            return myAccount.getOAccountName().getUniqueName()
        }

        fun getPassword(): String {
            return myAccount.getPassword()
        }

        fun isOAuth(): Boolean {
            return myAccount.isOAuth()
        }

        /**
         * @return Is this object persistent
         */
        fun isPersistent(): Boolean {
            return myAccount.isPersistent()
        }

        /**
         * Delete all Account's data
         * @return true = success
         */
        fun deleteData(): Boolean {
            setAndroidAccountDeleted()
            return true
        }

        private fun setAndroidAccountDeleted() {
            myAccount.data.setDataBoolean(KEY_DELETED, true)
        }

        fun setSyncedAutomatically(syncedAutomatically: Boolean) {
            myAccount.isSyncedAutomatically = syncedAutomatically
        }

        fun setOrder(order: Int) {
            myAccount.order = order
        }

        fun save() {
            if (saveSilently().getOrElse(false) && myAccount.myContext.isReady()) {
                MyPreferences.onPreferencesChanged()
            }
        }

        /** Save this MyAccount to AccountManager  */
        fun saveSilently(): Try<Boolean> {
            return if (myAccount.isValid) {
                myAccount.getNewOrExistingAndroidAccount()
                        .onSuccess { account: Account -> myAccount.data.updateFrom(myAccount) }
                        .flatMap { account: Account -> myAccount.data.saveIfChanged(account) }
                        .onFailure { e: Throwable -> myAccount.data.setPersistent(false) }
                        .onSuccess { result1: Boolean ->
                            MyLog.v(this) {
                                (if (result1) " Saved " else " Didn't change ") +
                                        this.toString()
                            }
                            myAccount.myContext.accounts().addIfAbsent(myAccount)
                            if (myAccount.myContext.isReady() && !myAccount.hasAnyTimelines()) {
                                TimelineSaver().setAddDefaults(true).setAccount(myAccount).execute(myAccount.myContext)
                            }
                        }
                        .onFailure { e: Throwable ->
                            MyLog.v(this) {
                                "Failed to save " + this.toString() +
                                        "; Error: " + e.message
                            }
                        }
            } else {
                MyLog.v(this) { "Didn't save invalid account: $myAccount" }
                Try.failure(Exception())
            }
        }

        fun getOriginConfig(): Try<Builder> {
            return getConnection().getConfig().map { config: OriginConfig ->
                if (config.nonEmpty) {
                    val originBuilder = Origin.Builder(myAccount.origin)
                    originBuilder.save(config)
                    MyLog.v(this, "Get Origin config succeeded $config")
                }
                this
            }
        }

        fun onCredentialsVerified(actor: Actor): Try<Builder> {
            var ok = actor.nonEmpty && !actor.oid.isEmpty() && actor.isUsernameValid()
            val errorSettingUsername = !ok
            var credentialsOfOtherAccount = false
            // We are comparing usernames ignoring case, but we fix correct case
            // as the Originating system tells us.
            if (ok && !myAccount.username.isEmpty()
                    && myAccount.data.accountName.username.compareTo(actor.getUsername(), ignoreCase = true) != 0) {
                // Credentials belong to other Account ??
                ok = false
                credentialsOfOtherAccount = true
            }
            if (ok) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED)
                actor.lookupActorId()
                actor.lookupUser()
                actor.user.setIsMyUser(TriState.TRUE)
                actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
                myAccount.actor = actor
                if (DatabaseConverterController.isUpgrading()) {
                    MyLog.v(this, "Upgrade in progress")
                    myAccount.actor.actorId = myAccount.data.getDataLong(KEY_ACTOR_ID, myAccount.actor.actorId)
                } else {
                    DataUpdater(myAccount).onActivity(actor.update(actor))
                }
                if (!isPersistent()) {
                    // Now we know the name (or proper case of the name) of this Account!
                    val sameName = myAccount.data.accountName.getUniqueName() == actor.uniqueName
                    if (!sameName) {
                        MyLog.i(this, "name changed from " + myAccount.data.accountName.getUniqueName() +
                                " to " + actor.uniqueName)
                        myAccount.data.updateFrom(myAccount)
                        val newData = myAccount.data.withAccountName(
                                AccountName.fromOriginAndUniqueName(myAccount.origin, actor.uniqueName))
                        myAccount = loadFromAccountData(newData, "onCredentialsVerified").myAccount
                    }
                    save()
                }
            }
            if (!ok || !myAccount.getCredentialsPresent()) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.FAILED)
            }
            save()
            if (credentialsOfOtherAccount) {
                MyLog.w(this, myAccount.myContext.context().getText(R.string.error_credentials_of_other_user).toString() + ": " +
                        actor.getUniqueNameWithOrigin() +
                        " account name: " + myAccount.getAccountName() +
                        " vs username: " + actor.getUsername())
                return Try.failure(ConnectionException(StatusCode.CREDENTIALS_OF_OTHER_ACCOUNT, actor.getUniqueNameWithOrigin()))
            }
            if (errorSettingUsername) {
                val msg = myAccount.myContext.context().getText(R.string.error_set_username).toString() + " " + actor.getUsername()
                MyLog.w(this, msg)
                return Try.failure(ConnectionException(StatusCode.AUTHENTICATION_ERROR, msg))
            }
            return Try.success(this)
        }

        fun setUserTokenWithSecret(token: String?, secret: String?) {
            getConnection().setUserTokenWithSecret(token, secret)
        }

        fun setCredentialsVerificationStatus(cv: CredentialsVerificationStatus) {
            myAccount.credentialsVerified = cv
            if (cv != CredentialsVerificationStatus.SUCCEEDED) {
                getConnection().clearAuthInformation()
            }
        }

        @Throws(ConnectionException::class)
        fun registerClient() {
            MyLog.v(this) { "Registering client application for " + myAccount.username }
            myAccount.setConnection()
            getConnection().registerClientForAccount()
        }

        fun getConnection(): Connection {
            return if (myAccount.connection.isEmpty)
                Connection.fromOrigin(myAccount.origin, TriState.fromBoolean(isOAuth()))
            else myAccount.connection
        }

        fun setPassword(password: String?) {
            if (StringUtil.notEmpty(password, "").compareTo(getConnection().getPassword()) != 0) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER)
                getConnection().setPassword(password)
            }
        }

        private fun assignActorId() {
            myAccount.actor.actorId = myAccount.origin.usernameToId(myAccount.username)
            if (myAccount.actor.actorId == 0L) {
                try {
                    DataUpdater(myAccount).onActivity(myAccount.actor.update(myAccount.actor))
                } catch (e: Exception) {
                    MyLog.e(this, "assignUserId to $myAccount", e)
                }
            }
        }

        override fun toString(): String {
            return myAccount.toString()
        }

        fun clearClientKeys() {
            myAccount.connection.clearClientKeys()
        }

        fun setSyncFrequencySeconds(syncFrequencySeconds: Long) {
            myAccount.syncFrequencySeconds = syncFrequencySeconds
        }

        override fun classTag(): String {
            return TAG
        }

        fun toJsonString(): String = myAccount.toJson().toString()

        companion object {
            private val TAG: String = MyAccount.TAG + "." + Builder::class.java.simpleName
            val EMPTY = Builder(MyAccount.EMPTY)

            /**
             * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
             */
            fun fromAccountName(accountName: AccountName): Builder {
                return fromMyAccount(myAccountFromName(accountName))
            }

            /**
             * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
             */
            private fun myAccountFromName(accountName: AccountName): MyAccount {
                if (accountName.myContext.isEmpty) return MyAccount.EMPTY
                val persistentAccount = accountName.myContext.accounts().fromAccountName(accountName)
                return if (persistentAccount.isValid) persistentAccount else MyAccount(accountName)
            }

            /** Loads existing account from Persistence  */
            fun loadFromAndroidAccount(myContext: MyContext, account: Account): Builder {
                return loadFromAccountData(AccountData.fromAndroidAccount(myContext, account), "fromAndroidAccount")
            }

            fun fromJsonString(myContext: MyContext, jsonString: String?): Builder =
                    JsonUtils.toJsonObject(jsonString)
                            .map { jso ->
                                if (myContext.isEmpty) EMPTY
                                else AccountData.fromJson(myContext, jso, false)
                                    .let { loadFromAccountData(it, "") }
                            }.getOrElse(EMPTY)


            fun loadFromAccountData(accountData: AccountData, method: String?): Builder {
                val myAccount = MyAccount(accountData)
                val builder = fromMyAccount(myAccount)
                if (! MyContextHolder.myContextHolder.isOnRestore()) builder.fixInconsistenciesWithChangedEnvironmentSilently()
                builder.logLoadResult(method)
                return builder
            }

            fun fromMyAccount(ma: MyAccount): Builder {
                return Builder(ma)
            }
        }
    }

    companion object {
        private val TAG: String = MyAccount::class.java.simpleName
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

        val EMPTY: MyAccount = MyAccount(AccountName.getEmpty())
        fun fromBundle(myContext: MyContext, bundle: Bundle?): MyAccount {
            return if (bundle == null) EMPTY else myContext.accounts().fromAccountName(bundle.getString(IntentExtra.ACCOUNT_NAME.key))
        }
    }

    init {
        actor = Actor.load(myContext, data.getDataLong(KEY_ACTOR_ID, 0L), false
        ) {
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
        connection.setPassword(data.getDataString(Connection.KEY_PASSWORD))
        credentialsVerified = CredentialsVerificationStatus.load(data)
        order = data.getDataInt(KEY_ORDER, 1)
    }
}
