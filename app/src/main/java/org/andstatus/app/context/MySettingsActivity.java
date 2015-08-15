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

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;

import com.example.android.supportv7.app.AppCompatPreferenceActivity;
import org.andstatus.app.R;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.service.MyServiceManager;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyTheme.loadTheme(this);
        super.onCreate(savedInstanceState);
        if (isRootScreen()) {
            MyContextHolder.initialize(this, this);
        }
        this.getSupportActionBar().setTitle(getTitleResId());
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
    protected void onResume() {
        super.onResume();
        if (mPreferencesChangedAt < MyPreferences.getPreferencesChangeTime()) {
            recreate();
            return;
        }
        if (isRootScreen()) {
            MyContextHolder.initialize(this, this);
            MyContextHolder.get().setInForeground(true);

            MyServiceManager.setServiceUnavailable();
            MyServiceManager.stopService();
        }
    }

    @Override
    protected boolean isValidFragment(String fragmentName) {
        return MySettingsFragment.class.getName().equals(fragmentName);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isRootScreen()) {
            if (startTimelineActivity) {
                MyContextHolder.release();
                // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
                Intent i = new Intent(this, TimelineActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            }
            MyContextHolder.get().setInForeground(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (isRootScreen()) {
                    closeAndGoBack();
                    return true;
                } else {
                    finish();
                    return true;
                }
            default:
                return super.onOptionsItemSelected(item);
        }
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

    private void closeAndGoBack() {
        MyLog.v(this, "Going back to a Timeline");
        finish();
        startTimelineActivity = true;
    }
}
