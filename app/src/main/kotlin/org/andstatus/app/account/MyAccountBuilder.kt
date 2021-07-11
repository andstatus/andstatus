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
import io.vavr.control.Try
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Connection
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginConfig
import org.andstatus.app.timeline.meta.TimelineSaver
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TaggedClass
import org.andstatus.app.util.TaggedInstance
import org.andstatus.app.util.TriState

/** Companion class used to load/create/change/delete [MyAccount]'s data  */
class MyAccountBuilder private constructor(
    myAccountIn: MyAccount,
    taggedInstance: TaggedInstance = TaggedInstance(MyAccountBuilder::class)
) : TaggedClass by taggedInstance {
    var myAccount = myAccountIn
        private set

    private fun fixInconsistenciesWithChangedEnvironmentSilently() {
        var changed = false
        if (isPersistent() && myAccount.actor.actorId == 0L) {
            changed = true
            assignActorId()
            MyLog.i(
                this, "MyAccount '" + myAccount.getAccountName()
                        + "' was not connected to the Actor table. actorId=" + myAccount.actor.actorId
            )
        }
        if (!myAccount.getCredentialsPresent()
                && myAccount.credentialsVerified == CredentialsVerificationStatus.SUCCEEDED
        ) {
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
            MyLog.i(this, "$method Load failed: Invalid account; $this \n${MyLog.currentStackTrace}")
        }
    }

    fun setOrigin(origin: Origin) {
        rebuildMyAccount(origin, getUniqueName())
    }

    fun setUniqueName(uniqueName: String) {
        rebuildMyAccount(myAccount.origin, uniqueName)
    }

    fun setOAuth(isOauthBoolean: Boolean): MyAccountBuilder {
        val isOauth = if (isOauthBoolean == myAccount.origin.isOAuthDefault()) TriState.UNKNOWN
            else TriState.fromBoolean(isOauthBoolean)
        myAccount.setOAuth(isOauth)
        return this
    }

    fun rebuildMyAccount(myContext: MyContext) {
        rebuildMyAccount(myContext.origins.fromName(myAccount.origin.name), getUniqueName())
    }

    private fun rebuildMyAccount(origin: Origin, uniqueName: String) {
        rebuildMyAccount(AccountName.fromOriginAndUniqueName(origin, uniqueName))
    }

    fun rebuildMyAccount(accountName: AccountName) {
        val ma = myAccount.myContext.accounts.fromAccountName(accountName.name)
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
        myAccount.data.setDataBoolean(MyAccount.KEY_DELETED, true)
    }

    fun setSyncedAutomatically(syncedAutomatically: Boolean) {
        myAccount.isSyncedAutomatically = syncedAutomatically
    }

    fun setOrder(order: Int) {
        myAccount.order = order
    }

    fun save() {
        if (saveSilently().getOrElse(false) && myAccount.myContext.isReady) {
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
                        myAccount.myContext.accounts.addIfAbsent(myAccount)
                        if (myAccount.myContext.isReady && !myAccount.hasAnyTimelines()) {
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

    fun getOriginConfig(): Try<MyAccountBuilder> {
        return getConnection().getConfig().map { config: OriginConfig ->
            if (config.nonEmpty) {
                val originBuilder = Origin.Builder(myAccount.origin)
                originBuilder.save(config)
                MyLog.v(this, "Get Origin config succeeded $config")
            }
            this
        }
    }

    fun onCredentialsVerified(actor: Actor): Try<MyAccountBuilder> {
        var ok = actor.nonEmpty && !actor.oid.isEmpty() && actor.isUsernameValid()
        val errorSettingUsername = !ok
        var credentialsOfOtherAccount = false
        // We are comparing usernames ignoring case, but we fix correct case
        // as the Originating system tells us.
        if (ok && myAccount.username.isNotEmpty()
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
                myAccount.actor.actorId = myAccount.data.getDataLong(MyAccount.KEY_ACTOR_ID, myAccount.actor.actorId)
            } else {
                DataUpdater(myAccount).onActivity(actor.update(actor))
            }
            if (!isPersistent()) {
                // Now we know the name (or proper case of the name) of this Account!
                val sameName = myAccount.data.accountName.getUniqueName() == actor.uniqueName
                if (!sameName) {
                    MyLog.i(
                        this, "name changed from " + myAccount.data.accountName.getUniqueName() +
                                " to " + actor.uniqueName
                    )
                    myAccount.data.updateFrom(myAccount)
                    val newData = myAccount.data.withAccountName(
                        AccountName.fromOriginAndUniqueName(myAccount.origin, actor.uniqueName)
                    )
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
            MyLog.w(
                this, myAccount.myContext.context.getText(R.string.error_credentials_of_other_user).toString() + ": " +
                        actor.getUniqueNameWithOrigin() +
                        " account name: " + myAccount.getAccountName() +
                        " vs username: " + actor.getUsername()
            )
            return Try.failure(
                ConnectionException(
                    ConnectionException.StatusCode.CREDENTIALS_OF_OTHER_ACCOUNT,
                    actor.getUniqueNameWithOrigin()
                )
            )
        }
        if (errorSettingUsername) {
            val msg = myAccount.myContext.context.getText(R.string.error_set_username).toString() + " " + actor.getUsername()
            MyLog.w(this, msg)
            return Try.failure(ConnectionException(ConnectionException.StatusCode.AUTHENTICATION_ERROR, msg))
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

    fun toJsonString(): String = myAccount.toJson().toString()

    companion object {
        val EMPTY = MyAccountBuilder(MyAccount.EMPTY)

        /**
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         */
        fun fromAccountName(accountName: AccountName): MyAccountBuilder {
            return fromMyAccount(myAccountFromName(accountName))
        }

        /**
         * If MyAccount with this name didn't exist yet, new temporary MyAccount will be created.
         */
        private fun myAccountFromName(accountName: AccountName): MyAccount {
            if (accountName.myContext.isEmpty) return MyAccount.EMPTY
            val persistentAccount = accountName.myContext.accounts.fromAccountName(accountName)
            return if (persistentAccount.isValid) persistentAccount else MyAccount(accountName)
        }

        /** Loads existing account from Persistence  */
        fun loadFromAndroidAccount(myContext: MyContext, account: Account): MyAccountBuilder {
            return loadFromAccountData(AccountData.fromAndroidAccount(myContext, account), "fromAndroidAccount")
        }

        fun fromJsonString(myContext: MyContext, jsonString: String?): MyAccountBuilder =
                JsonUtils.toJsonObject(jsonString)
                        .map { jso ->
                            if (myContext.isEmpty) EMPTY
                            else AccountData.fromJson(myContext, jso, false)
                                .let { loadFromAccountData(it, "") }
                        }.getOrElse(EMPTY)


        fun loadFromAccountData(accountData: AccountData, method: String?): MyAccountBuilder {
            val myAccount = MyAccount(accountData)
            val builder = fromMyAccount(myAccount)
            if (! MyContextHolder.myContextHolder.isOnRestore()) builder.fixInconsistenciesWithChangedEnvironmentSilently()
            builder.logLoadResult(method)
            return builder
        }

        fun fromMyAccount(ma: MyAccount): MyAccountBuilder {
            return MyAccountBuilder(ma)
        }
    }
}
