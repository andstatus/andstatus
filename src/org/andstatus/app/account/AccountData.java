/**
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
import android.content.PeriodicSync;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount.Builder.SaveResult;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class AccountData implements Parcelable, AccountDataWriter {
    private static final String TAG = AccountData.class.getSimpleName();

    /**
     * The Key for the android.accounts.Account bundle;
     */
    public static final String KEY_ACCOUNT = "account";
    
    private final JSONObject data;
    private boolean persistent = false;

    boolean isPersistent() {
        return persistent;
    }

    void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }
    
    public static AccountData fromAndroidAccount(MyContext myContext, Account account) {
        if (account == null) {
            throw new IllegalArgumentException(TAG + " account is null");
        }
        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        AccountData accountData = fromJsonString(am.getUserData(account, KEY_ACCOUNT), true);
        accountData.setDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, getSyncFrequencySeconds(account));
        return accountData;
    }

    private static long getSyncFrequencySeconds(Account account) {
        long syncFrequencySeconds = 0;
        List<PeriodicSync> syncs = ContentResolver.getPeriodicSyncs(account, MyProvider.AUTHORITY);
        if (!syncs.isEmpty()) {
            syncFrequencySeconds = syncs.get(0).period;
        }
        return syncFrequencySeconds;
    }
    
    public static AccountData fromJsonString(String userData, boolean persistent) {
        JSONObject jso = null;
        try {
            if (userData != null) {
                jso = new JSONObject(userData);
            }
        } catch (JSONException e) {
            MyLog.e(TAG, "fromJsonString", e);
        }
        return fromJson(jso, persistent);
    }

    public static AccountData fromJson(JSONObject jso, boolean persistent) {
        return new AccountData(jso, persistent);
    }
    
    private AccountData(JSONObject jso, boolean persistent) {
        if (jso == null) {
            data = new JSONObject();
        } else {
            data = jso;
            this.persistent = persistent; 
        }
    }
    
    /**
     * @param result 
     * @return true if Android account changed
     */
    void saveDataToAccount(MyContext myContext, Account androidAccount, SaveResult result) {
        AccountData oldData = fromAndroidAccount(myContext, androidAccount);
        result.changed = !this.equals(oldData);
        if (result.changed) {
            long syncFrequencySeconds = getDataLong(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, 0);
            if (syncFrequencySeconds > 0 && syncFrequencySeconds != getSyncFrequencySeconds(androidAccount)) {
                result.changed = true;
                setSyncFrequencySeconds(androidAccount, syncFrequencySeconds);
            }
            android.accounts.AccountManager am = AccountManager.get(myContext.context());
            am.setUserData(androidAccount, KEY_ACCOUNT, toJsonString());
            result.savedToAccountManager = true;
        }
        result.success = true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AccountData)) return false;
        final AccountData other = (AccountData)o;
        return hashCode() == other.hashCode();
    }

    @Override
    public int hashCode() {
        String text = Boolean.toString(isPersistent());
        text += toJsonString();
        return text.hashCode();
    }

    private void setSyncFrequencySeconds(Account androidAccount, long syncFrequencySeconds) {
        // See
        // http://developer.android.com/reference/android/content/ContentResolver.html#addPeriodicSync(android.accounts.Account, java.lang.String, android.os.Bundle, long)
        // and
        // http://stackoverflow.com/questions/11090604/android-syncadapter-automatically-initialize-syncing
        ContentResolver.removePeriodicSync(androidAccount, MyProvider.AUTHORITY, new Bundle());
        if (syncFrequencySeconds > 0) {
            ContentResolver.addPeriodicSync(androidAccount, MyProvider.AUTHORITY, new Bundle(), syncFrequencySeconds);
        }
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

    long getDataLong(String key, long defValue) {
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
        try {
            if (TextUtils.isEmpty(value)) {
                data.remove(key);
            } else {
                data.put(key, value);
            }
        } catch (Exception e) {
            MyLog.v(this, e);
        }
    }

    public static final Creator<AccountData> CREATOR 
    = new Creator<AccountData>() {

        @Override
        public AccountData createFromParcel(Parcel source) {
            return AccountData.fromBundle(source.readBundle());
        }

        @Override
        public AccountData[] newArray(int size) {
            return new AccountData[size];
        }
    };
    
    static AccountData fromBundle(Bundle bundle) {
        String jsonString = "";
        if (bundle != null) {
            jsonString = bundle.getString(KEY_ACCOUNT);
        }
        return fromJsonString(jsonString, false);
    }
    
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(toJsonString());
    }

    public String toJsonString() {
        try {
            return data.toString(2);
        } catch (JSONException e) {
            MyLog.e(this, e);
            return "";
        }
    }
}
