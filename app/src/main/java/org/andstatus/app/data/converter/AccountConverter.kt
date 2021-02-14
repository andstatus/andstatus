/*
 * Copyright (C) 2013-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data.converter

import android.accounts.Account
import android.accounts.AccountManager
import io.vavr.control.Try
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.context.MyContext
import org.andstatus.app.util.MyLog
import org.json.JSONObject
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
object AccountConverter {
    private val TAG: String? = AccountConverter::class.java.simpleName
    fun removeOldAccounts(am: AccountManager?,
                          accountsToRemove: MutableCollection<Account?>?) {
        if (!accountsToRemove.isEmpty()) {
            MyLog.i(TAG, "Removing " + accountsToRemove.size + " old accounts")
            for (account in accountsToRemove) {
                MyLog.i(TAG, "Removing old account: " + account.name)
                am.removeAccount(account, null, null)
            }
        }
    }

    fun convertJson(myContext: MyContext?, jsonIn: JSONObject?, isPersistent: Boolean): Try<JSONObject?>? {
        val version = AccountUtils.getVersion(jsonIn)
        return when (version) {
            AccountUtils.ACCOUNT_VERSION -> Try.success(jsonIn)
            0 -> Try.failure(NoSuchElementException("No version info found in $jsonIn"))
            16 -> Try.success(jsonIn).flatMap { json: JSONObject? -> Convert47.Companion.convertJson16(myContext, json, isPersistent) }
            else -> Try.failure(IllegalArgumentException("Unsuppoerted account version: $version"))
        }
    }
}