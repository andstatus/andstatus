/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

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
        TestSuite.waitForListLoaded(this, activity);

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
        assertFalse("Screen is locked", TestSuite.isScreenLocked(activity));
        
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().invokeMenuActionSync(activity, R.id.global_search_menu_id, 0);
        TestSuite.waitForIdleSync(this);
        getInstrumentation().sendStringSync(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        TestSuite.waitForIdleSync(this);
        Button view = (Button) activity.getWindow().getDecorView().findViewById(R.id.timelineTypeButton);
        assertTrue("Global search menu item clicked '" + view.getText() + "'", String.valueOf(view.getText()).contains(" *"));
        TestSuite.waitForIdleSync(this);
        assertMessagesArePublic(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
    }
    
    public void testSearch() throws InterruptedException {
        assertFalse("Screen is locked", TestSuite.isScreenLocked(activity));

        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_MENU);
        getInstrumentation().invokeMenuActionSync(activity, R.id.search_menu_id, 0);
        TestSuite.waitForIdleSync(this);
        getInstrumentation().sendStringSync(TestSuite.PUBLIC_MESSAGE_TEXT);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        TestSuite.waitForIdleSync(this);
        Button view = (Button) activity.getWindow().getDecorView().findViewById(R.id.timelineTypeButton);
        assertTrue("Search menu item clicked '" + view.getText() + "'", String.valueOf(view.getText()).contains(" *"));
        TestSuite.waitForIdleSync(this);
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
