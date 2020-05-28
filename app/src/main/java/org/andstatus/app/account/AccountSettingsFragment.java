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

package org.andstatus.app.account;

import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.R;
import org.andstatus.app.os.NonUiThreadExecutor;
import org.andstatus.app.os.UiThreadExecutor;
import org.andstatus.app.util.MyLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AccountSettingsFragment extends Fragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (ActivityRequestCode.fromId(requestCode)) {
            case REMOVE_ACCOUNT:
                if (Activity.RESULT_OK == resultCode) {
                    onRemoveAccount();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    private void onRemoveAccount() {
        StateOfAccountChangeProcess state = ((AccountSettingsActivity) getActivity()).getState();
        if (state.builder != null && state.builder.isPersistent()) {
            for (android.accounts.Account account : AccountUtils.getCurrentAccounts(getActivity())) {
                if (state.getAccount().getAccountName().equals(account.name)) {
                    MyLog.i(this, "Removing account: " + account.name);
                    android.accounts.AccountManager am = AccountManager.get(getActivity());
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            Bundle result = am.removeAccount(account, getActivity(), null, null)
                                .getResult(10, TimeUnit.SECONDS);
                            return result != null && result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT);
                        } catch (Exception e) {
                            MyLog.w(this, "Failed to remove account " + account.name, e);
                            return false;
                        }
                    }, NonUiThreadExecutor.INSTANCE)
                    .thenAcceptAsync(ok -> {
                        if (ok) {
                            getActivity().finish();
                        }
                    }, UiThreadExecutor.INSTANCE);
                    break;
                }
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.account_settings, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        final AccountSettingsActivity activity = (AccountSettingsActivity) getActivity();
        if (activity != null) {
            activity.updateScreen();
            switch (AccountSettingsActivity.FragmentAction.fromBundle(getArguments())) {
                case ON_ORIGIN_SELECTED:
                    activity.goToAddAccount();
                    break;
                default:
                    break;
            }
        }
        super.onActivityCreated(savedInstanceState);
    }

    
}
