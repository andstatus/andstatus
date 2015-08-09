/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.test.ActivityInstrumentationTestCase2;
import android.test.ActivityTestCase;
import android.test.InstrumentationTestCase;
import android.view.Menu;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.util.MyLog;

public class ActivityTestHelper<T extends MyActivity> extends InstrumentationTestCase {
    private InstrumentationTestCase mTestCase;
    private T mActivity;
    private ActivityMonitor activityMonitor = null;

    public ActivityTestHelper(ActivityTestCase testCase, T activity) {
        super();
        mTestCase = testCase;
        mActivity = activity;
    }

    public ActivityTestHelper(ActivityInstrumentationTestCase2<T> testCase, Class<? extends Activity> classOfActivityToMonitor) {
        super();
        mTestCase = testCase;
        addMonitor(classOfActivityToMonitor);
        mActivity = testCase.getActivity();
    }

    public ActivityMonitor addMonitor(Class<? extends Activity> classOfActivity) {
        activityMonitor = mTestCase.getInstrumentation().addMonitor(classOfActivity.getName(), null, false);
        return activityMonitor;
    }
    
    public Activity waitForNextActivity(String method, long timeOut) throws InterruptedException {
        Activity nextActivity = mTestCase.getInstrumentation().waitForMonitorWithTimeout(activityMonitor, timeOut);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(mTestCase, nextActivity, 2);
        activityMonitor = null;
        return nextActivity;
    }

    public boolean clickMenuItem(String method, int menuItemResourceId) throws InterruptedException {
        assertTrue(menuItemResourceId != 0);
        TestSuite.waitForIdleSync(mTestCase);
        MyLog.v(this, method + "-Log before run clickers");

        boolean clicked = mTestCase.getInstrumentation().invokeMenuActionSync(mActivity, menuItemResourceId, 0);
        if (clicked) {
            MyLog.i(this, method + "-Log instrumentation clicked");
        } else {
            MyLog.i(this, method + "-Log instrumentation couldn't click");
        }

        if (!clicked) {
            Menu menu = mActivity.getOptionsMenu();
            if (menu != null) {
                MenuItemClicker clicker = new MenuItemClicker(method, menu, menuItemResourceId);
                mTestCase.getInstrumentation().runOnMainSync(clicker);
                clicked = clicker.clicked;
                if (clicked) {
                    MyLog.i(this, method + "-Log performIdentifierAction clicked");
                } else {
                    MyLog.i(this, method + "-Log performIdentifierAction couldn't click");
                }
            }
        }

        if (!clicked) {
            MenuItemMock menuItem = new MenuItemMock(menuItemResourceId);
            mActivity.onOptionsItemSelected(menuItem);
            clicked = menuItem.called();
            if (clicked) {
                MyLog.i(this, method + "-Log onOptionsItemSelected clicked");
            } else {
                MyLog.i(this, method + "-Log onOptionsItemSelected couldn't click");
            }
        }
        TestSuite.waitForIdleSync(mTestCase);
        return clicked;
    }

    private static class MenuItemClicker implements Runnable {
        private String method;
        private Menu menu;
        private int menuItemResourceId;

        volatile boolean clicked = false;

        public MenuItemClicker(String method, Menu menu, int menuItemResourceId) {
            this.method = method;
            this.menu = menu;
            this.menuItemResourceId = menuItemResourceId;
        }

        @Override
        public void run() {
            MyLog.v(this, method + "-Log before click");
            clicked = menu.performIdentifierAction(menuItemResourceId, 0);
        }
    }
}
