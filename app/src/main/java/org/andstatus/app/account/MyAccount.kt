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
import android.os.Parcel
import android.os.Parcelable
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
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TaggedClass
import org.andstatus.app.util.TriState
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Immutable class that holds "AndStatus account"-specific information including:
 * a Social network (twitter.com, identi.ca etc.),
 * Username in that system and [Connection] to it.
 *
 * @author yvolk@yurivolkov.com
 */
class MyAccount internal constructor(val data: AccountData) : Comparable<MyAccount?>, IsEmpty, TaggedClass {
    private var actor: Actor

    @Volatile
    private var connection: Connection? = null

    /** Was this account authenticated last time _current_ credentials were verified?
     * CredentialsVerified.NEVER - after changes of "credentials": password/OAuth...
     */
    private var credentialsVerified: CredentialsVerificationStatus? = CredentialsVerificationStatus.NEVER

    /** Is this account authenticated with OAuth?  */
    private var isOAuth = true
    private var syncFrequencySeconds: Long = 0
    var isSyncable = true
    private var isSyncedAutomatically = true
    private val deleted: Boolean
    private var order = 0

    internal constructor(accountName: AccountName?) : this(AccountData.Companion.fromAccountName(accountName)) {}

    fun getValidOrCurrent(myContext: MyContext?): MyAccount? {
        return if (isValid()) this else myContext.accounts().currentAccount
    }

    fun getOAccountName(): AccountName? {
        return data.accountName
    }

    fun getActor(): Actor {
        return Actor.load(data.myContext(), actor.actorId, false, Supplier { actor })
    }

    fun getWebFingerId(): String? {
        return actor.getWebFingerId()
    }

    override fun isEmpty(): Boolean {
        return this === EMPTY
    }

    fun setConnection(): Connection? {
        connection = Connection.Companion.fromMyAccount(this, TriState.Companion.fromBoolean(isOAuth))
        return connection
    }

    private fun getNewOrExistingAndroidAccount(): Try<Account?>? {
        return AccountUtils.getExistingAndroidAccount(data.accountName).recoverWith(Exception::class.java
        ) { notFound: Exception? -> if (isValidAndSucceeded()) AccountUtils.addEmptyAccount(data.accountName, getPassword()) else Try.failure(notFound) }
    }

    fun getCredentialsPresent(): Boolean {
        return getConnection() != null && getConnection().getCredentialsPresent()
    }

    fun getCredentialsVerified(): CredentialsVerificationStatus? {
        return credentialsVerified
    }

    fun isValidAndSucceeded(): Boolean {
        return isValid() && getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED
    }

    private fun isPersistent(): Boolean {
        return data.isPersistent
    }

    fun isFollowing(thatActor: Actor?): Boolean {
        return data.myContext().users().friendsOfMyActors.entries.stream()
                .filter { entry: MutableMap.MutableEntry<Long?, MutableSet<Long?>?>? -> entry.key == thatActor.actorId }
                .anyMatch { entry: MutableMap.MutableEntry<Long?, MutableSet<Long?>?>? -> entry.value.contains(getActor().actorId) }
    }

