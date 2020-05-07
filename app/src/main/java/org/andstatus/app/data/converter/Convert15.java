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

package org.andstatus.app.data.converter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.account.AccountData;
import org.andstatus.app.account.AccountUtils;
import org.andstatus.app.account.CredentialsVerificationStatus;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;

import static org.andstatus.app.account.AccountUtils.KEY_VERSION;
import static org.andstatus.app.account.MyAccount.KEY_USERNAME;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;

class Convert15 extends ConvertOneStep {
    private final static String TAG = Convert15.class.getSimpleName();

    @Override
    protected void execute2() {
        versionTo = 16;
        boolean ok = convertAccounts(db, oldVersion) == versionTo;
        if (ok) {
            sql = "DELETE FROM Origin WHERE _ID IN(6, 7)";
            DbUtils.execSQL(db, sql);
        }
    }

    static int convertAccounts(SQLiteDatabase db, int oldVersion) {
        final String method = "convert14to16";
        final int versionTo = 16;
        boolean ok = false;
        try {
            MyLog.i(TAG, "Accounts upgrading step from version " + oldVersion + " to version " + versionTo );
            MyContext myContext = myContextHolder.getNow();
            myContext.origins().initialize(db);

            android.accounts.AccountManager am = AccountManager.get(myContext.context());
            Collection<Account> accountsToRemove = new ArrayList<>();
            for (Account androidAccount : AccountUtils.getAllAccounts(myContext.context())) {
                DatabaseConverterController.stillUpgrading();
                AndroidAccountData androidAccountData = new AndroidAccountData(am, androidAccount);
                int versionOldBefore16 = androidAccountData.getDataInt(KEY_VERSION, 0);
                AccountData accountDataOld = AccountData.fromAndroidAccount(myContext, androidAccount);
                int versionOld2 = accountDataOld.getDataInt(KEY_VERSION, 0);
                if (versionOld2 == versionTo) {
                    MyLog.i(TAG, "Account " + androidAccount.name + " is already converted?!, skipping");
                } else if ( versionOldBefore16 == 14 &&
                        !androidAccountData.getDataBoolean(MyAccount.KEY_DELETED, false)) {
                    MyLog.v(TAG, "Upgrading account " + androidAccount.name);
                    am.setUserData(androidAccount, KEY_VERSION, null);

                    AccountData accountData = AccountData.fromJson(myContext, new JSONObject(), false);
                    androidAccountData.moveStringKeyTo(KEY_USERNAME, accountData);
                    androidAccountData.moveStringKeyTo(Origin.KEY_ORIGIN_NAME, accountData);
                    androidAccountData.moveStringKeyTo(MyAccount.KEY_ACTOR_OID, accountData);
                    androidAccountData.moveLongKeyTo(MyAccount.KEY_ACTOR_ID, accountData);

                    Origin origin = myContext.origins().fromName(
                            accountData.getDataString(Origin.KEY_ORIGIN_NAME));

                    boolean isOauth = androidAccountData.getDataBoolean(MyAccount.KEY_OAUTH, origin.isOAuthDefault());
                    accountData.setDataBoolean(MyAccount.KEY_OAUTH, isOauth);
                    am.setUserData(androidAccount, MyAccount.KEY_OAUTH, null);
                    if (isOauth) {
                        androidAccountData.moveStringKeyTo("user_token", accountData);
                        androidAccountData.moveStringKeyTo("user_secret", accountData);
                    } else {
                        androidAccountData.moveStringKeyTo("password", accountData);
                    }

                    CredentialsVerificationStatus.load(androidAccountData).put(accountData);
                    am.setUserData(androidAccount, CredentialsVerificationStatus.KEY, null);

                    MyLog.v(TAG, method + "; " + accountData.toJsonString());

                    androidAccountData.moveLongKeyTo(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, accountData);
                    accountData.saveIfChanged(androidAccount).onFailure(e -> {
                        MyLog.e(TAG, "Failed to convert account " + androidAccount.name + ", deleting");
                        accountsToRemove.add(androidAccount);
                    });
                } else {
                    MyLog.e(TAG, "Account " + androidAccount.name +
                            " version less than 14 is not supported (" + versionOldBefore16 + "), deleting");
                    accountsToRemove.add(androidAccount);
                }
            }
            AccountConverter.removeOldAccounts(am, accountsToRemove);
            ok = true;
        } catch (Exception e) {
            MyLog.e(TAG, e);
        }
        if (ok) {
            MyLog.i(TAG, "Accounts upgrading step successfully upgraded accounts from " + oldVersion +
                    " to version " + versionTo);
        } else {
            MyLog.e(TAG, "Error upgrading accounts from " + oldVersion + " to version " + versionTo);
        }
        return ok ? versionTo : oldVersion;
    }
}