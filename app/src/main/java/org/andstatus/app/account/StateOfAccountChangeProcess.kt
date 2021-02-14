/* 
 * Copyright (C) 2010-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.Bundle;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtil;

import java.util.concurrent.atomic.AtomicReference;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/** State of the Account add/change process that we store between activity execution steps
*   It's not proper to persist a Bundle, 
*   see: <a href="http://groups.google.com/group/android-developers/browse_thread/thread/6526fe81d2d56a98">http://groups.google.com/group/android-developers/browse_thread/thread/6526fe81d2d56a98</a>.
*   
*   This class will be close to com.android.email.activity.setup.SetupData
**/
class StateOfAccountChangeProcess {
    private static final String TAG = StateOfAccountChangeProcess.class.getSimpleName();

    /** Stored state of the single object of this class
     * It's static so it generally stays intact between the {@link AccountSettingsActivity}'s instantiations 
     * */
    private static final AtomicReference<Bundle> STORED_STATE = new AtomicReference<>();
    
    private static final String ACCOUNT_ACTION_KEY = "account_action";
    private static final String ACCOUNT_AUTHENTICATOR_RESPONSE_KEY = "account_authenticator_response";
    private static final String ACCOUNT_KEY = "account";
    private static final String ACTION_COMPLETED_KEY = "action_completed";
    private static final String ACTION_SUCCEEDED_KEY = "action_succeeded";
    private static final String REQUEST_TOKEN_KEY = "request_token";
    private static final String REQUEST_SECRET_KEY = "request_secret";

    private String accountAction = Intent.ACTION_DEFAULT;
    volatile boolean actionCompleted = true;
    boolean actionSucceeded = true;
    AccountAuthenticatorResponse authenticatorResponse = null;

    // And this is what we constructed (maybe unsuccessfully)
    final MyAccount.Builder builder;

    boolean useThisState = false;
    
    /**
     * The state was restored
     */
    boolean restored = false;

    boolean accountShouldBeSelected = false;
    boolean originShouldBeSelected = false;

    private volatile String requestToken = null;
    private volatile String requestSecret = null;
    
    private StateOfAccountChangeProcess(Bundle bundle) {
        if (bundle != null && bundle.containsKey(ACTION_COMPLETED_KEY)) {
            setAccountAction(bundle.getString(ACCOUNT_ACTION_KEY));
            actionCompleted = bundle.getBoolean(ACTION_COMPLETED_KEY, true);
            actionSucceeded = bundle.getBoolean(ACTION_SUCCEEDED_KEY);
            builder = bundle.getParcelable(ACCOUNT_KEY);
            authenticatorResponse = bundle.getParcelable(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY);
            setRequestTokenWithSecret(bundle.getString(REQUEST_TOKEN_KEY), bundle.getString(REQUEST_SECRET_KEY));
            restored = true;
        } else {
            builder = MyAccount.Builder.fromMyAccount(MyAccount.EMPTY);
        }
    }

    static StateOfAccountChangeProcess fromStoredState() {
        return new StateOfAccountChangeProcess(STORED_STATE.get());
    }

