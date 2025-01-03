/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog

/**
 * Based on a very basic authenticator service for POP/IMAP...
 */
class AuthenticatorService : Service() {
    internal inner class Authenticator(private val context: Context) : AbstractAccountAuthenticator(context),
        Identifiable {
        override val instanceId = InstanceId.next()

        /**
         * We add account launching [AccountSettingsActivity] activity
         */
        override fun addAccount(
            response: AccountAuthenticatorResponse?, accountType: String,
            authTokenType: String?, requiredFeatures: Array<String?>?, options: Bundle?
        ): Bundle {
            val username: String? = options?.getString(OPTIONS_USERNAME)
            // There are two cases here:
            // 1) We are called with a username/password; this comes from the traditional email
            //    app UI; we simply create the account and return the proper bundle
            return if (options != null && options.containsKey(OPTIONS_PASSWORD) && !username.isNullOrEmpty()) {
                val account = Account(username, ANDROID_ACCOUNT_TYPE)
                AccountManager.get(this@AuthenticatorService).addAccountExplicitly(
                    account, options.getString(OPTIONS_PASSWORD), null
                )
                val b = Bundle()
                b.putString(AccountManager.KEY_ACCOUNT_NAME, options.getString(OPTIONS_USERNAME))
                b.putString(AccountManager.KEY_ACCOUNT_TYPE, ANDROID_ACCOUNT_TYPE)
                b
                // 2) The other case is that we're creating a new account from an Account manager
                //    activity.  In this case, we add an intent that will be used to gather the
                //    account information...
            } else {
                val b = Bundle()
                val intent = Intent(this@AuthenticatorService, AccountSettingsActivity::class.java)
                //  This is how we define what to do in {@link AccountSettingsActivity} activity
                intent.action = Intent.ACTION_INSERT
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
                b.putParcelable(AccountManager.KEY_INTENT, intent)
                b
            }
        }

        override fun confirmCredentials(
            response: AccountAuthenticatorResponse?, account: Account?,
            options: Bundle?
        ): Bundle? {
            return null
        }

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?): Bundle? {
            return null
        }

        override fun getAuthToken(
            response: AccountAuthenticatorResponse?, account: Account?,
            authTokenType: String?, loginOptions: Bundle?
        ): Bundle? {
            return null
        }

        override fun getAuthTokenLabel(authTokenType: String?): String? {
            // null means we don't have compartmentalized authtoken types
            return null
        }

        override fun hasFeatures(
            response: AccountAuthenticatorResponse?,
            account: Account?,
            features: Array<String?>?
        ): Bundle? {
            return null
        }

        override fun updateCredentials(
            response: AccountAuthenticatorResponse?, account: Account?,
            authTokenType: String?, loginOptions: Bundle?
        ): Bundle? {
            return null
        }

        override fun getAccountRemovalAllowed(response: AccountAuthenticatorResponse?, account: Account): Bundle {
            var deleted = true
            if (AccountUtils.isVersionCurrent(context, account)) {
                deleted = Try.of { myContextHolder.getNow() }
                    .map { myContext: MyContext ->
                        myContext.accounts
                            .fromAccountName(account.name)
                    }
                    .filter { obj: MyAccount -> obj.isValid }
                    .map { ma: MyAccount ->
                        MyLog.i(this, "Removing $ma")
                        ma.origin.myContext.timelines.onAccountDelete(ma)
                        MyPreferences.onPreferencesChanged()
                        true
                    }
                    .getOrElse(false)
            }
            val result = Bundle()
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, deleted)
            return result
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return if (AccountManager.ACTION_AUTHENTICATOR_INTENT == intent.action) {
            Authenticator(this).iBinder
        } else {
            null
        }
    }

    override fun onCreate() {
        super.onCreate()
        MyLog.v(this, "onCreate")
        myContextHolder.initialize(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        MyLog.v(this, "onDestroy")
    }

    companion object {
        val OPTIONS_USERNAME: String = "username"
        val OPTIONS_PASSWORD: String = "password"
        val ANDROID_ACCOUNT_TYPE: String = "org.andstatus.app"
    }
}
