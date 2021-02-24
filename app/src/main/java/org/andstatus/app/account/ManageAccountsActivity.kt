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
package org.andstatus.app.account

import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import org.andstatus.app.FirstActivity
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MySettingsActivity

/**
 * @author yvolk@yurivolkov.com
 */
class ManageAccountsActivity : MyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        mLayoutId = R.layout.account_settings_main
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            showFragment(AccountListFragment::class.java, Bundle())
        }
    }

    override fun onResume() {
        super.onResume()
        if ( MyContextHolder.myContextHolder.needToRestartActivity()) {
            FirstActivity.Companion.closeAllActivities(this)
             MyContextHolder.myContextHolder.initialize(this).thenStartActivity(intent)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        return when (item.getItemId()) {
            android.R.id.home -> {
                MySettingsActivity.Companion.goToMySettingsAccounts(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            MySettingsActivity.Companion.goToMySettingsAccounts(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}