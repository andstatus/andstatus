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
package org.andstatus.app.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import io.vavr.control.Try
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.Permissions
import org.andstatus.app.util.Permissions.PermissionType
import org.json.JSONObject
import java.util.*
import java.util.stream.Collectors

object AccountUtils {
    const val ACCOUNT_VERSION = 48
    val KEY_VERSION: String = "myversion" // Storing version of the account data

    /** The Key for the android.accounts.Account bundle  */
    val KEY_ACCOUNT: String = "account"

    fun isVersionCurrent(context: Context, account: Account): Boolean {
        return ACCOUNT_VERSION == getVersion(context, account)
    }

    fun getVersion(context: Context, account: Account): Int {
        return JsonUtils.toJsonObject(AccountManager.get(context).getUserData(account, KEY_ACCOUNT))
                .map(this::getVersion)
                .getOrElse(0)
    }

    fun isVersionCurrent(jso: JSONObject): Boolean {
        return ACCOUNT_VERSION == getVersion(jso)
    }

    fun getVersion(jso: JSONObject?): Int {
        return jso?.optInt(KEY_VERSION) ?: 0
    }

    /** Add this account to the Account Manager Without userdata yet  */
    fun addEmptyAccount(oAccountName: AccountName, password: String?): Try<Account> {
        return addEmptyAccount(AccountManager.get(oAccountName.getContext()), oAccountName.name, password)
    }

    /** Add this account to the Account Manager Without userdata yet  */
    fun addEmptyAccount(am: AccountManager, accountName: String?, password: String?): Try<Account> {
        return Try.of {
            val androidAccount = Account(accountName, AuthenticatorService.ANDROID_ACCOUNT_TYPE)
            if (am.addAccountExplicitly(androidAccount, password, null)) {
                // Without SyncAdapter we got the error:
                // SyncManager(865): can't find a sync adapter for SyncAdapterType Key
                // {name=org.andstatus.app.data.MyProvider, type=org.andstatus.app},
                // removing settings for it
                MyLog.v(AccountUtils::class.java) { "Persisted $accountName" }
                return@of androidAccount
            } else {
                val errorMessage = "Account was not added to AccountManager: $androidAccount"
                MyLog.e(AccountUtils::class.java, errorMessage)
                throw Exception(errorMessage)
            }
        }
    }

    fun getExistingAndroidAccount(oAccountName: AccountName): Try<Account> {
        for (account in getCurrentAccounts(oAccountName.getContext())) {
            if (oAccountName.name == account.name) {
                return Try.success(account)
            }
        }
        return Try.failure(NoSuchElementException(oAccountName.name))
    }

    private fun getSyncFrequencySeconds(account: Account?): Long {
        var syncFrequencySeconds: Long = 0
        val syncs = ContentResolver.getPeriodicSyncs(account, MatchedUri.AUTHORITY)
        if (!syncs.isEmpty()) {
            syncFrequencySeconds = syncs[0].period
        }
        return syncFrequencySeconds
    }

    fun setSyncFrequencySeconds(androidAccount: Account?, syncFrequencySeconds: Long) {
        // See
        // http://developer.android.com/reference/android/content/ContentResolver.html#addPeriodicSync(android.accounts.Account, java.lang.String, android.os.Bundle, long)
        // and
        // http://stackoverflow.com/questions/11090604/android-syncadapter-automatically-initialize-syncing
        if (syncFrequencySeconds != getSyncFrequencySeconds(androidAccount)) {
            ContentResolver.removePeriodicSync(androidAccount, MatchedUri.AUTHORITY, Bundle.EMPTY)
            if (syncFrequencySeconds > 0) {
                ContentResolver.addPeriodicSync(androidAccount, MatchedUri.AUTHORITY, Bundle.EMPTY, syncFrequencySeconds)
            }
        }
    }

    fun getCurrentAccounts(context: Context): MutableList<Account> {
        return getAllAccounts(context).stream().filter { a: Account -> isVersionCurrent(context, a) }.collect(Collectors.toList())
    }

    fun getAllAccounts(context: Context): List<Account> {
        if (Permissions.checkPermission(context, PermissionType.GET_ACCOUNTS)) {
            val am = AccountManager.get(context)
            return Arrays.stream(am.getAccountsByType(AuthenticatorService.ANDROID_ACCOUNT_TYPE))
                    .collect(Collectors.toList())
        }
        return emptyList()
    }
}