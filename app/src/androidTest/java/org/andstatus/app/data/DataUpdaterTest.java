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

package org.andstatus.app.data;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ConnectionGnuSocialTest;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.service.AttachmentDownloaderTest;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataUpdaterTest {
    private MyContext myContext;
    private Context context;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        myContext = TestSuite.getMyContextForTest();
        context = myContext.context();
        demoData.checkDataPath();
    }

    @Test
    public void testFriends() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();
        String noteOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        DemoNoteInserter.deleteOldNote(accountActor.origin, noteOid);

        CommandExecutionContext executionContext = new CommandExecutionContext(
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma));
        DataUpdater di = new DataUpdater(executionContext);
        String username = "somebody" + demoData.testRunUid + "@identi.ca";
        String actorOid = OriginPumpio.ACCOUNT_PREFIX + username;
        Actor somebody = Actor.fromOriginAndActorOid(accountActor.origin, actorOid);
        somebody.setUsername(username);
        somebody.followedByMe = TriState.FALSE;
        somebody.setProfileUrl("http://identi.ca/somebody");
        di.onActivity(accountActor.update(somebody));

        somebody.actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, accountActor.origin.getId(), actorOid);
        assertTrue("Actor " + username + " added", somebody.actorId != 0);
        DemoConversationInserter.assertIfActorIsMyFriend(somebody, false, ma);

        AActivity activity = AActivity.newPartialNote(accountActor, somebody, noteOid, System.currentTimeMillis(),
                DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContent("The test note by Somebody at run " + demoData.testRunUid);
        note.via = "MyCoolClient";
        note.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";

        TestSuite.clearAssertions();
        long noteId = di.onActivity(activity).getNote().noteId;
        assertNotEquals("Note added", 0, noteId);
        assertNotEquals("Activity added", 0, activity.getId());
        AssertionData data = TestSuite.getMyContextForTest().getAssertionData(DataUpdater.MSG_ASSERTION_KEY);
        assertFalse("Data put", data.isEmpty());
        assertEquals("Note Oid", noteOid, data.getValues()
                .getAsString(NoteTable.NOTE_OID));
        assertEquals("Note is loaded", DownloadStatus.LOADED,
                DownloadStatus.load(data.getValues().getAsInteger(NoteTable.NOTE_STATUS)));
        assertEquals("Note permalink before storage", note.url,
                data.getValues().getAsString(NoteTable.URL));
        assertEquals("Note permalink", note.url, accountActor.origin.notePermalink(noteId));

        assertEquals("Note stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)));
        long authorId = MyQuery.noteIdToLongColumnValue(ActivityTable.AUTHOR_ID, noteId);
        assertEquals("Author of the note", somebody.actorId, authorId);
        String url = MyQuery.noteIdToStringColumnValue(NoteTable.URL, noteId);
        assertEquals("Url of the note", note.url, url);
        long senderId = MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, noteId);
        assertEquals("Sender of the note", somebody.actorId, senderId);
        url = MyQuery.actorIdToStringColumnValue(ActorTable.PROFILE_URL, senderId);
        assertEquals("Url of the author " + somebody.getUsername(), somebody.getProfileUrl(), url);
        assertEquals("Latest activity of " + somebody, activity.getId(),
                MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, somebody.actorId));

        Uri contentUri = Timeline.getTimeline(TimelineType.FRIENDS, ma.getActorId(), Origin.EMPTY).getUri();
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimeSortOrder(TimelineType.FRIENDS, false);
        sa.addSelection(ActivityTable.ACTOR_ID + "=?", Long.toString(somebody.actorId));
        String[] PROJECTION = new String[] {
                NoteTable._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No notes of this actor in the Friends timeline", cursor != null && cursor.getCount() == 0);
        cursor.close();

        somebody.followedByMe = TriState.TRUE;
        di.onActivity(accountActor.update(somebody));
        DemoConversationInserter.assertIfActorIsMyFriend(somebody, true, ma);

        Set<Long> friendsIds = MyQuery.getFriendsIds(ma.getActorId());
        MyContextHolder.get().users().initialize();
        for (long id : friendsIds) {
            assertTrue("isFriend: " + id, MyContextHolder.get().users().isMeOrMyFriend(id));
        }

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Note by actor=" + somebody + " is not in the Friends timeline of " + ma,
                cursor != null && cursor.getCount() > 0);
        cursor.close();
    }

    @Test
    public void testPrivateNoteToMyAccount() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();

        String noteOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ-" + demoData.testRunUid;

        String username = "t131t@pumpity.net";
        Actor author = Actor.fromOriginAndActorOid(accountActor.origin,
                OriginPumpio.ACCOUNT_PREFIX + username);
        author.setUsername(username);

        final String noteName = "For You only";
        AActivity activity = new DemoNoteInserter(accountActor).buildActivity(
                author,
                noteName, "Hello, this is a test Private note by your namesake from http://pumpity.net",
                null, noteOid, DownloadStatus.LOADED);
        final Note note = activity.getNote();
        note.via = "AnyOtherClient";
        note.addRecipient(accountActor);
        note.setPublic(TriState.FALSE);
        final long noteId = new DataUpdater(ma).onActivity(activity).getNote().noteId;
        assertNotEquals("Note added", 0, noteId);
        assertNotEquals("Activity added", 0, activity.getId());

        assertEquals("Note should be private " + note, TriState.FALSE,
                MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
        assertEquals("Note name " + note, noteName, MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId));
        DemoNoteInserter.assertInteraction(activity, NotificationEventType.PRIVATE, TriState.TRUE);

        Audience audience = Audience.fromNoteId(accountActor.origin, noteId);
        assertNotEquals("No recipients for " + activity, 0, audience.getRecipients().size());
        assertEquals("Recipient " + ma.getAccountName() + "; " + audience.getRecipients(),
                ma.getActorId(), audience.getFirstNonPublic().actorId);
        assertEquals("Number of recipients for " + activity, 1, audience.getRecipients().size());
    }

    @Test
    public void noteFavoritedByOtherActor() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();

        String authorUsername = "anybody@pumpity.net";
        Actor author = Actor.fromOriginAndActorOid(accountActor.origin,
                OriginPumpio.ACCOUNT_PREFIX + authorUsername);
        author.setUsername(authorUsername);

        AActivity activity = AActivity.newPartialNote(accountActor,
                author, "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED" +  demoData.testRunUid,
                13312697000L, DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContent("This test note will be favorited by First Reader from http://pumpity.net");
        note.via = "SomeOtherClient";

        String otherUsername = "firstreader@identi.ca";
        Actor otherActor = Actor.fromOriginAndActorOid(accountActor.origin,
                OriginPumpio.ACCOUNT_PREFIX + otherUsername);
        otherActor.setUsername(otherUsername);
        AActivity likeActivity = AActivity.fromInner(otherActor, ActivityType.LIKE, activity);

        DataUpdater di = new DataUpdater(ma);
        long noteId = di.onActivity(likeActivity).getNote().noteId;
        assertNotEquals("Note added", 0, noteId);
        assertNotEquals("First activity added", 0, activity.getId());
        assertNotEquals("LIKE activity added", 0, likeActivity.getId());

        List<Actor> stargazers = MyQuery.getStargazers(myContext.getDatabase(), accountActor.origin, note.noteId);
        boolean favoritedByOtherActor = false;
        for (Actor actor : stargazers) {
            if (actor.equals(accountActor)) {
                fail("Note is favorited by my account " + ma + " - " + note);
            } else if (actor.equals(author)) {
                fail("Note is favorited by my author " + note);
            } if (actor.equals(otherActor)) {
                favoritedByOtherActor = true;
            } else {
                fail("Note is favorited by unexpected actor " + actor + " - " + note);
            }
        }
        assertEquals("Note is not favorited by " + otherActor + ": " + stargazers,
                true, favoritedByOtherActor);
        assertNotEquals("Note is favorited (by some my account)", TriState.TRUE,
                MyQuery.noteIdToTriState(NoteTable.FAVORITED, noteId));
        assertEquals("Activity is subscribed " + likeActivity, TriState.UNKNOWN,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, likeActivity.getId()));
        DemoNoteInserter.assertInteraction(likeActivity, NotificationEventType.EMPTY, TriState.FALSE);
        assertEquals("Note is reblogged", TriState.UNKNOWN,
                MyQuery.noteIdToTriState(NoteTable.REBLOGGED, noteId));

        // TODO: Below is actually a timeline query test, so maybe expand / move...
        Uri contentUri = Timeline.getTimeline(TimelineType.EVERYTHING, 0, ma.getOrigin()).getUri();
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimeSortOrder(TimelineType.EVERYTHING, false);
        sa.addSelection(NoteTable.NOTE_ID + " = ?", Long.toString(noteId));
        String[] PROJECTION = new String[] {
                ActivityTable.ACTIVITY_ID,
                ActivityTable.ACTOR_ID,
                ActivityTable.SUBSCRIBED,
                ActivityTable.INS_DATE,
                NoteTable.NOTE_ID,
                NoteTable.FAVORITED,
                NoteTable.UPDATED_DATE,
                ActivityTable.ACCOUNT_ID,
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        boolean noteFound = false;
        while (cursor.moveToNext()) {
            assertEquals("Note with other id returned", noteId, DbUtils.getLong(cursor, NoteTable.NOTE_ID));
            noteFound = true;
            assertNotEquals("Note is favorited", TriState.TRUE, DbUtils.getTriState(cursor, NoteTable.FAVORITED));
            assertNotEquals("Activity is subscribed", TriState.TRUE,
                    DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED));
        }
        cursor.close();
        assertTrue("Note is not in Everything timeline, noteId=" + noteId, noteFound);

    }

    @Test
    public void testReplyNoteFavoritedByMyActor() throws ConnectionException {
        oneReplyNoteFavoritedByMyActor("_it1", true);
        oneReplyNoteFavoritedByMyActor("_it2", false);
        oneReplyNoteFavoritedByMyActor("_it2", true);
    }

    private void oneReplyNoteFavoritedByMyActor(String iterationId, boolean favorited) {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();

        String authorUsername = "example@pumpity.net";
        Actor author = Actor.fromOriginAndActorOid(accountActor.origin,
                OriginPumpio.ACCOUNT_PREFIX + authorUsername);
        author.setUsername(authorUsername);

        AActivity activity = AActivity.newPartialNote(accountActor, author,
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123" + iterationId + demoData.testRunUid,
                13312795000L, DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContent("The test note by Example from the http://pumpity.net " +  iterationId);
        note.via = "UnknownClient";
        if (favorited) note.addFavoriteBy(accountActor, TriState.TRUE);

        String inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB" + iterationId  + demoData.testRunUid;
        AActivity inReplyTo = AActivity.newPartialNote(accountActor,
                Actor.fromOriginAndActorOid(accountActor.origin,
                        "irtUser" +  iterationId + demoData.testRunUid)
                        .setUsername("irt" + authorUsername +  iterationId),
                inReplyToOid, 0, DownloadStatus.UNKNOWN);
        note.setInReplyTo(inReplyTo);

        DataUpdater di = new DataUpdater(ma);
        long noteId = di.onActivity(activity).getNote().noteId;
        assertNotEquals("Note added " + activity.getNote(), 0, noteId);
        assertNotEquals("Activity added " + accountActor, 0, activity.getId());
        if (!favorited) {
            assertNotEquals("In reply to note added " + inReplyTo.getNote(), 0, inReplyTo.getNote().noteId);
            assertNotEquals("In reply to activity added " + inReplyTo, 0, inReplyTo.getId());
        }

        List<Actor> stargazers = MyQuery.getStargazers(myContext.getDatabase(), accountActor.origin, note.noteId);
        boolean favoritedByMe = false;
        for (Actor actor : stargazers) {
            if (actor.equals(accountActor)) {
                favoritedByMe = true;
            } else if (actor.equals(author)) {
                fail("Note is favorited by my author " + note);
            } else {
                fail("Note is favorited by unexpected actor " + actor + " - " + note);
            }
        }
        if (favorited) {
            assertEquals("Note " + note.noteId + " is not favorited by " + accountActor + ": "
                            + stargazers + "\n" + activity,
                    true, favoritedByMe);
            assertEquals("Note should be favorited (by some my account) " + activity, TriState.TRUE,
                    MyQuery.noteIdToTriState(NoteTable.FAVORITED, noteId));
        }
        assertEquals("Activity is subscribed", TriState.UNKNOWN,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, activity.getId()));
        DemoNoteInserter.assertInteraction(activity, NotificationEventType.EMPTY, TriState.FALSE);
        assertEquals("Note is reblogged", TriState.UNKNOWN,
                MyQuery.noteIdToTriState(NoteTable.REBLOGGED, noteId));
        assertEquals("Note stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)));

        long inReplyToId = MyQuery.oidToId(OidEnum.NOTE_OID, accountActor.origin.getId(),
                inReplyToOid);
        assertTrue("In reply to note added", inReplyToId != 0);
        assertEquals("Note reply status is unknown", DownloadStatus.UNKNOWN, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, inReplyToId)));
    }

    @Test
    public void testNoteWithAttachment() throws Exception {
        AActivity activity = ConnectionGnuSocialTest.getNoteWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext());

        MyAccount ma = MyContextHolder.get().accounts().getFirstSucceededForOrigin(activity.getActor().origin);
        assertTrue("Account is valid " + ma, ma.isValid());
        DataUpdater di = new DataUpdater(ma);
        long noteId = di.onActivity(activity).getNote().noteId;
        assertNotEquals("Note added " + activity.getNote(), 0, noteId);
        assertNotEquals("Activity added " + activity, 0, activity.getId());

        DownloadData dd = DownloadData.getSingleAttachment(noteId);
        assertEquals("Image URI stored", activity.getNote().attachments.list.get(0).getUri(), dd.getUri());
    }

    @Test
    public void testUnsentNoteWithAttachment() throws Exception {
        final String method = "testUnsentNoteWithAttachment";
        MyAccount ma = MyContextHolder.get().accounts().getFirstSucceeded();
        Actor accountActor = ma.getActor();
        AActivity activity = AActivity.newPartialNote(accountActor, accountActor, "",
                System.currentTimeMillis(), DownloadStatus.SENDING);
        Note note = activity.getNote();
        note.setContent("Unsent note with an attachment " + demoData.testRunUid);
        note.attachments.add(Attachment.fromUri(demoData.localImageTestUri));
        new DataUpdater(ma).onActivity(activity);
        assertNotEquals("Note added " + activity, 0, note.noteId);
        assertNotEquals("Activity added " + activity, 0, activity.getId());
        assertEquals("Status of unsent note", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note.noteId)));

        DownloadData dd = DownloadData.getSingleAttachment(note.noteId
        );
        assertEquals("Image URI stored", note.attachments.list.get(0).getUri(), dd.getUri());
        assertEquals("Local image immediately loaded " + dd, DownloadStatus.LOADED, dd.getStatus());

        DbUtils.waitMs(method, 1000);

        // Emulate receiving of note
        final String oid = "sentMsgOid" + demoData.testRunUid;
        AActivity activity2 = AActivity.newPartialNote(accountActor, activity.getAuthor(), oid,
                System.currentTimeMillis(), DownloadStatus.LOADED);
        Note note2 = activity2.getNote();
        note2.setContent("Just sent: " + note.getContent());
        note2.attachments.add(Attachment.fromUri(demoData.image1Url));
        note2.noteId = note.noteId;
        new DataUpdater(ma).onActivity(activity2);

        assertEquals("Row id didn't change", note.noteId, note2.noteId);
        assertEquals("Note content updated", note2.getContent(),
                MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, note.noteId));
        assertEquals("Status of loaded note", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note.noteId)));

        DownloadData dd2 = DownloadData.getSingleAttachment(note2.noteId
        );
        assertEquals("New image URI stored", note2.attachments.list.get(0).getUri(), dd2.getUri());

        assertEquals("Not loaded yet. " + dd2, DownloadStatus.ABSENT, dd2.getStatus());
        AttachmentDownloaderTest.loadAndAssertStatusForRow(dd2, DownloadStatus.LOADED, false);
    }

    @Test
    public void testUsernameChanged() {
        MyAccount ma = TestSuite.getMyContextForTest().accounts().fromAccountName(demoData.gnusocialTestAccountName);
        Actor accountActor = ma.getActor();
        String username = "peter" + demoData.testRunUid;
        Actor actor1 = new DemoNoteInserter(ma).buildActorFromOid("34804" + demoData.testRunUid);
        actor1.setUsername(username);
        actor1.setProfileUrl("https://" + demoData.gnusocialTestOriginName + ".example.com/");
        
        DataUpdater di = new DataUpdater(ma);
        long actorId1 = di.onActivity(accountActor.update(actor1)).getObjActor().actorId;
        assertTrue("Actor added", actorId1 != 0);
        assertEquals("username stored", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));

        Actor actor1partial = Actor.fromOriginAndActorOid(actor1.origin, actor1.oid);
        assertTrue("Partially defined", actor1partial.isPartiallyDefined());
        long actorId1partial = di.onActivity(accountActor.update(actor1partial)).getObjActor().actorId;
        assertEquals("Same Actor", actorId1, actorId1partial);
        assertEquals("Partially defined Actor shouldn't change Username", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));
        assertEquals("Partially defined Actor shouldn't change WebfingerId", actor1.getWebFingerId(),
                MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, actorId1));
        assertEquals("Partially defined Actor shouldn't change Real name", actor1.getRealName(),
                MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, actorId1));

        actor1.setUsername(actor1.getUsername() + "renamed");
        long actorId1Renamed = di.onActivity(accountActor.update(actor1)).getObjActor().actorId;
        assertEquals("Same Actor renamed", actorId1, actorId1Renamed);
        assertEquals("Same Actor renamed", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));

        Actor actor2SameOldUsername = new DemoNoteInserter(ma).buildActorFromOid("34805"
                + demoData.testRunUid);
        actor2SameOldUsername.setUsername(username);
        long actorId2 = di.onActivity(accountActor.update(actor2SameOldUsername)).getObjActor().actorId;
        assertTrue("Other Actor with the same Actor name as old name of Actor", actorId1 != actorId2);
        assertEquals("Username stored", actor2SameOldUsername.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId2));

        Actor actor3SameNewUsername = new DemoNoteInserter(ma).buildActorFromOid("34806"
                + demoData.testRunUid);
        actor3SameNewUsername.setUsername(actor1.getUsername());
        actor3SameNewUsername.setProfileUrl("https://" + demoData.gnusocialTestOriginName + ".other.example.com/");
        long actorId3 = di.onActivity(accountActor.update(actor3SameNewUsername)).getObjActor().actorId;
        assertTrue("Actor added " + actor3SameNewUsername, actorId3 != 0);
        assertTrue("Other Actor with the same username as the new name of actor1, but different WebFingerId", actorId1 != actorId3);
        assertEquals("username stored for actorId=" + actorId3, actor3SameNewUsername.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId3));
    }

    @Test
    public void testInsertActor() {
        MyAccount ma = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        Actor actor = new DemoNoteInserter(ma).buildActorFromOid("34807" + demoData.testRunUid);
        Actor accountActor = ma.getActor();

        DataUpdater di = new DataUpdater(ma);
        long id = di.onActivity(accountActor.update(actor)).getObjActor().actorId;
        assertTrue("Actor added", id != 0);
        assertEquals("Username", actor.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, id));
        assertEquals("oid", actor.oid,
                MyQuery.actorIdToStringColumnValue(ActorTable.ACTOR_OID, id));
        assertEquals("Display name", actor.getRealName(),
                MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, id));
        assertEquals("Location", actor.location,
                MyQuery.actorIdToStringColumnValue(ActorTable.LOCATION, id));
        assertEquals("profile image URL", actor.avatarUrl,
                MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, id));
        assertEquals("profile URL", actor.getProfileUrl(),
                MyQuery.actorIdToStringColumnValue(ActorTable.PROFILE_URL, id));
        assertEquals("Banner URL", actor.bannerUrl,
                MyQuery.actorIdToStringColumnValue(ActorTable.BANNER_URL, id));
        assertEquals("Homepage", actor.getHomepage(),
                MyQuery.actorIdToStringColumnValue(ActorTable.HOMEPAGE, id));
        assertEquals("WebFinger ID", actor.getWebFingerId(),
                MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, id));
        assertEquals("Description", actor.getDescription(),
                MyQuery.actorIdToStringColumnValue(ActorTable.DESCRIPTION, id));
        assertEquals("Notes count", actor.notesCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.NOTES_COUNT, id));
        assertEquals("Favorites count", actor.favoritesCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.FAVORITES_COUNT, id));
        assertEquals("Following (friends) count", actor.followingCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.FOLLOWING_COUNT, id));
        assertEquals("Followers count", actor.followersCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.FOLLOWERS_COUNT, id));
        assertEquals("Created at", actor.getCreatedDate(),
                MyQuery.actorIdToLongColumnValue(ActorTable.CREATED_DATE, id));
        assertEquals("Created at", actor.getUpdatedDate(),
                MyQuery.actorIdToLongColumnValue(ActorTable.UPDATED_DATE, id));
    }

    @Test
    public void testReplyInBody() {
        MyAccount ma = demoData.getConversationMyAccount();
        String buddyUsername = "buddy" +  demoData.testRunUid + "@example.com";
        String content = "@" + buddyUsername + " I'm replying to you in a note's body."
                + " Hope you will see this as a real reply!";
        addOneNote4testReplyInContent(buddyUsername, content, true);

        addOneNote4testReplyInContent(buddyUsername, "Oh, " + content, true);

        long actorId1 = MyQuery.webFingerIdToId(ma.getOriginId(), buddyUsername);
        assertEquals("Actor has temp Oid", Actor.getTempOid(buddyUsername, ""), MyQuery.idToOid(OidEnum.ACTOR_OID, actorId1, 0));

        String realBuddyOid = "acc:" + buddyUsername;
        Actor actor = Actor.fromOriginAndActorOid(ma.getOrigin(), realBuddyOid);
        actor.setUsername(buddyUsername);
        DataUpdater di = new DataUpdater(ma);
        long actorId2 = di.onActivity(ma.getActor().update(actor)).getObjActor().actorId;
        assertEquals(actorId1, actorId2);
        assertEquals("TempOid replaced with real", realBuddyOid, MyQuery.idToOid(OidEnum.ACTOR_OID, actorId1, 0));

        content = "<a href=\"http://example.com/a\">@" + buddyUsername + "</a>, this is an HTML <i>formatted</i> note";
        addOneNote4testReplyInContent(buddyUsername, content, true);

        buddyUsername = demoData.conversationAuthorThirdUsername;
        content = "@" + buddyUsername + " I know you are already in our cache";
        addOneNote4testReplyInContent(buddyUsername, content, true);
    }

    private void addOneNote4testReplyInContent(String buddyUsername, String content, boolean isReply) {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();

        DataUpdater di = new DataUpdater(ma);
        String username = "somebody" + demoData.testRunUid + "@somewhere.net";
        String actorOid = OriginPumpio.ACCOUNT_PREFIX + username;
        Actor somebody = Actor.fromOriginAndActorOid(accountActor.origin, actorOid);
        somebody.setUsername(username);
        somebody.setProfileUrl("https://somewhere.net/" + username);

        AActivity activity = AActivity.newPartialNote(accountActor, somebody, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContent(content);
        note.via = "MyCoolClient";

        long noteId = di.onActivity(activity).getNote().noteId;
        Actor buddy = Actor.EMPTY;
        for (Actor actor : activity.recipients().getRecipients()) {
            if (actor.getUsername().equals(buddyUsername)) {
                buddy = actor;
                break;
            }
        }
        assertTrue("Note added", noteId != 0);
        if (isReply) {
            assertTrue("'" + buddyUsername + "' should be a recipient " + activity.recipients().getRecipients(),
                    buddy.nonEmpty());
            assertNotEquals("'" + buddyUsername + "' is not added " + buddy, 0, buddy.actorId);
        } else {
            assertTrue("Don't treat this note as a reply:'"
                    + note.getContent() + "'", buddy.isEmpty());
        }
    }

    @Test
    public void testMention() {
        MyAccount ma = demoData.getMyAccount(demoData.gnusocialTestAccountName);
        Actor accountActor = ma.getActor();
        MyAccount myMentionedAccount = demoData.getMyAccount(demoData.gnusocialTestAccount2Name);
        Actor myMentionedUser = myMentionedAccount.getActor().setUsername(myMentionedAccount.getUsername());
        Actor author1 = Actor.fromOriginAndActorOid(accountActor.origin, "sam" + demoData.testRunUid);
        author1.setUsername("samBrook");

        AActivity activity1 = AActivity.newPartialNote(accountActor, author1, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        Note note = activity1.getNote();
        note.setContent("@" + myMentionedUser.getUsername() + " I mention your another account");
        note.via = "AndStatus";

        AActivity activity2 = AActivity.from(accountActor, ActivityType.UPDATE);
        activity2.setActor(author1);
        activity2.setActivity(activity1);

        DataUpdater di = new DataUpdater(ma);
        long noteId = di.onActivity(activity2).getNote().noteId;
        assertTrue("Note added", noteId != 0);
    }
}
