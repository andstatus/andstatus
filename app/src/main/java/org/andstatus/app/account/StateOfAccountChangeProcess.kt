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
package org.andstatus.app.account

import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.content.Intent
import android.os.Bundle
import org.andstatus.app.IntentExtra
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.MyLog
import java.util.concurrent.atomic.AtomicReference

/** State of the Account add/change process that we store between activity execution steps
 * It's not proper to persist a Bundle,
 * see: [http://groups.google.com/group/android-developers/browse_thread/thread/6526fe81d2d56a98](http://groups.google.com/group/android-developers/browse_thread/thread/6526fe81d2d56a98).
 *
 * This class will be close to com.android.email.activity.setup.SetupData
 */
class StateOfAccountChangeProcess private constructor(bundle: Bundle?) {
    private var accountAction: String? = Intent.ACTION_DEFAULT

    @Volatile
    var actionCompleted = true
    var actionSucceeded = true
    var authenticatorResponse: AccountAuthenticatorResponse? = null

    // And this is what we constructed (maybe unsuccessfully)
    val builder: MyAccount.Builder?
    var useThisState = false

    /**
     * The state was restored
     */
    var restored = false
    var accountShouldBeSelected = false
    var originShouldBeSelected = false

    @Volatile
    private var requestToken: String? = null

    @Volatile
    private var requestSecret: String? = null
    fun getRequestToken(): String? {
        return requestToken
    }

    fun getRequestSecret(): String? {
        return requestSecret
    }

    /** null means to clear the old values  */
    fun setRequestTokenWithSecret(token: String?, secret: String?) {
        requestToken = if (token.isNullOrEmpty()) {
            null
        } else {
            token
        }
        MyLog.d(TAG, if (token.isNullOrEmpty()) "Clearing Request Token" else "Saving Request Token: $token")
        requestSecret = if (secret.isNullOrEmpty()) {
            null
        } else {
            secret
        }
        MyLog.d(TAG, if (secret.isNullOrEmpty()) "Clearing Request Secret" else "Saving Request Secret: $secret")
    }

    /**
     * Store the state of the not completed actions in the global static object
     * or forget old state of completed action
     */
    fun save() {
        if (actionCompleted) {
            forget()
        } else {
            STORED_STATE.updateAndGet {
                val bundle = Bundle()
                bundle.putString(ACCOUNT_ACTION_KEY, getAccountAction())
                bundle.putBoolean(ACTION_COMPLETED_KEY, actionCompleted)
                bundle.putBoolean(ACTION_SUCCEEDED_KEY, actionSucceeded)
                bundle.putParcelable(ACCOUNT_KEY, builder)
                bundle.putParcelable(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY, authenticatorResponse)
                bundle.putString(REQUEST_TOKEN_KEY, requestToken)
                bundle.putString(REQUEST_SECRET_KEY, requestSecret)
                bundle
            }
            MyLog.v(this, "State saved")
        }
    }

    fun forget() {
        authenticatorResponse = null
        STORED_STATE.set(null)
    }

    fun getAccountAction(): String? {
        return accountAction
    }

    fun isUsernameNeededToStartAddingNewAccount(): Boolean {
        return builder?.let {
            it.getOrigin().originType.isUsernameNeededToStartAddingNewAccount(it.isOAuth())
        } ?: false
    }

    fun getAccount(): MyAccount {
        return builder?.getAccount() ?: MyAccount.EMPTY
    }

    fun setAccountAction(accountAction: String?) {
        if (accountAction.isNullOrEmpty()) {
            this.accountAction = Intent.ACTION_DEFAULT
        } else {
            this.accountAction = accountAction
        }
    }

