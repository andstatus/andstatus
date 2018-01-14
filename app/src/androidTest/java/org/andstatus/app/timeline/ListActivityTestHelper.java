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

package org.andstatus.app.timeline;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.list.ContextMenuItem;
import org.andstatus.app.list.MyBaseListActivity;
import org.andstatus.app.msg.BaseMessageViewItem;
import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.view.SelectorDialog;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ListActivityTestHelper<T extends MyBaseListActivity> {
    private final T mActivity;
    private ActivityMonitor mActivityMonitor = null;
    private String dialogTagToMonitor = null;
    private SelectorDialog dialogToMonitor = null;

    public ListActivityTestHelper(T activity) {
        super();
        mActivity = activity;
    }

    public ListActivityTestHelper(T activity, Class<? extends Activity> classOfActivityToMonitor) {
        super();
        addMonitor(classOfActivityToMonitor);
        mActivity = activity;
    }

    public static <T extends MyBaseListActivity> ListActivityTestHelper<T> newForSelectorDialog( T activity,
                                                              String dialogTagToMonitor) {
        ListActivityTestHelper<T> helper = new ListActivityTestHelper<T>(activity);
        helper.dialogTagToMonitor = dialogTagToMonitor;
        return helper;
    }

    /**
     * @return success
     */
    public boolean invokeContextMenuAction4ListItemId(String methodExt, long listItemId, ContextMenuItem menuItem,
                                                      int childViewId) throws InterruptedException {
        final String method = "invokeContextMenuAction4ListItemId";
        boolean success = false;
        String msg = "";
        for (long attempt = 1; attempt < 4; attempt++) {
            TestSuite.waitForIdleSync();
            int position = getPositionOfListItemId(listItemId);
            msg = "listItemId=" + listItemId + "; menu Item=" + menuItem + "; position=" + position + "; attempt=" + attempt;
            MyLog.v(this, msg);
            if (position >= 0 && getListItemIdAtPosition(position) == listItemId ) {
                selectListPosition(methodExt, position);
                if (invokeContextMenuAction(methodExt, mActivity, position, menuItem.getId(), childViewId)) {
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
        TestSuite.waitForIdleSync();
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                int position = positionIn;
                if (listAdapter.getCount() <= position) {
                    position = listAdapter.getCount() - 1;
                }
                MyLog.v(methodExt, method + " on setSelection " + position
                        + " of " + (listAdapter.getCount() - 1));
                listView.setSelectionFromTop(position + listView.getHeaderViewsCount(), 0);
            }
        });
        TestSuite.waitForIdleSync();
        MyLog.v(methodExt, method + " ended");
    }


    /**
     * @return success
     *
     * Note: This method cannot be invoked on the main thread.
     * See https://github.com/google/google-authenticator-android/blob/master/tests/src/com/google/android/apps/authenticator/TestUtilities.java
     */
    private boolean invokeContextMenuAction(final String methodExt, final MyBaseListActivity activity,
                                            int position, final int menuItemId, int childViewId) throws InterruptedException {
        final String method = "invokeContextMenuAction";
        MyLog.v(methodExt, method + " started on menuItemId=" + menuItemId + " at position=" + position);
        boolean success = false;
        int position1 = position;
        for (long attempt = 1; attempt < 4; attempt++) {
            goToPosition(methodExt, position);
            if (!longClickListAtPosition(methodExt, position1, childViewId)) {
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
            InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    MyLog.v(methodExt, method + "; before performContextMenuIdentifierAction");
                    activity.getWindow().performContextMenuIdentifierAction(menuItemId, 0);
                }
            });
            TestSuite.waitForIdleSync();
        }
        MyLog.v(methodExt, method + " ended " + success);
        return success;
    }

    private boolean longClickListAtPosition(final String methodExt, final int position, int childViewId) throws InterruptedException {
        final View parentView = getViewByPosition(position);
        if (parentView == null) {
            MyLog.i(methodExt, "Parent view at list position " + position + " doesn't exist");
            return false;
        }
        View childView = null;
        if (childViewId != 0) {
            childView = parentView.findViewById(childViewId);
            if (childView == null) {
                MyLog.i(methodExt, "Child view " + childViewId + " at list position " + position + " doesn't exist");
                return false;
            }
        }
        final View viewToClick = childViewId == 0 ? parentView : childView;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                final String msg = "performLongClick on "
                        + (childViewId == 0 ? "" : "child " + childViewId + " ")
                        + viewToClick + " at position " + position;
                MyLog.v(methodExt, msg);
                try {
                    viewToClick.performLongClick();
                } catch (Exception e) {
                    MyLog.e(msg, e);
                }
            }
        });
        TestSuite.waitForIdleSync();
        return true;
    }

    public void goToPosition(final String methodExt, final int position) throws InterruptedException {
        final ListView listView = getListView();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                final String msg = "goToPosition " + position;
                MyLog.v(methodExt, msg);
                try {
                    listView.setSelectionFromTop(position + listView.getHeaderViewsCount(), 0);
                } catch (Exception e) {
                    MyLog.e(msg, e);
                }
            }
        });
        TestSuite.waitForIdleSync();
    }

    // See http://stackoverflow.com/questions/24811536/android-listview-get-item-view-by-position
    public View getViewByPosition(int position) {
        return getViewByPosition(position, getListView(), getListAdapter());
    }

    public View getViewByPosition(int position, ListView listView, final ListAdapter listAdapter) {
        final String method = "getViewByPosition";
        final int firstListItemPosition = listView.getFirstVisiblePosition() - listView.getHeaderViewsCount();
        final int lastListItemPosition = firstListItemPosition + listView.getChildCount() - 1 - listView.getHeaderViewsCount();
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
        final String method = "getListItemIdOfLoadedReply";
        long idOut = 0;
        for (int ind = 0; ind < getListAdapter().getCount(); ind++) {
            BaseMessageViewItem item = toBaseMessageViewItem(getListAdapter().getItem(ind));
            if (!item.isEmpty()) {
                if (item.inReplyToMsgId != 0 && item.msgStatus == DownloadStatus.LOADED) {
                    DownloadStatus statusOfReplied = DownloadStatus.load(
                            MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, item.inReplyToMsgId));
                    if (statusOfReplied == DownloadStatus.LOADED) {
                        MyLog.v(this, method + ": found " + item);
                        idOut = getListAdapter().getItemId(ind);
                        break;
                    }
                    MyLog.v(this, method + ": found reply to not loaded message: " + item);
                }
            }
        }
        assertNotEquals( method + " in " + getListAdapter(), 0, idOut);
        return idOut;
    }

    @NonNull
    static BaseMessageViewItem toBaseMessageViewItem(Object objItem) {
        if (ActivityViewItem.class.isAssignableFrom(objItem.getClass())) {
            return ((ActivityViewItem) objItem).message;
        } else if (BaseMessageViewItem.class.isAssignableFrom(objItem.getClass())) {
            return ((BaseMessageViewItem) objItem);
        }
        return MessageViewItem.EMPTY;
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                MyLog.v(methodExt, "onPerformClick " + msgLog);

                // One of the two should work
                viewToClick.performClick();
                listView.performItemClick(
                        viewToClick,
                        position + listView.getHeaderViewsCount(), listItemId);

                MyLog.v(methodExt, "afterClick " + msgLog);
            }
        });
        MyLog.v(methodExt, method + " ended, " + msgLog);
        TestSuite.waitForIdleSync();
    }

    public void addMonitor(Class<? extends Activity> classOfActivity) {
        mActivityMonitor = InstrumentationRegistry.getInstrumentation().addMonitor(classOfActivity.getName(), null, false);
    }
    
    public Activity waitForNextActivity(String methodExt, long timeOut) throws InterruptedException {
        Activity nextActivity = InstrumentationRegistry.getInstrumentation().waitForMonitorWithTimeout(mActivityMonitor, timeOut);
        MyLog.v(methodExt, "After waitForMonitor: " + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(nextActivity, 2);
        mActivityMonitor = null;
        return nextActivity;
    }

    public SelectorDialog waitForSelectorDialog(String methodExt, int timeout) throws InterruptedException {
        final String method = "waitForSelectorDialog";
        SelectorDialog selectorDialog = null;
        boolean isVisible = false;
        for (int ind=0; ind<20; ind++) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
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
    
        InstrumentationRegistry.getInstrumentation().runOnMainSync(clicker);
        MyLog.v(methodExt, "After click view");
        TestSuite.waitForIdleSync();
    }
}
