/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.actor;

import android.content.Intent;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.note.NoteContextMenuItem;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ListActivityTestHelper;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActorListTest extends TimelineActivityTest<ActivityViewItem> {

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        final MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        MyContextHolder.get().accounts().setCurrentAccount(ma);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                Timeline.getTimeline(TimelineType.HOME, ma.getActorId(), Origin.EMPTY).getUri());
    }

    @Test
    public void testActorsOfNote() throws InterruptedException {
        final String method = "testActorsOfNote";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(), ActorList.class);
        long noteId = MyQuery.oidToId(OidEnum.NOTE_OID, demoData.getConversationOriginId(),
                demoData.conversationMentionsNoteOid);
        String body = MyQuery.noteIdToStringColumnValue(NoteTable.BODY, noteId);
        String logMsg = MyQuery.noteInfoForLog(noteId);

        List<Actor> actors = Actor.fromOriginAndActorOid(demoData.getConversationMyAccount().getOrigin(), "").extractActorsFromBodyText(body, false);
        assertEquals(logMsg, 3, actors.size());
        assertEquals(logMsg, "unknownUser@example.com", actors.get(2).getUsername());

        ActivityViewItem item = ActivityViewItem.EMPTY;
        TimelineData<ActivityViewItem> timelineData = getActivity().getListData();
        for (int position=0; position < timelineData.size(); position++) {
            ActivityViewItem item2 = timelineData.getItem(position);
            if (item2.noteViewItem.getId() == noteId) {
                item = item2;
                break;
            }
        }
        boolean noteWasFound = !item.equals(ActivityViewItem.EMPTY);
        if (!noteWasFound) {
            item = timelineData.getItem(0);
            String logMsg1 = "The note was not found in the timeline " + timelineData +
                    " new item: " + item;
            logMsg += "\n" + logMsg1;
            MyLog.i(method, logMsg1);
        }

        assertTrue("Invoked Context menu for " + logMsg, helper.invokeContextMenuAction4ListItemId(method,
                item.getId(), NoteContextMenuItem.ACTORS_OF_NOTE, R.id.note_wrapper));

        ActorList actorList = (ActorList) helper.waitForNextActivity(method, 15000);
        TestSuite.waitForListLoaded(actorList, 1);

        List<ActorViewItem> listItems = actorList.getListLoader().getList();

        if (noteWasFound) {
            assertEquals(listItems.toString(), 5, listItems.size());

            Actor actorE = DemoConversationInserter.getActors().get(demoData.conversationAuthorThirdActorOid);
            assertTrue("Found " + demoData.conversationAuthorThirdActorOid + " cached ", actorE != null);
            Actor actorA = getByActorOid(listItems, demoData.conversationAuthorThirdActorOid);
            assertTrue("Found " + demoData.conversationAuthorThirdActorOid + ", " + logMsg, actorA != null);
            compareAttributes(actorE, actorA, true);
        }

        ListActivityTestHelper<ActorList> actorListHelper = new ListActivityTestHelper<>(actorList);
        actorListHelper.clickListAtPosition(method, actorListHelper.getPositionOfListItemId(listItems.get(
                listItems.size() > 2 ? 2 : 0).getActorId()));
        DbUtils.waitMs(method, 500);
    }

    private void compareAttributes(Actor expected, Actor actual, boolean forActorList) {
        assertEquals("Oid", expected.oid, actual.oid);
        assertEquals("Username", expected.getUsername(), actual.getUsername());
        assertEquals("WebFinger ID", expected.getWebFingerId(), actual.getWebFingerId());
        assertEquals("Display name", expected.getRealName(), actual.getRealName());
        assertEquals("Description", expected.getDescription(), actual.getDescription());
        assertEquals("Location", expected.location, actual.location);
        assertEquals("Profile URL", expected.getProfileUrl(), actual.getProfileUrl());
        assertEquals("Homepage", expected.getHomepage(), actual.getHomepage());
        if (!forActorList) {
            assertEquals("Avatar URL", expected.avatarUrl, actual.avatarUrl);
            assertEquals("Banner URL", expected.bannerUrl, actual.bannerUrl);
        }
        assertEquals("Notes count", expected.notesCount, actual.notesCount);
        assertEquals("Favorites count", expected.favoritesCount, actual.favoritesCount);
        assertEquals("Following (friends) count", expected.followingCount, actual.followingCount);
        assertEquals("Followers count", expected.followersCount, actual.followersCount);
        assertEquals("Created at", expected.getCreatedDate(), actual.getCreatedDate());
        assertEquals("Updated at", expected.getUpdatedDate(), actual.getUpdatedDate());
    }

    static Actor getByActorOid(List<ActorViewItem> listItems, String oid) {
        for (ActorViewItem item : listItems) {
            if (item.actor.oid.equals(oid)) {
                return item.actor;
            }
        }
        return null;
    }
}
