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
import io.vavr.control.Try
import org.andstatus.app.ActivityRequestCode
import org.andstatus.app.R
import org.andstatus.app.account.AccountSettingsActivity.FragmentAction
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncRunnable
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
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
        val accountSettingsActivity = activity as AccountSettingsActivity? ?: return
        val state = accountSettingsActivity.state
        if (state.builder.isPersistent()) {
            for (account in AccountUtils.getCurrentAccounts(accountSettingsActivity)) {
                if (state.myAccount.getAccountName() == account.name) {
                    MyLog.i(this, "Removing account: " + account.name)
                    AsyncRunnable(taskId = this, AsyncEnum.DEFAULT_POOL, cancelable = false)
                        .doInBackground {
                            Try.of { AccountManager.get(accountSettingsActivity) }
                                .map { it.removeAccount(account, accountSettingsActivity, null, null) }
                                .map { it.getResult(10, TimeUnit.SECONDS) }
                                .flatMap { result ->
                                    if (result != null && result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)) {
                                        TryUtils.SUCCESS
                                    } else TryUtils.failure("result: $result")
                                }

                        }
                        .onPostExecute { params: Unit, result: Try<Unit> ->
                            result.onSuccess { accountSettingsActivity.finish() }
                                .onFailure { accountSettingsActivity.appendError(result.cause.toString()) }
                        }
                        .execute(this, Unit)
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
