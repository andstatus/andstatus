/*
 * Copyright (C) 2013 - 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.database.DatabaseConverterController;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TamperingDetector;

/**
 * Holds globally cached state of the application: {@link MyContext}  
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public final class MyContextHolder {
    private static final String TAG = MyContextHolder.class.getSimpleName();
    private final static long appStartedAt = SystemClock.elapsedRealtime();
    private static volatile boolean isShuttingDown = false;

    private static final Object CONTEXT_LOCK = new Object();
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyContext contextCreator = MyContextImpl.newEmpty("static");
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyFutureContext myFutureContext = new MyEmptyFutureContext(contextCreator);
    private static volatile boolean onRestore = false;
    @NonNull
    private static volatile ExecutionMode executionMode = ExecutionMode.UNKNOWN;

    private MyContextHolder() {
    }
    
    /**
     * Immediately get currently available context, even if it's empty
     */
    @NonNull
    public static MyContext get() {
        return myFutureContext.getNow();
    }
    
    /**
     * This is mainly for mocking / testing
     * @return previous MyContext
     */
    public static MyContext replaceCreator(@NonNull MyContext contextCreatorNew) {
        synchronized (CONTEXT_LOCK) {
            MyContext myContextOld = get();
            release();
            contextCreator = contextCreatorNew;
            myFutureContext = new MyEmptyFutureContext(contextCreator);
            return myContextOld;
        }
    }

    /**
     * Initialize asynchronously
     * @return true if the Activity is being restarted
     */
    public static boolean initializeThenRestartMe(@NonNull Activity activity) {
        MyFutureContext myFutureContext = getMyFutureContext(activity, activity, false);
        if (myFutureContext.needsBackgroundWork() || !get().isReady()) {
            activity.finish();
            myFutureContext.thenStartActivity(activity);
            return true;
        }
        return false;
    }

    public static void initializeByFirstActivity(@NonNull FirstActivity firstActivity) {
        MyFutureContext myFutureContext = getMyFutureContext(firstActivity, firstActivity, false);
        myFutureContext.thenStartNextActivity(firstActivity);
    }

    /**
     * Reinitializes in a case preferences have been changed
     * Blocks on initialization
     */
    public static MyContext initialize(Context context, Object calledBy) {
        return getMyFutureContext(context, calledBy, false).getBlocking();
    }

    public static MyFutureContext getMyFutureContext(Context context) {
        return getMyFutureContext(context, context);
    }

    public static MyFutureContext getMyFutureContext(Context context, Object calledBy) {
        return getMyFutureContext(context, calledBy, false);
    }

    public static MyFutureContext getMyFutureContext(Context context, Object calledBy, boolean duringUpgrade) {
        storeContextIfNotPresent(context, calledBy);
        if (isShuttingDown) {
            MyLog.d(TAG, "Skipping initialization: device is shutting down (called by: " + calledBy + ")");
            return new MyEmptyFutureContext(contextCreator);
        }
        if (!duringUpgrade && DatabaseConverterController.isUpgrading()) {
            MyLog.d(TAG, "Skipping initialization: upgrade in progress (called by: " + calledBy + ")");
            return new MyEmptyFutureContext(contextCreator);
        }
        if (needToInitialize()) {
            MyLog.v(TAG, "myFutureContext " + (myFutureContext.isEmpty() ? "isEmpty " : "") + get());
            boolean launchExecution = false;
            synchronized(CONTEXT_LOCK) {
                if (needToInitialize()) {
                    myFutureContext = new MyFutureContext(contextCreator, myFutureContext.getNow(), calledBy);
                    launchExecution = true;
                }
            }
            if (launchExecution) {
                MyLog.v(TAG, "myFutureContext launch " + (myFutureContext.isEmpty() ? "isEmpty " : "") + get());
                myFutureContext.executeOnNonUiThread();
            }
        }
        return myFutureContext;
    }

    private static boolean needToInitialize() {
        return myFutureContext.isEmpty() || (myFutureContext.completedBackgroundWork() && get().isExpired());
    }

    public static void setExpiredIfConfigChanged() {
        if (get().initialized() && isConfigChanged()) {
            synchronized(CONTEXT_LOCK) {
                if (get().initialized() && isConfigChanged()) {
                    long preferencesChangeTimeLast = MyPreferences.getPreferencesChangeTime() ;
                    if (get().preferencesChangeTime() != preferencesChangeTimeLast) {
                        MyLog.v(TAG, "Preferences changed "
                                + RelativeTime.secondsAgo(preferencesChangeTimeLast)
                                + " seconds ago, refreshing...");
                        get().setExpired();
                    }
                }
            }
        }
    }

    public static boolean isConfigChanged() {
        return get().preferencesChangeTime() != MyPreferences.getPreferencesChangeTime();
    }

    /**
     *  Quickly return, providing context for the deferred initialization
     */
    public static void storeContextIfNotPresent(Context context, Object calledBy) {
        if (contextCreator.context() == null) {
            synchronized(CONTEXT_LOCK) {
                if (contextCreator.context() == null) {
                    String callerName = MyLog.objToTag(calledBy) ;
                    if (context == null) {
                        throw new IllegalStateException(TAG + ": context is unknown yet, called by " + callerName);
                    }
                    // This allows to refer to the context
                    // even before myInitializedContext is initialized
                    contextCreator = contextCreator.newCreator(context, callerName);
                    if (contextCreator.context() == null) {
                        throw new IllegalStateException(TAG + ": no compatible context, called by " + callerName);
                    }
                    myFutureContext = new MyEmptyFutureContext(contextCreator);
                }
            }
        }
    }
    
    public static void release() {
        if (!get().isExpired()) {
            synchronized(CONTEXT_LOCK) {
                get().setExpired();
            }
        }
    }

    public static void upgradeIfNeeded(Activity upgradeRequestor) {
        if (get().state() == MyContextState.UPGRADING) {
            DatabaseConverterController.attemptToTriggerDatabaseUpgrade(upgradeRequestor);
        }
    }

    public static String getSystemInfo(Context context, boolean showVersion) {
        StringBuilder builder = new StringBuilder();
        if (showVersion) builder.append(getVersionText(context));
        I18n.appendWithSpace(builder, "started "
                + RelativeTime.getDifference(context, appStartedAt, SystemClock.elapsedRealtime()));
        builder.append("\n");
        builder.append(ImageCaches.getCacheInfo());
        builder.append("\n");
        builder.append(AsyncTaskLauncher.threadPoolInfo());
        return builder.toString();
    }

    public static String getVersionText(Context context) {
        StringBuilder builder = new StringBuilder();
        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                builder.append(pi.packageName + " v." + pi.versionName + " (" + pi.versionCode + ")");
            } catch (PackageManager.NameNotFoundException e) {
                MyLog.e(TAG, "Unable to obtain package information", e);
            }
        }
        if (builder.length() == 0) {
            builder.append("AndStatus v.?");
        }
        I18n.appendWithSpace(builder, (getExecutionMode() == ExecutionMode.DEVICE ? "" : getExecutionMode().code));
        I18n.appendWithSpace(builder, TamperingDetector.getAppSignatureInfo());
        return builder.toString();
    }

    public static void setOnRestore(boolean onRestore) {
        MyContextHolder.onRestore = onRestore;
    }

    public static boolean isOnRestore() {
        return onRestore;
    }

    public static void setExecutionMode(@NonNull ExecutionMode executionMode) {
        if (MyContextHolder.executionMode != executionMode) {
            MyContextHolder.executionMode = executionMode;
            if (executionMode != ExecutionMode.DEVICE) {
                MyLog.i(TAG, "Executing: " + getVersionText(get().context()));
            }
        }
    }

    @NonNull
    public static ExecutionMode getExecutionMode() {
        if (executionMode == ExecutionMode.UNKNOWN) {
            setExecutionMode(calculateExecutionMode());
        }
        return executionMode;
    }

    @NonNull
    private static ExecutionMode calculateExecutionMode() {
        Context context = get().context();
        if (context == null) {
            return ExecutionMode.UNKNOWN;
        }
        if ("true".equals(Settings.System.getString(context.getContentResolver(), "firebase.test.lab"))) {
            // See https://firebase.google.com/docs/test-lab/android-studio
            return get().isTestRun() ? ExecutionMode.FIREBASE_TEST : ExecutionMode.ROBO_TEST;
        }
        if (get().isTestRun()) {
            return ExecutionMode.TEST;
        }
        return ExecutionMode.DEVICE;
    }

    static boolean isScreenSupported() {
        return getExecutionMode() != ExecutionMode.TRAVIS_TEST;
    }

    public static void onShutDown() {
        isShuttingDown = true;
        get().setExpired();
    }

    public static boolean isShuttingDown() {
        return isShuttingDown;
    }
}
