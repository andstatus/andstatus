/* 
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.R
import org.andstatus.app.account.AccountSettingsActivity.FragmentAction
import org.andstatus.app.os.NonUiThreadExecutor
import org.andstatus.app.os.UiThreadExecutor
import org.andstatus.app.util.MyLog
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class AccountSettingsFragment : Fragment() {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (ActivityRequestCode.fromId(requestCode)) {
            ActivityRequestCode.REMOVE_ACCOUNT -> if (Activity.RESULT_OK == resultCode) {
                onRemoveAccount()
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun onRemoveAccount() {
        val accountSettingsActivity = activity as AccountSettingsActivity?
        val state = accountSettingsActivity?.state
        if (state?.builder != null && state.builder.isPersistent()) {
            for (account in AccountUtils.getCurrentAccounts(accountSettingsActivity)) {
                if (state.myAccount.getAccountName() == account.name) {
                    MyLog.i(this, "Removing account: " + account.name)
                    val am = AccountManager.get(activity)
                    CompletableFuture.supplyAsync({
                        try {
                            val result = am.removeAccount(account, activity, null, null)
                                    .getResult(10, TimeUnit.SECONDS)
                            return@supplyAsync result != null && result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)
                        } catch (e: Exception) {
                            MyLog.w(this, "Failed to remove account " + account.name, e)
                            return@supplyAsync false
                        }
                    }, NonUiThreadExecutor.INSTANCE)
                            .thenAcceptAsync({ ok: Boolean ->
                                if (ok) {
                                    activity?.finish()
                                }
                            }, UiThreadExecutor.INSTANCE)
                    break
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.account_settings, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val activity = activity as AccountSettingsActivity?
        if (activity != null) {
            activity.updateScreen()
            when (FragmentAction.fromBundle(arguments)) {
                FragmentAction.ON_ORIGIN_SELECTED -> activity.goToAddAccount()
                else -> {
                }
            }
        }
        super.onActivityCreated(savedInstanceState)
    }
}