/*
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

import junit.framework.TestCase;

import android.content.Context;
import android.test.InstrumentationTestCase;

import org.andstatus.app.data.MyDatabaseConverter;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class TestSuite extends TestCase {
    private static volatile boolean initialized = false;
    private static volatile Context context;
    private static volatile String dataPath;
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static synchronized Context initialize(InstrumentationTestCase testCase) {
        if (initialized) {
            MyLog.d(TestSuite.class, "Already initialized");
            return context;
        }
        for (int iter=0; iter<5; iter++) {
            MyLog.d(TestSuite.class, "Initializing Test Suite");
            context = testCase.getInstrumentation().getTargetContext();
            if (context == null) {
                MyLog.e(TestSuite.class, "targetContext is null.");
                throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
            }
            MyLog.d(TestSuite.class, "Before MyContextHolder.initialize " + iter);
            try {
                MyContextHolder.initialize(context, testCase);
                MyLog.d(TestSuite.class, "After MyContextHolder.initialize " + iter);
                break;
            } catch (IllegalStateException e) {
                MyLog.w(TestSuite.class, "Error caught " + iter);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertTrue("MyContext state=" + MyContextHolder.get().state(), MyContextHolder.get().state() != MyContextState.EMPTY);
        
        if (MyPreferences.shouldSetDefaultValues()) {
            MyLog.d(TestSuite.class, "Before setting default preferences");
            // Default values for the preferences will be set only once
            // and in one place: here
            MyPreferences.setDefaultValues(R.xml.preferences_test, false);
            if (MyPreferences.shouldSetDefaultValues()) {
                MyLog.e(TestSuite.class, "Default values were not set?!");   
            } else {
                MyLog.i(TestSuite.class, "Default values has been set");   
            }
        }
        MyPreferences.getDefaultSharedPreferences().edit().putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(MyLog.VERBOSE)).commit();
        MyLog.forget();
        assertTrue("Log level set to verbose", MyLog.isLoggable(TestSuite.class, MyLog.VERBOSE));
        MyServiceManager.setServiceUnavailable();

        if (MyContextHolder.get().state() == MyContextState.UPGRADING) {
            MyLog.d(TestSuite.class, "Upgrade needed");
            MyDatabaseConverter.triggerDatabaseUpgrade();
        }
        waitTillUpgradeEnded();
        
        initialized =  MyContextHolder.get().isReady();
        assertTrue("Test Suite initialized, MyContext state=" + MyContextHolder.get().state(), initialized);
        dataPath = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        MyLog.v("TestSuite", "Test Suite initialized, MyContext state=" + MyContextHolder.get().state() 
                + "; databasePath=" + dataPath);
        
        return context;
    }

    public static String checkDataPath(Object objTag) {
        String message = "";
        String dataPath2 = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        if (dataPath.equalsIgnoreCase(dataPath2)) {
            return "ok";
        }
        message =  (MyContextHolder.get().isReady() ? "" : "not ready; ") + dataPath2 + " instead of " + dataPath;
        MyLog.e(objTag, message);
        return message;
    }
    
    public static synchronized void forget() {
        context = null;
        MyLog.d(TestSuite.class, "Before forget");
        MyContextHolder.release();
        initialized = false;
    }
    
    public static void waitTillUpgradeEnded() {
        for (int i=1; i < 100; i++) {
            if(!MyDatabaseConverter.isUpgrading()) {
                break;
            }
            MyLog.d(TestSuite.class, "Waiting for upgrade to end " + i);
            try {
                Thread.sleep(2000);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }            
        }
        assertTrue("Not upgrading now", !MyDatabaseConverter.isUpgrading());
    }
}
