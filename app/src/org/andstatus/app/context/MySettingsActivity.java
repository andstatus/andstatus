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
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;

import org.andstatus.app.TimelineActivity;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/** See http://developer.android.com/guide/topics/ui/settings.html */
public class MySettingsActivity extends Activity {

    private boolean startTimelineActivity = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MyPreferences.loadTheme(this);
        super.onCreate(savedInstanceState);

        MyContextHolder.initialize(this, this);
        
        getFragmentManager().beginTransaction()
        .replace(android.R.id.content, new MySettingsFragment(), MySettingsFragment.class.getSimpleName())
        .commit();
    }

    @Override
    protected void onResume() {
        super.onResume();

        MyContextHolder.initialize(this, this);
        MyContextHolder.get().setInForeground(true);
        
        MyServiceManager.setServiceUnavailable();
        MyServiceManager.stopService();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (startTimelineActivity) {
            MyContextHolder.release();
            // On modifying activity back stack see http://stackoverflow.com/questions/11366700/modification-of-the-back-stack-in-android
            Intent i = new Intent(this, TimelineActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        MyContextHolder.get().setInForeground(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                closeAndGoBack();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK
                && event.getRepeatCount() == 0
                && !MyContextHolder.get().persistentAccounts().isEmpty()) {
            closeAndGoBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closeAndGoBack() {
        MyLog.v(this, "Going back to the Timeline");
        finish();
        startTimelineActivity = true;
    }
}
