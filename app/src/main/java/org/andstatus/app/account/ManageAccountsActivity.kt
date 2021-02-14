/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.context.MySettingsActivity;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public class ManageAccountsActivity extends MyActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.account_settings_main;
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            showFragment(AccountListFragment.class, new Bundle());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myContextHolder.needToRestartActivity()) {
            FirstActivity.closeAllActivities(this);
            myContextHolder.initialize(this).thenStartActivity(getIntent());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                MySettingsActivity.goToMySettingsAccounts(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            MySettingsActivity.goToMySettingsAccounts(this);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
