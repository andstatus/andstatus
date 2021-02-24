/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.account.AccountData
import org.andstatus.app.account.AccountName
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Connection
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

internal class Convert47 : ConvertOneStep() {
    override fun execute2() {
        convertAccounts()
    }

    private fun convertAccounts() {
        val method = "convert47"
        val versionFrom = 16
        val accountsConverted = AtomicInteger()
        progressLogger.logProgress("$stepTitle: Converting accounts")
        val myContext: MyContext =  MyContextHolder.myContextHolder.getNow()
        myContext.origins().initialize(db)
        val am = AccountManager.get(myContext.context())
        val accountsToRemove: MutableCollection<Account?> = ArrayList()
        for (accountIn in AccountUtils.getAllAccounts(myContext.context())) {
            DatabaseConverterController.Companion.stillUpgrading()
            val jsonIn = JsonUtils.toJsonObject(am.getUserData(accountIn, AccountUtils.KEY_ACCOUNT))
            val versionIn = jsonIn.map(CheckedFunction<JSONObject?, Int?> { obj: JSONObject? -> AccountUtils.getVersion() }).getOrElse(0)
            if (versionIn >= versionTo) {
                MyLog.i(TAG, "Account " + accountIn.name + " is already converted?!, skipping")
            } else if (versionIn == versionFrom &&
                    !jsonIn.map { jso: JSONObject? -> jso.optBoolean(MyAccount.Companion.KEY_DELETED) }.getOrElse(false)) {
                MyLog.v(TAG, "Upgrading account " + accountIn.name)
                jsonIn.flatMap { jso: JSONObject? -> convertJson16(myContext, jso, true) }
                        .flatMap { jsonOut: JSONObject? ->
                            val accountData: AccountData = AccountData.Companion.fromJson(myContext, jsonOut, false)
                            MyLog.v(TAG, method + "; " + accountData.toJsonString())
                            val accountName = JsonUtils.optString(jsonOut, MyAccount.Companion.KEY_ACCOUNT_NAME)
                            (if (accountName == accountIn.name) Try.success(accountIn) else AccountUtils.addEmptyAccount(am, accountName, JsonUtils.optString(jsonOut, Connection.Companion.KEY_PASSWORD)))
                                    .flatMap { accountOut: Account? ->
                                        accountData.saveIfChanged(accountOut)
                                                .map { b: Boolean? -> accountOut }
                                    }
                        }
                        .onSuccess { accountNew: Account? ->
                            accountsConverted.incrementAndGet()
                            if (accountNew.name.equals(accountIn.name, ignoreCase = true)) {
                                progressLogger.logProgress(stepTitle + ": Converted account " +
                                        accountNew.name + " with the same name")
                            } else {
                                progressLogger.logProgress(stepTitle + ": Converted account " +
                                        accountIn.name + " to " + accountNew.name + ", deleting the old one")
                                accountsToRemove.add(accountIn)
                            }
                        }
                        .onFailure { e: Throwable? ->
                            progressLogger.logProgress(stepTitle + ": Failed to convert account " +
                                    accountIn.name + ", deleting")
                            accountsToRemove.add(accountIn)
                        }
            } else {
                MyLog.e(TAG, "Account " + accountIn.name +
                        " version " + versionIn + " less than " + versionFrom + " is unsupported, deleting")
                accountsToRemove.add(accountIn)
            }
        }
        AccountConverter.removeOldAccounts(am, accountsToRemove)
        if (accountsConverted.toInt() > 0) {
            progressLogger.logProgress(stepTitle + ": Successfully upgraded " + accountsConverted.toInt() + " accounts")
        } else {
            progressLogger.logProgress("$stepTitle: No accounts upgraded from $oldVersion to version $versionTo")
        }
    }

    companion object {
        private val TAG: String? = Convert47::class.java.simpleName
        fun convertJson16(myContext: MyContext?, jsonIn: JSONObject?, isPersistent: Boolean): Try<JSONObject?>? {
            val versionTo = 48
            val originName = JsonUtils.optString(jsonIn, Origin.Companion.KEY_ORIGIN_NAME)
            val origin = myContext.origins().fromName(originName)
            if (origin.isEmpty) return Try.failure(NoSuchElementException("Origin wan't found for $jsonIn"))
            val oldName = JsonUtils.optString(jsonIn, MyAccount.Companion.KEY_USERNAME).trim { it <= ' ' }
            var newUserName = oldName
            var newUniqueName = ""
            var host = ""
            if (oldName.contains("@")) {
                host = AccountName.Companion.accountNameToHost(oldName)
                newUniqueName = oldName
                newUserName = Actor.Companion.uniqueNameToUsername(origin, oldName).orElse("")
            } else {
                host = origin.accountNameHost
                newUniqueName = "$oldName@$host"
                newUserName = oldName
            }
            val accountName = newUniqueName + "/" + origin.getOriginInAccountName(host)
            val jso: JSONObject
            try {
                jso = JSONObject(jsonIn.toString())
                jso.put(MyAccount.Companion.KEY_ACCOUNT_NAME, accountName)
                jso.put(MyAccount.Companion.KEY_USERNAME, newUserName)
                jso.put(MyAccount.Companion.KEY_UNIQUE_NAME, newUniqueName)
                jso.put(AccountUtils.KEY_VERSION, versionTo)
            } catch (e: JSONException) {
                return Try.failure(Exception("Failed to convert $jsonIn", e))
            }
            return Try.success(jso)
        }
    }

    init {
        versionTo = 48
    }
}