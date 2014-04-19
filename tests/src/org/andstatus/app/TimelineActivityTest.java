package org.andstatus.app;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.app.ListActivity;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DataInserterTest;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyService;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyService.ServiceState;
import org.andstatus.app.util.MyLog;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivityTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity activity;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma != null);
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent();
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key, TimelineTypeEnum.HOME.save());
        // In order to shorten opening of activity in a case of large database
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, false);
        setActivityIntent(intent);
        
        activity = getActivity();

        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        MyLog.i(this, "setUp ended");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public TimelineActivityTest() {
        super(TimelineActivity.class);
    }

    public void testBlogButton() throws InterruptedException {
        final String method = "testBlogButton";
        TestSuite.waitForListLoaded(this, activity);
        
        final Button createMessageButton = (Button) activity.findViewById(R.id.createMessageButton);
        assertTrue(createMessageButton != null);
        assertTrue("Blog button is visible", createMessageButton.getVisibility() == android.view.View.VISIBLE);
        View editorView = activity.findViewById(R.id.message_editor);
        assertTrue(editorView != null);
        Button sendButton = (Button) activity.findViewById(R.id.messageEditSendButton);
        assertTrue(sendButton != null);
        assertFalse(editorView.getVisibility() == android.view.View.VISIBLE);

        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                createMessageButton.performClick();
            }
          };
        
        MyLog.v(this, method + "-Log before run clicker 1");
        activity.runOnUiThread(clicker);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
        assertTrue("Editor appeared", editorView.getVisibility() == android.view.View.VISIBLE);

        MyLog.v(this, method + "-Log before run clicker 2");
        activity.runOnUiThread(clicker);
        getInstrumentation().waitForIdleSync();
        Thread.sleep(500);
        assertFalse("Editor hidden again", editorView.getVisibility() == android.view.View.VISIBLE);
    }
    
    public void testOpeningConversationActivity() throws InterruptedException {
        final String method = "testOpeningConversationActivity";
        TestSuite.waitForListLoaded(this, activity);
        
        selectListPosition(method, 0);

        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(ConversationActivity.class.getName(), null, false);
        
        activity.runOnUiThread(new Runnable() {
            // See
            // http://stackoverflow.com/questions/8094268/android-listview-performitemclick
            @Override
            public void run() {
                int position = 0;
                long rowId = ((ListActivity) activity).getListAdapter().getItemId(position);
                MyLog.v(this, method + "-Log on performClick, rowId=" + rowId);
                getListView().performItemClick(
                        getListView().getAdapter().getView(position, null, null),
                        position, rowId);
            }
        });

        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 40000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(this, nextActivity);
        Thread.sleep(500);
        nextActivity.finish();        
    }

    private void selectListPosition(final String method, final int positionIn) throws InterruptedException {
        TestSuite.waitForIdleSync(this);
        MyLog.v(this, method + " before setSelection " + positionIn);
        
        activity.runOnUiThread(new Runnable() {
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
        TestSuite.waitForIdleSync(this);
        MyLog.v(this, method + " after  setSelection");
    }

    private ListView getListView() {
        return (ListView) activity.findViewById(android.R.id.list);
    }

    public void testPositionOnContentChange() throws Exception {
        final String method = "testPositionOnContentChange";
        TestSuite.waitForListLoaded(this, activity);
        
        int position1 = 20;
        selectListPosition(method, position1);
        Thread.sleep(2000);
        long itemId = getListView().getAdapter().getItemId(position1);
        int count1 = getListView().getAdapter().getCount();
        new DataInserterTest().insertConversation("p1");
        CommandData commandData = new CommandData(CommandEnum.CREATE_FAVORITE, TestSuite.CONVERSATION_ACCOUNT_NAME);
        MyService.broadcastState(activity, ServiceState.RUNNING, commandData);
        int firstScrollPos = 0;
        int count2 = 0;
        boolean found = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            TestSuite.waitForIdleSync(this);
            Thread.sleep(2000 * (attempt + 1));
            firstScrollPos = getListView().getFirstVisiblePosition();
            for (int ind = 0; ind < 2; ind++) {
                count2 = getListView().getAdapter().getCount();
                if (itemId == getListView().getAdapter().getItemId(firstScrollPos + ind)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                break;
            }
        }
        assertTrue("The item was found. "
                + "position1=" + position1 + " of " + count1
                + "; position2=" + firstScrollPos + " of " + count2, found);
        
    }
    
    public void testOpeningAccountSelector() throws InterruptedException {
        final String method = "testOpeningAccountSelector";
        TestSuite.waitForListLoaded(this, activity);

        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(AccountSelector.class.getName(), null, false);

        final Button accountButton = (Button) activity.findViewById(R.id.selectAccountButton);
        assertTrue(accountButton != null);
        
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                accountButton.performClick();
            }
          };
    
        MyLog.v(this, method + "-Log before run clicker 1");
        activity.runOnUiThread(clicker);
          
        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(this, nextActivity);
        Thread.sleep(500);
        nextActivity.finish();        
    }
}
