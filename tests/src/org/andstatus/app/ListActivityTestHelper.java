package org.andstatus.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.ListActivity;
import android.app.Instrumentation.ActivityMonitor;
import android.test.ActivityInstrumentationTestCase2;
import android.test.ActivityTestCase;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.util.MyLog;

public class ListActivityTestHelper<T extends ListActivity> extends InstrumentationTestCase {
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

    public void invokeContextMenuAction(String method, int position, ContextMenuItem menuItem) throws InterruptedException {
        selectListPosition(method, position);
        MyLog.v(this, method + "; before invokeContextMenuAction on item=" + menuItem);
        
        invokeContextMenuAction(mTestCase.getInstrumentation(), mActivity, getListView()
                .getChildAt(position), menuItem.getId());
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
                MyLog.v(this, method + " on     setSelection " + position 
                        + " of " + (la.getCount() - 1));
                getListView().setSelection(position);
            }
        });
        TestSuite.waitForIdleSync(mTestCase);
        MyLog.v(this, method + " after  setSelection");
    }

    /**
     * InstrumentationTestCase.getInstrumentation().invokeContextMenuAction doesn't work properly
     *
     * Note: This method cannot be invoked on the main thread.
     * See https://github.com/google/google-authenticator-android/blob/master/tests/src/com/google/android/apps/authenticator/TestUtilities.java
     */
    public static void invokeContextMenuAction(
        Instrumentation instrumentation, final Activity activity, final View view, final int itemId) throws InterruptedException {
        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                MyLog.v("invokeContextMenuAction", "performLongClick");
                view.performLongClick();
            }
        });
        TestSuite.waitForIdleSync(instrumentation);

        instrumentation.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                MyLog.v("invokeContextMenuAction", "performContextMenuIdentifierAction");
                activity.getWindow().performContextMenuIdentifierAction(itemId, 0);
            }
        });
        TestSuite.waitForIdleSync(instrumentation);
        MyLog.v("invokeContextMenuAction", "ended");
    } 
    
    public ListView getListView() {
        return (ListView) mActivity.findViewById(android.R.id.list);
    }

    public int getPositionOfReply() {
        int position = -1;
        for (int ind = 0; ind < getListView().getCount(); ind++) {
            long itemId = getListView().getAdapter().getItemId(ind);
            if (MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, itemId) != 0) {
                position = ind; 
                break;
            }
        }
        return position;
    }

    public int getPositionOfItemId(long itemId) {
        int position = -1;
        for (int ind = 0; ind < getListView().getCount(); ind++) {
            if (itemId == getListView().getAdapter().getItemId(ind)) {
                position = ind; 
                break;
            }
        }
        return position;
    }

    public long getItemIdAtPosition(int position) {
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
                long rowId = ((ListActivity) mActivity).getListAdapter().getItemId(position);
                MyLog.v(this, method + "-Log on performClick, rowId=" + rowId);
                getListView().performItemClick(
                        getListView().getAdapter().getView(position, null, null),
                        position, rowId);
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
        mTestCase.getInstrumentation().waitForIdleSync();
    }
    
    public void clickMenuItem(String method, int menuItemResourceId) throws InterruptedException {
        assertTrue(menuItemResourceId != 0);

        MyLog.v(this, method + "-Log before run clicker");
        if (!mTestCase.getInstrumentation().invokeMenuActionSync(mActivity, menuItemResourceId, 0)) {
            MyLog.w(this, method + "-Log returned false");
        }
        Thread.sleep(500);
        mTestCase.getInstrumentation().waitForIdleSync();
    }
    
}
