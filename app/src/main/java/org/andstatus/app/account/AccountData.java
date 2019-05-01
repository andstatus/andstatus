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

package org.andstatus.app.account;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import io.vavr.control.Try;

import static org.andstatus.app.account.AccountUtils.KEY_VERSION;
import static org.andstatus.app.account.MyAccount.KEY_ACTOR_ID;
import static org.andstatus.app.account.MyAccount.KEY_ACTOR_OID;
import static org.andstatus.app.account.MyAccount.KEY_OAUTH;
import static org.andstatus.app.account.MyAccount.KEY_ORDER;
import static org.andstatus.app.account.MyAccount.KEY_UNIQUE_NAME;
import static org.andstatus.app.account.MyAccount.KEY_USERNAME;

public class AccountData implements Parcelable, AccountDataWriter, IdentifiableInstance {
    private static final String TAG = AccountData.class.getSimpleName();
    public static final AccountData EMPTY = new AccountData(MyContext.EMPTY, new JSONObject(), false);

    protected final long instanceId = InstanceId.next();
    private final MyContext myContext;
    public final AccountName accountName;
    private volatile JSONObject data;
    private volatile boolean persistent = false;

    @NonNull
    public static AccountData fromAndroidAccount(MyContext myContext, Account androidAccount) {
        if (androidAccount == null) {
            throw new IllegalArgumentException(TAG + " account is null");
        }
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        String jsonString = am.getUserData(androidAccount, AccountUtils.KEY_ACCOUNT);
        AccountData accountData = fromJsonString(myContext, jsonString, true);
        accountData.setDataBoolean(MyAccount.KEY_IS_SYNCABLE,
                ContentResolver.getIsSyncable(androidAccount, MatchedUri.AUTHORITY) != 0);
        accountData.setDataBoolean(MyAccount.KEY_IS_SYNCED_AUTOMATICALLY,
                ContentResolver.getSyncAutomatically(androidAccount, MatchedUri.AUTHORITY));
        accountData.logMe("Loaded from account " + androidAccount.name);
        return accountData;
    }

    private static AccountData fromJsonString(MyContext myContext, String userData, boolean persistent) {
        return JsonUtils.toJsonObject(userData).map(jso -> fromJson(myContext, jso, persistent)).getOrElse(EMPTY);
    }

    public static AccountData fromJson(MyContext myContext,@NonNull JSONObject jso, boolean persistent) {
        return new AccountData(myContext, jso, persistent);
    }
    
    private AccountData(MyContext myContext, @NonNull JSONObject jso, boolean persistent) {
        this.myContext = myContext;
        data = jso;
        this.persistent = persistent;
        Origin origin = myContext.origins().fromName(getDataString(Origin.KEY_ORIGIN_NAME));
        accountName = AccountName.fromOriginAndUniqueName(origin, getDataString(KEY_UNIQUE_NAME));
        logMe("new " + accountName.getName() + " from jso");
    }

    public static AccountData fromAccountName(@NonNull AccountName accountName) {
        return new AccountData(accountName, new JSONObject());
    }

    private AccountData(@NonNull AccountName accountName, JSONObject jso) {
        this.myContext = accountName.myContext();
        this.accountName = accountName;
        data = jso;
        updateFromAccountName();
        logMe("new from " + accountName.getName() + " and jso");
    }

    public AccountData withAccountName(@NonNull AccountName accountName) {
        return new AccountData(accountName, data);
    }

