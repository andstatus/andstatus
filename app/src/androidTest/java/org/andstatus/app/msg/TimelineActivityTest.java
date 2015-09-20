/**
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

import android.app.Activity;
import android.content.Intent;
import android.widget.ListView;

import org.andstatus.app.ListActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.ConversationInserter;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceEventsBroadcaster;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceState;
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
        MyLog.setLogToFile(true);
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MyContextHolder.get().persistentAccounts().setCurrentAccount(ma);
        
        Intent intent = new Intent(Intent.ACTION_VIEW, 
                MatchedUri.getTimelineUri(ma.getUserId(), TimelineType.HOME, false, 0));
        setActivityIntent(intent);
        
        mActivity = getActivity();

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
    
    public void testOpeningConversationActivity() throws InterruptedException {
        final String method = "testOpeningConversationActivity";
        TestSuite.waitForListLoaded(this, mActivity, 10);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, ConversationActivity.class); 
        long msgId = helper.getListItemIdOfReply();
        helper.selectListPosition(method, helper.getPositionOfListItemId(msgId));
        helper.clickListAtPosition(method, helper.getPositionOfListItemId(msgId));
        Activity nextActivity = helper.waitForNextActivity(method, 40000);
        Thread.sleep(500);
        nextActivity.finish();        
    }

    private ListView getListView() {
        return (ListView) mActivity.findViewById(android.R.id.list);
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
        TestSuite.waitForListLoaded(this, mActivity, position0 + 2);
        
        new ListActivityTestHelper<TimelineActivity>(this, mActivity).selectListPosition(method, position0);
        int position1 = getListView().getFirstVisiblePosition();
        long itemId = getListView().getAdapter().getItemId(position1);
        int count1 = getListView().getAdapter().getCount() - 1;

        new ConversationInserter().insertConversation("p" + iterationId);
        broadcastCommandExecuted();
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
                if (attempt == 3) {
                    MyLog.v(this, "New messages were not loaded, repeating broadcast command executed");
                    broadcastCommandExecuted();
                }
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

    private void broadcastCommandExecuted() {
        CommandData commandData = new CommandData(CommandEnum.CREATE_FAVORITE, TestSuite.CONVERSATION_ACCOUNT_NAME);
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.RUNNING)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND)
                .broadcast();
    }
    
    public void testOpeningAccountSelector() throws InterruptedException {
        final String method = "testOpeningAccountSelector";
        TestSuite.waitForListLoaded(this, mActivity, 10);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, AccountSelector.class);
        helper.clickView(method, R.id.selectAccountButton);
        Activity nextActivity = helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(this, nextActivity, 3);
        Thread.sleep(500);
        nextActivity.finish();        
    }

    public void testActAs() throws InterruptedException {
        final String method = "testActAs";
        TestSuite.waitForListLoaded(this, mActivity, 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, AccountSelector.class);
        long msgId = helper.getListItemIdOfReply();
        helper.invokeContextMenuAction4ListItemId(method, msgId, ContextMenuItem.NONEXISTENT);
        long userId1 = getActivity().getContextMenu().getActorUserIdForCurrentMessage();
        String logMsg = "msgId=" + msgId + "; userId1=" + userId1;
        assertTrue(logMsg, userId1 != 0 );

        helper.invokeContextMenuAction4ListItemId(method, msgId, ContextMenuItem.ACT_AS);

        AccountSelector accountSelector = (AccountSelector) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(this, accountSelector, 3);
        ListActivityTestHelper<AccountSelector> asHelper = new ListActivityTestHelper<AccountSelector>(this, accountSelector);
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(userId1);
        asHelper.clickListAtPosition(method, asHelper.getPositionOfListItemId(ma.firstOtherAccountOfThisOrigin().getUserId()));
        Thread.sleep(500);

        long userId2 = getActivity().getContextMenu().getActorUserIdForCurrentMessage();
        logMsg += "; userId2=" + userId2;
        assertTrue(logMsg, userId1 != userId2 );
    }
}
