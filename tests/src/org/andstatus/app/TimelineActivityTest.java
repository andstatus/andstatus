package org.andstatus.app;

import android.app.Activity;
import android.app.Instrumentation.ActivityMonitor;
import android.app.ListActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyPreferences;
import org.andstatus.app.util.MyLog;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivityTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity mActivity;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initialize(this);
        TestSuite.enshureDataAdded();

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma != null);
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        // In order to shorten opening of activity in a case of large database
        MyPreferences.getDefaultSharedPreferences().edit().putBoolean(MyPreferences.KEY_TIMELINE_IS_COMBINED, false).commit();
        
        mActivity = getActivity();
        TestSuite.waitForListLoaded(mActivity);

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
        final Button createMessageButton = (Button) mActivity.findViewById(R.id.createMessageButton);
        assertTrue(createMessageButton != null);
        assertTrue("Blog button is visible", createMessageButton.getVisibility() == android.view.View.VISIBLE);
        View editorView = mActivity.findViewById(R.id.message_editor);
        assertTrue(editorView != null);
        Button sendButton = (Button) mActivity.findViewById(R.id.messageEditSendButton);
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
        mActivity.runOnUiThread(clicker);
        Thread.sleep(5000);
        assertTrue("Editor appeared", editorView.getVisibility() == android.view.View.VISIBLE);

        MyLog.v(this, method + "-Log before run clicker 2");
        mActivity.runOnUiThread(clicker);
        Thread.sleep(5000);
        assertFalse("Editor hidden again", editorView.getVisibility() == android.view.View.VISIBLE);
    }
    
    public void testOpeningConversationActivity() throws InterruptedException {
        final String method = "testOpeningConversationActivity";
        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(ConversationActivity.class.getName(), null, false);

        final ListView listView = (ListView) mActivity.findViewById(android.R.id.list);
        MyLog.v(this, method + "-Log setSelection");
        mActivity.runOnUiThread(new Runnable() {
          // See http://stackoverflow.com/questions/8094268/android-listview-performitemclick
          @Override
          public void run() {
              int position = 0;
              listView.setSelection(position);
              MyLog.v(this, method + "-Log performClick");
              listView.performItemClick(
                      listView.getAdapter().getView(position, null, null), 
                      position, 
                      ((ListActivity) mActivity).getListAdapter().getItemId(position));              
          }
        });

        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 30000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(nextActivity);
        Thread.sleep(500);
        nextActivity.finish();        
    }
    
    public void testOpeningAccountSelector() throws InterruptedException {
        final String method = "testOpeningAccountSelector";
        ActivityMonitor activityMonitor = getInstrumentation().addMonitor(AccountSelector.class.getName(), null, false);

        final Button accountButton = (Button) mActivity.findViewById(R.id.selectAccountButton);
        assertTrue(accountButton != null);
        
        Runnable clicker = new Runnable() {
            @Override
            public void run() {
                MyLog.v(this, method + "-Log before click");
                accountButton.performClick();
            }
          };
    
        MyLog.v(this, method + "-Log before run clicker 1");
        mActivity.runOnUiThread(clicker);
          
        Activity nextActivity = getInstrumentation().waitForMonitorWithTimeout(activityMonitor, 15000);
        MyLog.v(this, method + "-Log after waitForMonitor: " 
                + nextActivity);
        assertNotNull("Next activity is opened and captured", nextActivity);
        TestSuite.waitForListLoaded(nextActivity);
        Thread.sleep(500);
        nextActivity.finish();        
    }
}
