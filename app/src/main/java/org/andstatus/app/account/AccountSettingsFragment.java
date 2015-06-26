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
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.ActivityRequestCode;
import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;

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
        android.accounts.AccountManager am = AccountManager.get(getActivity());
        StateOfAccountChangeProcess state = ((AccountSettingsActivity) getActivity()).getState();
        if (state.builder.isPersistent()) {
            android.accounts.Account[] aa = am
                    .getAccountsByType(AuthenticatorService.ANDROID_ACCOUNT_TYPE);
            for (android.accounts.Account account : aa) {
                if (state.getAccount().getAccountName().equals(account.name)) {
                    MyLog.i(this, "Removing account: " + account.name);
                    am.removeAccount(account, null, null);
                }
            }
            getActivity().finish();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.account_settings, container, false);
        MyPreferences.setTrueBlack(view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        ((AccountSettingsActivity) getActivity()).restoreState(getActivity().getIntent(), "onCreateView");
        super.onActivityCreated(savedInstanceState);
    }

    
}
