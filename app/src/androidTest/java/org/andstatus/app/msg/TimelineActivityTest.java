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
import org.andstatus.app.SelectorDialog;
import org.andstatus.app.WhichPage;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.ConversationInserter;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsBroadcaster;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.util.MyLog;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivityTest extends android.test.ActivityInstrumentationTestCase2<TimelineActivity> {

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
        TestSuite.waitForListLoaded(this, getActivity(), 10);
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<TimelineActivity>(this, ConversationActivity.class);
        long msgId = helper.getListItemIdOfLoadedReply();
        helper.selectListPosition(method, helper.getPositionOfListItemId(msgId));
        helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.OPEN_CONVERSATION);
        Activity nextActivity = helper.waitForNextActivity(method, 40000);
        Thread.sleep(500);
        nextActivity.finish();        
    }

    private ListView getListView() {
        return (ListView) getActivity().findViewById(android.R.id.list);
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
        TestSuite.waitForListLoaded(this, getActivity(), 1);
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                getActivity().showList(WhichPage.TOP);
            }
        });
        TestSuite.waitForListLoaded(this, getActivity(), position0 + 2);

        new ListActivityTestHelper<TimelineActivity>(this, getActivity()).selectListPosition(method, position0);
        int position1 = getListView().getFirstVisiblePosition();
        long maxDateLoaded1 = getActivity().getListAdapter().getItem(0).sentDate;
        long updatedAt1 = getActivity().getListAdapter().getPages().updatedAt;
        long itemId = getListView().getAdapter().getItemId(position1);
        int count1 = getListView().getAdapter().getCount();

        new ConversationInserter().insertConversation("p" + iterationId);
        broadcastCommandExecuted();
        long updatedAt2 = 0;
        long maxDateLoaded2 = 0;
        int count2 = 0;
        int position2 = 0;
        int position2Any = -1;
        boolean found = false;
        for (int attempt = 0; attempt < 6; attempt++) {
            TestSuite.waitForIdleSync(this);
            count2 = getListView().getAdapter().getCount();
            updatedAt2 = getActivity().getListAdapter().getPages().updatedAt;
            maxDateLoaded2 = getActivity().getListAdapter().getItem(0).sentDate;
            if (updatedAt2 > updatedAt1) {
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
                + ((position2Any >=0) ? " foundAt=" + position2Any : "")
                + ", updated in " + (updatedAt2 - updatedAt1) + "ms";
        MyLog.v(this, logText);
        assertTrue(logText, found);
        assertTrue("Newer items loaded; " + logText, maxDateLoaded2 > maxDateLoaded1);
    }

    private void broadcastCommandExecuted() {
        CommandData commandData = CommandData.newCommand(CommandEnum.CREATE_FAVORITE,
                TestSuite.getConversationMyAccount());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.RUNNING)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND)
                .broadcast();
    }
    
    public void testOpeningAccountSelector() throws InterruptedException {
        final String method = "testOpeningAccountSelector";
        TestSuite.waitForListLoaded(this, getActivity(), 10);
        ListActivityTestHelper<TimelineActivity> helper =
                ListActivityTestHelper.newForSelectorDialog(this, AccountSelector.getDialogTag());
        helper.clickView(method, R.id.selectAccountButton);
        SelectorDialog selectorDialog = helper.waitForSelectorDialog(method, 15000);
        Thread.sleep(500);
        selectorDialog.dismiss();
    }

    public void testActAs() throws InterruptedException {
        final String method = "testActAs";
        TestSuite.waitForListLoaded(this, getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper =
                ListActivityTestHelper.newForSelectorDialog(this, AccountSelector.getDialogTag());
        long msgId = helper.getListItemIdOfLoadedReply();
        String logMsg = "msgId:" + msgId
                + "; text:'" + MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId) + "'";
        assertTrue(logMsg, helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.NONEXISTENT));
        long userId1 = getActivity().getContextMenu().getActorUserIdForCurrentMessage();
        logMsg += "; userId1=" + userId1;
        assertTrue(logMsg, userId1 != 0);

        helper.invokeContextMenuAction4ListItemId(method, msgId, MessageListContextMenuItem.ACT_AS);

        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(userId1);
        MyAccount ma2 = ma.firstOtherAccountOfThisOrigin();
        logMsg += ", user1:" + ma.getAccountName() + ", user2:" + ma2.getAccountName();
        assertNotSame(logMsg, ma, ma2);

        helper.selectIdFromSelectorDialog(method, ma2.getUserId());
        Thread.sleep(500);

        long userId3 = getActivity().getContextMenu().getActorUserIdForCurrentMessage();
        MyAccount ma3 = MyContextHolder.get().persistentAccounts().fromUserId(userId3);
        logMsg += ", userId2Actual:" + ma3.getAccountName();
        assertEquals(logMsg, ma2.getUserId(), userId3);
    }
}
