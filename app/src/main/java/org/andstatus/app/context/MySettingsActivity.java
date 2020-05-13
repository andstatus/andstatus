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

package org.andstatus.app.context;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.MyActivity;
import org.andstatus.app.R;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/** See <a href="http://developer.android.com/guide/topics/ui/settings.html">Settings</a>
 */
public class MySettingsActivity extends MyActivity implements
        PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private long mPreferencesChangedAt = MyPreferences.getPreferencesChangeTime();
    private boolean resumedOnce = false;

    /**
     * Based on http://stackoverflow.com/questions/14001963/finish-all-activities-at-a-time
     */
    public static void closeAllActivities(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), MySettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(IntentExtra.FINISH.key, true);
        context.startActivity(intent);
    }

    protected void onCreate(Bundle savedInstanceState) {
        resumedOnce = false;
        mLayoutId = R.layout.my_settings;
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            // Create the fragment only when the activity is created for the first time.
            // ie. not after orientation changes
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(MySettingsFragment.FRAGMENT_TAG);
            if (fragment == null) {
                fragment = new MySettingsFragment();
            }

            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.settings_container, fragment, MySettingsFragment.FRAGMENT_TAG);
            ft.commit();
        }
        parseNewIntent(getIntent());
    }


    // TODO: Not fully implemented, but it is unused yet...
    @Override
    public boolean onPreferenceStartScreen(PreferenceFragmentCompat preferenceFragmentCompat,
                                           PreferenceScreen preferenceScreen) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        MySettingsFragment fragment = new MySettingsFragment();
        Bundle args = new Bundle();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.getKey());
        fragment.setArguments(args);
        ft.replace(R.id.settings_container, fragment, preferenceScreen.getKey());
        ft.addToBackStack(preferenceScreen.getKey());
        ft.commit();
        return true;
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        final Bundle args = pref.getExtras();
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, pref.getKey());
        MySettingsFragment fragment = new MySettingsFragment();
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, fragment, pref.getKey())
                .addToBackStack(pref.getKey())
                .commit();
        return true;
    }

    private boolean isRootScreen() {
        return getSettingsGroup() == MySettingsGroup.UNKNOWN;
    }

    private MySettingsGroup getSettingsGroup() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.settings_container);
        return MySettingsGroup.from(fragment);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        logEvent("onNewIntent", "");
        super.onNewIntent(intent);
        parseNewIntent(intent);
    }

    private void parseNewIntent(Intent intent) {
        if (intent.getBooleanExtra(IntentExtra.FINISH.key, false)) {
            logEvent("parseNewIntent", "finish requested");
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPreferencesChangedAt < MyPreferences.getPreferencesChangeTime() || !myContextHolder.getFuture().isReady()) {
            logEvent("onResume", "Recreating");
            myContextHolder.ifNeededInitializeThenRestartMe(this);
            return;
        }
        if (isRootScreen()) {
            myContextHolder.getNow().setInForeground(true);
            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();
        }
        resumedOnce = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        logEvent("onPause", "");
        if (isRootScreen()) {
            myContextHolder.getNow().setInForeground(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (isRootScreen()) {
                    closeAndRestartApp();
                } else if(getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void closeAndRestartApp() {
        if (resumedOnce) {
            logEvent("closeAndRestartApp", "");
            myContextHolder.setExpired(false).thenStartApp();
        }
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isRootScreen()
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0) {
            closeAndRestartApp();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        super.finish();
        logEvent("finish", "");
    }

    private void logEvent(String method, String msgLog_in) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; " + (msgLog_in + "; settingsGroup:" + getSettingsGroup()));
        }
    }
}
