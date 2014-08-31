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
import org.andstatus.app.data.ConversationInserter;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceBroadcaster;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceState;
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
        MyLog.setLogToFile(true);
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
        MyLog.setLogToFile(false);
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
        
        selectListPosition(method, getPositionOfReply());

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

    private int getPositionOfReply() {
        int position = 0;
        for (int ind = 0; ind < getListView().getCount(); ind++) {
            long itemId = getListView().getAdapter().getItemId(position);
            if (MyProvider.msgIdToLongColumnValue(MyDatabase.Msg.IN_REPLY_TO_MSG_ID, itemId) != 0) {
                break;
            }
            position++;
        }
        return position;
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

    /** It really makes difference if we are near the end of the list or not
     *  This is why we have two similar methods
     */
    public void testPositionOnContentChange1() throws Exception {
        onePositionOnContentChange(10, 1);
    }

    public void testPositionOnContentChange2() throws Exception {
        onePositionOnContentChange(10, 2);
    }
    
    private void onePositionOnContentChange(int position0, int iterationId) throws InterruptedException, Exception {
        final String method = "testPositionOnContentChange" + iterationId;
        TestSuite.waitForListLoaded(this, activity);
        
        selectListPosition(method, position0);
        int position1 = getListView().getFirstVisiblePosition();
        long itemId = getListView().getAdapter().getItemId(position1);
        int count1 = getListView().getAdapter().getCount() - 1;

        new ConversationInserter().insertConversation("p1");
        CommandData commandData = new CommandData(CommandEnum.CREATE_FAVORITE, TestSuite.CONVERSATION_ACCOUNT_NAME);
        MyServiceBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.RUNNING)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND)
                .broadcast();
        int count2 = 0;
        int position2 = 0;
        int position2Any = -1;
        boolean found = false;
        for (int attempt = 0; attempt < 6; attempt++) {
            TestSuite.waitForIdleSync(this);
            count2 = getListView().getAdapter().getCount() - 1;
            if (count2 > count1) {
                position2 = getListView().getFirstVisiblePosition();
                for (int ind = 0; ind < count2; ind++) {
                    if (itemId == getListView().getAdapter().getItemId(ind)) {
                        position2Any = ind;
                    }
                    if ( ind >= position2 && ind <= position2 + 2 
                            && itemId == getListView().getAdapter().getItemId(ind)) {
                        found = true;
                        break;
                    }
                }
            } else {
                Thread.sleep(2000 * (attempt + 1));
            }
            if (found) {
                break;
            }
        }
        String logText = method +  " The item id=" + itemId + " was " + (found ? "" : " not") + " found. "
                + "position1=" + position1 + " of " + count1
                + "; position2=" + position2 + " of " + count2
                + ((position2Any >=0) ? " foundAt=" + position2Any : "");
        MyLog.v(this, logText);
        assertTrue(logText, found);
        assertTrue("More items loaded; " + logText, count2 > count1);
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
