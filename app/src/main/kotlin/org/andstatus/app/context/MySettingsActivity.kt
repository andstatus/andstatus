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
package org.andstatus.app.context

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import org.andstatus.app.FirstActivity
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog

/** See [Settings](http://developer.android.com/guide/topics/ui/settings.html)
 */
class MySettingsActivity : MyActivity(MySettingsActivity::class),
    PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val mPreferencesChangedAt = MyPreferences.getPreferencesChangeTime()
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        resumedOnce = false
        mLayoutId = R.layout.my_settings
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val ft = supportFragmentManager.beginTransaction()
            ft.replace(R.id.settings_container, myFragment(), MySettingsFragment.FRAGMENT_TAG)
            ft.commit()
        }
        parseNewIntent(intent)
    }

    /** Create the fragment only when the activity is created for the first time.
     * ie. not after orientation changes */
    private fun myFragment(): PreferenceFragmentCompat =
        (supportFragmentManager.findFragmentByTag(MySettingsFragment.FRAGMENT_TAG) as PreferenceFragmentCompat?)
            ?: MySettingsFragment()

    // TODO: Not fully implemented, but it is unused yet...
    override fun onPreferenceStartScreen(
        preferenceFragmentCompat: PreferenceFragmentCompat,
        preferenceScreen: PreferenceScreen
    ): Boolean {
        val ft = supportFragmentManager.beginTransaction()
        val fragment = MySettingsFragment()
        val args = Bundle()
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey())
        fragment.arguments = args
        ft.replace(R.id.settings_container, fragment, preferenceScreen.getKey())
        ft.addToBackStack(preferenceScreen.getKey())
        ft.commit()
        return true
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.getExtras()
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey())
        val fragment = MySettingsFragment()
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)
        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment, pref.getKey())
            .addToBackStack(pref.getKey())
            .commit()
        return true
    }

    private fun isRootScreen(): Boolean {
        return getSettingsGroup() == MySettingsGroup.UNKNOWN
    }

    private fun getSettingsGroup(): MySettingsGroup {
        val fragment = supportFragmentManager.findFragmentById(R.id.settings_container)
        return MySettingsGroup.from(fragment)
    }

    override fun onNewIntent(intent: Intent) {
        logEvent("onNewIntent", "")
        super.onNewIntent(intent)
        parseNewIntent(intent)
    }

    private fun parseNewIntent(intent: Intent) {
        if (intent.getBooleanExtra(IntentExtra.FINISH.key, false)) {
            logEvent("parseNewIntent", "finish requested")
            finish()
        } else {
            val settingsGroup: MySettingsGroup = MySettingsGroup.fromIntent(intent)
            if (settingsGroup != MySettingsGroup.UNKNOWN) {
                if (!myContextHolder.needToRestartActivity()) {
                    intent.removeExtra(IntentExtra.SETTINGS_GROUP.key)
                    setIntent(intent)
                }
                val preference = Preference(this)
                preference.key = settingsGroup.key
                onPreferenceStartFragment(myFragment(), preference)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mPreferencesChangedAt < MyPreferences.getPreferencesChangeTime() || myContextHolder.needToRestartActivity()) {
            if (initializeThenRestartActivity()) {
                logEvent("onResume", "Recreating")
                return
            }
        }
        if (isRootScreen()) {
            myContextHolder.getNow().isInForeground = true
            MyServiceManager.setServiceUnavailable()
            MyServiceManager.stopService()
        }
        resumedOnce = true
    }

    override fun onPause() {
        super.onPause()
        logEvent("onPause", "")
        if (isRootScreen()) {
            myContextHolder.getNow().isInForeground = false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.getItemId()) {
            android.R.id.home -> {
                when {
                    isRootScreen() -> closeAndRestartApp()
                    supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                    else -> finish()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun closeAndRestartApp() {
        if (resumedOnce) {
            if (onFinishAction.compareAndSet(OnFinishAction.NONE, OnFinishAction.RESTART_APP)) {
                finish()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isRootScreen() && keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            closeAndRestartApp()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun logEvent(method: String?, msgLog_in: String?) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; " + (msgLog_in + "; settingsGroup:" + getSettingsGroup()))
        }
    }

    companion object {
        fun goToMySettingsAccounts(activity: Activity) {
            FirstActivity.restartApp(activity, "goToMySettingsAccounts")
                .thenStartActivity("openSettings", Intent(activity, MySettingsActivity::class.java))
                .thenStartActivity(
                    "startSettingsAccounts",
                    MySettingsGroup.ACCOUNTS.addTo(
                        Intent(
                            activity.applicationContext,
                            MySettingsActivity::class.java
                        )
                    )
                )
        }
    }
}