    companion object {
        private val TAG: String = StateOfAccountChangeProcess::class.java.simpleName

        /** Stored state of the single object of this class
         * It's static so it generally stays intact between the [AccountSettingsActivity]'s instantiations
         */
        private val STORED_STATE: AtomicReference<Bundle> = AtomicReference()
        private val ACCOUNT_ACTION_KEY: String = "account_action"
        private val ACCOUNT_AUTHENTICATOR_RESPONSE_KEY: String = "account_authenticator_response"
        private val ACCOUNT_KEY: String = "account"
        private val ACTION_COMPLETED_KEY: String = "action_completed"
        private val ACTION_SUCCEEDED_KEY: String = "action_succeeded"
        private val REQUEST_TOKEN_KEY: String = "request_token"
        private val REQUEST_SECRET_KEY: String = "request_secret"

        fun fromStoredState(): StateOfAccountChangeProcess {
            return StateOfAccountChangeProcess(STORED_STATE.get())
        }

        fun fromIntent(intent: Intent): StateOfAccountChangeProcess {
            STORED_STATE.set(null)
            val state = fromStoredState()
            state.setAccountAction(intent.action)
            val extras = intent.extras
            if (extras != null) {
                // For a usage example see also com.android.email.activity.setup.AccountSettings.onCreate(Bundle)

                // Unparcel Extras!
                state.authenticatorResponse = extras.getParcelable(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE)
                if (state.authenticatorResponse != null) {
                    state.useThisState = true
                }
                if (!state.useThisState) {
                    // Maybe we received MyAccount name as a parameter?!
                    val accountName = extras.getString(IntentExtra.ACCOUNT_NAME.key)
                    if (!accountName.isNullOrEmpty()) {
                        state.builder?.rebuildMyAccount(
                                AccountName.fromAccountName( MyContextHolder.myContextHolder.getNow(), accountName))
                        state.useThisState = state.builder?.isPersistent() ?: false
                    }
                }
                if (!state.useThisState) {
                    val originName = extras.getString(IntentExtra.ORIGIN_NAME.key)
                    if (!originName.isNullOrEmpty()) {
                        val origin: Origin =  MyContextHolder.myContextHolder.getBlocking().origins().fromName(originName)
                        if (origin.isPersistent()) {
                            state.builder?.setOrigin(origin)
                            state.useThisState = state.builder != null
                        }
                    }
                }
            }
            if (state.getAccount().isEmpty && state.getAccountAction() != Intent.ACTION_INSERT) {
                when ( MyContextHolder.myContextHolder.getNow().accounts().size()) {
                    0 -> state.setAccountAction(Intent.ACTION_INSERT)
                    1 -> state.builder?.rebuildMyAccount(
                             MyContextHolder.myContextHolder.getNow().accounts().currentAccount.getOAccountName())
                    else -> state.accountShouldBeSelected = true
                }
            }
            if (state.getAccount().isEmpty) {
                if (state.getAccountAction() == Intent.ACTION_INSERT) {
                    val origin: Origin =  MyContextHolder.myContextHolder.getNow()
                            .origins()
                            .firstOfType(OriginType.UNKNOWN)
                    state.builder?.rebuildMyAccount(AccountName.fromOriginAndUniqueName(origin, ""))
                    state.originShouldBeSelected = true
                } else {
                    state.builder?.rebuildMyAccount(
                             MyContextHolder.myContextHolder.getNow().accounts().currentAccount.getOAccountName())
                }
                if (state.builder?.isPersistent() == true) {
                    state.setAccountAction(Intent.ACTION_INSERT)
                } else {
                    state.setAccountAction(Intent.ACTION_VIEW)
                }
            } else {
                if (state.builder?.isPersistent() == true) {
                    state.setAccountAction(Intent.ACTION_EDIT)
                } else {
                    state.setAccountAction(Intent.ACTION_INSERT)
                }
            }
            return state
        }
    }

    init {
        if (bundle != null && bundle.containsKey(ACTION_COMPLETED_KEY)) {
            setAccountAction(bundle.getString(ACCOUNT_ACTION_KEY))
            actionCompleted = bundle.getBoolean(ACTION_COMPLETED_KEY, true)
            actionSucceeded = bundle.getBoolean(ACTION_SUCCEEDED_KEY)
            builder = bundle.getParcelable(ACCOUNT_KEY)
            authenticatorResponse = bundle.getParcelable(ACCOUNT_AUTHENTICATOR_RESPONSE_KEY)
            setRequestTokenWithSecret(bundle.getString(REQUEST_TOKEN_KEY), bundle.getString(REQUEST_SECRET_KEY))
            restored = true
        } else {
            builder = MyAccount.Builder.fromMyAccount(MyAccount.EMPTY)
        }
    }
}