    AccountData updateFrom(MyAccount myAccount) {
        setDataString(KEY_ACTOR_OID, myAccount.getActor().oid);
        myAccount.getCredentialsVerified().put(this);
        setDataBoolean(KEY_OAUTH, myAccount.isOAuth());
        setDataLong(KEY_ACTOR_ID, myAccount.getActor().actorId);
        if (myAccount.getConnection() != null) {
            myAccount.getConnection().saveTo(this);
        }
        setPersistent(true);
        setDataBoolean(MyAccount.KEY_IS_SYNCABLE, myAccount.isSyncable);
        setDataBoolean(MyAccount.KEY_IS_SYNCED_AUTOMATICALLY, myAccount.isSyncedAutomatically());
        setDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, myAccount.getSyncFrequencySeconds());
        // We don't create accounts of other versions
        setDataInt(KEY_VERSION, AccountUtils.ACCOUNT_VERSION);
        setDataInt(KEY_ORDER, myAccount.getOrder());
        logMe("updated from " + myAccount);
        return this;
    }

    private void updateFromAccountName() {
        setDataString(AccountUtils.KEY_ACCOUNT, accountName.getName());
        setDataString(KEY_USERNAME, accountName.username);
        setDataString(KEY_UNIQUE_NAME, accountName.getUniqueName());
        setDataString(Origin.KEY_ORIGIN_NAME, accountName.getOriginName());
    }

    public MyContext myContext() {
        return myContext.isReady() ? myContext : MyContextHolder.get();
    }

    boolean isPersistent() {
        return persistent;
    }

    void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /** @return changed (and successfully saved) or not */
    public Try<Boolean> saveIfChanged(Account androidAccount) {
        AccountData oldData = fromAndroidAccount(myContext, androidAccount);
        if (this.equals(oldData)) return Try.success(false);

        long syncFrequencySeconds = getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0);
        if (syncFrequencySeconds <= 0) {
            syncFrequencySeconds = MyPreferences.getSyncFrequencySeconds();
        }
        AccountUtils.setSyncFrequencySeconds(androidAccount, syncFrequencySeconds);

        boolean isSyncable = getDataBoolean(MyAccount.KEY_IS_SYNCABLE, true);
        if (isSyncable != (ContentResolver.getIsSyncable(androidAccount, MatchedUri.AUTHORITY) > 0)) {
            ContentResolver.setIsSyncable(androidAccount, MatchedUri.AUTHORITY, isSyncable ? 1 : 0);
        }
        boolean syncAutomatically = getDataBoolean(MyAccount.KEY_IS_SYNCED_AUTOMATICALLY, true);
        if (syncAutomatically != ContentResolver.getSyncAutomatically(androidAccount, MatchedUri.AUTHORITY)) {
            // We need to preserve sync on/off during backup/restore.
            // don't know about "network tickles"... See:
            // http://stackoverflow.com/questions/5013254/what-is-a-network-tickle-and-how-to-i-go-about-sending-one
            ContentResolver.setSyncAutomatically(androidAccount, MatchedUri.AUTHORITY, syncAutomatically);
        }
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        String jsonString = toJsonString();
        logMe("Saving to " + androidAccount.name);
        am.setUserData(androidAccount, AccountUtils.KEY_ACCOUNT, jsonString);
        return Try.success(true);
    }

    public boolean isVersionCurrent() {
        return AccountUtils.ACCOUNT_VERSION == getVersion();
    }

    public int getVersion() {
        return getDataInt(KEY_VERSION, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AccountData)) return false;

        final AccountData other = (AccountData)o;
        return isPersistent() == other.isPersistent() && toJsonString().equals(other.toJsonString());
    }

    @Override
    public int hashCode() {
        String text = Boolean.toString(isPersistent());
        text += toJsonString();
        return text.hashCode();
    }

    @Override
    public boolean dataContains(String key) {
        boolean contains = false;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                contains = true;
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return contains;
    }

    boolean getDataBoolean(String key, boolean defValue) {
        boolean value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = SharedPreferencesUtil.isTrue(str);
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return value;
    }

    @Override
    public String getDataString(String key, String defValue) {
        return data.optString(key, defValue);
    }

    @Override
    public int getDataInt(String key, int defValue) {
        int value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = Integer.parseInt(str);
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return value;
    }

    public long getDataLong(String key, long defValue) {
        long value = defValue;
        try {
            String str = getDataString(key, "null");
            if (str.compareTo("null") != 0) {
                value = Long.parseLong(str);
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
        return value;
    }
    
    public void setDataBoolean(String key, boolean value) {
        try {
            setDataString(key, Boolean.toString(value));
        } catch (Exception e) {
            MyLog.v(this, e);
        }
    }
    
    @Override
    public void setDataLong(String key, long value) {
        try {
            setDataString(key, Long.toString(value));
        } catch (Exception e) {
            MyLog.v(this, e);
        }
    }
    
    @Override
    public void setDataInt(String key, int value) {
        try {
            setDataString(key, Integer.toString(value));
        } catch (Exception e) {
            MyLog.v(this, e);
        }
    }

    @Override
    public void setDataString(String key, String value) {
        if (StringUtils.isEmpty(value)) {
            data = JsonUtils.remove(data, key);
        } else {
            data = JsonUtils.put(data, key, value);
        }
    }

    public static final Creator<AccountData> CREATOR = new Creator<AccountData>() {

        @Override
        public AccountData createFromParcel(Parcel source) {
            return AccountData.fromBundle(MyContextHolder.get(), source.readBundle());
        }

        @Override
        public AccountData[] newArray(int size) {
            return new AccountData[size];
        }
    };
    
    static AccountData fromBundle(MyContext myContext, Bundle bundle) {
        String jsonString = "";
        if (bundle != null) {
            jsonString = bundle.getString(AccountUtils.KEY_ACCOUNT);
        }
        return fromJsonString(myContext, jsonString, false).logMe("Loaded from bundle");
    }

    private AccountData logMe(String msg) {
        MyLog.v(this, () -> msg + ":\n" + toJsonString());
        return this;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(toJsonString());
    }

    public JSONObject toJSon() {
        return data;
    }

    public String toJsonString() {
        return JsonUtils.toString(data, 2);
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }
}
