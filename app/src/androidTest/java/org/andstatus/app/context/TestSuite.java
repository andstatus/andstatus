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

package org.andstatus.app.context;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.view.ViewGroup;
import android.widget.ListView;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.Permissions;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author yvolk@yurivolkov.com
 */
public class TestSuite {
    private static final String TAG = TestSuite.class.getSimpleName();
    private static volatile boolean initialized = false;
    private static volatile Context context;
    private static volatile String dataPath;
    private static volatile boolean dataAdded = false;

    public static boolean isInitializedWithData() {
        return initialized && dataAdded;
    }

    public static Context initializeWithData(Object testCase) {
        initialize(testCase);
        ensureDataAdded();
        return getMyContextForTest().context();
    }
    
    public static synchronized Context initialize(Object testCase) {
        final String method = "initialize";
        if (initialized) {
            return context;
        }
        MyLog.setMinLogLevel(MyLog.VERBOSE);
        for (int iter=1; iter<6; iter++) {
            MyLog.i(TAG, "Initializing Test Suite, iteration=" + iter);
            if (testCase == null) {
                MyLog.e(TAG, "testCase is null.");
                throw new IllegalArgumentException("testCase is null");
            }
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            if (instrumentation == null) {
                MyLog.e(TAG, "testCase.getInstrumentation() is null.");
                throw new IllegalArgumentException("testCase.getInstrumentation() returned null");
            }
            context = instrumentation.getTargetContext();
            if (context == null) {
                MyLog.e(TAG, "targetContext is null.");
                throw new IllegalArgumentException("testCase.getInstrumentation().getTargetContext() returned null");
            }
            MyLog.i(TAG, "Before MyContextHolder.initialize " + iter);
            try {
                MyContextHolder.setCreator(new MyContextTestImpl(null, context, testCase));
                MyContext myContext = MyContextHolder.initialize(context, testCase);
                MyLog.i(TAG, "After MyContextHolder.initialize " + iter + " " + myContext);
                if (myContext.state() == MyContextState.READY) break;
            } catch (IllegalStateException e) {
                MyLog.i(TAG, "Error caught, iteration=" + iter, e);
            }
            DbUtils.waitMs(method, 3000);
        }
        MyLog.i(TAG, "After Initializing Test Suite loop");
        MyContextHolder.setExecutionMode(
                ExecutionMode.load(InstrumentationRegistry.getArguments().getString("executionMode")));
        final MyContext myContext = MyContextHolder.get();
        assertNotEquals("MyContext state " + myContext, MyContextState.EMPTY, myContext.state());

        int logLevel = MyLog.VERBOSE;
        MyLog.setMinLogLevel(logLevel);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_DOWNLOAD_AND_DISPLAY_ATTACHED_IMAGES, true);
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_ATTACH_IMAGES_TO_MY_NOTES, true);
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        assertTrue("Level " + logLevel + " should be loggable", MyLog.isLoggable(TAG, logLevel));
        MyServiceManager.setServiceUnavailable();

        if (MyContextHolder.get().state() != MyContextState.READY) {
            MyLog.d(TAG, "MyContext is not ready: " + MyContextHolder.get().state());
            if (MyContextHolder.get().state() == MyContextState.NO_PERMISSIONS) {
                Permissions.setAllGranted(true);
            }
            waitUntilContextIsReady();
        }

        MyLog.d(TAG, "Before check isReady " + MyContextHolder.get());
        initialized =  MyContextHolder.get().isReady();
        assertTrue("Test Suite initialized, MyContext state=" + MyContextHolder.get().state(), initialized);
        dataPath = MyContextHolder.get().context().getDatabasePath("andstatus").getPath();
        MyLog.v("TestSuite", "Test Suite initialized, MyContext state=" + MyContextHolder.get().state() 
                + "; databasePath=" + dataPath);
        
        if (FirstActivity.checkAndUpdateLastOpenedAppVersion(MyContextHolder.get().context(), true)) {
            MyLog.i(TAG, "New version of application is running");
        }
        return context;
    }

    public static synchronized void forget() {
        MyLog.d(TAG, "Before forget");
        MyContextHolder.release();
        context = null;
        initialized = false;
    }
    
    public static void waitUntilContextIsReady() {
        final String method = "waitUntilContextIsReady";
        Intent intent = new Intent(MyContextHolder.get().context(), HelpActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        MyContextHolder.get().context().startActivity(intent);
        for (int i=100; i > 0; i--) {
            DbUtils.waitMs(method, 2000);
            MyLog.d(TAG, "Waiting for context " + i + " " + MyContextHolder.get().state());
            switch (MyContextHolder.get().state()) {
                case READY:
                case ERROR:
                    i = 0;
                    break;
                default:
                    break;
            }
        }
        assertEquals("Is Not ready", MyContextState.READY, MyContextHolder.get().state());

        intent = new Intent(MyContextHolder.get().context(), HelpActivity.class);
        intent.putExtra(HelpActivity.EXTRA_CLOSE_ME, true);
        MyContextHolder.get().context().startActivity(intent);
        DbUtils.waitMs(method, 2000);
    }

    public static void clearAssertions() {
        getMyContextForTest().getAssertions().clear();
    }
    
    public static MyContextTestImpl getMyContextForTest() {
        if (!(MyContextHolder.get() instanceof MyContextTestImpl)) {
            fail("Wrong type of current context");
        }
        return (MyContextTestImpl) MyContextHolder.get();
    }
    
    public static void setHttpConnectionMockClass(Class<? extends HttpConnection> httpConnectionMockClass) {
        getMyContextForTest().setHttpConnectionMockClass(httpConnectionMockClass);
    }

    public static void setHttpConnectionMockInstance(HttpConnection httpConnectionMockInstance) {
        getMyContextForTest().setHttpConnectionMockInstance(httpConnectionMockInstance);
    }

    public static void onDataDeleted() {
        dataAdded = false;
    }

    private static void ensureDataAdded() {
        final String method = "ensureDataAdded";
        MyLog.v(method, method + ": started");
        if (!dataAdded) {
            dataAdded = true;
            demoData.createNewInstance();
            demoData.add(getMyContextForTest(), dataPath);
        }
        MyLog.v(method, method + ": ended");
    }

    public static Date utcTime(int year, int month, int day, int hour, int minute, int second) {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.set(year, month, day, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();        
    }

    public static Date utcTime(long millis) {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(millis);
        return cal.getTime();
    }

    public static int waitForListLoaded(Activity activity, int minCount) throws InterruptedException {
        final String method = "waitForListLoaded";
        final ViewGroup list = (ViewGroup) activity.findViewById(android.R.id.list);
        assertTrue(list != null);
        int itemsCount = 0;
        for (int ind=0; ind<40; ind++) {
            DbUtils.waitMs(method, 2000);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            int itemsCountNew = ListView.class.isInstance(list)
                    ? ((ListView) list).getCount()
                    : list.getChildCount();
            MyLog.v(TAG, "waitForListLoaded; countNew=" + itemsCountNew + ", prev=" + itemsCount + ", min=" + minCount);
            if (itemsCountNew >= minCount && itemsCount == itemsCountNew) {
                break;
            }
            itemsCount = itemsCountNew;
        }
        assertTrue("There are " + itemsCount + " items (min=" + minCount + ") in the list of " + activity.getClass().getSimpleName(),
                itemsCount >= minCount);
        return itemsCount;
    }

    public static void waitForIdleSync() throws InterruptedException {
        final String method = "waitForIdleSync";
        DbUtils.waitMs(method, 200);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        DbUtils.waitMs(method, 2000);
    }
    
    public static boolean isScreenLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        return km == null || km.inKeyguardRestrictedInputMode();
    }

    public static boolean setAndWaitForIsInForeground(boolean isInForeground) {
        final String method = "setAndWaitForIsInForeground";
        MyContextHolder.get().setInForeground(isInForeground);
        for (int pass = 0; pass < 300; pass++) {
            if (MyContextHolder.get().isInForeground() == isInForeground) {
                return true;
            }
            if (DbUtils.waitMs(method, 100)) {
                return false;
            }
        }
        return false;
    }

}
