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
import android.util.Log;

import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyAccountConverter {
    private static final String TAG = MyAccount.class.getSimpleName();

    public static int convert11to12(SQLiteDatabase db, int oldVersion) {
        final int versionTo = 12;
        boolean ok = false;
        String step = "";
        try {
            Log.i(TAG, "Accounts upgrading step from version " + oldVersion + " to version " + versionTo );
            Context context = MyPreferences.getContext();
            
            android.accounts.AccountManager am = AccountManager.get(context);
            android.accounts.Account[] aa = am.getAccountsByType( AuthenticatorService.ANDROID_ACCOUNT_TYPE );
            for (android.accounts.Account account : aa) {
                MyAccount.Builder builderOld = new MyAccount.Builder(account);
                if (builderOld.getVersion() == versionTo) {
                    Log.i(TAG, "Account " + account.name + " already converted?!");
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
                    if (originName.equalsIgnoreCase("identi.ca")) {
                        username += "@identi.ca";
                        originName = "pump.io";
                        originId = Origin.OriginEnum.PUMPIO.getId();
                    } else if (originName.equalsIgnoreCase("twitter")) {
                        originName = "twitter";
                    }
                    long userId = MyProvider.userNameToId(db, originId, username);
                    String userOid = MyProvider.idToOid(db, OidEnum.USER_OID, userId, 0);
                    AccountName accountNameNew = AccountName.fromOriginAndUserNames(originName, username);
                    Log.i(TAG, "Upgrading account " + account.name + " to " + username + "; oid=" + userOid + "; id=" + userId);

                    MbUser accountMbUser = MbUser.fromOriginAndUserOid(originId, userOid);
                    accountMbUser.userName = username;
                    MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName("/" + originName, TriState.TRUE);
                    
                    String userToken = builderOld.getAccount().getDataString("user_token", "");
                    String userSecret = builderOld.getAccount().getDataString("user_secret", "");
                    
                    builder.setUserTokenWithSecret(userToken, userSecret);
                    builder.setDataLong(MyAccount.Builder.KEY_USER_ID, userId);
                    SharedPreferencesUtil.rename(context, prefsFileNameOld, accountNameNew.prefsFileName());
                    builder.onVerifiedCredentials(accountMbUser, null);
                }
            }
            Log.i(TAG, "Removing old accounts");
            for (android.accounts.Account account : aa) {
                am.removeAccount(account, null, null);
            }
            ok = true;
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        if (ok) {
            Log.i(TAG, "Accounts upgrading step successfully upgraded accounts from " + oldVersion + " to version " + versionTo);
        } else {
            Log.e(TAG, "Error upgrading accounts from " + oldVersion + " to version " + versionTo
                    + " step='" + step +"'");
        }
        return (ok ? versionTo : oldVersion) ;
    }

}
