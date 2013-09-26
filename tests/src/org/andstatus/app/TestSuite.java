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
    
    public static Context initialize(InstrumentationTestCase testCase) {
        if (initialized) {
            return context;
        }
        Log.d(TAG, "Initializing Test Suite");
        context = testCase.getInstrumentation().getTargetContext();
        if (context == null) {
            Log.e(TAG, "targetContext is null.");
            throw new IllegalArgumentException("this.getInstrumentation().getTargetContext() returned null");
        }
        MyPreferences.initialize(context, testCase);
        if (MyPreferences.shouldSetDefaultValues()) {
            // Default values for the preferences will be set only once
            // and in one place: here
            MyPreferences.setDefaultValues(R.xml.preferences, false);
            if (MyPreferences.shouldSetDefaultValues()) {
                Log.e(TAG, "Default values were not set?!");   
            } else {
                Log.i(TAG, "Default values has been set");   
            }
        }
        MyPreferences.getDefaultSharedPreferences().edit().putString(MyPreferences.KEY_MIN_LOG_LEVEL, Integer.toString(Log.VERBOSE)).commit();
        
        initialized =  (context != null);
        Log.d(TAG, "Test Suite" + (initialized ? "" : " was not") + " initialized");
        assertTrue("Test Suite initialized", initialized);
        return context;
    }
}
