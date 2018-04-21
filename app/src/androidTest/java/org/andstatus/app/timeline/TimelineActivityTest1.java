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

import android.app.Activity;
import android.content.Intent;
import android.widget.CheckBox;
import android.widget.ListView;

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.AccountSelector;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.note.ConversationActivity;
import org.andstatus.app.note.NoteContextMenuItem;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceEvent;
import org.andstatus.app.service.MyServiceEventsBroadcaster;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.service.MyServiceState;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.view.SelectorDialog;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * On activity testing: http://developer.android.com/tools/testing/activity_testing.html
 * @author yvolk@yurivolkov.com
 */
public class TimelineActivityTest1 extends TimelineActivityTest<ActivityViewItem> {
    private MyAccount ma = MyAccount.EMPTY;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        MyContextHolder.get().accounts().setCurrentAccount(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW, Timeline.getTimeline(TimelineType.HOME, ma.getActorId(), Origin.EMPTY).getUri());
    }

    @Test
    public void testOpeningConversationActivity() throws InterruptedException {
        final String method = "testOpeningConversationActivity";
        TestSuite.waitForListLoaded(getActivity(), 7);
        assertTrue("MyService is available", MyServiceManager.isServiceAvailable());
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(),
                ConversationActivity.class);
        long noteId = helper.getListItemIdOfLoadedReply();
        helper.selectListPosition(method, helper.getPositionOfListItemId(noteId));
        helper.invokeContextMenuAction4ListItemId(method, noteId, NoteContextMenuItem.OPEN_CONVERSATION, R.id.note_wrapper);
        Activity nextActivity = helper.waitForNextActivity(method, 40000);
        DbUtils.waitMs(method, 500);
        nextActivity.finish();
    }

    private ListView getListView() {
        return (ListView) getActivity().findViewById(android.R.id.list);
    }

    /** It really makes difference if we are near the end of the list or not
     *  This is why we have two similar methods
     */
    @Test
    public void testPositionOnContentChange1() throws MalformedURLException, InterruptedException {
        onePositionOnContentChange(5, 1);
    }

    @Test
    public void testPositionOnContentChange2() throws MalformedURLException, InterruptedException {
        onePositionOnContentChange(0, 2);
    }
    
    private void onePositionOnContentChange(int position0, int iterationId) throws InterruptedException, MalformedURLException {
        final String method = "testPositionOnContentChange" + iterationId;
        TestSuite.waitForListLoaded(getActivity(), 1);
        getInstrumentation().runOnMainSync(() -> getActivity().showList(WhichPage.TOP));
        TestSuite.waitForListLoaded(getActivity(), position0 + 2);

        TimelineData<ActivityViewItem> timelineData = getActivity().getListData();
        for (int ind = 0; ind < timelineData.size(); ind++) {
            ActivityViewItem item = timelineData.getItem(ind);
            assertEquals("Origin of the Item " + ind + " " + item.toString(), ma.getOrigin(), item.origin);
        }

        boolean collapseDuplicates = MyPreferences.isCollapseDuplicates();
        assertEquals(collapseDuplicates, ((CheckBox) getActivity().findViewById(R.id.collapseDuplicatesToggle)).isChecked());
        assertEquals(collapseDuplicates, getActivity().getListData().isCollapseDuplicates());

        new ListActivityTestHelper<>(getActivity()).selectListPosition(method, position0);
        int position1 = getListView().getFirstVisiblePosition();
        long maxDateLoaded1 = getActivity().getListAdapter().getItem(0).getDate();
        long updatedAt1 = getActivity().getListData().updatedAt;
        long itemId = getListView().getAdapter().getItemId(position1);
        int count1 = getListView().getAdapter().getCount();

        demoData.insertPumpIoConversation("p" + iterationId);
        broadcastCommandExecuted();

        long updatedAt2 = 0;
        long maxDateLoaded2 = 0;
        int count2 = 0;
        int position2 = 0;
        int position2Any = -1;
        boolean found = false;
        for (int attempt = 0; attempt < 6; attempt++) {
            TestSuite.waitForIdleSync();
            count2 = getListView().getAdapter().getCount();
            updatedAt2 = getActivity().getListData().updatedAt;
            maxDateLoaded2 = getActivity().getListAdapter().getItem(0).getDate();
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
                    MyLog.v(this, "New notes were not loaded, repeating broadcast command executed");
                    broadcastCommandExecuted();
                }
                if (DbUtils.waitMs(method, 2000 * (attempt + 1))) {
                    break;
                }
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

        assertEquals(collapseDuplicates, ((CheckBox) getActivity().findViewById(R.id.collapseDuplicatesToggle)).isChecked());
        assertEquals(collapseDuplicates, getActivity().getListData().isCollapseDuplicates());
        if (collapseDuplicates) {
            found = false;
            for (int ind = 0; ind < getActivity().getListData().size(); ind++) {
                ViewItem item = getActivity().getListData().getItem(ind);
                if (item.isCollapsed()) {
                    found = true;
                    break;
                }
            }
            assertTrue("Collapsed not found", found);
        }
    }

    private void broadcastCommandExecuted() {
        CommandData commandData = CommandData.newAccountCommand(CommandEnum.LIKE,
                demoData.getConversationMyAccount());
        MyServiceEventsBroadcaster.newInstance(MyContextHolder.get(), MyServiceState.RUNNING)
                .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND)
                .broadcast();
    }

    @Test
    public void testOpeningAccountSelector() throws InterruptedException {
        final String method = "testOpeningAccountSelector";
        TestSuite.waitForListLoaded(getActivity(), 7);
        ListActivityTestHelper<TimelineActivity> helper =
                ListActivityTestHelper.newForSelectorDialog(getActivity(), AccountSelector.getDialogTag());
        helper.clickView(method, R.id.selectAccountButton);
        SelectorDialog selectorDialog = helper.waitForSelectorDialog(method, 15000);
        DbUtils.waitMs(method, 500);
        selectorDialog.dismiss();
    }

    @Test
    public void testActAs() throws InterruptedException {
        final String method = "testActAs";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper =
                ListActivityTestHelper.newForSelectorDialog(getActivity(), AccountSelector.getDialogTag());
        long noteId = helper.getListItemIdOfLoadedReply();
        String logMsg = "noteId:" + noteId
                + "; text:'" + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'";

        // This context menu item doesn't exist
        assertTrue(logMsg, helper.invokeContextMenuAction4ListItemId(method, noteId,
                NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT, R.id.note_wrapper));

        MyAccount actor1 = getActivity().getContextMenu().getActingAccount();
        logMsg += "; actor1:" + actor1;
        assertTrue(logMsg, actor1.isValid());

        ActivityTestHelper.closeContextMenu(getActivity());

        helper.invokeContextMenuAction4ListItemId(method, noteId, NoteContextMenuItem.ACT_AS, R.id.note_wrapper);

        MyAccount actor2 = actor1.firstOtherAccountOfThisOrigin();
        logMsg += ", actor2:" + actor2.getAccountName();
        assertNotSame(logMsg, actor1, actor2);

        helper.selectIdFromSelectorDialog(logMsg, actor2.getActorId());
        DbUtils.waitMs(method, 500);

        MyAccount actor3 = getActivity().getContextMenu().getSelectedActingAccount();
        logMsg += ", actor2Actual:" + actor3.getAccountName();
        assertEquals(logMsg, actor2, actor3);
    }
}
