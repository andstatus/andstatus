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
import android.util.Log;

import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class TestSuite extends TestCase {
    private static final String TAG = TestSuite.class.getSimpleName();
    private static volatile boolean initialized = false;
    private static volatile Context context;
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static synchronized Context initialize(InstrumentationTestCase testCase) {
        if (initialized) {
            Log.d(TAG, "Already initialized");
            return context;
        }
        Log.d(TAG, "Initializing Test Suite");
        context = testCase.getInstrumentation().getTargetContext();
        if (context == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        Log.d(TAG, "Before MyPreferences.initialize");
        MyPreferences.initialize(context, testCase);
        Log.d(TAG, "After MyPreferences.initialize");
        if (MyPreferences.shouldSetDefaultValues()) {
            Log.d(TAG, "Before setting default preferences");
            // Default values for the preferences will be set only once
            // and in one place: here
            MyPreferences.setDefaultValues(R.xml.preferences_test, false);
            if (MyPreferences.shouldSetDefaultValues()) {
                Log.e(TAG, "Default values were not set?!");   
            } else {
                Log.i(TAG, "Default values has been set");   
            }
        }
        MyPreferences.getDefaultSharedPreferences().edit().putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(Log.VERBOSE)).commit();
        MyLog.forget();
        assertTrue("Log level set to verbose", MyLog.isLoggable(TAG, Log.VERBOSE));
        MyServiceManager.setServiceUnavailable();
        
        initialized =  (context != null);
        Log.d(TAG, "Test Suite" + (initialized ? "" : " was not") + " initialized");
        assertTrue("Test Suite initialized", initialized);
        
        waitTillUpgradeEnded();
        
        return context;
    }

    public static synchronized void forget() {
        context = null;
        Log.d(TAG, "Before forget");
        MyPreferences.forget();
        initialized = false;
    }
    
    public static void waitTillUpgradeEnded() {
        for (int i=1; i < 11; i++) {
            if(!MyPreferences.isUpgrading()) {
                break;
            }
            Log.d(TAG, "Waiting for upgrade to end " + i);
            try {
                Thread.sleep(200);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }            
        }
        assertTrue("Not upgrading now", !MyPreferences.isUpgrading());
    }
}
