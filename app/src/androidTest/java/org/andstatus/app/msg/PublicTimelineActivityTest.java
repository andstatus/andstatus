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

package org.andstatus.app.msg;

import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public class PublicTimelineActivityTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {
    private TimelineActivity mActivity;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        assertEquals(ma.getUserId(), MyContextHolder.get().persistentAccounts().getCurrentAccountUserId());
        
        Intent intent = new Intent(Intent.ACTION_VIEW, 
                MatchedUri.getTimelineUri(ma.getUserId(), TimelineType.PUBLIC, false, 0));
        setActivityIntent(intent);
        
        mActivity = getActivity();
        TestSuite.waitForListLoaded(this, mActivity, 2);
        
        assertEquals(ma.getUserId(), mActivity.getCurrentMyAccountUserId());
        assertEquals(TimelineType.PUBLIC, mActivity.getTimelineType());
        
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        MyLog.i(this, "setUp ended");
    }

    @Override
    protected void tearDown() throws Exception {
        mActivity.finish();
        super.tearDown();
    }

    public PublicTimelineActivityTest() {
        super(TimelineActivity.class);
    }
    
    public void testGlobalSearchInOptionsMenu() throws InterruptedException {
        assertFalse("Screen is locked", TestSuite.isScreenLocked(mActivity));

        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<TimelineActivity>(this, mActivity);
        helper.clickMenuItem("Global search", R.id.global_search_menu_id);
        getInstrumentation().sendStringSync(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        waitForButtonClickedEvidence("Global search menu item clicked");
        assertMessagesArePublic(TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT);
    }

    private void waitForButtonClickedEvidence(String caption) throws InterruptedException {
        boolean found = false;
        final StringBuilder sb = new StringBuilder();
        for (int attempt = 0; attempt < 6; attempt++) {
            TestSuite.waitForIdleSync(this);
            
            Runnable clicker = new Runnable() {
                @Override
                public void run() {
                    sb.setLength(0);
                    if (mActivity != null) {
                        TextView item = (TextView) mActivity.findViewById(R.id.timelineTypeButton);
                        if (item != null) {
                            sb.append(item.getText());
                        }
                    }
                }
            };
            mActivity.runOnUiThread(clicker);
            
            
            if (sb.toString().contains(" *")) {
                found = true;
                break;
            } else {
                Thread.sleep(2000 * (attempt + 1));
            }
        }
        assertTrue(caption + " '" + (sb.toString()) + "'", found);
    }
    
    public void testSearch() throws InterruptedException {
        assertFalse("Screen is locked", TestSuite.isScreenLocked(mActivity));

        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<TimelineActivity>(this, mActivity);
        helper.clickMenuItem("Global search", R.id.search_menu_id);
        getInstrumentation().sendStringSync(TestSuite.PUBLIC_MESSAGE_TEXT);
        getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER);
        waitForButtonClickedEvidence("Search menu item clicked");
        assertMessagesArePublic(TestSuite.PUBLIC_MESSAGE_TEXT);
    }

    private void assertMessagesArePublic(String publicMessageText) throws InterruptedException {
        
        int msgCount = 0;
        for (int attempt=0; attempt < 3; attempt++) {
            TestSuite.waitForIdleSync(this);
            msgCount = oneAttempt(publicMessageText);
            if (msgCount > 0) {
                break;
            }
            Thread.sleep(2000 * (attempt + 1));
        }
        assertTrue("Messages found", msgCount > 0);
    }

    private int oneAttempt(String publicMessageText) {
        final ViewGroup list = (ViewGroup) mActivity.findViewById(android.R.id.list);
        int msgCount = 0;
        for (int index = 0; index < list.getChildCount(); index++) {
            View messageView = list.getChildAt(index);
            TextView bodyView = (TextView) messageView.findViewById(R.id.message_body);
            TextView idView = (TextView) messageView.findViewById(R.id.id);
            if (bodyView != null && idView != null) {
                long id = Long.parseLong(String.valueOf(idView.getText()));
                assertTrue("Message #" + id + " '" + bodyView.getText() + "' contains '" + publicMessageText + "'",
                        String.valueOf(bodyView.getText()).contains(publicMessageText));
                long storedPublic = MyQuery.msgIdToLongColumnValue(Msg.PUBLIC, id);
                assertTrue("Message #" + id + " '" + bodyView.getText() + "' is public", storedPublic != 0);
                msgCount++;
            }
        }
        MyLog.v(this, "Public messages with '" + publicMessageText + "' found: " + msgCount);
        return msgCount;
    }
}
