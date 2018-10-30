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
import org.andstatus.app.activity.ActivityViewItem;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
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
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ActorListTest extends TimelineActivityTest<ActivityViewItem> {
    private long noteId;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        noteId = MyQuery.oidToId(OidEnum.NOTE_OID, demoData.getConversationOriginId(),
                demoData.conversationMentionsNoteOid);

        final Timeline timeline = Timeline.getTimeline(TimelineType.EVERYTHING, 0, Origin.EMPTY);
        long updatedDate = MyQuery.noteIdToLongColumnValue(NoteTable.UPDATED_DATE, noteId);
        timeline.setVisibleItemId(noteId);
        timeline.setVisibleOldestDate(updatedDate);
        timeline.setVisibleY(0);

        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW, timeline.getUri());
    }

    @Test
    public void testActorsOfNote() throws InterruptedException {
        final String method = "testActorsOfNote";
        TestSuite.waitForListLoaded(getActivity(), 2);
        ListActivityTestHelper<TimelineActivity> helper = new ListActivityTestHelper<>(getActivity(), ActorList.class);
        String content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId);
        String logMsg = MyQuery.noteInfoForLog(noteId);

        List<Actor> actors = Actor.fromOriginAndActorOid(demoData.getConversationMyAccount().getOrigin(), "")
                .extractActorsFromContent(content, Actor.EMPTY);
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
        assertTrue("No view item. " + logMsg + "\n" +
                "The note was not found in the timeline " + timelineData, item.nonEmpty());

        assertTrue("Invoked Context menu for " + logMsg, helper.invokeContextMenuAction4ListItemId(method,
                item.getId(), NoteContextMenuItem.ACTORS_OF_NOTE, R.id.note_wrapper));

        ActorList actorList = (ActorList) helper.waitForNextActivity(method, 25000);
        TestSuite.waitForListLoaded(actorList, 1);

        List<ActorViewItem> listItems = actorList.getListLoader().getList();

        assertEquals(listItems.toString(), 5, listItems.size());

        Actor actorE = MyContextHolder.get().users().actors.values().stream()
                .filter(actor -> actor.oid.equals(demoData.conversationAuthorThirdActorOid))
                .findAny().orElse(Actor.EMPTY);
        assertTrue("Found " + demoData.conversationAuthorThirdActorOid
                + " cached " + MyContextHolder.get().users().actors, actorE.nonEmpty());
        Actor actorA = getByActorOid(listItems, demoData.conversationAuthorThirdActorOid);
        assertTrue("Found " + demoData.conversationAuthorThirdActorOid + ", " + logMsg, actorA != null);
        compareAttributes(actorE, actorA, false);

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
            assertEquals("Avatar URL", expected.getAvatarUrl(), actual.getAvatarUrl());
            assertEquals("Endpoints", expected.endpoints, actual.endpoints);
        }
        assertEquals("Notes count", expected.notesCount, actual.notesCount);
        assertEquals("Favorites count", expected.favoritesCount, actual.favoritesCount);
        assertEquals("Following (friends) count", expected.followingCount, actual.followingCount);
        assertEquals("Followers count", expected.followersCount, actual.followersCount);
        assertEquals("Created at", expected.getCreatedDate(), actual.getCreatedDate());
        assertEquals("Updated at",
                expected.getUpdatedDate() == DATETIME_MILLIS_NEVER
                    ? SOME_TIME_AGO
                    : expected.getUpdatedDate(),
                actual.getUpdatedDate());
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
