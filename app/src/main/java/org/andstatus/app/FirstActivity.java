/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

/** Activity to be started, when Application is not initialised yet (or needs re-initialization).
 * It allows to avoid "Application not responding" errors.
 * It is transparent and shows progress indicator only, launches next activity after application initialization.
 * */
public class FirstActivity extends AppCompatActivity {

    public enum NeedToStart {
        HELP,
        CHANGELOG,
        OTHER
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading);
        startNextActivity(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        startNextActivity(intent);
    }

    private void startNextActivity(Intent intent) {
        if (MyContextHolder.get().isReady() || MyContextHolder.get().state() == MyContextState.UPGRADING) {
            startNextActivitySync(MyContextHolder.get(), intent);
            finish();
        } else {
            MyContextHolder.initializeByFirstActivity(this);
        }
    }

    public void startNextActivitySync(MyContext myContext) {
        startNextActivitySync(myContext, getIntent());
    }

    private void startNextActivitySync(MyContext myContext, Intent myIntent) {
        switch (needToStartNext(this, myContext)) {
            case HELP:
                HelpActivity.startMe(this, true, HelpActivity.PAGE_LOGO);
                break;
            case CHANGELOG:
                HelpActivity.startMe(this, true, HelpActivity.PAGE_CHANGELOG);
                break;
            default:
                Intent intent = new Intent(this, TimelineActivity.class);
                if (myIntent != null) {
                    String action = myIntent.getAction();
                    if (!StringUtils.isEmpty(action)) {
                        intent.setAction(action);
                    }
                    Uri data = myIntent.getData();
                    if (data != null) {
                        intent.setData(data);
                    }
                    Bundle extras = myIntent.getExtras();
                    if (extras != null) {
                        intent.putExtras(extras);
                    }
                }
                startActivity(intent);
                break;
        }
    }

    public static NeedToStart needToStartNext(Context context, MyContext myContext) {
        if (!myContext.isReady()) {
            MyLog.i(context, "Context is not ready: " + myContext.toString());
            return NeedToStart.HELP;
        } else if (myContext.accounts().isEmpty()) {
            MyLog.i(context, "No AndStatus Accounts yet");
            return NeedToStart.HELP;
        }
        if (myContext.isReady() && checkAndUpdateLastOpenedAppVersion(context, false)) {
            return NeedToStart.CHANGELOG;
        }
        return NeedToStart.OTHER;
    }


    /**
     * @return true if we opened previous version
     */
    public static boolean checkAndUpdateLastOpenedAppVersion(Context context, boolean update) {
        boolean changed = false;
        long versionCodeLast =  SharedPreferencesUtil.getLong(MyPreferences.KEY_VERSION_CODE_LAST);
        PackageManager pm = context.getPackageManager();
        PackageInfo pi;
        try {
            pi = pm.getPackageInfo(context.getPackageName(), 0);
            int versionCode =  pi.versionCode;
            if (versionCodeLast < versionCode) {
                // Even if the Actor will see only the first page of the Help activity,
                // count this as showing the Change Log
                MyLog.v(FirstActivity.class, () -> "Last opened version=" + versionCodeLast
                        + ", current is " + versionCode
                        + (update ? ", updating" : "")
                );
                changed = true;
                if ( update && MyContextHolder.get().isReady()) {
                    SharedPreferencesUtil.putLong(MyPreferences.KEY_VERSION_CODE_LAST, versionCode);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(FirstActivity.class, "Unable to obtain package information", e);
        }
        return changed;
    }
}
