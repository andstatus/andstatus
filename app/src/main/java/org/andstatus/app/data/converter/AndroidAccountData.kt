/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.account.AccountDataReader
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil

internal class AndroidAccountData(private val am: AccountManager?, private val androidAccount: Account?) : AccountDataReader {
    override fun getDataInt(key: String?, defValue: Int): Int {
        var value = defValue
        try {
            val str = getDataString(key, "null")
            if (str.compareTo("null") != 0) {
                value = str.toInt()
            }
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
        return value
    }

    fun getDataLong(key: String?, defValue: Long): Long {
        var value = defValue
        try {
            val str = getDataString(key, "null")
            if (str.compareTo("null") != 0) {
                value = str.toLong()
            }
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
        return value
    }

    fun getDataBoolean(key: String?, defValue: Boolean): Boolean {
        var value = defValue
        try {
            val str = getDataString(key, "null")
            if (str.compareTo("null") != 0) {
                value = SharedPreferencesUtil.isTrue(str)
            }
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
        return value
    }

    override fun dataContains(key: String?): Boolean {
        var contains = false
        try {
            val str = getDataString(key, "null")
            if (str.compareTo("null") != 0) {
                contains = true
            }
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
        return contains
    }

    /**
     * Actor's and User Data associated with the account
     */
    override fun getDataString(key: String?, defValue: String?): String? {
        var value = defValue
        val str = am.getUserData(androidAccount, key)
        if (!str.isNullOrEmpty()) {
            value = str
        }
        return value
    }

    fun moveStringKeyTo(key: String?, accountData: AccountDataWriter?) {
        accountData.setDataString(key, getDataString(key, null))
        am.setUserData(androidAccount, key, null)
    }

    fun moveLongKeyTo(key: String?, accountData: AccountDataWriter?) {
        accountData.setDataLong(key, getDataLong(key, 0L))
        am.setUserData(androidAccount, key, null)
    }
}