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

package org.andstatus.app.data.converter;

import android.accounts.Account;
import android.accounts.AccountManager;

import org.andstatus.app.account.AccountData;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.AccountUtils;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import io.vavr.control.Try;

import static org.andstatus.app.account.AccountUtils.KEY_ACCOUNT;
import static org.andstatus.app.account.AccountUtils.KEY_VERSION;
import static org.andstatus.app.account.MyAccount.KEY_ACCOUNT_NAME;
import static org.andstatus.app.account.MyAccount.KEY_UNIQUE_NAME;
import static org.andstatus.app.account.MyAccount.KEY_USERNAME;
import static org.andstatus.app.net.social.Connection.KEY_PASSWORD;

class Convert47 extends ConvertOneStep {
    private final static String TAG = Convert47.class.getSimpleName();

    Convert47() {
        versionTo = 48;
    }

    @Override
    protected void execute2() {
        convertAccounts();
    }

    private void convertAccounts() {
        final String method = "convert47";
        final int versionFrom = 16;
        AtomicInteger accountsConverted = new AtomicInteger();

        progressLogger.logProgress(stepTitle + ": Converting accounts");
        MyContext myContext = MyContextHolder.get();
        myContext.origins().initialize(db);

        android.accounts.AccountManager am = AccountManager.get(myContext.context());
        Collection<Account> accountsToRemove = new ArrayList<>();
        for (Account accountIn : AccountUtils.getAllAccounts(myContext.context())) {
            DatabaseConverterController.stillUpgrading();
            Try<JSONObject> jsonIn = JsonUtils.toJsonObject(am.getUserData(accountIn, KEY_ACCOUNT));
            int versionIn = jsonIn.map(AccountUtils::getVersion).getOrElse(0);
            if (versionIn >= versionTo) {
                MyLog.i(TAG, "Account " + accountIn.name + " is already converted?!, skipping");
            } else if ( versionIn == versionFrom &&
                    !jsonIn.map(jso -> jso.optBoolean(MyAccount.KEY_DELETED)).getOrElse(false)) {
                MyLog.v(TAG, "Upgrading account " + accountIn.name);

                jsonIn.flatMap(jso -> convertJson16(myContext, jso, true))
                .flatMap(jsonOut -> {
                    AccountData accountData = AccountData.fromJson(myContext, jsonOut, false);
                    MyLog.v(TAG, method + "; " + accountData.toJsonString());
                    String accountName = jsonOut.optString(KEY_ACCOUNT_NAME);

                    return (accountName.equals(accountIn.name)
                            ? Try.success(accountIn)
                            : AccountUtils.addEmptyAccount(am, accountName, jsonOut.optString(KEY_PASSWORD)))
                        .flatMap(accountOut ->
                                accountData.saveIfChanged(myContext, accountOut)
                                .map(b -> accountOut));
                })
                .onSuccess(accountNew -> {
                    accountsConverted.incrementAndGet();
                    if (accountNew.name.equalsIgnoreCase(accountIn.name)) {
                        progressLogger.logProgress(stepTitle + ": Converted account " +
                                accountNew.name + " with the same name");
                    } else {
                        progressLogger.logProgress(stepTitle + ": Converted account " +
                                accountIn.name + " to " + accountNew.name + ", deleting the old one");
                        accountsToRemove.add(accountIn);
                    }
                })
                .onFailure(e -> {
                    progressLogger.logProgress(stepTitle + ": Failed to convert account " +
                            accountIn.name + ", deleting");
                    accountsToRemove.add(accountIn);
                });
            } else {
                MyLog.e(TAG, "Account " + accountIn.name +
                        " version " + versionIn + " less than " + versionFrom + " is unsupported, deleting");
                accountsToRemove.add(accountIn);
            }
        }
        AccountConverter.removeOldAccounts(am, accountsToRemove);
        if (accountsConverted.intValue() > 0) {
            progressLogger.logProgress(stepTitle + ": Successfully upgraded " + accountsConverted.intValue() + " accounts");
        } else {
            progressLogger.logProgress(stepTitle + ": No accounts upgraded from " + oldVersion + " to version " + versionTo);
        }
    }

    static Try<JSONObject> convertJson16(MyContext myContext, JSONObject jsonIn, boolean isPersistent) {
        final int versionTo = 48;
        String originName = jsonIn.optString(Origin.KEY_ORIGIN_NAME);
        Origin origin = myContext.origins().fromName(originName);
        if (origin.isEmpty()) return Try.failure(new NoSuchElementException("Origin wan't found for " + jsonIn));

        String oldName = jsonIn.optString(KEY_USERNAME).trim();
        String newUserName = oldName;
        String newUniqueName = "";
        String host = "";
        if (oldName.contains("@")) {
            host = AccountName.accountNameToHost(oldName);
            newUniqueName = oldName;
            newUserName = Actor.uniqueNameToUsername(origin, oldName).orElse("");
        } else {
            host = origin.getAccountNameHost();
            newUniqueName = oldName + "@" + host;
            newUserName = oldName;
        }
        String accountName = newUniqueName + "/" + origin.getOriginInAccountName(host);
        JSONObject jso;
        try {
            jso = new JSONObject(jsonIn.toString());
            jso.put(KEY_ACCOUNT_NAME, accountName);
            jso.put(KEY_USERNAME, newUserName);
            jso.put(KEY_UNIQUE_NAME, newUniqueName);
            jso.put(KEY_VERSION, versionTo);
        } catch (JSONException e) {
            return Try.failure(new Exception("Failed to convert " + jsonIn, e));
        }
        return Try.success(jso);
    }
}
