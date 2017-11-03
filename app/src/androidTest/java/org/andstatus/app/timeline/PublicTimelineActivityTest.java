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

package org.andstatus.app.timeline;

import android.content.Intent;
import android.support.test.espresso.action.TypeTextAction;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.msg.BaseMessageViewItem;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.pressImeActionButton;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withResourceName;
import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.util.EspressoUtils.setChecked;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author yvolk@yurivolkov.com
 */
public class PublicTimelineActivityTest extends TimelineActivityTest {
    private MyAccount ma;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOrigin(
                MyContextHolder.get().persistentOrigins().fromName(demoData.GNUSOCIAL_TEST_ORIGIN_NAME));
        assertTrue(ma.isValidAndSucceeded());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);

        assertEquals(ma.getUserId(), MyContextHolder.get().persistentAccounts().getCurrentAccountUserId());
        MyLog.i(this, "setUp ended");

        return new Intent(Intent.ACTION_VIEW,
                MatchedUri.getTimelineUri(Timeline.getTimeline(TimelineType.PUBLIC, ma, 0, null)));
    }

    @Test
    public void testGlobalSearchInOptionsMenu() throws InterruptedException {
        oneSearchTest("testGlobalSearchInOptionsMenu", demoData.GLOBAL_PUBLIC_MESSAGE_TEXT, true);
    }

    @Test
    public void testSearch() throws InterruptedException {
        oneSearchTest("testSearch", demoData.PUBLIC_MESSAGE_TEXT, false);
    }

    private void oneSearchTest(String method, String messageText, boolean internetSearch) throws InterruptedException {
        int menu_id = R.id.search_menu_id;
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        TestSuite.waitForListLoaded(getActivity(), 2);
        assertEquals(ma, getActivity().getCurrentMyAccount());
        assertEquals(TimelineType.PUBLIC, getActivity().getTimeline().getTimelineType());

        assertFalse("Screen is locked", TestSuite.isScreenLocked(getActivity()));
        ActivityTestHelper<TimelineActivity> helper = new ActivityTestHelper<>(getActivity(),
                TimelineActivity.class);
        helper.clickMenuItem(method, menu_id);

        onView(withId(R.id.internet_search)).perform(setChecked(internetSearch));
        onView(withResourceName("search_text")).perform(new TypeTextAction(messageText),
                pressImeActionButton());
        TimelineActivity nextActivity = (TimelineActivity) helper.waitForNextActivity(method, 40000);
        waitForButtonClickedEvidence(nextActivity, method, messageText);
        assertMessagesArePublic(nextActivity, messageText);
        nextActivity.finish();
    }

    private void waitForButtonClickedEvidence(final TimelineActivity timelineActivity, String caption,
                                              String queryString) throws InterruptedException {
        final String method = "waitForButtonClickedEvidence";
        boolean found = false;
        final StringBuilder sb = new StringBuilder();
        for (int attempt = 0; attempt < 6; attempt++) {
            TestSuite.waitForIdleSync();
            
            Runnable probe = new Runnable() {
                @Override
                public void run() {
                    sb.setLength(0);
                    if (timelineActivity != null) {
                        TextView item = (TextView) timelineActivity.findViewById(R.id.timelineTypeButton);
                        if (item != null) {
                            sb.append(item.getText());
                        }
                    }
                }
            };
            timelineActivity.runOnUiThread(probe);
            
            
            if (sb.toString().contains(queryString)) {
                found = true;
                break;
            } else if (DbUtils.waitMs(method, 2000 * (attempt + 1))) {
                break;
            }
        }
        assertTrue(caption + " '" + (sb.toString()) + "'", found);
    }

    private void assertMessagesArePublic(TimelineActivity timelineActivity, String publicMessageText) throws InterruptedException {
        final String method = "assertMessagesArePublic";
        int msgCount = 0;
        for (int attempt=0; attempt < 3; attempt++) {
            TestSuite.waitForIdleSync();
            msgCount = oneAttempt(timelineActivity, publicMessageText);
            if (msgCount > 0 || DbUtils.waitMs(method, 2000 * (attempt + 1))) {
                break;
            }
        }
        assertTrue("Messages found", msgCount > 0);
    }

    private int oneAttempt(TimelineActivity timelineActivity, String publicMessageText) {
        final ViewGroup list = timelineActivity.findViewById(android.R.id.list);
        int msgCount = 0;
        for (int index = 0; index < list.getChildCount(); index++) {
            View messageView = list.getChildAt(index);
            TextView bodyView = messageView.findViewById(R.id.message_body);
            final BaseMessageViewItem viewItem = ListActivityTestHelper.toBaseMessageViewItem(
                    timelineActivity.getListAdapter().getItem(messageView));
            if (bodyView != null) {
                assertTrue("Message #" + viewItem.getId() + " '" + viewItem.getBody()
                                + "' contains '" + publicMessageText + "'\n" + viewItem,
                        String.valueOf(viewItem.getBody()).contains(publicMessageText));
                assertNotEquals("Message #" + viewItem.getId() + " '" + viewItem.getBody()
                        + "' is private" + "\n" + viewItem, TriState.TRUE,
                        MyQuery.msgIdToTriState(MsgTable.PRIVATE, viewItem.getId()));
                msgCount++;
            }
        }
        MyLog.v(this, "Public messages with '" + publicMessageText + "' found: " + msgCount);
        return msgCount;
    }
}
