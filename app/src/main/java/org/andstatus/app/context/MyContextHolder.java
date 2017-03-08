/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.database.DatabaseConverterController;
import org.andstatus.app.graphics.MyImageCache;
import org.andstatus.app.net.http.TlsSniSocketFactory;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TamperingDetector;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * Holds globally cached state of the application: {@link MyContext}  
 * This design was inspired by the "Java Concurrency in Practice" book by Brian Goetz et al.
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public final class MyContextHolder {
    private static final String TAG = MyContextHolder.class.getSimpleName();
    public final static long appStartedAt = SystemClock.elapsedRealtime();

    private static final Object CONTEXT_LOCK = new Object();
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyContext myContextCreator = MyContextImpl.newEmpty("static");
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyContext myInitializedContext = null;
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyFutureTaskExpirable<MyContext> myFutureContext = null;
    private static volatile boolean onRestore = false;

    private MyContextHolder() {
    }
    
    /**
     * Immediately get currently available context, even if it's empty
     */
    @NonNull
    public static MyContext get() {
        MyContext myContext = myInitializedContext;
        if (myContext == null) {
            myContext = myContextCreator;
        }
        return myContext;
    }
    
    /**
     * This is mainly for mocking / testing
     * @return previous MyContext
     */
    public static MyContext replaceCreator(@NonNull MyContext myContextNew) {
        if (myContextNew == null) {
            throw new IllegalArgumentException("replace: myContext_new should not be null");
        }
        synchronized (CONTEXT_LOCK) {
            MyContext myContextOld = myInitializedContext;
            if (myContextOld == null) {
                myContextOld = myContextCreator;
            }
            myContextCreator = myContextNew;
            release();
            return myContextOld;
        }
    }
    
    /**
     * Reinitializes in a case preferences have been changed
     * Blocks on initialization
     */
    public static MyContext initialize(Context context, Object initializedBy) {
        if (DatabaseConverterController.isUpgrading()) {
            MyLog.v(TAG, "Skipping initialization: upgrade in progress (called by: " + initializedBy + ")");
            return get();
        }
        return initializeDuringUpgrade(context, initializedBy);
    }

    public static MyContext initializeDuringUpgrade(Context context, Object initializedBy) {
        MyContext myContext = myInitializedContext;
        if (myContext != null && myContext.initialized() && !myContext.isExpired()) {
            MyLog.v(TAG, "Already initialized " + myContext +  " (called" +
                    " " +
                    "by: " +
                    initializedBy + ")");
            return myContext;
        }

        MyLog.d(TAG, "Starting initialization by " + initializedBy);
        try {
            return getBlocking(context, initializedBy);
        } catch (InterruptedException e) {
            myContext = get();
            MyLog.d(TAG, "Initialize was interrupted, releasing resources...", e);
            synchronized(CONTEXT_LOCK) {
                myContext.setExpired();
            }
            Thread.currentThread().interrupt();
            return myContext;
        }
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
     *  Wait till the MyContext instance is available. Start the initialization if necessary. 
     */
    private static MyContext getBlocking(Context context, Object calledBy) throws InterruptedException {
        MyContext myContext =  myInitializedContext;
        while (myContext == null || !myContext.initialized() || myContext.isExpired()) {
            MyFutureTaskExpirable<MyContext> myFutureContextCopy = myFutureContext;
            if (myFutureContextCopy == null || myFutureContextCopy.isExpired()) {
                myContext = createMyFutureContext(context, calledBy, myContext);
            } else {
                myContext = waitForMyFutureContext(myContext, calledBy);
            }
        }
        return myContext;
    }

    private static MyContext createMyFutureContext(Context contextIn, Object calledBy,
            MyContext myContextIn) {
        String method = "createMyFutureContext";
        MyContext myContextOut = myContextIn;
        final String callerName = MyLog.objTagToString(calledBy) ;
        MyLog.v(TAG, method + " entered by " + callerName);
        storeContextIfNotPresent(contextIn, calledBy);
        final Context contextForCallable = myContextCreator.context();
        Callable<MyContext> callable = new Callable<MyContext>() {
            @Override
            public MyContext call() {
                return myContextCreator.newInitialized(contextForCallable, callerName);
            }
        };
        MyFutureTaskExpirable<MyContext> futureTask = new MyFutureTaskExpirable<MyContext>(callable);
        synchronized (CONTEXT_LOCK) {
            if (myInitializedContext != null) {
                myContextOut = myInitializedContext;
            }
            if (myFutureContext == null || myFutureContext.isExpired()) {
                try {
                    myFutureContext = futureTask;
                    myFutureContext.run();
                } catch (Exception e) {
                    MyLog.e(TAG, method + " exception on futureTask.run", e);
                }
            }
        }
        return myContextOut;
    }

    private static MyContext waitForMyFutureContext(MyContext myContextIn, Object calledBy)
            throws InterruptedException {
        String method = "waitForMyFutureContext";
        final String callerName = MyLog.objTagToString(calledBy) ;
        MyContext myContextOut = myContextIn;
        try {
            MyLog.v(TAG, method + " may block at Future.get: " + callerName);
            myContextOut = myFutureContext.get();
            MyLog.v(TAG, method + " passed Future.get: " + callerName);
            boolean releaseGlobalNeeded = false;
            String msgLog = "";
            synchronized (CONTEXT_LOCK) {
                if (myFutureContext == null || myFutureContext.isExpired()) {
                    msgLog = "myFutureContext is null or expired " + callerName;
                    myContextOut = null;
                } else if (myContextOut.isExpired()) {
                    msgLog = "myContextOut is expired " + callerName + " " + myContextOut;
                    myFutureContext.setExpired();
                    myContextOut = null;
                } else if (myContextOut.initialized()) {
                    if (myInitializedContext == myContextOut) {
                        msgLog = "the same " + myContextOut;
                    } else {
                        if (myInitializedContext != null && myInitializedContext.initialized()) {
                            myInitializedContext.release();
                            releaseGlobalNeeded = true;
                        }
                        myInitializedContext = myContextOut;
                        msgLog = "initialized " + myContextOut;
                    }
                } else {
                    msgLog = "myContextOut is NOT initialized " + callerName  + " " + myContextOut;
                    myFutureContext.setExpired();
                    myContextOut = null;
                }
            }
            MyLog.i(TAG, method + " " + msgLog);
            if (releaseGlobalNeeded) {
                releaseGlobal();
            }
        } catch (ExecutionException e) {
            MyLog.v(TAG, method + " by " + callerName, e);
            myFutureContext.setExpired();
        }
        return myContextOut;
    }

    /**
     *  Quickly return, providing context for the deferred initialization
     */
    public static void storeContextIfNotPresent(Context context, Object initializedBy) {
        String initializerName = MyLog.objTagToString(initializedBy) ;
        synchronized(CONTEXT_LOCK) {
            if (myContextCreator.context() == null) {
                if (context == null) {
                    throw new IllegalStateException(TAG + ": context is unknown yet, called by " + initializerName);
                }
                // This allows to refer to the context
                // even before myInitializedContext is initialized
                myContextCreator = myContextCreator.newCreator(context, initializerName);
                if (myContextCreator.context() == null) {
                    throw new IllegalStateException(TAG + ": no compatible context, called by " + initializerName);
                }
            }
        }
    }
    
    public static void release() {
        MyLog.d(TAG, "Releasing resources");
        synchronized(CONTEXT_LOCK) {
            if (myInitializedContext != null) {
                myInitializedContext.setExpired();
            }
            if (myFutureContext != null) {
                myFutureContext.setExpired();
            }
        }
    }

    private static void releaseGlobal() {
        TlsSniSocketFactory.forget();
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        SharedPreferencesUtil.forget();
        MyLog.i(TAG, "releaseGlobal completed");
    }

    public static void upgradeIfNeeded(Activity upgradeRequestor) {
        if (get().state() == MyContextState.UPGRADING) {
            DatabaseConverterController.attemptToTriggerDatabaseUpgrade(upgradeRequestor);
        }
    }

    public static String getSystemInfo(Context context, boolean showVersion) {
        StringBuilder builder = new StringBuilder();
        if (showVersion) builder.append(getVersionText(context));
        I18n.appendWithSpace(builder, "started " + RelativeTime.getDifference(context, appStartedAt, SystemClock.elapsedRealtime()));
        builder.append("\n");
        builder.append(MyImageCache.getCacheInfo());
        builder.append("\n");
        builder.append(AsyncTaskLauncher.threadPoolInfo());
        return builder.toString();
    }

    public static String getVersionText(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            return pi.packageName + " v." + pi.versionName + " (" + pi.versionCode + ") " +
                    TamperingDetector.getAppSignatureInfo();
        } catch (PackageManager.NameNotFoundException e) {
            MyLog.e(TAG, "Unable to obtain package information", e);
        }
        return "AndStatus v.?";
    }

    public static void setOnRestore(boolean onRestore) {
        MyContextHolder.onRestore = onRestore;
    }

    public static boolean isOnRestore() {
        return onRestore;
    }

}
