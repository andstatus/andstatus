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
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.note.NoteContextMenuItem;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ListScreenTestHelper;
import org.andstatus.app.timeline.TimelineActivity;
import org.andstatus.app.timeline.TimelineActivityTest;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import java.util.List;

import io.vavr.control.Try;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;
import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ActorsScreenTest extends TimelineActivityTest<ActivityViewItem> {
    private long noteId;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);

        noteId = MyQuery.oidToId(OidEnum.NOTE_OID, demoData.getPumpioConversationOrigin().getId(),
                demoData.conversationMentionsNoteOid);
        assertNotEquals("No note with oid " + demoData.conversationMentionsNoteOid, 0, noteId);

        final Timeline timeline = myContextHolder.getNow().timelines().get(TimelineType.EVERYTHING,
                Actor.EMPTY, Origin.EMPTY);
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
        ListScreenTestHelper<TimelineActivity> helper = new ListScreenTestHelper<>(getActivity(), ActorsScreen.class);
        String content = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, noteId);
        String logMsg = MyQuery.noteInfoForLog(getActivity().getMyContext(), noteId);

        List<Actor> actors = Actor.newUnknown(demoData.getPumpioConversationAccount().getOrigin(), GroupType.UNKNOWN)
                .extractActorsFromContent(content, Actor.EMPTY);
        assertEquals(logMsg, 3, actors.size());
        assertEquals(logMsg, "unknownUser", actors.get(2).getUsername());
        assertEquals(logMsg, "unknownUser@example.com", actors.get(2).getUniqueName());
        assertEquals(logMsg, "unknownuser@example.com", actors.get(2).getWebFingerId());

        ActorsScreen actorsScreen = Try.of(() -> tryToOpenActorsScreen(method, helper, logMsg))
                .recover(AssertionError.class, e -> tryToOpenActorsScreen(method, helper, logMsg))
                .getOrElseThrow(AssertionError::new);

        List<ActorViewItem> listItems = actorsScreen.getListLoader().getList();

        assertEquals(listItems.toString(), 5, listItems.size());

        Actor actorE = myContextHolder.getNow().users().actors.values().stream()
                .filter(actor -> actor.oid.equals(demoData.conversationAuthorThirdActorOid))
                .findAny().orElse(Actor.EMPTY);
        assertTrue("Found " + demoData.conversationAuthorThirdActorOid
                + " cached " + myContextHolder.getNow().users().actors, actorE.nonEmpty());
        Actor actorA = getByActorOid(listItems, demoData.conversationAuthorThirdActorOid);
        assertTrue("Not found " + demoData.conversationAuthorThirdActorOid + ", " + logMsg, actorA.nonEmpty());
        compareAttributes(actorE, actorA, false);

        ListScreenTestHelper<ActorsScreen> actorsScreenHelper = new ListScreenTestHelper<>(actorsScreen);
        actorsScreenHelper.clickListAtPosition(method, actorsScreenHelper.getPositionOfListItemId(listItems.get(
                listItems.size() > 2 ? 2 : 0).getActorId()));
        DbUtils.waitMs(method, 500);
    }

    private ActorsScreen tryToOpenActorsScreen(String method, ListScreenTestHelper<TimelineActivity> helper, String logMsg) throws InterruptedException {
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

        ActorsScreen actorsScreen = (ActorsScreen) helper.waitForNextActivity(method, 25000);
        TestSuite.waitForListLoaded(actorsScreen, 1);
        return actorsScreen;
    }

    private void compareAttributes(Actor expected, Actor actual, boolean forActorsScreen) {
        assertEquals("Oid", expected.oid, actual.oid);
        assertEquals("Username", expected.getUsername(), actual.getUsername());
        assertEquals("WebFinger ID", expected.getWebFingerId(), actual.getWebFingerId());
        assertEquals("Display name", expected.getRealName(), actual.getRealName());
        assertEquals("Description", expected.getSummary(), actual.getSummary());
        assertEquals("Location", expected.location, actual.location);
        assertEquals("Profile URL", expected.getProfileUrl(), actual.getProfileUrl());
        assertEquals("Homepage", expected.getHomepage(), actual.getHomepage());
        if (!forActorsScreen) {
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
        return Actor.EMPTY;
    }
}
