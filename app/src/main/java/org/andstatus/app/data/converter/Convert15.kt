/*
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.account.AccountData
import org.andstatus.app.account.AccountUtils
import org.andstatus.app.account.CredentialsVerificationStatus
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import org.json.JSONObject
import java.util.*

internal class Convert15 : ConvertOneStep() {
    override fun execute2() {
        versionTo = 16
        val ok = convertAccounts(db, oldVersion) == versionTo
        if (ok) {
            sql = "DELETE FROM Origin WHERE _ID IN(6, 7)"
            DbUtils.execSQL(db, sql)
        }
    }

    companion object {
        private val TAG: String? = Convert15::class.java.simpleName
        fun convertAccounts(db: SQLiteDatabase?, oldVersion: Int): Int {
            val method = "convert14to16"
            val versionTo = 16
            var ok = false
            try {
                MyLog.i(TAG, "Accounts upgrading step from version $oldVersion to version $versionTo")
                val myContext: MyContext = MyContextHolder.Companion.myContextHolder.getNow()
                myContext.origins().initialize(db)
                val am = AccountManager.get(myContext.context())
                val accountsToRemove: MutableCollection<Account?> = ArrayList()
                for (androidAccount in AccountUtils.getAllAccounts(myContext.context())) {
                    DatabaseConverterController.Companion.stillUpgrading()
                    val androidAccountData = AndroidAccountData(am, androidAccount)
                    val versionOldBefore16 = androidAccountData.getDataInt(AccountUtils.KEY_VERSION, 0)
                    val accountDataOld: AccountData = AccountData.Companion.fromAndroidAccount(myContext, androidAccount)
                    val versionOld2 = accountDataOld.getDataInt(AccountUtils.KEY_VERSION, 0)
                    if (versionOld2 == versionTo) {
                        MyLog.i(TAG, "Account " + androidAccount.name + " is already converted?!, skipping")
                    } else if (versionOldBefore16 == 14 &&
                            !androidAccountData.getDataBoolean(MyAccount.Companion.KEY_DELETED, false)) {
                        MyLog.v(TAG, "Upgrading account " + androidAccount.name)
                        am.setUserData(androidAccount, AccountUtils.KEY_VERSION, null)
                        val accountData: AccountData = AccountData.Companion.fromJson(myContext, JSONObject(), false)
                        androidAccountData.moveStringKeyTo(MyAccount.Companion.KEY_USERNAME, accountData)
                        androidAccountData.moveStringKeyTo(Origin.Companion.KEY_ORIGIN_NAME, accountData)
                        androidAccountData.moveStringKeyTo(MyAccount.Companion.KEY_ACTOR_OID, accountData)
                        androidAccountData.moveLongKeyTo(MyAccount.Companion.KEY_ACTOR_ID, accountData)
                        val origin = myContext.origins().fromName(
                                accountData.getDataString(Origin.Companion.KEY_ORIGIN_NAME))
                        val isOauth = androidAccountData.getDataBoolean(MyAccount.Companion.KEY_OAUTH, origin.isOAuthDefault)
                        accountData.setDataBoolean(MyAccount.Companion.KEY_OAUTH, isOauth)
                        am.setUserData(androidAccount, MyAccount.Companion.KEY_OAUTH, null)
                        if (isOauth) {
                            androidAccountData.moveStringKeyTo("user_token", accountData)
                            androidAccountData.moveStringKeyTo("user_secret", accountData)
                        } else {
                            androidAccountData.moveStringKeyTo("password", accountData)
                        }
                        CredentialsVerificationStatus.Companion.load(androidAccountData).put(accountData)
                        am.setUserData(androidAccount, CredentialsVerificationStatus.Companion.KEY, null)
                        MyLog.v(TAG, method + "; " + accountData.toJsonString())
                        androidAccountData.moveLongKeyTo(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, accountData)
                        accountData.saveIfChanged(androidAccount).onFailure { e: Throwable? ->
                            MyLog.e(TAG, "Failed to convert account " + androidAccount.name + ", deleting")
                            accountsToRemove.add(androidAccount)
                        }
                    } else {
                        MyLog.e(TAG, "Account " + androidAccount.name +
                                " version less than 14 is not supported (" + versionOldBefore16 + "), deleting")
                        accountsToRemove.add(androidAccount)
                    }
                }
                AccountConverter.removeOldAccounts(am, accountsToRemove)
                ok = true
            } catch (e: Exception) {
                MyLog.e(TAG, e)
            }
            if (ok) {
                MyLog.i(TAG, "Accounts upgrading step successfully upgraded accounts from " + oldVersion +
                        " to version " + versionTo)
            } else {
                MyLog.e(TAG, "Error upgrading accounts from $oldVersion to version $versionTo")
            }
            return if (ok) versionTo else oldVersion
        }
    }
}