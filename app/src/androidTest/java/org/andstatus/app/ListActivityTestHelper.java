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
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.test.ActivityInstrumentationTestCase2;
import android.test.ActivityTestCase;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.util.MyLog;

public class ListActivityTestHelper<T extends MyBaseListActivity> extends InstrumentationTestCase {
    private final Instrumentation mInstrumentation;
    private final T mActivity;
    private ActivityMonitor mActivityMonitor = null;
    private String dialogTagToMonitor = null;
    private SelectorDialog dialogToMonitor = null;

    public ListActivityTestHelper(ActivityTestCase testCase, T activity) {
        this(testCase.getInstrumentation(), activity);
    }

    public ListActivityTestHelper(Instrumentation instrumentation, T activity) {
        super();
        mInstrumentation = instrumentation;
        mActivity = activity;
    }

    public ListActivityTestHelper(ActivityInstrumentationTestCase2<T> testCase, Class<? extends Activity> classOfActivityToMonitor) {
        super();
        mInstrumentation = testCase.getInstrumentation();
        addMonitor(classOfActivityToMonitor);
        mActivity = testCase.getActivity();
    }

    public static ListActivityTestHelper newForSelectorDialog(
            ActivityInstrumentationTestCase2<? extends MyBaseListActivity> testCase,
            String dialogTagToMonitor) {
        ListActivityTestHelper helper =
                new ListActivityTestHelper(testCase.getInstrumentation(), testCase.getActivity());
        helper.dialogTagToMonitor = dialogTagToMonitor;
        return helper;
    }

    /**
     * @return success
     */
    public boolean invokeContextMenuAction4ListItemId(String methodExt, long listItemId, ContextMenuItem menuItem) throws InterruptedException {
        final String method = "invokeContextMenuAction4ListItemId";
        boolean success = false;
        String msg = "";
        for (long attempt = 1; attempt < 4; attempt++) {
            TestSuite.waitForIdleSync(mInstrumentation);
            int position = getPositionOfListItemId(listItemId);
            msg = "listItemId=" + listItemId + "; menu Item=" + menuItem + "; position=" + position + "; attempt=" + attempt;
            MyLog.v(this, msg);
            if (position >= 0 && getListItemIdAtPosition(position) == listItemId ) {
                selectListPosition(methodExt, position);
                if (invokeContextMenuAction(methodExt, mActivity, position, menuItem.getId())) {
                    long id2 = getListItemIdAtPosition(position);
                    if (id2 == listItemId) {
                        success = true;
                        break;
                    } else {
                        MyLog.i(methodExt, method + "; Position changed, now pointing to listItemId=" + id2 + "; " + msg);
                    }
                }
            }
        }
        MyLog.v(methodExt, method + " ended " + success + "; " + msg);
        TestSuite.waitForIdleSync(mInstrumentation);
        return success;
    }

    public void selectListPosition(final String methodExt, final int positionIn) throws InterruptedException {
        selectListPosition(methodExt, positionIn, getListView(), getListAdapter());
    }