    static StateOfAccountChangeProcess fromIntent(Intent intent) {
        STORED_STATE.set(null);
        StateOfAccountChangeProcess state = fromStoredState();
        state.setAccountAction(intent.getAction());   
        
        Bundle extras = intent.getExtras();
        if (extras != null) {
            // For a usage example see also com.android.email.activity.setup.AccountSettings.onCreate(Bundle)

            // Unparcel Extras!
            state.authenticatorResponse = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
            if (state.authenticatorResponse != null) {
                state.useThisState = true;
            }

            if (!state.useThisState) {
                // Maybe we received MyAccount name as a parameter?!
                String accountName = extras.getString(IntentExtra.ACCOUNT_NAME.key);
                if (StringUtil.nonEmpty(accountName)) {
                    state.builder.rebuildMyAccount(
                            AccountName.fromAccountName(myContextHolder.getNow(), accountName));
                    state.useThisState = state.builder.isPersistent();
                }
            }

            if (!state.useThisState) {
                String originName = extras.getString(IntentExtra.ORIGIN_NAME.key);
                if (StringUtil.nonEmpty(originName)) {
                    Origin origin = myContextHolder.getBlocking().origins().fromName(originName);
                    if (origin.isPersistent()) {
                        state.builder.setOrigin(origin);
                        state.useThisState = true;
                    }
                }
            }
        }

        if (state.getAccount().isEmpty() && !state.getAccountAction().equals(Intent.ACTION_INSERT)) {
            switch (myContextHolder.getNow().accounts().size()) {
                case 0:
                    state.setAccountAction(Intent.ACTION_INSERT);
                    break;
                case 1:
                    state.builder.rebuildMyAccount(
                            myContextHolder.getNow().accounts().getCurrentAccount().getOAccountName());
                    break;
                default:
                    state.accountShouldBeSelected = true;
                    break;
            }
        }
        
        if (state.getAccount().isEmpty()) {
            if (state.getAccountAction().equals(Intent.ACTION_INSERT)) {
                Origin origin = myContextHolder.getNow()
                        .origins()
                        .firstOfType(OriginType.UNKNOWN);
                state.builder.rebuildMyAccount(AccountName.fromOriginAndUniqueName(origin, ""));
                state.originShouldBeSelected = true;
            } else {
                state.builder.rebuildMyAccount(
                        myContextHolder.getNow().accounts().getCurrentAccount().getOAccountName());
            }
            if (!state.builder.isPersistent()) {
                state.setAccountAction(Intent.ACTION_INSERT);
            } else {
                state.setAccountAction(Intent.ACTION_VIEW);
            }
        } else {
            if (state.builder.isPersistent()) {
                state.setAccountAction(Intent.ACTION_EDIT);
            } else {
                state.setAccountAction(Intent.ACTION_INSERT);
            }
        }
        
        return state;
    }

    String getRequestToken() {
        return requestToken;
    }

    String getRequestSecret() {
        return requestSecret;
    }
    
    /** null means to clear the old values */
    void setRequestTokenWithSecret(String token, String secret) {
        if (StringUtil.isEmpty(token)) {
            requestToken = null;
        } else {
            requestToken = token;
        }
        MyLog.d(TAG, StringUtil.isEmpty(token) ? "Clearing Request Token" : "Saving Request Token: " + token);
        if (StringUtil.isEmpty(secret)) {
            requestSecret = null;
        } else {
            requestSecret = secret;
        }
        MyLog.d(TAG, StringUtil.isEmpty(secret) ? "Clearing Request Secret" : "Saving Request Secret: " + secret);
    }
    
    /**
     * Store the state of the not completed actions in the global static object
     * or forget old state of completed action
     */
    void save() {
        if (actionCompleted) {
            forget();
        } else {
            STORED_STATE.updateAndGet(b -> {
                Bundle bundle = new Bundle();
                bundle.putString(ACCOUNT_ACTION_KEY, getAccountAction());
                bundle.putBoolean(ACTION_COMPLETED_KEY, actionCompleted);
                bundle.putBoolean(ACTION_SUCCEEDED_KEY, actionSucceeded);
                bundle.putParcelable(ACCOUNT_KEY, builder);
                bundle.putParcelable(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY, authenticatorResponse);
                bundle.putString(REQUEST_TOKEN_KEY, requestToken);
                bundle.putString(REQUEST_SECRET_KEY, requestSecret);
                return bundle;
            });
            MyLog.v(this, "State saved");
        }
    }
    
    void forget() {
        authenticatorResponse = null;
        STORED_STATE.set(null);
    }

    String getAccountAction() {
        return accountAction;
    }

    boolean isUsernameNeededToStartAddingNewAccount() {
        return builder.getOrigin().getOriginType().isUsernameNeededToStartAddingNewAccount(builder.isOAuth());
    }

    MyAccount getAccount() {
        return builder.getAccount();
    }

    void setAccountAction(String accountAction) {
        if (StringUtil.isEmpty(accountAction)) {
            this.accountAction = Intent.ACTION_DEFAULT;
        } else {
            this.accountAction = accountAction;
        }
    }
}