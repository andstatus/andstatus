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

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.data.MyDatabaseConverter;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyAccountConverter {
    private static final String TAG = MyAccount.class.getSimpleName();

    private MyAccountConverter() {
    }
    
    public static int convert11to12(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 12;
        boolean ok = false;
        String step = "";
        try {
            MyLog.i(TAG, "Accounts upgrading step from version " + oldVersion + " to version " + versionTo );
            Context context = MyContextHolder.get().context();
            
            android.accounts.AccountManager am = AccountManager.get(context);
            android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
            Collection<android.accounts.Account> accountsToRemove = new ArrayList<android.accounts.Account>(); 
            for (android.accounts.Account account : aa) {
                MyDatabaseConverter.stillUpgrading();
                MyAccount.Builder builderOld = new MyAccount.Builder(account);
                if (builderOld.getVersion() == versionTo) {
                    MyLog.i(TAG, "Account " + account.name + " already converted?!");
                } else {
                    // Structure of the account name changed!
                    long originId = Origin.OriginEnum.TWITTER.getId();
                    String originName = "twitter";
                    String username = account.name;
                    int indSlash = account.name.indexOf("/");
                    if (indSlash >= 0) {
                        originName = account.name.substring(0, indSlash);
                        if (indSlash < account.name.length()-1) {
                            username = account.name.substring(indSlash + 1);
                        }
                    }
                    String prefsFileNameOld = "user_" + originName + "-" + username;
                    if ("identi.ca".equalsIgnoreCase(originName)) {
                        username += "@identi.ca";
                        originName = "pump.io";
                        originId = Origin.OriginEnum.PUMPIO.getId();
                    } else if ("twitter".equalsIgnoreCase(originName)) {
                        originName = "twitter";
                    }
                    long userId = MyProvider.userNameToId(db, originId, username);
                    String userOid = MyProvider.idToOid(db, OidEnum.USER_OID, userId, 0);
                    AccountName accountNameNew = AccountName.fromOriginAndUserNames(originName, username);
                    MyLog.i(TAG, "Upgrading account " + account.name + " to " + username + "; oid=" + userOid + "; id=" + userId);

                    MbUser accountMbUser = MbUser.fromOriginAndUserOid(originId, userOid);
                    accountMbUser.userName = username;
                    MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName("/" + originName, TriState.TRUE);
                    
                    String userToken = builderOld.getAccount().getDataString("user_token", "");
                    String userSecret = builderOld.getAccount().getDataString("user_secret", "");
                    
                    builder.setUserTokenWithSecret(userToken, userSecret);
                    builder.setDataLong(MyAccount.Builder.KEY_USER_ID, userId);
                    SharedPreferencesUtil.rename(context, prefsFileNameOld, accountNameNew.prefsFileName());
                    builder.onCredentialsVerified(accountMbUser, null);
                    
                    accountsToRemove.add(account);
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
            MyLog.e(TAG, "Error upgrading accounts from " + oldVersion + " to version " + versionTo
                    + " step='" + step +"'");
        }
        return ok ? versionTo : oldVersion;
    }

}