    public void selectListPosition(final String methodExt, final int positionIn,
                                   final ListView listView,
                                   final ListAdapter listAdapter) throws InterruptedException {
        final String method = "selectListPosition";
        MyLog.v(methodExt, method + " started; position=" + positionIn);
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                int position = positionIn;
                if (listAdapter.getCount() <= position) {
                    position = listAdapter.getCount() - 1;
                }
                MyLog.v(methodExt, method + " on setSelection " + position
                        + " of " + (listAdapter.getCount() - 1));
                listView.setSelectionFromTop(position, 0);
            }
        });
        TestSuite.waitForIdleSync(mInstrumentation);
        MyLog.v(methodExt, method + " ended");
    }


    /**
     * InstrumentationTestCase.getInstrumentation().invokeContextMenuAction doesn't work properly
     * @return success
     *
     * Note: This method cannot be invoked on the main thread.
     * See https://github.com/google/google-authenticator-android/blob/master/tests/src/com/google/android/apps/authenticator/TestUtilities.java
     */
    private boolean invokeContextMenuAction(final String methodExt, final MyBaseListActivity activity,
                   int position, final int menuItemId) throws InterruptedException {
        final String method = "invokeContextMenuAction";
        MyLog.v(methodExt, method + " started on menuItemId=" + menuItemId + " at position=" + position);
        boolean success = false;
        int position1 = position;
        for (long attempt = 1; attempt < 4; attempt++) {
            if (!longClickListAtPosition(methodExt, position1)) {
                break;
            }
            if (mActivity.getPositionOfContextMenu() == position) {
                success = true;
                break;
            }
            MyLog.i(methodExt, method + "; Context menu created for position " + mActivity.getPositionOfContextMenu()
                    + " instead of " + position
                    + "; was set to " + position1 + "; attempt " + attempt);
            position1 = position + (position1 - mActivity.getPositionOfContextMenu());
        }
        if (success) {
            mInstrumentation.runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    MyLog.v(methodExt, method + "; before performContextMenuIdentifierAction");
                    activity.getWindow().performContextMenuIdentifierAction(menuItemId, 0);
                }
            });
            TestSuite.waitForIdleSync(mInstrumentation);
        }
        MyLog.v(methodExt, method + " ended " + success);
        return success;
    }

    private boolean longClickListAtPosition(final String methodExt, final int position) throws InterruptedException {
        final View viewToClick = getViewByPosition(position);
        if (viewToClick == null) {
            MyLog.i(methodExt, "View at list position " + position + " doesn't exist");
            return false;
        }
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                MyLog.v(methodExt, "performLongClick on " + viewToClick + " at position " + position);
                viewToClick.performLongClick();
            }
        });
        TestSuite.waitForIdleSync(mInstrumentation);
        return true;
    }

    // See http://stackoverflow.com/questions/24811536/android-listview-get-item-view-by-position
    public View getViewByPosition(int position) {
        return getViewByPosition(position, getListView(), getListAdapter());
    }

    public View getViewByPosition(int position, ListView listView, final ListAdapter listAdapter) {
        final String method = "getViewByPosition";
        final int firstListItemPosition = listView.getFirstVisiblePosition();
        final int lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1;
        View view;
        if (position < firstListItemPosition || position > lastListItemPosition ) {
            if (position < 0 || listAdapter == null
                    || listAdapter.getCount() < position + 1) {
                view = null;
            } else {
                view = listAdapter.getView(position, null, listView);
            }
        } else {
            final int childIndex = position - firstListItemPosition;
            view = listView.getChildAt(childIndex);
        }
        MyLog.v(this, method + ": pos:" + position + ", first:" + firstListItemPosition
                + ", last:" + lastListItemPosition + ", view:" + view);
        return view;
    }

    public ListView getListView() {
        return mActivity.getListView();
    }

    public long getListItemIdOfLoadedReply() {
        long idOut = 0;
        for (int ind = 0; ind < getListAdapter().getCount(); ind++) {
            long itemId = getListAdapter().getItemId(ind);
            if (MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_MSG_ID, itemId) != 0) {
                if (DownloadStatus.load(MyQuery.msgIdToStringColumnValue(MsgTable.MSG_STATUS,
                        itemId)) == DownloadStatus.LOADED) {
                    idOut = itemId;
                    break;
                }
            }
        }
        assertTrue("getListItemIdOfReply in " + getListAdapter(), idOut > 0);
        return idOut;
    }

    public int getPositionOfListItemId(long itemId) {
        int position = -1;
        for (int ind = 0; ind < getListAdapter().getCount(); ind++) {
            if (itemId == getListAdapter().getItemId(ind)) {
                position = ind; 
                break;
            }
        }
        return position;
    }

    public long getListItemIdAtPosition(int position) {
        long itemId = 0;
        if(position >= 0 && position < getListAdapter().getCount()) {
            itemId = getListAdapter().getItemId(position);
        }
        return itemId;
    }

    private ListAdapter getListAdapter() {
        return mActivity.getListAdapter();
    }

    public void clickListAtPosition(final String methodExt, final int position) throws InterruptedException {
        clickListAtPosition(methodExt, position, getListView(), getListAdapter());
    }

    public void clickListAtPosition(final String methodExt, final int position,
                                    final ListView listView,
                                    ListAdapter listAdapter) throws InterruptedException {
        final String method = "clickListAtPosition";
        final View viewToClick = getViewByPosition(position, listView, listAdapter);
        final long listItemId = listAdapter.getItemId(position);
        final String msgLog = method + "; id:" + listItemId + ", position:" + position + ", view:" + viewToClick;
        mInstrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                MyLog.v(methodExt, "onPerformClick " + msgLog);

                // One of the two should work
                viewToClick.performClick();
                listView.performItemClick(
                        viewToClick,
                        position, listItemId);

                MyLog.v(methodExt, "afterClick " + msgLog);
            }
        });
        MyLog.v(methodExt, method + " ended, " + msgLog);
        TestSuite.waitForIdleSync(mInstrumentation);
    }

    public void addMonitor(Class<? extends Activity> classOfActivity) {
        mActivityMonitor = mInstrumentation.addMonitor(classOfActivity.getName(), null, false);
    }
    
    public Activity waitForNextActivity(String methodExt, long timeOut) throws InterruptedException {
        Activity nextActivity = mInstrumentation.waitForMonitorWithTimeout(mActivityMonitor, timeOut);
        MyLog.v(methodExt, "After waitForMonitor: " + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(mInstrumentation, nextActivity, 2);
        mActivityMonitor = null;
        return nextActivity;
    }

    public SelectorDialog waitForSelectorDialog(String methodExt, int timeout) throws InterruptedException {
        final String method = "waitForSelectorDialog";
        SelectorDialog selectorDialog = null;
        boolean isVisible = false;
        for (int ind=0; ind<20; ind++) {
            mInstrumentation.waitForIdleSync();
            selectorDialog = (SelectorDialog) mActivity.getSupportFragmentManager().findFragmentByTag(dialogTagToMonitor);
            if (selectorDialog != null && selectorDialog.isVisible()) {
                isVisible = true;
                break;
            }
            if (DbUtils.waitMs(method, 1000)) {
                break;
            }
        }
        assertTrue(methodExt + ": Didn't find SelectorDialog with tag:'" + dialogTagToMonitor + "'", selectorDialog != null);
        assertTrue(isVisible);

        final ListView list = selectorDialog.getListView();
        assertTrue(list != null);
        int itemsCount = 0;
        int minCount = 1;
        for (int ind=0; ind<60; ind++) {
            if (DbUtils.waitMs(method, 2000)) {
                break;
            }
            mInstrumentation.waitForIdleSync();
            int itemsCountNew = list.getCount();
            MyLog.v(methodExt, "waitForSelectorDialog; countNew=" + itemsCountNew + ", prev=" + itemsCount + ", min=" + minCount);
            if (itemsCountNew >= minCount && itemsCount == itemsCountNew) {
                break;
            }
            itemsCount = itemsCountNew;
        }
        assertTrue("There are " + itemsCount + " items (min=" + minCount + ") in the list of " + dialogTagToMonitor,
                itemsCount >= minCount);
        return selectorDialog;
    }

    public void selectIdFromSelectorDialog(String method, long id) throws InterruptedException {
        SelectorDialog selector = waitForSelectorDialog(method, 15000);
        int position = selector.getListAdapter().getPositionById(id);
        assertTrue(method + "; Item id:" + id + " found", position >= 0);
        selectListPosition(method, position, selector.getListView(), selector.getListAdapter());
        clickListAtPosition(method, position, selector.getListView(), selector.getListAdapter());
    }

    // TODO: Unify interface with my List Activity
    public View getSelectorViewByPosition(int position) {
        SelectorDialog selector = dialogToMonitor;
        final String method = "getSelectorViewByPosition";
        final int firstListItemPosition = selector.getListView().getFirstVisiblePosition();
        final int lastListItemPosition = firstListItemPosition + selector.getListView().getChildCount() - 1;
        View view;
        if (position < firstListItemPosition || position > lastListItemPosition ) {
            if (position < 0 || getListAdapter() == null
                    || selector.getListAdapter().getCount() < position + 1) {
                view = null;
            } else {
                view = selector.getListAdapter().getView(position, null, getListView());
            }
        } else {
            final int childIndex = position - firstListItemPosition;
            view = selector.getListView().getChildAt(childIndex);
        }
        MyLog.v(this, method + ": pos:" + position + ", first:" + firstListItemPosition
                + ", last:" + lastListItemPosition + ", view:" + view);
        return view;
    }

    public void clickView(String methodExt, int resourceId) throws InterruptedException {
        clickView(methodExt, mActivity.findViewById(resourceId));
    }
    
    public void clickView(final String methodExt, final View view) throws InterruptedException {
        assertTrue(view != null);
        
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(methodExt, "Before click view");
                view.performClick();
            }
          };
    
        mInstrumentation.runOnMainSync(clicker);
        MyLog.v(methodExt, "After click view");
        TestSuite.waitForIdleSync(mInstrumentation);
    }
}