    fun getShortestUniqueAccountName(): String? {
        var uniqueName = getAccountName()
        var found = false
        var possiblyUnique = getActor().getUniqueName()
        for (persistentAccount in data.myContext().accounts().get()) {
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
            possiblyUnique = getUsername()
            for (persistentAccount in data.myContext().accounts().get()) {
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
                for (persistentAccount in data.myContext().accounts().get()) {
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

    fun nonValid(): Boolean {
        return !isValid()
    }

    fun isValid(): Boolean {
        return (!deleted
                && actor.actorId != 0L && connection != null && data.accountName.isValid
                && !actor.oid.isNullOrEmpty())
    }

    private fun setOAuth(isOAuthTriState: TriState?) {
        var isOAuthBoolean = true
        isOAuthBoolean = if (isOAuthTriState == TriState.UNKNOWN) {
            data.getDataBoolean(KEY_OAUTH, getOrigin().isOAuthDefault())
        } else {
            isOAuthTriState.toBoolean(getOrigin().isOAuthDefault())
        }
        isOAuth = getOrigin().getOriginType().fixIsOAuth(isOAuthBoolean)
    }

    fun getUsername(): String? {
        return actor.getUsername()
    }

    /**
     * @return account name, unique for this application and suitable for android.accounts.AccountManager
     * The name is permanent and cannot be changed. This is why it may be used as Id
     */
    fun getAccountName(): String? {
        return data.accountName.name
    }

    fun getActorId(): Long {
        return actor.actorId
    }

    fun getActorOid(): String {
        return actor.oid
    }

    /**
     * @return The system in which the Account is defined, see [OriginTable]
     */
    val origin: Origin get() = data.accountName.origin

    fun getOriginId(): Long {
        return getOrigin().id
    }

    fun getConnection(): Connection? {
        return if (connection == null) ConnectionEmpty.Companion.EMPTY else connection
    }

    fun areClientKeysPresent(): Boolean {
        return getConnection().areOAuthClientKeysPresent()
    }

    fun getOAuthService(): OAuthService? {
        return getConnection().getOAuthService()
    }

    fun getOrder(): Int {
        return order
    }

    fun charactersLeftForNote(html: String?): Int {
        return getOrigin().charactersLeftForNote(html)
    }

    fun alternativeTermForResourceId(resId: Int): Int {
        return getOrigin().alternativeTermForResourceId(resId)
    }

    fun isOAuth(): Boolean {
        return isOAuth
    }

    fun getPassword(): String? {
        return getConnection().getPassword()
    }

    fun isUsernameNeededToStartAddingNewAccount(): Boolean {
        return getOrigin().getOriginType().isUsernameNeededToStartAddingNewAccount(isOAuth())
    }

    fun isUsernameValid(): Boolean {
        return actor.isUsernameValid()
    }

    fun isSearchSupported(searchObjects: SearchObjects?): Boolean {
        return getConnection().hasApiEndpoint(if (searchObjects == SearchObjects.NOTES) ApiRoutineEnum.SEARCH_NOTES else ApiRoutineEnum.SEARCH_ACTORS)
    }

    fun requestSync() {
        if (!isPersistent()) return
        AccountUtils.getExistingAndroidAccount(data.accountName)
                .onSuccess { a: Account? -> ContentResolver.requestSync(a, MatchedUri.Companion.AUTHORITY, Bundle()) }
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
            return MyStringBuilder.Companion.formatKeyValue(this, "EMPTY")
        }
        var members = (if (isValid()) "" else "(invalid) ") + "accountName:" + data.accountName + ","
        try {
            if (actor != null && actor.nonEmpty()) {
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
            if (connection == null) {
                members += "connection:null,"
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
        return MyStringBuilder.Companion.formatKeyValue(this, members)
    }

    fun toJson(): JSONObject? {
        return data.updateFrom(this).toJSon()
    }

    fun toAccountButtonText(): String? {
        var accountButtonText = getShortestUniqueAccountName()
        if (!isValidAndSucceeded()) {
            accountButtonText = "($accountButtonText)"
        }
        return accountButtonText
    }

    override fun compareTo(another: MyAccount?): Int {
        if (this === another) {
            return 0
        }
        if (another == null) {
            return -1
        }
        if (isValid() != another.isValid()) {
            return if (isValid()) -1 else 1
        }
        return if (order > another.order) 1 else if (order < another.order) -1 else getAccountName().compareTo(another.getAccountName())
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is MyAccount) return false
        val myAccount = o as MyAccount?
        return if (data.accountName != myAccount.data.accountName) false else StringUtil.equalsNotEmpty(actor.oid, myAccount.actor.oid)
    }

    override fun hashCode(): Int {
        var result = data.accountName.hashCode()
        if (!actor.oid.isNullOrEmpty()) {
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
        return if (isValid() && isPersistent()) data.myContext().timelines()
                .filter(false, TriState.UNKNOWN, TimelineType.UNKNOWN, actor,  Origin.EMPTY)
                .map { obj: Timeline? -> obj.getSyncSucceededDate() }.max { obj: Long?, anotherLong: Long? -> obj.compareTo(anotherLong) }.orElse(0L) else 0L
    }

    fun hasAnyTimelines(): Boolean {
        for (timeline in data.myContext().timelines().values()) {
            if (timeline.myAccountToSync == this) {
                return true
            }
        }
        MyLog.v(this) { getAccountName() + " doesn't have any timeline" }
        return false
    }

    override fun classTag(): String? {
        return TAG
    }

    /** Companion class used to load/create/change/delete [MyAccount]'s data  */
    class Builder private constructor(@field:Volatile private var myAccount: MyAccount?) : Parcelable, TaggedClass {
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
            if (myAccount.isValid()) {
                MyLog.v(this) { "$method Loaded $this" }
            } else {
                MyLog.i(this, """$method Failed to load: Invalid account; $this
${MyLog.getStackTrace(Exception())}""")
            }
        }

        fun setOrigin(origin: Origin?) {
            rebuildMyAccount(origin, getUniqueName())
        }

        fun setUniqueName(uniqueName: String?) {
            rebuildMyAccount(getOrigin(), uniqueName)
        }

        fun setOAuth(isOauthBoolean: Boolean): Builder? {
            val isOauth = if (isOauthBoolean == getOrigin().isOAuthDefault()) TriState.UNKNOWN else TriState.Companion.fromBoolean(isOauthBoolean)
            myAccount.setOAuth(isOauth)
            return this
        }

        fun rebuildMyAccount(myContext: MyContext?) {
            rebuildMyAccount(myContext.origins().fromName(getOrigin().getName()), getUniqueName())
        }

        private fun rebuildMyAccount(origin: Origin?, uniqueName: String?) {
            rebuildMyAccount(AccountName.Companion.fromOriginAndUniqueName(origin, uniqueName))
        }

        fun rebuildMyAccount(accountName: AccountName?) {
            val ma = accountName.myContext().accounts().fromAccountName(accountName.getName())
            myAccount = if (ma.isValid) ma else MyAccount(getAccount().data.withAccountName(accountName))
        }

        fun getOrigin(): Origin? {
            return myAccount.getOrigin()
        }

        fun getUniqueName(): String? {
            return getAccount().getOAccountName().getUniqueName()
        }

        fun getPassword(): String? {
            return getAccount().getPassword()
        }

        fun isOAuth(): Boolean {
            return getAccount().isOAuth()
        }

        fun getAccount(): MyAccount? {
            return myAccount
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
            val ok = true
            if (isPersistent() && myAccount.actor.actorId != 0L) {
                // TODO: Delete data for this Account ?!
                myAccount.actor.actorId = 0
            }
            setAndroidAccountDeleted()
            return ok
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
            if (saveSilently().getOrElse(false) && myContext().isReady()) {
                MyPreferences.onPreferencesChanged()
            }
        }

        /** Save this MyAccount to AccountManager  */
        fun saveSilently(): Try<Boolean?>? {
            return if (myAccount.isValid()) {
                myAccount.getNewOrExistingAndroidAccount()
                        .onSuccess(Consumer { account: Account? -> myAccount.data.updateFrom(myAccount) })
                        .flatMap { account: Account? -> myAccount.data.saveIfChanged(account) }
                        .onFailure { e: Throwable? -> myAccount.data.isPersistent = false }
                        .onSuccess { result1: Boolean? ->
                            MyLog.v(this) {
                                (if (result1) " Saved " else " Didn't change ") +
                                        this.toString()
                            }
                            myContext().accounts().addIfAbsent(myAccount)
                            if (myContext().isReady() && !myAccount.hasAnyTimelines()) {
                                TimelineSaver().setAddDefaults(true).setAccount(myAccount).execute(myContext())
                            }
                        }
                        .onFailure { e: Throwable? ->
                            MyLog.v(this) {
                                "Failed to save" + this.toString() +
                                        "; Error: " + e.message
                            }
                        }
            } else {
                MyLog.v(this) { "Didn't save invalid account: $myAccount" }
                Try.failure(Exception())
            }
        }

        fun getOriginConfig(): Try<Builder?>? {
            return getConnection().getConfig().map { config: OriginConfig? ->
                if (config.nonEmpty()) {
                    val originBuilder = Origin.Builder(myAccount.getOrigin())
                    originBuilder.save(config)
                    MyLog.v(this, "Get Origin config succeeded $config")
                }
                this
            }
        }

        fun onCredentialsVerified(actor: Actor): Try<Builder?>? {
            var ok = actor.nonEmpty() && !actor.oid.isNullOrEmpty() && actor.isUsernameValid
            val errorSettingUsername = !ok
            var credentialsOfOtherAccount = false
            // We are comparing usernames ignoring case, but we fix correct case
            // as the Originating system tells us.
            if (ok && !myAccount.getUsername().isNullOrEmpty()
                    && myAccount.data.accountName.username.compareTo(actor.username, ignoreCase = true) != 0) {
                // Credentials belong to other Account ??
                ok = false
                credentialsOfOtherAccount = true
            }
            if (ok) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.SUCCEEDED)
                actor.lookupActorId()
                actor.lookupUser()
                actor.user.setIsMyUser(TriState.TRUE)
                actor.updatedDate = MyLog.uniqueCurrentTimeMS()
                myAccount.actor = actor
                if (DatabaseConverterController.Companion.isUpgrading()) {
                    MyLog.v(this, "Upgrade in progress")
                    myAccount.actor.actorId = myAccount.data.getDataLong(KEY_ACTOR_ID, myAccount.actor.actorId)
                } else {
                    DataUpdater(myAccount).onActivity(actor.update(actor))
                }
                if (!isPersistent()) {
                    // Now we know the name (or proper case of the name) of this Account!
                    val sameName = myAccount.data.accountName.uniqueName == actor.uniqueName
                    if (!sameName) {
                        MyLog.i(this, "name changed from " + myAccount.data.accountName.uniqueName +
                                " to " + actor.uniqueName)
                        myAccount.data.updateFrom(myAccount)
                        val newData = myAccount.data.withAccountName(
                                AccountName.Companion.fromOriginAndUniqueName(myAccount.getOrigin(), actor.uniqueName))
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
                MyLog.w(this, myContext().context().getText(R.string.error_credentials_of_other_user).toString() + ": " +
                        actor.uniqueNameWithOrigin +
                        " account name: " + myAccount.getAccountName() +
                        " vs username: " + actor.username)
                return Try.failure(ConnectionException(StatusCode.CREDENTIALS_OF_OTHER_ACCOUNT, actor.uniqueNameWithOrigin))
            }
            if (errorSettingUsername) {
                val msg = myContext().context().getText(R.string.error_set_username).toString() + " " + actor.username
                MyLog.w(this, msg)
                return Try.failure(ConnectionException(StatusCode.AUTHENTICATION_ERROR, msg))
            }
            return Try.success(this)
        }

        fun setUserTokenWithSecret(token: String?, secret: String?) {
            getConnection().setUserTokenWithSecret(token, secret)
        }

        fun setCredentialsVerificationStatus(cv: CredentialsVerificationStatus?) {
            myAccount.credentialsVerified = cv
            if (cv != CredentialsVerificationStatus.SUCCEEDED) {
                getConnection().clearAuthInformation()
            }
        }

        @Throws(ConnectionException::class)
        fun registerClient() {
            MyLog.v(this) { "Registering client application for " + myAccount.getUsername() }
            myAccount.setConnection()
            getConnection().registerClientForAccount()
        }

        fun getConnection(): Connection? {
            return if (myAccount.getConnection().isEmpty()) Connection.Companion.fromOrigin(getOrigin(), TriState.Companion.fromBoolean(isOAuth())) else myAccount.getConnection()
        }

        fun setPassword(password: String?) {
            if (StringUtil.notEmpty(password, "").compareTo(getConnection().getPassword()) != 0) {
                setCredentialsVerificationStatus(CredentialsVerificationStatus.NEVER)
                getConnection().setPassword(password)
            }
        }

        private fun assignActorId() {
            myAccount.actor.actorId = myAccount.getOrigin().usernameToId(myAccount.getUsername())
            if (myAccount.actor.actorId == 0L) {
                try {
                    DataUpdater(myAccount).onActivity(myAccount.actor.update(myAccount.actor))
                } catch (e: Exception) {
                    MyLog.e(this, "assignUserId to $myAccount", e)
                }
            }
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel?, flags: Int) {
            save()
            dest.writeParcelable(myAccount.data, flags)
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

        fun myContext(): MyContext? {
            return myAccount.data.myContext()
        }

        override fun classTag(): String? {
            return TAG
        }

        companion object {
            private val TAG: String? = MyAccount.TAG + "." + Builder::class.java.simpleName

            /**
             * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
             */
            fun fromAccountName(accountName: AccountName?): Builder? {
                return fromMyAccount(myAccountFromName(accountName))
            }

            /**
             * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
             */
            private fun myAccountFromName(accountName: AccountName?): MyAccount? {
                val persistentAccount = accountName.myContext().accounts().fromAccountName(accountName)
                return if (persistentAccount.isValid) persistentAccount else MyAccount(accountName)
            }

            /** Loads existing account from Persistence  */
            fun loadFromAndroidAccount(myContext: MyContext?, account: Account): Builder? {
                return loadFromAccountData(AccountData.Companion.fromAndroidAccount(myContext, account), "fromAndroidAccount")
            }

            fun loadFromAccountData(accountData: AccountData, method: String?): Builder? {
                val myAccount = MyAccount(accountData)
                val builder = fromMyAccount(myAccount)
                if (! MyContextHolder.myContextHolder.isOnRestore()) builder.fixInconsistenciesWithChangedEnvironmentSilently()
                builder.logLoadResult(method)
                return builder
            }

            fun fromMyAccount(ma: MyAccount?): Builder? {
                return Builder(ma)
            }

            val CREATOR: Parcelable.Creator<Builder?>? = object : Parcelable.Creator<Builder?> {
                override fun createFromParcel(source: Parcel?): Builder? {
                    return loadFromAccountData(AccountData.Companion.this.createFromParcel(source), "createFromParcel")
                }

                override fun newArray(size: Int): Array<Builder?>? {
                    return arrayOfNulls<Builder?>(size)
                }
            }
        }
    }

    companion object {
        private val TAG: String = MyAccount::class.java.simpleName
        val EMPTY: MyAccount = MyAccount(AccountName.Companion.getEmpty())
        val KEY_ACCOUNT_NAME: String = "account_name"

        /** Username for the account  */
        val KEY_USERNAME: String = "username"

        /** A name that is unique for an origin  */
        val KEY_UNIQUE_NAME: String = "unique_name"

        /** [ActorTable._ID] in our System.  */
        val KEY_ACTOR_ID: String = "user_id"

        /** [ActorTable.ACTOR_OID] in Microblogging System.  */
        val KEY_ACTOR_OID: String = "user_oid"

        /** Is OAuth on for this MyAccount?  */
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
        fun fromBundle(myContext: MyContext?, bundle: Bundle?): MyAccount {
            return if (bundle == null) EMPTY else myContext.accounts().fromAccountName(bundle.getString(IntentExtra.ACCOUNT_NAME.key))
        }
    }

    init {
        actor = Actor.Companion.load(data.myContext(), data.getDataLong(KEY_ACTOR_ID, 0L), false,
                Supplier<Actor?> {
                    Actor.Companion.fromOid(data.accountName.getOrigin(), data.getDataString(KEY_ACTOR_OID))
                            .withUniqueName(data.accountName.uniqueName)
                            .lookupUser()
                })
        deleted = data.getDataBoolean(KEY_DELETED, false)
        syncFrequencySeconds = data.getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0L)
        isSyncable = data.getDataBoolean(KEY_IS_SYNCABLE, true)
        isSyncedAutomatically = data.getDataBoolean(KEY_IS_SYNCED_AUTOMATICALLY, true)
        setOAuth(TriState.Companion.fromBoolean(data.getDataBoolean(KEY_OAUTH, getOrigin().isOAuthDefault())))
        setConnection()
        getConnection().setPassword(data.getDataString(Connection.Companion.KEY_PASSWORD))
        credentialsVerified = CredentialsVerificationStatus.Companion.load(data)
        order = data.getDataInt(KEY_ORDER, 1)
    }
}