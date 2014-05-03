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
import android.text.TextUtils;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

class AndroidAccountData implements AccountDataReader {
    private AccountManager am;
    private Account androidAccount;

    AndroidAccountData(AccountManager am, Account androidAccount) {
        this.am = am;
        this.androidAccount = androidAccount;
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

    /**
     * User Data associated with the account
     */
    @Override
    public String getDataString(String key, String defValue) {
        String value = defValue;
        String str = am.getUserData(androidAccount, key);
        if (!TextUtils.isEmpty(str)) {
            value = str;
        }
        return value;
    }

    void moveStringKeyTo(String key, AccountDataWriter accountData) {
        accountData.setDataString(key, getDataString(key, null));
        am.setUserData(androidAccount, key, null);
    }

    void moveLongKeyTo(String key, AccountDataWriter accountData) {
        accountData.setDataLong(key, getDataLong(key, 0L));
        am.setUserData(androidAccount, key, null);
    }
}
