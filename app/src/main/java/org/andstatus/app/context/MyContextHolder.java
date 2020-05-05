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

import androidx.annotation.NonNull;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.data.converter.DatabaseConverterController;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.net.http.TlsSniSocketFactory;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.syncadapter.SyncInitiator;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TamperingDetector;

import java.util.function.Supplier;

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
    @NonNull
    private static volatile MyFutureContext myFutureContext = MyEmptyFutureContext.EMPTY;
    private static volatile boolean onRestore = false;
    @NonNull
    private static volatile ExecutionMode executionMode = ExecutionMode.UNKNOWN;

    private MyContextHolder() {
    }

    /** Immediately get currently available context, even if it's empty */
    @NonNull
    public static MyContext get(Context context) {
        storeContextIfNotPresent(context, context);
        return get();
    }

    /** Immediately get currently available context, even if it's empty */
    @NonNull
    public static MyContext get() {
        return myFutureContext.getNow();
    }
    
    /** This is mainly for mocking / testing
     * @return true if succeeded */
    static boolean trySetCreator(@NonNull MyContext contextCreatorNew) {
        synchronized (CONTEXT_LOCK) {
            if (myFutureContext.needsBackgroundWork()) return false;

            myFutureContext.getNow().release(() -> "trySetCreator");
            myFutureContext = new MyEmptyFutureContext(contextCreatorNew);
        }
        return true;
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
            return new MyEmptyFutureContext(myFutureContext.getNow());
        }
        if (!duringUpgrade && DatabaseConverterController.isUpgrading()) {
            MyLog.d(TAG, "Skipping initialization: upgrade in progress (called by: " + calledBy + ")");
            return new MyEmptyFutureContext(myFutureContext.getNow());
        }
        if (needToInitialize()) {
            MyLog.v(TAG, () -> "myFutureContext " + (myFutureContext.isEmpty() ? "isEmpty " : "") + get());
            boolean launchExecution = false;
            synchronized(CONTEXT_LOCK) {
                if (needToInitialize()) {
                    myFutureContext = new MyFutureContext(myFutureContext.getNow());
                    launchExecution = true;
                }
            }
            if (launchExecution) {
                MyLog.v(TAG, () -> "myFutureContext launch " + (myFutureContext.isEmpty() ? "isEmpty " : "") + get());
                myFutureContext.executeOnNonUiThread(calledBy);
            }
        }
        return myFutureContext;
    }

    private static boolean needToInitialize() {
        return myFutureContext.isEmpty() || (myFutureContext.completedBackgroundWork() && get().isExpired());
    }

    public static void setExpiredIfConfigChanged() {
        if (get().isConfigChanged()) {
            synchronized(CONTEXT_LOCK) {
                final MyContext myContext = get();
                if (myContext.isConfigChanged()) {
                    long preferencesChangeTimeLast = MyPreferences.getPreferencesChangeTime() ;
                    if (myContext.preferencesChangeTime() != preferencesChangeTimeLast) {
                        myContext.setExpired(() -> "Preferences changed "
                                + RelativeTime.secondsAgo(preferencesChangeTimeLast)
                                + " seconds ago, refreshing...");
                    }
                }
            }
        }
    }

    /**
     * This allows to refer to the context even before myInitializedContext is initialized.
     * Quickly returns, providing context for the deferred initialization
     */
    public static void storeContextIfNotPresent(Context context, Object calledBy) {
        if (context == null || myFutureContext.getNow().context() != null) return;

        synchronized(CONTEXT_LOCK) {
            if (myFutureContext.getNow().context() == null) {
                MyContext contextCreator = myFutureContext.getNow().newCreator(context, calledBy);
                requireNonNullContext(contextCreator.context(), calledBy, "no compatible context");
                myFutureContext = new MyEmptyFutureContext(contextCreator);
            }
        }
    }

    private static void requireNonNullContext(Context context, Object calledBy, String message) {
        if (context == null) {
            throw new IllegalStateException(TAG + ": " + message + ", called by " + MyStringBuilder.objToTag(calledBy));
        }
    }

    public static void upgradeIfNeeded(Activity upgradeRequestor) {
        if (get().state() == MyContextState.UPGRADING && upgradeRequestor != null) {
            DatabaseConverterController.attemptToTriggerDatabaseUpgrade(upgradeRequestor);
        }
    }

    public static String getSystemInfo(Context context, boolean showVersion) {
        StringBuilder builder = new StringBuilder();
        if (showVersion) builder.append(getVersionText(context));
        MyStringBuilder.appendWithSpace(builder, MyLog.currentDateTimeForLogLine());
        MyStringBuilder.appendWithSpace(builder, ", started");
        MyStringBuilder.appendWithSpace(builder, RelativeTime.getDifference(context, appStartedAt, SystemClock.elapsedRealtime()));
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
        MyStringBuilder.appendWithSpace(builder, (getExecutionMode() == ExecutionMode.DEVICE ? "" : getExecutionMode().code));
        MyStringBuilder.appendWithSpace(builder, TamperingDetector.getAppSignatureInfo());
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
        release(() -> "onShutDown");
    }

    public static boolean isShuttingDown() {
        return isShuttingDown;
    }

    public static void release(Supplier<String> reason) {
        release(get(), reason);
    }

    public static void release(MyContext previousContext, Supplier<String> reason) {
        SyncInitiator.unregister(previousContext);
        MyServiceManager.setServiceUnavailable();
        TlsSniSocketFactory.forget();
        previousContext.save(reason);
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        SharedPreferencesUtil.forget();
        previousContext.release(reason);
        MyLog.v(TAG, () -> "release completed, " + reason.get());
    }
}
