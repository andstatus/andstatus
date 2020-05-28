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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.context.MyLocale;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.MySettingsGroup;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.os.UiThreadExecutor;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/** Activity to be started, when Application is not initialised yet (or needs re-initialization).
 * It allows to avoid "Application not responding" errors.
 * It is transparent and shows progress indicator only, launches next activity after application initialization.
 * */
public class FirstActivity extends AppCompatActivity implements IdentifiableInstance {
    private static final String TAG = FirstActivity.class.getSimpleName();
    private static final String SET_DEFAULT_VALUES = "setDefaultValues";
    private static final AtomicReference<TriState> resultOfSettingDefaults = new AtomicReference<>(TriState.UNKNOWN);
    public static AtomicBoolean isFirstrun = new AtomicBoolean(true);
    private final long instanceId = InstanceId.next();

    /**
     * Based on http://stackoverflow.com/questions/14001963/finish-all-activities-at-a-time
     */
    public static void closeAllActivities(Context context) {
        context.startActivity(MyAction.CLOSE_ALL_ACTIVITIES.getIntent());
    }

    public enum NeedToStart {
        HELP,
        CHANGELOG,
        OTHER
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            if (isFirstrun.compareAndSet(true, false)) {
                MyLocale.onAttachBaseContext(this);
            }
            setContentView(R.layout.loading);
        } catch (Throwable e) {
            MyLog.w(this, "Couldn't setContentView", e);
        }
        parseNewIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        parseNewIntent(intent);
    }

    private void parseNewIntent(Intent intent) {
        switch (MyAction.fromIntent(intent)) {
            case INITIALIZE_APP:
                myContextHolder.initialize(this)
                .whenSuccessAsync(myContext -> finish(), UiThreadExecutor.INSTANCE);
                break;
            case SET_DEFAULT_VALUES:
                setDefaultValuesOnUiThread(this);
                finish();
                break;
            case CLOSE_ALL_ACTIVITIES:
                finish();
                break;
            default:
                if (myContextHolder.getFuture().isReady() || myContextHolder.getNow().state() == MyContextState.UPGRADING) {
                    startNextActivitySync(myContextHolder.getNow(), intent);
                    finish();
                } else {
                    myContextHolder.initialize(this)
                    .with(future -> future.whenCompleteAsync(startNextActivity, UiThreadExecutor.INSTANCE));
                }
        }
    }

    private BiConsumer<MyContext, Throwable> startNextActivity = (myContext, throwable) -> {
        boolean launched = false;
        if (myContext != null && myContext.isReady() && !myContext.isExpired()) {
            try {
                startNextActivitySync(myContext, getIntent());
                launched = true;
            } catch (android.util.AndroidRuntimeException e) {
                MyLog.w(TAG, "Launching next activity from firstActivity", e);
            } catch (java.lang.SecurityException e) {
                MyLog.d(TAG, "Launching activity", e);
            }
        }
        if (!launched) {
            HelpActivity.startMe(
                    myContext == null ? myContextHolder.getNow().context() : myContext.context(),
                    true, HelpActivity.PAGE_LOGO);
        }
        this.finish();
    };

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
                    if (!StringUtil.isEmpty(action)) {
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

    public static void goHome(Activity activity) {
        try {
            MyLog.v(TAG, () -> "goHome from " + MyStringBuilder.objToTag(activity));
            startApp(activity);
        } catch (Exception e) {
            MyLog.v(TAG, "goHome", e);
            myContextHolder.thenStartApp();
        }
    }

    public static void startApp(MyContext myContext) {
        Context context = myContext.context();
        startApp(context);
    }

    private static void startApp(Context context) {
        Intent intent = new Intent(context, FirstActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
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
                MyLog.v(TAG, () -> "Last opened version=" + versionCodeLast
                        + ", current is " + versionCode
                        + (update ? ", updating" : "")
                );
                changed = true;
                if ( update && myContextHolder.getNow().isReady()) {
                    SharedPreferencesUtil.putLong(MyPreferences.KEY_VERSION_CODE_LAST, versionCode);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(TAG, "Unable to obtain package information", e);
        }
        return changed;
    }

    public static void startMeAsync(Context context, MyAction myAction) {
        Intent intent = new Intent(context, FirstActivity.class);
        intent.setAction(myAction.getAction());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /** @return success */
    public static boolean setDefaultValues(Context context) {
        if (context == null) {
            MyLog.e(TAG, SET_DEFAULT_VALUES + " no context");
            return false;
        }
        synchronized (resultOfSettingDefaults) {
            resultOfSettingDefaults.set(TriState.UNKNOWN);
            try {
                if (context instanceof Activity) {
                    final Activity activity = (Activity) context;
                    activity.runOnUiThread( () -> setDefaultValuesOnUiThread(activity));
                } else {
                    startMeAsync(context, MyAction.SET_DEFAULT_VALUES);
                }
                for (int i = 0; i < 100; i++) {
                    DbUtils.waitMs(TAG, 50);
                    if (resultOfSettingDefaults.get().known) break;
                }
            } catch (Exception e) {
                MyLog.e(TAG, SET_DEFAULT_VALUES + " error:" + e.getMessage() +
                        "\n" + MyLog.getStackTrace(e));
            }
        }
        return resultOfSettingDefaults.get().toBoolean(false);
    }

    private static void setDefaultValuesOnUiThread(Activity activity) {
        try {
            MyLog.i(activity, SET_DEFAULT_VALUES + " started");
            MySettingsGroup.setDefaultValues(activity);
            resultOfSettingDefaults.set(TriState.TRUE);
            MyLog.i(activity, SET_DEFAULT_VALUES + " completed");
            return;
        } catch (Exception e ) {
            MyLog.w(activity, SET_DEFAULT_VALUES + " error:" + e.getMessage() +
                    "\n" + MyLog.getStackTrace(e));
        }
        resultOfSettingDefaults.set(TriState.FALSE);
    }
}
