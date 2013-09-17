package org.andstatus.app;

import junit.framework.TestCase;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.util.Log;

import org.andstatus.app.data.MyPreferences;

public class TestSuite extends TestCase {
    private static final String TAG = TestSuite.class.getSimpleName();
    private static boolean initialized = false;
    private static Context context;
    
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
