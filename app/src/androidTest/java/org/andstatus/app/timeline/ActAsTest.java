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

import org.andstatus.app.ActivityTestHelper;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.note.ConversationActivity;
import org.andstatus.app.note.NoteContextMenuItem;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActAsTest extends TimelineActivityTest<ActivityViewItem> {

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        final MyAccount ma = MyContextHolder.get().accounts()
                .fromAccountName(demoData.gnusocialTestAccountName);
        assertTrue(ma.isValid());
        MyContextHolder.get().accounts().setCurrentAccount(ma);

        MyLog.i(this, "setUp ended");
        final Timeline timeline = Timeline.getTimeline(TimelineType.EVERYTHING, 0, ma.getOrigin());
        timeline.forgetPositionsAndDates();
        return new Intent(Intent.ACTION_VIEW, timeline.getUri());
    }

    @Test
    public void actAsActor() throws InterruptedException {
        final String method = "actAsActor";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(),
                ConversationActivity.class);
        long listItemId = helper.getListItemIdOfLoadedReply();
        long noteId = MyQuery.activityIdToLongColumnValue(ActivityTable.NOTE_ID, listItemId);
        String logMsg = "itemId=" + listItemId + ", noteId=" + noteId + " text='"
                + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId) + "'";
        assertEquals("Default actor", MyAccount.EMPTY, getActivity().getContextMenu().getSelectedActingAccount());

        boolean invoked = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT, R.id.note_wrapper);
        MyAccount actor1 = getActivity().getContextMenu().getSelectedActingAccount();
        logMsg += ";" + (invoked ? "" : " failed to invoke context menu 1," ) + " actor1=" + actor1;
        assertTrue(logMsg, actor1.isValid());

        ActivityTestHelper.closeContextMenu(getActivity());

        logMsg += "MyContext: " + MyContextHolder.get();
        MyAccount firstOtherActor = actor1.firstOtherAccountOfThisOrigin();
        logMsg += "; firstOtherActor=" + firstOtherActor;
        assertNotEquals(logMsg, actor1, firstOtherActor);

        boolean invoked2 = helper.invokeContextMenuAction4ListItemId(method, listItemId,
                NoteContextMenuItem.ACT_AS_FIRST_OTHER_ACCOUNT, R.id.note_wrapper);
        MyAccount actor2 = getActivity().getContextMenu().getSelectedActingAccount();
        logMsg += ";" + (invoked2 ? "" : " failed to invoke context menu 2," ) + " actor2=" + actor2;
        assertNotEquals(logMsg, actor1, actor2);
    }

}
