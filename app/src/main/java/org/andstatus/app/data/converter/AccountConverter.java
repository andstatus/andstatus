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

package org.andstatus.app.data.converter;

import org.andstatus.app.account.AccountUtils;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;

import java.util.Collection;
import java.util.NoSuchElementException;

import io.vavr.control.Try;

/**
 * @author yvolk@yurivolkov.com
 */
public class AccountConverter {

    private static final String TAG = AccountConverter.class.getSimpleName();

    private AccountConverter() { /* Empty*/ }

    static void removeOldAccounts(android.accounts.AccountManager am,
                                  Collection<android.accounts.Account> accountsToRemove) {
        if (!accountsToRemove.isEmpty()) {
            MyLog.i(TAG, "Removing " + accountsToRemove.size() + " old accounts");
            for (android.accounts.Account account : accountsToRemove) {
                MyLog.i(TAG, "Removing old account: " + account.name);
                am.removeAccount(account, null, null);
            }
        }
    }

    public static Try<JSONObject> convertJson(MyContext myContext, JSONObject jsonIn, boolean isPersistent) {
        int version = AccountUtils.getVersion(jsonIn);
        switch (version) {
            case AccountUtils.ACCOUNT_VERSION:
                return Try.success(jsonIn);
            case 0:
                return Try.failure(new NoSuchElementException("No version info found in " + jsonIn));
            case 16:
                return Try.success(jsonIn).flatMap(json -> Convert47.convertJson16(myContext, json, isPersistent));
            default:
                return Try.failure(new IllegalArgumentException("Unsuppoerted account version: " + version));
        }
    }

}
