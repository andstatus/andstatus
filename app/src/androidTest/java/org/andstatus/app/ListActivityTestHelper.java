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
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.MyLog;

public class ListActivityTestHelper<T extends MyListActivity> extends InstrumentationTestCase {
    private InstrumentationTestCase mTestCase;
    private T mActivity;
    private ActivityMonitor activityMonitor = null;

    public ListActivityTestHelper(ActivityTestCase testCase, T activity) {
        super();
        mTestCase = testCase;
        mActivity = activity;
    }

    public ListActivityTestHelper(ActivityInstrumentationTestCase2<T> testCase, Class<? extends Activity> classOfActivityToMonitor) {
        super();
        mTestCase = testCase;
        addMonitor(classOfActivityToMonitor);
        mActivity = testCase.getActivity();
    }

    /**
     * @return success
     */
    public boolean invokeContextMenuAction(String method, int position, ContextMenuItem menuItem) throws InterruptedException {
        selectListPosition(method, position);
        MyLog.v(this, method + "; before invokeContextMenuAction on menu Item=" + menuItem + " at position=" + position);
        return invokeContextMenuAction(method, mActivity, position, menuItem.getId());
    }
    
    public void selectListPosition(final String method, final int positionIn) throws InterruptedException {
        MyLog.v(this, method + " before setSelection " + positionIn);
        mTestCase.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                int position = positionIn;
                ListAdapter la = getListView().getAdapter();
                if (la.getCount() <= position) {
                    position = la.getCount() - 1;
                }
                MyLog.v(this, method + " on setSelection " + position
                        + " of " + (la.getCount() - 1));
                getListView().setSelectionFromTop(position, 0);
            }
        });
        TestSuite.waitForIdleSync(mTestCase);
        MyLog.v(this, method + " after setSelection");
    }

    /**
     * InstrumentationTestCase.getInstrumentation().invokeContextMenuAction doesn't work properly
     * @return success
     *
     * Note: This method cannot be invoked on the main thread.
     * See https://github.com/google/google-authenticator-android/blob/master/tests/src/com/google/android/apps/authenticator/TestUtilities.java
     */
    private boolean invokeContextMenuAction(final String method, final MyListActivity activity,
                   int position, final int menuItemId) throws InterruptedException {
        boolean success = false;
        int position1 = position;
        for (long attempt = 1; attempt < 4; attempt++) {
            longClickAtPosition(method, position1);
            if (mActivity.getPositionOfContextMenu() == position) {
                success = true;
                break;
            }
            MyLog.i(method, "Context menu created for position " + mActivity.getPositionOfContextMenu()
                    + " instead of " + position
                    + "; was set to " + position1 + "; attempt " + attempt);
            position1 = position + (position1 - mActivity.getPositionOfContextMenu());
        }
        if (success) {
            mTestCase.getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    MyLog.v(method, "performContextMenuIdentifierAction");
                    activity.getWindow().performContextMenuIdentifierAction(menuItemId, 0);
                }
            });
            TestSuite.waitForIdleSync(mTestCase.getInstrumentation());
        }
        MyLog.v(method, "invokeContextMenuAction ended " + success);
        return success;
    }

    private void longClickAtPosition(final String method, final int position) throws InterruptedException {
        final View view = getListView().getChildAt(position);
        mTestCase.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                MyLog.v(method, "performLongClick on " + view + " at position " + position);
                view.performLongClick();
            }
        });
        TestSuite.waitForIdleSync(mTestCase.getInstrumentation());
    }

    public ListView getListView() {
        return (ListView) mActivity.findViewById(android.R.id.list);
    }

    public long getListItemIdOfReply() {
        long idOut = -1;
        for (int ind = 0; ind < getListView().getCount(); ind++) {
            long itemId = getListView().getAdapter().getItemId(ind);
            if (MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, itemId) != 0) {
                idOut = itemId;
                break;
            }
        }
        return idOut;
    }

    public int getPositionOfListItemId(long itemId) {
        int position = -1;
        for (int ind = 0; ind < getListView().getCount(); ind++) {
            if (itemId == getListView().getAdapter().getItemId(ind)) {
                position = ind; 
                break;
            }
        }
        return position;
    }

    public long getListItemIdAtPosition(int position) {
        long itemId = 0;
        if(position >= 0 && position < getListView().getCount()) {
            itemId = getListView().getAdapter().getItemId(position);
        }
        return itemId;
    }
    
    public void clickListPosition(final String method, final int position) throws InterruptedException {
        mTestCase.getInstrumentation().runOnMainSync(new Runnable() {
            // See
            // http://stackoverflow.com/questions/8094268/android-listview-performitemclick
            @Override
            public void run() {
                long listItemId = mActivity.getListAdapter().getItemId(position);
                MyLog.v(this, method + "-Log on performClick, listItemId=" + listItemId);
                getListView().performItemClick(
                        getListView().getAdapter().getView(position, null, null),
                        position, listItemId);
            }
        });
        TestSuite.waitForIdleSync(mTestCase);
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
    
    public void clickView(final String method, int resourceId) throws InterruptedException {
        clickView(method, mActivity.findViewById(resourceId));
    }
    
    public void clickView(final String method, final View view) throws InterruptedException {
        assertTrue(view != null);
        
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                view.performClick();
            }
          };
    
        MyLog.v(this, method + "-Log before run clicker");
        mTestCase.getInstrumentation().runOnMainSync(clicker);
        TestSuite.waitForIdleSync(mTestCase);
    }
}
