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
package org.andstatus.app.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextEmpty
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.MatchedUri
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil
import org.json.JSONObject

class AccountData : Parcelable, AccountDataWriter, IdentifiableInstance {
    override val instanceId = InstanceId.next()
    private val myContext: MyContext
    val accountName: AccountName

    @Volatile
    private var data: JSONObject

    @Volatile
    private var persistent = false

    private constructor(myContext: MyContext, jso: JSONObject, persistent: Boolean) {
        this.myContext = myContext
        data = jso
        this.persistent = persistent
        val origin = if (myContext.isEmpty) Origin.EMPTY else myContext.origins().fromName(getDataString(Origin.KEY_ORIGIN_NAME))
        accountName = AccountName.fromOriginAndUniqueName(origin, getDataString(MyAccount.KEY_UNIQUE_NAME))
        logMe("new " + accountName.name + " from jso")
    }

    private constructor(accountName: AccountName, jso: JSONObject) {
        myContext = accountName.myContext()
        this.accountName = accountName
        data = jso
        updateFromAccountName()
        logMe("new from " + accountName.name + " and jso")
    }

    fun withAccountName(accountName: AccountName): AccountData {
        return AccountData(accountName, data)
    }

    fun updateFrom(myAccount: MyAccount): AccountData {
        setDataString(MyAccount.KEY_ACTOR_OID, myAccount.actor.oid)
        myAccount.getCredentialsVerified().put(this)
        setDataBoolean(MyAccount.KEY_OAUTH, myAccount.isOAuth())
        setDataLong(MyAccount.KEY_ACTOR_ID, myAccount.actor.actorId)
        myAccount.connection.saveTo(this)
        setPersistent(true)
        setDataBoolean(MyAccount.KEY_IS_SYNCABLE, myAccount.isSyncable)
        setDataBoolean(MyAccount.KEY_IS_SYNCED_AUTOMATICALLY, myAccount.isSyncedAutomatically())
        setDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, myAccount.getSyncFrequencySeconds())
        // We don't create accounts of other versions
        setDataInt(AccountUtils.KEY_VERSION, AccountUtils.ACCOUNT_VERSION)
        setDataInt(MyAccount.KEY_ORDER, myAccount.getOrder())
        logMe("updated from $myAccount")
        return this
    }

    private fun updateFromAccountName() {
        setDataString(AccountUtils.KEY_ACCOUNT, accountName.name)
        setDataString(MyAccount.KEY_USERNAME, accountName.username)
        setDataString(MyAccount.KEY_UNIQUE_NAME, accountName.getUniqueName())
        setDataString(Origin.KEY_ORIGIN_NAME, accountName.getOriginName())
    }

    fun myContext(): MyContext {
        return myContext
    }

    fun isPersistent(): Boolean {
        return persistent
    }

    fun setPersistent(persistent: Boolean) {
        this.persistent = persistent
    }

    /** @return changed (and successfully saved) or not
     */
    fun saveIfChanged(androidAccount: Account): Try<Boolean> {
        val oldData = fromAndroidAccount(myContext, androidAccount)
        if (this == oldData) return Try.success(false)
        var syncFrequencySeconds = getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0)
        if (syncFrequencySeconds <= 0) {
            syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds()
        }
        AccountUtils.setSyncFrequencySeconds(androidAccount, syncFrequencySeconds)
        val isSyncable = getDataBoolean(MyAccount.KEY_IS_SYNCABLE, true)
        if (isSyncable != ContentResolver.getIsSyncable(androidAccount, MatchedUri.AUTHORITY) > 0) {
            ContentResolver.setIsSyncable(androidAccount, MatchedUri.AUTHORITY, if (isSyncable) 1 else 0)
        }
        val syncAutomatically = getDataBoolean(MyAccount.KEY_IS_SYNCED_AUTOMATICALLY, true)
        if (syncAutomatically != ContentResolver.getSyncAutomatically(androidAccount, MatchedUri.AUTHORITY)) {
            // We need to preserve sync on/off during backup/restore.
            // don't know about "network tickles"... See:
            // http://stackoverflow.com/questions/5013254/what-is-a-network-tickle-and-how-to-i-go-about-sending-one
            ContentResolver.setSyncAutomatically(androidAccount, MatchedUri.AUTHORITY, syncAutomatically)
        }
        val am = AccountManager.get(myContext.context())
        val jsonString = toJsonString()
        logMe("Saving to " + androidAccount.name)
        am.setUserData(androidAccount, AccountUtils.KEY_ACCOUNT, jsonString)
        return Try.success(true)
    }

    fun isVersionCurrent(): Boolean {
        return AccountUtils.ACCOUNT_VERSION == getVersion()
    }

    fun getVersion(): Int {
        return getDataInt(AccountUtils.KEY_VERSION, 0)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is AccountData) return false
        return isPersistent() == other.isPersistent() && toJsonString() == other.toJsonString()
    }

    override fun hashCode(): Int {
        var text: String? = java.lang.Boolean.toString(isPersistent())
        text += toJsonString()
        return text.hashCode()
    }

    override fun dataContains(key: String): Boolean {
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

    fun getDataBoolean(key: String, defValue: Boolean): Boolean {
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

    override fun getDataString(key: String, defValue: String): String {
        return JsonUtils.optString(data, key, defValue)
    }

    override fun getDataInt(key: String, defValue: Int): Int {
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

    fun getDataLong(key: String, defValue: Long): Long {
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

    fun setDataBoolean(key: String, value: Boolean) {
        try {
            setDataString(key, java.lang.Boolean.toString(value))
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
    }

    override fun setDataLong(key: String, value: Long) {
        try {
            setDataString(key, java.lang.Long.toString(value))
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
    }

    override fun setDataInt(key: String, value: Int) {
        try {
            setDataString(key, Integer.toString(value))
        } catch (e: Exception) {
            MyLog.v(this, e)
        }
    }

    override fun setDataString(key: String, value: String?) {
        data = if (value.isNullOrEmpty()) {
            JsonUtils.remove(data, key)
        } else {
            JsonUtils.put(data, key, value)
        }
    }

    private fun logMe(msg: String?): AccountData {
        MyLog.v(this) { "$msg: ${toJsonString()}" }
        return this
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(toJsonString())
    }

    fun toJSon(): JSONObject {
        return data
    }

    fun toJsonString(): String {
        return JsonUtils.toString(data, 2)
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = AccountData::class.java.simpleName
        val EMPTY: AccountData = AccountData(MyContextEmpty.EMPTY, JSONObject(), false)
        fun fromAndroidAccount(myContext: MyContext, androidAccount: Account?): AccountData {
            requireNotNull(androidAccount) { "$TAG account is null" }
            val am = AccountManager.get(myContext.context())
            val jsonString: String = am.getUserData(androidAccount, AccountUtils.KEY_ACCOUNT) ?: ""
            val accountData = fromJsonString(myContext, jsonString, true)
            accountData.setDataBoolean(MyAccount.KEY_IS_SYNCABLE,
                    ContentResolver.getIsSyncable(androidAccount, MatchedUri.AUTHORITY) != 0)
            accountData.setDataBoolean(MyAccount.KEY_IS_SYNCED_AUTOMATICALLY,
                    ContentResolver.getSyncAutomatically(androidAccount, MatchedUri.AUTHORITY))
            accountData.logMe("Loaded from account " + androidAccount.name)
            return accountData
        }

        private fun fromJsonString(myContext: MyContext, userData: String, persistent: Boolean): AccountData {
            return JsonUtils.toJsonObject(userData).map { jso: JSONObject -> fromJson(myContext, jso, persistent) }
                    .getOrElse(EMPTY)
        }

        fun fromJson(myContext: MyContext, jso: JSONObject, persistent: Boolean): AccountData {
            return AccountData(myContext, jso, persistent)
        }

        fun fromAccountName(accountName: AccountName): AccountData {
            return AccountData(accountName, JSONObject())
        }

        @JvmField
        val CREATOR: Parcelable.Creator<AccountData> = object : Parcelable.Creator<AccountData> {
            override fun createFromParcel(source: Parcel): AccountData {
                return fromBundle( MyContextHolder.myContextHolder.getNow(), source.readBundle())
            }

            override fun newArray(size: Int): Array<AccountData?> {
                return arrayOfNulls<AccountData>(size)
            }
        }

        fun fromBundle(myContext: MyContext, bundle: Bundle?): AccountData {
            var jsonString = ""
            if (bundle != null) {
                jsonString = bundle.getString(AccountUtils.KEY_ACCOUNT) ?: ""
            }
            return fromJsonString(myContext, jsonString, false).logMe("Loaded from bundle")
        }
    }
}