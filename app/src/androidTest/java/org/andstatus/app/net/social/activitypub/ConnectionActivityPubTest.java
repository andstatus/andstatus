/* 
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.activitypub;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.net.social.InputActorPage;
import org.andstatus.app.net.social.InputTimelinePage;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.net.social.activitypub.VerifyCredentialsActivityPubTest.ACTOR_OID;
import static org.andstatus.app.net.social.activitypub.VerifyCredentialsActivityPubTest.UNIQUE_NAME_IN_ORIGIN;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.text.IsEmptyString.emptyString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ConnectionActivityPubTest {
    private ConnectionMock mock;

    String pawooActorOid = "https://pawoo.net/users/pawooAndStatusTester";
    String pawooNoteOid = "https://pawoo.net/users/pawooAndStatusTester/statuses/101727836012435643";

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
        mock = ConnectionMock.newFor(demoData.activityPubTestAccountName);
    }

    @Test
    public void getTimeline() throws IOException {
        String sinceId = "";
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma);
        Actor actorForTimeline = Actor.fromOid(mock.getData().getOrigin(), ACTOR_OID)
                .withUniqueName(UNIQUE_NAME_IN_ORIGIN);
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox");
        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.of(sinceId), TimelinePosition.EMPTY, 20, actorForTimeline).get();
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 5, timeline.size());

        AActivity activity = timeline.get(4);
        assertEquals("Creating a Note " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Note oid " + note, "https://pleroma.site/objects/34ab2ec5-4307-4e0b-94d6-a789d4da1240", note.oid);
        assertEquals("Conversation oid " + note,"https://pleroma.site/contexts/c62ba280-2a11-473e-8bd1-9435e9dc83ae",
                note.conversationOid);
        assertEquals("Note name " + note, "", note.getName());
        assertThat("Note body " + note, note.getContent(), startsWith("We could successfully create an account"));
        assertEquals("Activity updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
                TestSuite.utcTime(activity.getUpdatedDate()).toString());
        assertEquals("Note updated at " + TestSuite.utcTime(note.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
                TestSuite.utcTime(note.getUpdatedDate()).toString());
        Actor actor = activity.getActor();
        assertEquals("Actor's oid " + activity, "https://pleroma.site/users/ActivityPubTester", actor.oid);
        assertEquals("Actor's Webfinger " + activity, "", actor.getWebFingerId());

        assertEquals("Actor is an Author", actor, activity.getAuthor());
        assertEquals("Should be Create " + activity, ActivityType.CREATE, activity.type);
        assertEquals("Favorited by me " + activity, TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor));

        activity = timeline.get(3);
        assertEquals("Is not FOLLOW " + activity, ActivityType.FOLLOW, activity.type);
        assertEquals("Actor", "https://pleroma.site/users/ActivityPubTester", activity.getActor().oid);
        assertEquals("Actor is my friend", TriState.UNKNOWN, activity.getActor().isMyFriend);
        assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType());
        Actor objActor = activity.getObjActor();
        assertEquals("objActor followed", "https://pleroma.site/users/AndStatus", objActor.oid);
        assertEquals("Actor is my friend", TriState.UNKNOWN, objActor.isMyFriend);

        for (int ind = 0; ind < 3; ind++) {
            activity = timeline.get(ind);
            assertEquals("Is not UPDATE " + activity, ActivityType.UPDATE, activity.type);
            assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType());
            objActor = activity.getObjActor();
            assertEquals("Actor is my friend", TriState.UNKNOWN, objActor.isMyFriend);
            assertEquals("Url of objActor", "https://pleroma.site/users/AndStatus", objActor.getProfileUrl());
            assertEquals("WebFinger ID", "andstatus@pleroma.site", objActor.getWebFingerId());
            assertEquals("Avatar", "https://pleroma.site/media/c5f60f06-6620-46b6-b676-f9f4571b518e/bfa1745b8c221225cc6551805d9eaa8bebe5f36fc1856b4924bcfda5d620334d.png",
                    objActor.getAvatarUrl());
        }
    }

    @Test
    public void getNotesByActor() throws IOException {
        String ACTOR_OID2 = "https://pleroma.site/users/kaniini";
        String sinceId = "";
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_outbox_pleroma);
        Actor actorForTimeline = Actor.fromOid(mock.getData().getOrigin(), ACTOR_OID2)
                .withUniqueName(UNIQUE_NAME_IN_ORIGIN);
        actorForTimeline.endpoints.add(ActorEndpointType.API_OUTBOX, ACTOR_OID2 + "/outbox");
        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.ACTOR_TIMELINE,
                TimelinePosition.of(sinceId), TimelinePosition.EMPTY, 20, actorForTimeline).get();
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 10, timeline.size());

        AActivity activity = timeline.get(2);
        assertEquals("Announcing " + activity, ActivityType.ANNOUNCE, activity.type);
        assertEquals("Announcing a Note " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Note oid " + note, "https://lgbtq.cool/users/abby/statuses/101702144808655868", note.oid);
        Actor actor = activity.getActor();
        assertEquals("Actor's oid " + activity, ACTOR_OID2, actor.oid);

        assertEquals("Author is unknown", Actor.EMPTY, activity.getAuthor());
    }

    @Test
    public void noteFromPawooNet() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_note_from_pawoo_net_pleroma);
        AActivity activity8 = mock.connection.getNote(pawooNoteOid).get();
        assertEquals("Updating " + activity8, ActivityType.UPDATE, activity8.type);
        assertEquals("Acting on a Note " + activity8, AObjectType.NOTE, activity8.getObjectType());
        Note note8 = activity8.getNote();
        assertEquals("Note oid " + note8, pawooNoteOid, note8.oid);
        Actor author = activity8.getAuthor();
        assertEquals("Author's oid " + activity8, pawooActorOid, author.oid);
        assertEquals("Actor is author", author, activity8.getActor());
        assertThat("Note body " + note8, note8.getContent(),
                containsString("how two attached images may look like"));
        assertEquals("Note updated at " + TestSuite.utcTime(note8.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.MARCH, 10, 18, 46, 31).toString(),
                TestSuite.utcTime(note8.getUpdatedDate()).toString());
    }

    @Test
    public void getTimeline2() throws IOException {
        String sinceId = "";
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma_2);
        Actor actorForTimeline = Actor.fromOid(mock.getData().getOrigin(), ACTOR_OID)
                .withUniqueName(UNIQUE_NAME_IN_ORIGIN);
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox");
        InputTimelinePage timeline = mock.connection.getTimeline(true, Connection.ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.of(sinceId), TimelinePosition.EMPTY, 20, actorForTimeline).get();
        assertNotNull("timeline returned", timeline);
        assertEquals("Number of items in the Timeline", 10, timeline.size());

        AActivity activity8 = timeline.get(8);
        assertEquals("Creating " + activity8, ActivityType.CREATE, activity8.type);
        assertEquals("Acting on a Note " + activity8, AObjectType.NOTE, activity8.getObjectType());
        Note note8 = activity8.getNote();
        assertEquals("Note oid " + note8, pawooNoteOid, note8.oid);
        Actor author = activity8.getAuthor();
        assertEquals("Author's oid " + activity8, pawooActorOid, author.oid);
        assertEquals("Actor is author", author, activity8.getActor());
        assertThat("Note summary should be absent " + note8, note8.getSummary(), is(emptyString()));
        assertThat("Note body " + note8, note8.getContent(),
                containsString("how two attached images may look like"));
        assertEquals("Note updated at " + TestSuite.utcTime(note8.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.MARCH, 10, 18, 46, 31).toString(),
                TestSuite.utcTime(note8.getUpdatedDate()).toString());

        assertEquals("Media attachments " + note8.attachments, 2, note8.attachments.size());
        Attachment attachment0 = note8.attachments.list.get(0);
        assertEquals("Content type", MyContentType.IMAGE, attachment0.contentType);
        assertEquals("Media URI", UriUtils.fromString("https://img.pawoo.net/media_attachments/files/013/102/220/original/b70c78bee2bf7c99.jpg"),
                attachment0.getUri());
        Attachment attachment1 = note8.attachments.list.get(1);
        assertEquals("Content type", MyContentType.IMAGE, attachment1.contentType);
        assertEquals("Media URI", UriUtils.fromString("https://img.pawoo.net/media_attachments/files/013/102/261/original/104659a0cd852f39.jpg"),
                attachment1.getUri());

        AActivity activity9 = timeline.get(9);
        assertEquals("Creating a Note " + activity9, AObjectType.NOTE, activity9.getObjectType());
        Note note9 = activity9.getNote();
        assertEquals("Timeline position " + activity9,
                "https://pleroma.site/users/AndStatus/inbox",
                activity9.getTimelinePosition().getPosition());
        assertEquals("Activity oid " + activity9,
                "https://pleroma.site/activities/0f74296c-0f8c-43e2-a250-692f3e61c9c3",
                activity9.getOid());
        assertEquals("Note oid " + note9, "https://pleroma.site/objects/78bcd5dd-c1ee-4ac1-b2e0-206a508e60e9", note9.oid);
        assertEquals("Conversation oid " + note9,"https://pleroma.site/contexts/cebf1c4d-f7f2-46a5-8025-fd8bd9cde1ab", note9.conversationOid);
        assertEquals("Note name " + note9, "", note9.getName());
        assertThat("Note body " + note9, note9.getContent(),
                is("@pawooandstatustester@pawoo.net We are implementing conversation retrieval via #ActivityPub"));
        assertEquals("Activity updated at " + TestSuite.utcTime(activity9.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.MARCH, 15, 4, 38, 48).toString(),
                TestSuite.utcTime(activity9.getUpdatedDate()).toString());
        assertEquals("Note updated at " + TestSuite.utcTime(note9.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.MARCH, 15, 4, 38, 48).toString(),
                TestSuite.utcTime(note9.getUpdatedDate()).toString());
        Actor actor9 = activity9.getActor();
        assertEquals("Actor's oid " + activity9, ACTOR_OID, actor9.oid);
        assertEquals("Actor's Webfinger " + activity9, "", actor9.getWebFingerId());

        assertEquals("Actor is an Author", actor9, activity9.getAuthor());
        assertEquals("Should be Create " + activity9, ActivityType.CREATE, activity9.type);
        assertEquals("Favorited by me " + activity9, TriState.UNKNOWN, activity9.getNote().getFavoritedBy(activity9.accountActor));
    }

    @Test
    public void testGetFriends() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_friends_pleroma);
        Actor actor = Actor.fromOid(mock.getData().getOrigin(), "https://pleroma.site/users/ActivityPubTester");
        actor.endpoints.add(ActorEndpointType.API_FOLLOWING, "https://pleroma.site/users/ActivityPubTester/following");
        InputActorPage page = mock.connection.getFriendsOrFollowers(Connection.ApiRoutineEnum.GET_FRIENDS,
                TimelinePosition.EMPTY, actor).get();
        assertEquals("Number of actors, " +
                "who " + actor.getUniqueNameWithOrigin() + " is following " + page, 1, page.size());
        assertEquals("https://pleroma.site/users/AndStatus", page.get(0).oid);
    }

    @Test
    public void testGetNoteWithAudience() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_with_audience_pleroma);
        String noteOid = "https://pleroma.site/objects/032e7c06-48aa-4cc9-b84a-0a36a24a7779";
        AActivity activity = mock.connection.getNote(noteOid).get();
        assertEquals("Creating " + activity, ActivityType.CREATE, activity.type);
        assertEquals("Acting on a Note " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Note oid " + note, noteOid, note.oid);
        Actor author = activity.getAuthor();
        assertEquals("Author's oid " + activity, ACTOR_OID, author.oid);
        assertEquals("Actor is author", author, activity.getActor());
        assertThat("Note body " + note, note.getContent(),
                containsString("Testing public reply to Conversation participants"));
        assertEquals("Note updated at " + TestSuite.utcTime(note.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.MARCH, 31, 11, 39, 54).toString(),
                TestSuite.utcTime(note.getUpdatedDate()).toString());

        Audience audience = activity.audience();
        assertEquals("Visibility of " + activity, TriState.TRUE, audience.getVisibility());
        List<String> oids = Arrays.asList(
            "https://pleroma.site/users/kaniini",
            "https://pawoo.net/users/pawooAndStatusTester",
            "https://pleroma.site/users/ActivityPubTester",
            "https://pleroma.site/users/AndStatus/followers");
        oids.forEach(oid -> {
            assertTrue("Audience should contain " + oid + "\n " + activity + "\n " + audience, audience.containsOid(oid));
        });

        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(),
                CommandData.newTimelineCommand(CommandEnum.UPDATE_NOTE, mock.getData().getMyAccount(), TimelineType.SENT));
        new DataUpdater(executionContext).onActivity(activity);

        Audience audienceStored = Audience.fromNoteId(mock.getData().getOrigin(), note.noteId, note.getVisibility());
        oids.forEach(oid -> {
            assertTrue("Audience should contain " + oid + "\n " + activity + "\n " + audienceStored, audienceStored.containsOid(oid));
        });
        assertTrue("Audience of " + activity + "\n " + audienceStored, audienceStored.hasNonSpecial());

    }

    @Test
    public void getNoteWithAttachment() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_with_attachment_pleroma);
        String noteOid = "https://queer.hacktivis.me/objects/afc8092f-d25e-40a5-9dfe-5a067fb2e67d";
        AActivity activity = mock.connection.getNote(noteOid).get();
        assertEquals("Updating " + activity, ActivityType.UPDATE, activity.type);
        assertEquals("Acting on a Note " + activity, AObjectType.NOTE, activity.getObjectType());
        Note note = activity.getNote();
        assertEquals("Note oid " + note, noteOid, note.oid);
        Actor author = activity.getAuthor();
        assertEquals("Author's oid " + activity, "https://queer.hacktivis.me/users/AndStatus", author.oid);
        assertThat("Note name " + note, note.getName(), is("TestPost003"));
        assertThat("Note summary " + note, note.getSummary(), is("TestPost003Subject"));
        assertThat("Note body " + note, note.getContent(), is("With attachment"));
        assertEquals("Note updated at " + TestSuite.utcTime(note.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.NOVEMBER, 10, 10, 44, 37).toString(),
                TestSuite.utcTime(note.getUpdatedDate()).toString());

        Audience audience = activity.audience();
        assertEquals("Visibility of " + activity, TriState.TRUE, audience.getVisibility());
        List<String> oids = Arrays.asList(
                "https://queer.hacktivis.me/users/AndStatus/followers"
        );
        oids.forEach(oid -> {
            assertTrue("Audience should contain " + oid + "\n " + activity + "\n " + audience, audience.containsOid(oid));
        });

        Attachments attachments = activity.getNote().attachments;
        assertTrue("Attachments of " + activity, attachments.nonEmpty());

        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(),
                CommandData.newTimelineCommand(CommandEnum.UPDATE_NOTE, mock.getData().getMyAccount(), TimelineType.SENT));
        new DataUpdater(executionContext).onActivity(activity);

        Attachments attachmentsStored = Attachments.load(MyContextHolder.get(), activity.getNote().noteId);
        assertTrue("Attachments should be stored of " + activity + "\n " + attachmentsStored + "\n",
                attachmentsStored.nonEmpty());
        assertEquals("Attachment stored of " + activity + "\n " + attachmentsStored + "\n",
                attachments.list, attachmentsStored.list);

        Audience audienceStored = Audience.fromNoteId(mock.getData().getOrigin(), note.noteId);
        oids.forEach(oid -> {
            assertTrue("Audience should contain " + oid + "\n " + activity + "\n " + audienceStored, audienceStored.containsOid(oid));
        });
        assertTrue("Audience of " + activity + "\n " + audienceStored, audienceStored.hasNonSpecial());
    }
}
