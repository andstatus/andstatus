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

package org.andstatus.app;

import android.content.Context;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import org.andstatus.app.data.MyDatabaseConverter;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Holds globally cached state of the application: {@link MyContext}  
 * This design was inspired by the "Java Concurrency in Practice" book by Brian Goetz et al.
 * @author yvolk@yurivolkov.com
 */
@ThreadSafe
public final class MyContextHolder {
    private static final String TAG = MyContextHolder.class.getSimpleName();

    private static Integer contextLock = 0;
    @GuardedBy("contextLock")
    private static volatile MyContext myContextCreator = MyContextImpl.getEmpty();
    @GuardedBy("contextLock")
    private static volatile MyContext myInitializedContext = null;
    @GuardedBy("contextLock")
    private static volatile Future<MyContext> myFutureContext = null;

    private MyContextHolder() {};
    
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
     * @param myContext_new Should not be null
     * @return previous MyContext
     */
    public static MyContext replaceCreator(MyContext myContext_new) {
        if (myContext_new == null) {
            throw new IllegalArgumentException("replace: myContext_new should not be null");
        }
        synchronized (contextLock) {
            MyContext myContext_old = myInitializedContext;
            myContextCreator = myContext_new;
            release();
            return myContext_old;
        }
    }
    
    /**
     * Reinitializes in a case preferences have been changed
     * Blocks on initialization
     * @return preferencesChangeTime or 0 in a case of error
     */
    public static long initialize(Context context, Object initializedBy) {
        if (get().initialized() && arePreferencesChanged()) {
            synchronized(contextLock) {
                if (get().initialized() && arePreferencesChanged()) {
                    long preferencesChangeTime_last = MyPreferences.getPreferencesChangeTime() ;
                    if (get().preferencesChangeTime() != preferencesChangeTime_last) {
                        MyLog.v(TAG, "Preferences changed " + (java.lang.System.currentTimeMillis() - preferencesChangeTime_last)/1000 +  " seconds ago, refreshing...");
                        release();
                    }
                }
            }
        }
        if (get().initialized()) {
            MyLog.v(TAG, "Already initialized by " + get().initializedBy() +  " (called by: " + initializedBy + ")");
        }
        try {
            return getBlocking(context, initializedBy).preferencesChangeTime();
        } catch (InterruptedException e) {
            MyLog.d(TAG, "Initialize was interrupted");
            return 0;
        }
    }

    public static boolean arePreferencesChanged() {
        return (get().preferencesChangeTime() != MyPreferences.getPreferencesChangeTime());
    }
    
    /**
     *  Wait till the MyContext instance is available. Start the initialization if necessary. 
     */
    public static MyContext getBlocking(Context context, Object initializedBy) throws InterruptedException {
        MyContext myContext = myInitializedContext;
        while (myContext == null || !myContext.initialized()) {
            if (myFutureContext == null) {
                final String initializerName = MyLog.objTagToString(initializedBy) ;
                if (myContextCreator.context() == null) {
                    if (context == null) {
                        throw new IllegalStateException("MyContextHolder: context is unknown yet");
                    }
                    synchronized (contextLock) {
                        // This allows to refer to the context 
                        // even before myInitializedContext is initialized
                        myContextCreator = myContextCreator.newCreator(context, initializerName); 
                    }
                }
                final Context contextFinal = myContextCreator.context();
                Callable<MyContext> callable = new Callable<MyContext>() {
                    @Override
                    public MyContext call() throws Exception {
                        return myContextCreator.newInitialized(contextFinal, initializerName);
                    }
                };
                FutureTask<MyContext> futureTask = new FutureTask<MyContext>(callable);
                synchronized (contextLock) {
                    if (myInitializedContext != null) {
                        myContext = myInitializedContext;
                        break;
                    }
                    if (myFutureContext == null) {
                        myFutureContext = futureTask;
                        futureTask.run();
                    }
                }
            }
            try {
                MyLog.v(TAG, "May block at myFutureContext.get()");
                myContext = myFutureContext.get();
                MyLog.v(TAG, "Passed myFutureContext.get()");
                synchronized (contextLock) {
                    if (myInitializedContext != null) {
                        myContext = myInitializedContext;
                        break;
                    } else {
                        myInitializedContext = myContext;
                    }
                }
            } catch (ExecutionException e) {
                myFutureContext = null;
            }
        }
        return myContext;
    }

    public static void release() {
        synchronized(contextLock) {
            if (myInitializedContext != null) {
                myInitializedContext.release();
                myInitializedContext = null;
            }
            myFutureContext = null;
        }
    }
    
    public static void upgradeIfNeeded() {
        if (get().state() == MyContextState.UPGRADING) {
            release();
            MyDatabaseConverter.triggerDatabaseUpgrade();
            initialize(null, "After upgrade process");
        }
    }
}
