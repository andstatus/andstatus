package org.andstatus.app;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;

/**
 * @author yvolk@yurivolkov.com
 */
public class PublicTimelineActivityTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity activity;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.STATUSNET_TEST_ACCOUNT_NAME);
        assertTrue(ma != null);
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent();
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key, TimelineTypeEnum.PUBLIC.save());
        // In order to shorten opening of activity in a case of large database
        intent.putExtra(IntentExtra.EXTRA_TIMELINE_IS_COMBINED.key, false);
        setActivityIntent(intent);
        
        activity = getActivity();
        TestSuite.waitForListLoaded(activity);

        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        MyLog.i(this, "setUp ended");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public PublicTimelineActivityTest() {
        super(TimelineActivity.class);
    }
    
    public void testGlobalSearchInOptionsMenu() throws InterruptedException {
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().invokeMenuActionSync(activity, R.id.global_search_menu_id, 0);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendStringSync(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        getInstrumentation().waitForIdleSync();
        Button view = (Button) activity.getWindow().getDecorView().findViewById(R.id.timelineTypeButton);
        assertTrue("Global search menu item clicked '" + view.getText() + "'", String.valueOf(view.getText()).contains(" *"));
        Thread.sleep(1000);
    }
    
    public void testSearch() throws InterruptedException {
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().invokeMenuActionSync(activity, R.id.search_menu_id, 0);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendStringSync(TestSuite.PUBLIC_MESSAGE_TEXT);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        getInstrumentation().waitForIdleSync();
        Button view = (Button) activity.getWindow().getDecorView().findViewById(R.id.timelineTypeButton);
        assertTrue("Search menu item clicked '" + view.getText() + "'", String.valueOf(view.getText()).contains(" *"));
        Thread.sleep(1000);
        assertMessagesArePublic(TestSuite.PUBLIC_MESSAGE_TEXT);
    }

    private void assertMessagesArePublic(String publicMessageText) {
        final ViewGroup list = (ViewGroup) activity.findViewById(android.R.id.list);
        int msgCount = 0;
        for (int index = 0; index < list.getChildCount(); index++) {
            View messageView = list.getChildAt(index);
            TextView bodyView = (TextView) messageView.findViewById(R.id.message_body);
            TextView idView = (TextView) messageView.findViewById(R.id.id);
            if (bodyView != null && idView != null) {
                long id = Long.parseLong(String.valueOf(idView.getText()));
                assertTrue("Message #" + id + " '" + bodyView.getText() + "' contains '" + publicMessageText + "'",
                        String.valueOf(bodyView.getText()).contains(publicMessageText));
                long storedPublic = MyProvider.msgIdToLongColumnValue(Msg.PUBLIC, id);
                assertTrue("Message #" + id + " '" + bodyView.getText() + "' is public", storedPublic != 0);
                msgCount++;
            }
        }
        MyLog.v(this, "Public messages with '" + publicMessageText + "' found: " + msgCount);
        assertTrue("Messages found", msgCount > 0);
    }
}
