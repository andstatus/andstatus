/**
 * Copyright (C) 2013-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.accounts.AccountManager;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.account.MyAccount.Builder;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyDatabaseConverterController;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyAccountConverter {
    private static final String TAG = MyAccount.class.getSimpleName();

    private MyAccountConverter() {
    }

    public static int convert14to16(SQLiteDatabase db, int oldVersion) {
        final String method = "convert14to16";
        final int versionTo = 16;
        boolean ok = false;
        try {
            MyLog.i(TAG, "Accounts upgrading step from version " + oldVersion + " to version " + versionTo );
            MyContext myContext = MyContextHolder.get();
            myContext.persistentOrigins().initialize(db);
            
            android.accounts.AccountManager am = AccountManager.get(myContext.context());
            android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
            Collection<android.accounts.Account> accountsToRemove = new ArrayList<android.accounts.Account>(); 
            for (android.accounts.Account androidAccount : aa) {
                MyDatabaseConverterController.stillUpgrading();
                AndroidAccountData androidAccountData = new AndroidAccountData(am, androidAccount);
                int versionOldBefore16 = androidAccountData.getDataInt(MyAccount.KEY_VERSION, 0);
                AccountData accountDataOld = AccountData.fromAndroidAccount(myContext, androidAccount);
                int versionOld2 = accountDataOld.getDataInt(MyAccount.KEY_VERSION, 0);
                if (versionOld2 == versionTo) {
                    MyLog.i(TAG, "Account " + androidAccount.name + " is already converted?!, skipping");
                } else if ( versionOldBefore16 == 14 && !androidAccountData.getDataBoolean(MyAccount.KEY_DELETED, false)) {
                    MyLog.v(TAG, "Upgrading account " + androidAccount.name);
                    am.setUserData(androidAccount, MyAccount.KEY_VERSION, null);

                    AccountData accountData = AccountData.fromJson(null, false);
                    androidAccountData.moveStringKeyTo(MyAccount.KEY_USERNAME, accountData);
                    androidAccountData.moveStringKeyTo(Origin.KEY_ORIGIN_NAME, accountData);
                    androidAccountData.moveStringKeyTo(MyAccount.KEY_USER_OID, accountData);
                    androidAccountData.moveLongKeyTo(MyAccount.KEY_USER_ID, accountData);
                    
                    Origin origin = myContext.persistentOrigins().fromName(accountData.getDataString(Origin.KEY_ORIGIN_NAME, ""));
                    
                    boolean isOauth = androidAccountData.getDataBoolean(MyAccount.KEY_OAUTH, origin.isOAuthDefault());
                    accountData.setDataBoolean(MyAccount.KEY_OAUTH, isOauth);
                    am.setUserData(androidAccount, MyAccount.KEY_OAUTH, null);
                    if (isOauth) {
                        androidAccountData.moveStringKeyTo("user_token", accountData);
                        androidAccountData.moveStringKeyTo("user_secret", accountData);
                    } else {
                        androidAccountData.moveStringKeyTo("password", accountData);
                    }

                    MyAccount.CredentialsVerificationStatus.load(androidAccountData).put(accountData);
                    am.setUserData(androidAccount, MyAccount.CredentialsVerificationStatus.KEY, null);
                    
                    MyLog.v(TAG, method + "; " + accountData.toJsonString());
                    
                    androidAccountData.moveLongKeyTo(MyPreferences.KEY_SYNC_FREQUENCY_SECONDS, accountData);
                    
                    Builder builder = Builder.fromAccountData(myContext, accountData, method);
                    if (!builder.saveSilently().success) {
                        MyLog.e(TAG, "Failed to convert account " + androidAccount.name + ", deleting");
                        accountsToRemove.add(androidAccount);
                    }
                } else {
                    MyLog.e(TAG, "Account " + androidAccount.name + " version is unknown (" + versionOldBefore16 + "), deleting");
                    accountsToRemove.add(androidAccount);
                }
            }
            removeOldAccounts(am, accountsToRemove);
            ok = true;
        } catch (Exception e) {
            MyLog.e(TAG, e);
        }
        if (ok) {
            MyLog.i(TAG, "Accounts upgrading step successfully upgraded accounts from " + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(TAG, "Error upgrading accounts from " + oldVersion + " to version " + versionTo);
        }
        return ok ? versionTo : oldVersion;
    }

    private static void removeOldAccounts(android.accounts.AccountManager am,
            Collection<android.accounts.Account> accountsToRemove) {
        if (!accountsToRemove.isEmpty()) {
            MyLog.i(TAG, "Removing " + accountsToRemove.size() + " old accounts");
            for (android.accounts.Account account : accountsToRemove) {
                MyLog.i(TAG, "Removing old account: " + account.name);
                am.removeAccount(account, null, null);
            }
        }
    }
}
