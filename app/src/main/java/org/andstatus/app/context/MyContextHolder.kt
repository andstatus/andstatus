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
import android.content.Intent;
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
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.UiThreadExecutor;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.TaggedClass;
import org.andstatus.app.util.TamperingDetector;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.vavr.control.Try;

/**
 * Holds globally cached state of the application: {@link MyContext}  
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public final class MyContextHolder implements TaggedClass {
    private static final String TAG = MyContextHolder.class.getSimpleName();
    public final static MyContextHolder myContextHolder = new MyContextHolder();

    private final long appStartedAt = SystemClock.elapsedRealtime();
    private volatile boolean isShuttingDown = false;

    private final Object CONTEXT_LOCK = new Object();
    @GuardedBy("CONTEXT_LOCK")
    @NonNull
    private volatile MyFutureContext myFutureContext = MyFutureContext.completed(MyContext.EMPTY);
    private volatile boolean onRestore = false;
    @NonNull
    private volatile ExecutionMode executionMode = ExecutionMode.UNKNOWN;

    private MyContextHolder() {
    }

    /** Immediately get currently available context, even if it's empty */
    @NonNull
    public MyContext getNow() {
        return getFuture().getNow();
    }

    /** Immediately get completed context or previous if not completed,
     * or failure if future failed */
    @NonNull
    public Try<MyContext> tryNow() {
        return getFuture().tryNow();
    }

    public MyContext getBlocking() {
        return myFutureContext.tryBlocking().getOrElse(myFutureContext.getNow());
    }

    public MyFutureContext getFuture() {
        return myFutureContext;
    }

    /** This is mainly for mocking / testing
     * @return true if succeeded */
    boolean trySetCreator(@NonNull MyContext contextCreatorNew) {
        synchronized (CONTEXT_LOCK) {
            if (!myFutureContext.future.isDone()) return false;

            myFutureContext.getNow().release(() -> "trySetCreator");
            myFutureContext = MyFutureContext.completed(contextCreatorNew);
        }
        return true;
    }

    public boolean needToRestartActivity() {
        return !getFuture().isReady();
    }

    public MyContextHolder initialize(Context context) {
        return initializeInner(context, context, false);
    }

    /** Reinitialize in a case preferences have been changed */
    public MyContextHolder initialize(Context context, Object calledBy) {
        return initializeInner(context, calledBy, false);
    }

    public MyContextHolder initializeDuringUpgrade(Context upgradeRequestor) {
        return  initializeInner(upgradeRequestor, upgradeRequestor, true);
    }

    private MyContextHolder initializeInner(Context context, Object calledBy, boolean duringUpgrade) {
        storeContextIfNotPresent(context, calledBy);
        if (isShuttingDown) {
            MyLog.d(this, "Skipping initialization: device is shutting down (called by: " + calledBy + ")");
        } else if (!duringUpgrade && DatabaseConverterController.isUpgrading()) {
            MyLog.d(this, "Skipping initialization: upgrade in progress (called by: " + calledBy + ")");
        } else {
            synchronized(CONTEXT_LOCK) {
                myFutureContext = MyFutureContext.fromPrevious(myFutureContext, calledBy);
            }
        }
        return this;
    }

    public MyContextHolder thenStartActivity(Intent intent) {
        return whenSuccessAsync(myContext -> MyFutureContext.startActivity(myContext, intent), UiThreadExecutor.INSTANCE);
    }

    public MyContextHolder thenStartApp() {
        return whenSuccessAsync(FirstActivity::startApp, UiThreadExecutor.INSTANCE);
    }

    public MyContextHolder whenSuccessAsync(Consumer<MyContext> consumer, Executor executor) {
        synchronized(CONTEXT_LOCK) {
            myFutureContext = myFutureContext.whenSuccessAsync(consumer, executor);
        }
        return this;
    }

    public MyContextHolder whenSuccessOrPreviousAsync(Consumer<MyContext> consumer, Executor executor) {
        synchronized(CONTEXT_LOCK) {
            myFutureContext = myFutureContext.whenSuccessOrPreviousAsync(consumer, executor);
        }
        return this;
    }

    public MyContextHolder with(UnaryOperator<CompletableFuture<MyContext>> future) {
        synchronized(CONTEXT_LOCK) {
            myFutureContext = myFutureContext.with(future);
        }
        return this;
    }

    /**
     * This allows to refer to the context even before myInitializedContext is initialized.
     * Quickly returns, providing context for the deferred initialization
     */
    public MyContextHolder storeContextIfNotPresent(Context context, Object calledBy) {
        if (context == null || getNow().context() != null) return this;

        synchronized(CONTEXT_LOCK) {
            if (myFutureContext.getNow().context() == null) {
                MyContext contextCreator = myFutureContext.getNow().newCreator(context, calledBy);
                requireNonNullContext(contextCreator.context(), calledBy, "no compatible context");
                myFutureContext = MyFutureContext.completed(contextCreator);
            }
        }
        return this;
    }

    private static void requireNonNullContext(Context context, Object calledBy, String message) {
        if (context == null) {
            throw new IllegalStateException(TAG + ": " + message + ", called by " + MyStringBuilder.objToTag(calledBy));
        }
    }

    public void upgradeIfNeeded(Activity upgradeRequestor) {
        if (getNow().state() == MyContextState.UPGRADING && upgradeRequestor != null) {
            DatabaseConverterController.attemptToTriggerDatabaseUpgrade(upgradeRequestor);
        }
    }

    public String getSystemInfo(Context context, boolean showVersion) {
        StringBuilder builder = new StringBuilder();
        if (showVersion) builder.append(getVersionText(context));
        MyStringBuilder.appendWithSpace(builder, MyLog.currentDateTimeForLogLine());
        MyStringBuilder.appendWithSpace(builder, ", started");
        MyStringBuilder.appendWithSpace(builder, RelativeTime.getDifference(context, appStartedAt,
                SystemClock.elapsedRealtime()));
        builder.append("\n");
        builder.append(ImageCaches.getCacheInfo());
        builder.append("\n");
        builder.append(AsyncTaskLauncher.threadPoolInfo());
        return builder.toString();
    }

    public String getVersionText(Context context) {
        StringBuilder builder = new StringBuilder();
        if (context != null) {
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                builder.append(pi.packageName + " v." + pi.versionName + " (" + pi.versionCode + ")");
            } catch (PackageManager.NameNotFoundException e) {
                MyLog.w(this, "Unable to obtain package information", e);
            }
        }
        if (builder.length() == 0) {
            builder.append("AndStatus v.?");
        }
        MyStringBuilder.appendWithSpace(builder, (getExecutionMode() == ExecutionMode.DEVICE ? "" : getExecutionMode().code));
        MyStringBuilder.appendWithSpace(builder, TamperingDetector.getAppSignatureInfo());
        return builder.toString();
    }

    public MyContextHolder setOnRestore(boolean onRestore) {
        this.onRestore = onRestore;
        return this;
    }

    public boolean isOnRestore() {
        return onRestore;
    }

    public void setExecutionMode(@NonNull ExecutionMode executionMode) {
        if (this.executionMode != executionMode) {
            this.executionMode = executionMode;
            if (executionMode != ExecutionMode.DEVICE) {
                MyLog.i(this, "Executing: " + getVersionText(getNow().context()));
            }
        }
    }

    @NonNull
    public ExecutionMode getExecutionMode() {
        if (executionMode == ExecutionMode.UNKNOWN) {
            setExecutionMode(calculateExecutionMode());
        }
        return executionMode;
    }

    @NonNull
    private ExecutionMode calculateExecutionMode() {
        Context context = getNow().context();
        if (context == null) {
            return ExecutionMode.UNKNOWN;
        }
        if ("true".equals(Settings.System.getString(context.getContentResolver(), "firebase.test.lab"))) {
            // See https://firebase.google.com/docs/test-lab/android-studio
            return getNow().isTestRun() ? ExecutionMode.FIREBASE_TEST : ExecutionMode.ROBO_TEST;
        }
        if (getNow().isTestRun()) {
            return ExecutionMode.TEST;
        }
        return ExecutionMode.DEVICE;
    }

    boolean isScreenSupported() {
        return getExecutionMode() != ExecutionMode.TRAVIS_TEST;
    }

    public void onShutDown() {
        isShuttingDown = true;
        release(() -> "onShutDown");
    }

    public boolean isShuttingDown() {
        return isShuttingDown;
    }

    public void release(Supplier<String> reason) {
        synchronized(CONTEXT_LOCK) {
            myFutureContext = myFutureContext.releaseNow(reason);
        }
    }

    @Override
    public String classTag() {
        return TAG;
    }
}
