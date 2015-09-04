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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.example.android.supportv7.app.AppCompatPreferenceActivity;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import java.util.List;

/** See http://developer.android.com/guide/topics/ui/settings.html
 *  Source of the {@link AppCompatPreferenceActivity} class is here:
 *  https://github.com/android/platform_development/blob/master/samples/Support7Demos/src/com/example/android/supportv7/app/AppCompatPreferenceActivity.java
 * */
public class MySettingsActivity extends AppCompatPreferenceActivity {

    public static final String ANDROID_FRAGMENT_ARGUMENTS_KEY = ":android:show_fragment_args";
    public static final String PREFERENCES_GROUPS_KEY = "preferencesGroup";
    public static final String ANDROID_NO_HEADERS_KEY = ":android:no_headers";
    private boolean startTimelineActivity = false;
    private long mPreferencesChangedAt = MyPreferences.getPreferencesChangeTime();
    private long mInstanceId = 0;

    public static void closeAllActivities(Context context) {
        Intent intent = new Intent(context.getApplicationContext(), MySettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(IntentExtra.FINISH.key, true);
        context.startActivity(intent);
    }

    protected void onCreate(Bundle savedInstanceState) {
        MyTheme.loadTheme(this);
        super.onCreate(savedInstanceState);
        if (isRootScreen()) {
            MyContextHolder.initialize(this, this);
        }
        this.getSupportActionBar().setTitle(getTitleResId());

        boolean isNew = mInstanceId == 0;
        if (isNew) {
            mInstanceId = InstanceId.next();
        }
        logEvent("onCreate", isNew ? "" : "Reuse the same");
        parseNewIntent(getIntent());
    }

    private boolean isRootScreen() {
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            return !bundle.getBoolean(ANDROID_NO_HEADERS_KEY);
        }
        return true;
    }

    private int getTitleResId() {
        int titleResId = R.string.settings_activity_title;
        if (!isRootScreen()) {
            Bundle bundle = getIntent().getBundleExtra(ANDROID_FRAGMENT_ARGUMENTS_KEY);
            if (bundle != null) {
                MyPreferencesGroupsEnum preferencesGroup = MyPreferencesGroupsEnum.load(
                        bundle.getString(PREFERENCES_GROUPS_KEY));
                if (preferencesGroup != MyPreferencesGroupsEnum.UNKNOWN) {
                    titleResId = preferencesGroup.getTitleResId();
                }
            }
        }
        return titleResId;
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.preference_headers, target);
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return MySettingsFragment.class.getName().equals(fragmentName);
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
        if (mPreferencesChangedAt < MyPreferences.getPreferencesChangeTime() || !MyContextHolder.get().initialized()) {
            logEvent("onResume", "Recreating");
            restartMe(this);
            return;
        }
        if (isRootScreen()) {
            MyContextHolder.get().setInForeground(true);
            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        logEvent("onPause", "");
        if (isRootScreen()) {
            MyContextHolder.get().setInForeground(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (isRootScreen()) {
                    closeAndGoBack();
                } else {
                    finish();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void closeAndGoBack() {
        logEvent("closeAndGoBack", "");
        startTimelineActivity = true;
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isRootScreen()
                && keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0
                && !MyContextHolder.get().persistentAccounts().isEmpty()) {
            closeAndGoBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * See http://stackoverflow.com/questions/1397361/how-do-i-restart-an-android-activity
     */
    public static void restartMe(Activity activity) {
        Intent intent = activity.getIntent();
        activity.finish();
        activity.startActivity(intent);
    }

    @Override
    public void finish() {
        logEvent("finish", startTimelineActivity ? " and return" : "");
        super.finish();
        if (startTimelineActivity) {
            MyContextHolder.release();
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            Intent i = new Intent(this, TimelineActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
    }

    private void logEvent(String method, String msgLog_in) {
        if (MyLog.isVerboseEnabled()) {
            String msgLog = msgLog_in
                    + " instanceId:" + mInstanceId
                    + ( isRootScreen() ? "; rootScreen" : "");
            Bundle bundle = getIntent().getBundleExtra(ANDROID_FRAGMENT_ARGUMENTS_KEY);
            if (bundle != null) {
                msgLog += "; preferenceGroup:" + bundle.getString(PREFERENCES_GROUPS_KEY);
            }
            MyLog.v(this, method + "; " + msgLog);
        }
    }
}
