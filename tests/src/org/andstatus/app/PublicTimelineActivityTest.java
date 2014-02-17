package org.andstatus.app;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.TimelineTypeEnum;
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
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        Thread.sleep(1000);
        Button view = (Button) activity.getWindow().getDecorView().findViewById(R.id.timelineTypeButton);
        assertTrue("Global search menu item clicked '" + view.getText() + "'", String.valueOf(view.getText()).contains(" *"));
        Thread.sleep(2000);
    }
    
    public void testSearch() throws InterruptedException {
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().invokeMenuActionSync(activity, R.id.search_menu_id, 0);
        Thread.sleep(1000);
        getInstrumentation().sendStringSync(TestSuite.PUBLIC_MESSAGE_TEXT);
        getInstrumentation().waitForIdleSync();
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        Thread.sleep(1000);
        Button view = (Button) activity.getWindow().getDecorView().findViewById(R.id.timelineTypeButton);
        assertTrue("Search menu item clicked '" + view.getText() + "'", String.valueOf(view.getText()).contains(" *"));
        Thread.sleep(2000);
    }
}
