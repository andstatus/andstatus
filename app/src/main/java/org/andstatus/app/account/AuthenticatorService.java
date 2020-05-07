/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Based on a very basic authenticator service for POP/IMAP...
 */
public class AuthenticatorService extends Service {

    public static final String OPTIONS_USERNAME = "username";
    public static final String OPTIONS_PASSWORD = "password";
    public static final String ANDROID_ACCOUNT_TYPE = "org.andstatus.app";
    
    class Authenticator extends AbstractAccountAuthenticator {
        private final Context context;

        public Authenticator(Context context) {
            super(context);
            this.context = context;
        }

        /** 
         * We add account launching {@link AccountSettingsActivity} activity
         */
        @Override
        public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
                String authTokenType, String[] requiredFeatures, Bundle options)
                throws NetworkErrorException {
            // There are two cases here:
            // 1) We are called with a username/password; this comes from the traditional email
            //    app UI; we simply create the account and return the proper bundle
            if (options != null && options.containsKey(OPTIONS_PASSWORD)
                    && options.containsKey(OPTIONS_USERNAME)) {
                final Account account = new Account(options.getString(OPTIONS_USERNAME), ANDROID_ACCOUNT_TYPE);
                AccountManager.get(AuthenticatorService.this).addAccountExplicitly(
                            account, options.getString(OPTIONS_PASSWORD), null);

                Bundle b = new Bundle();
                b.putString(AccountManager.KEY_ACCOUNT_NAME, options.getString(OPTIONS_USERNAME));
                b.putString(AccountManager.KEY_ACCOUNT_TYPE, ANDROID_ACCOUNT_TYPE);
                return b;
            // 2) The other case is that we're creating a new account from an Account manager
            //    activity.  In this case, we add an intent that will be used to gather the
            //    account information...
            } else {
                Bundle b = new Bundle();
                Intent intent = new Intent(AuthenticatorService.this, AccountSettingsActivity.class);
                //  This is how we define what to do in {@link AccountSettingsActivity} activity
                intent.setAction(Intent.ACTION_INSERT);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); 
                intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
                b.putParcelable(AccountManager.KEY_INTENT, intent);
                return b;
            }
        }

        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                Bundle options) {
            return null;
        }

        @Override
        public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
            return null;
        }

        @Override
        public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                String authTokenType, Bundle loginOptions) throws NetworkErrorException {
            return null;
        }

        @Override
        public String getAuthTokenLabel(String authTokenType) {
            // null means we don't have compartmentalized authtoken types
            return null;
        }

        @Override
        public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) {
            return null;
        }

        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                String authTokenType, Bundle loginOptions) {
            return null;
        }

        @Override
        public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response, Account account) {
            boolean deleted = true;
            if (AccountUtils.isVersionCurrent(context, account)) {
                deleted = myContextHolder
                    .initialize(context, context, false)
                    .getFuture()
                    .tryBlocking()
                    .map(myContext -> myContext.accounts().fromAccountName(account.name))
                    .map(ma -> {
                        if (ma.isValid()) {
                            myContextHolder.getNow().timelines().onAccountDelete(ma);
                            MyPreferences.onPreferencesChanged();
                            return true;
                        }
                        return false;
                    })
                    .getOrElse(false);
            }
            final Bundle result = new Bundle();
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, deleted);
            return result;
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        if (AccountManager.ACTION_AUTHENTICATOR_INTENT.equals(intent.getAction())) {
            return new Authenticator(this).getIBinder();
        } else {
            return null;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        MyLog.v(this, "onCreate");
        myContextHolder.getInitialized(this, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MyLog.v(this, "onDestroy");
    }
}
