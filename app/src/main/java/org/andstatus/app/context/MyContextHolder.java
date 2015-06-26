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

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.data.MyDatabaseConverterController;
import org.andstatus.app.util.MyLog;

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

    private static final Object CONTEXT_LOCK = new Object();
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyContext myContextCreator = MyContextImpl.getEmpty();
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyContext myInitializedContext = null;
    @GuardedBy("CONTEXT_LOCK")
    private static volatile MyFutureTaskExpirable<MyContext> myFutureContext = null;

    private MyContextHolder() {
    }
    
    /**
     * Immediately get currently available context, even if it's empty
     */
    public static MyContext get() {
        MyContext myContext = myInitializedContext;
        if (myContext == null) {
            myContext = myContextCreator;
        }
        return myContext;
    }
    
    /**
     * This is mainly for mocking / testing
     * @param myContextNew Should not be null
     * @return previous MyContext
     */
    public static MyContext replaceCreator(MyContext myContextNew) {
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
     * @return preferencesChangeTime or 0 in a case of error
     */
    public static long initialize(Context context, Object initializedBy) {
        if (MyDatabaseConverterController.isUpgrading()) {
            MyLog.v(TAG, "Skipping initialization: upgrade in progress (called by: " + initializedBy + ")");
            return 0;
        }
        return initializeDuringUpgrade(context, initializedBy);
    }

    public static long initializeDuringUpgrade(Context context, Object initializedBy) {
        if (get().initialized() && arePreferencesChanged()) {
            synchronized(CONTEXT_LOCK) {
                if (get().initialized() && arePreferencesChanged()) {
                    long preferencesChangeTimeLast = MyPreferences.getPreferencesChangeTime() ;
                    if (get().preferencesChangeTime() != preferencesChangeTimeLast) {
                        MyLog.v(TAG, "Preferences changed "
                                + java.util.concurrent.TimeUnit.MILLISECONDS
                                        .toSeconds(java.lang.System.currentTimeMillis()
                                                - preferencesChangeTimeLast)
                                + " seconds ago, refreshing...");
                        get().setExpired();
                    }
                }
            }
        }
        if (get().initialized() && !get().isExpired()) {
            MyLog.v(TAG, "Already initialized by " + get().initializedBy() +  " (called by: " + initializedBy + ")");
        }
        try {
            return getBlocking(context, initializedBy).preferencesChangeTime();
        } catch (InterruptedException e) {
            MyLog.d(TAG, "Initialize was interrupted, releasing resources...", e);
            synchronized(CONTEXT_LOCK) {
                get().setExpired();
            }
            Thread.currentThread().interrupt();
            return 0;
        }
    }

    public static boolean arePreferencesChanged() {
        return get().preferencesChangeTime() != MyPreferences.getPreferencesChangeTime();
    }
    
    /**
     *  Wait till the MyContext instance is available. Start the initialization if necessary. 
     */
    public static MyContext getBlocking(Context context, Object calledBy) throws InterruptedException {
        MyContext myContext =  myInitializedContext;
        while (myContext == null || !myContext.initialized() || myContext.isExpired()) {
            MyFutureTaskExpirable<MyContext> myFutureContextCopy = null;
            synchronized (CONTEXT_LOCK) {
                myFutureContextCopy = myFutureContext;
            }
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
            
            MyFutureTaskExpirable<MyContext> myFutureContextCopy = null;
            synchronized (CONTEXT_LOCK) {
                myFutureContextCopy = myFutureContext;
            }
            myContextOut = myFutureContextCopy.get();
            
            MyLog.v(TAG, method + " passed Future.get: " + callerName);
            synchronized (CONTEXT_LOCK) {
                if (myFutureContext == null || myFutureContext.isExpired()) {
                    MyLog.i(TAG, method + " myFutureContext is null or expired " + callerName);
                    myContextOut = null;
                } else if (myContextOut.isExpired()) {
                    MyLog.i(TAG, method + " myContextOut is expired " + callerName);
                    myFutureContext.setExpired();
                    myContextOut = null;
                } else if (myContextOut.initialized()) {
                    if (myInitializedContext != null && myInitializedContext.initialized()) {
                        myInitializedContext.release();
                    }
                    myInitializedContext = myContextOut;
                } else {
                    MyLog.i(TAG, method + " myContextOut is NOT initialized " + callerName);
                    myFutureContext.setExpired();
                    myContextOut = null;
                }
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
        if (myContextCreator.context() == null) {
            if (context == null) {
                throw new IllegalStateException("MyContextHolder: context is unknown yet, called by " + initializerName);
            }
            synchronized (CONTEXT_LOCK) {
                // This allows to refer to the context 
                // even before myInitializedContext is initialized
                myContextCreator = myContextCreator.newCreator(context, initializerName); 
            }
            if (myContextCreator.context() == null) {
                throw new IllegalStateException("MyContextHolder: no compatible context, called by " + initializerName);
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
    
    public static void upgradeIfNeeded(Activity upgradeRequestor) {
        if (get().state() == MyContextState.UPGRADING) {
            MyDatabaseConverterController.attemptToTriggerDatabaseUpgrade(upgradeRequestor);
        }
    }
}
