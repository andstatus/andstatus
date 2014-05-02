/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyDatabaseConverterController;
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

    public static int convert12to14(SQLiteDatabase db, int oldVersion, String twitterName,
            String statusNetSystemName) {
        final int versionTo = 14;
        boolean ok = false;
        try {
            MyLog.i(TAG, "Accounts upgrading step from version " + oldVersion + " to version " + versionTo );
            Context context = MyContextHolder.get().context();
            MyContextHolder.get().persistentOrigins().initialize(db);
            
            android.accounts.AccountManager am = AccountManager.get(context);
            android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
            Collection<android.accounts.Account> accountsToRemove = new ArrayList<android.accounts.Account>(); 
            for (android.accounts.Account account : aa) {
                MyDatabaseConverterController.stillUpgrading();
                MyAccount.Builder builderOld = MyAccount.Builder.fromAndroidAccount(MyContextHolder.get(), account);
                if (builderOld.getVersion() == versionTo) {
                    MyLog.i(TAG, "Account " + account.name + " already converted?!");
                } else {
                    // TODO: setVersion to versionTo
                    String originNameOld = AccountName.accountNameToOriginName(account.name);
                    if ("twitter".equals(originNameOld)) {
                        if (builderOld.onOriginNameChanged(twitterName)) {
                            accountsToRemove.add(account);
                        }
                    } else if ("status.net".equals(originNameOld) 
                            && builderOld.onOriginNameChanged(statusNetSystemName)) {
                        accountsToRemove.add(account);
                    }
                    builderOld.saveSilently();
                }
            }
            MyLog.i(TAG, "Removing old accounts");
            for (android.accounts.Account account : accountsToRemove) {
                MyLog.i(TAG, "Removing old account: " + account.name);
                am.removeAccount(account, null, null);
            }
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
}
