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

import androidx.test.platform.app.InstrumentationRegistry;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpoints;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ConnectionGnuSocialTest;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.note.NoteEditorData;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.service.AttachmentDownloaderTest;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.data.DemoNoteInserter.assertVisibility;
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
        TestSuite.initializeWithAccounts(this);
        myContext = TestSuite.getMyContextForTest();
        context = myContext.context();
        demoData.checkDataPath();
    }

    @Test
    public void testFriends() {
        MyAccount ma = demoData.getPumpioConversationAccount();
        Actor accountActor = ma.getActor();
        String noteOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        DemoNoteInserter.deleteOldNote(accountActor.origin, noteOid);

        CommandExecutionContext executionContext = new CommandExecutionContext(
                myContext, CommandData.newAccountCommand(CommandEnum.EMPTY, ma));
        DataUpdater dataUpdater = new DataUpdater(executionContext);
        String username = "somebody" + demoData.testRunUid + "@identi.ca";
        String actorOid = OriginPumpio.ACCOUNT_PREFIX + username;
        Actor somebody = Actor.fromOid(accountActor.origin, actorOid);
        somebody.setUsername(username);
        somebody.isMyFriend = TriState.FALSE;
        somebody.setProfileUrl("http://identi.ca/somebody");
        somebody.build();
        dataUpdater.onActivity(accountActor.update(somebody));

        somebody.actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, accountActor.origin.getId(), actorOid);
        assertTrue("Actor " + username + " added", somebody.actorId != 0);
        DemoConversationInserter.assertIfActorIsMyFriend(somebody, false, ma);

        AActivity activity = AActivity.newPartialNote(accountActor, somebody, noteOid, System.currentTimeMillis(),
                DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContentPosted("The test note by Somebody at run " + demoData.testRunUid);
        note.via = "MyCoolClient";
        note.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";

        TestSuite.clearAssertions();
        long noteId = dataUpdater.onActivity(activity).getNote().noteId;
        assertNotEquals("Note added", 0, noteId);
        assertNotEquals("Activity added", 0, activity.getId());
        AssertionData data = TestSuite.getMyContextForTest().getAssertionData(DataUpdater.MSG_ASSERTION_KEY);
        assertTrue("Data put", data.nonEmpty());
        assertEquals("Note Oid", noteOid, data.getValues()
                .getAsString(NoteTable.NOTE_OID));
        assertEquals("Note is loaded", DownloadStatus.LOADED,
                DownloadStatus.load(data.getValues().getAsInteger(NoteTable.NOTE_STATUS)));
        assertEquals("Note permalink before storage", note.url,
                data.getValues().getAsString(NoteTable.URL));
        assertEquals("Note permalink", note.url, accountActor.origin.getNotePermalink(noteId));

        assertEquals("Note stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)));
        long authorId = MyQuery.noteIdToLongColumnValue(ActivityTable.AUTHOR_ID, noteId);
        assertEquals("Author of the note", somebody.actorId, authorId);
        String url = MyQuery.noteIdToStringColumnValue(NoteTable.URL, noteId);
        assertEquals("Url of the note", note.url, url);
        long senderId = MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, noteId);
        assertEquals("Sender of the note", somebody.actorId, senderId);
        url = MyQuery.actorIdToStringColumnValue(ActorTable.PROFILE_PAGE, senderId);
        assertEquals("Url of the author " + somebody.getUsername(), somebody.getProfileUrl(), url);
        assertEquals("Latest activity of " + somebody, activity.getId(),
                MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, somebody.actorId));

        Uri contentUri = myContext.timelines().get(TimelineType.FRIENDS, ma.getActor(), Origin.EMPTY).getUri();
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimelineSortOrder(TimelineType.FRIENDS, false);
        sa.addSelection(ActivityTable.ACTOR_ID + "=?", Long.toString(somebody.actorId));
        String[] projection = new String[] {NoteTable._ID};
        Cursor cursor = context.getContentResolver().query(contentUri, projection, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No cursor of Friends timeline", cursor != null);
        assertEquals("Should be no notes of this actor in the Friends timeline", 0, cursor.getCount());
        cursor.close();

        somebody.isMyFriend = TriState.TRUE;
        somebody.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        dataUpdater.onActivity(accountActor.update(somebody));
        DemoConversationInserter.assertIfActorIsMyFriend(somebody, true, ma);

        cursor = context.getContentResolver().query(contentUri, projection, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Note by actor=" + somebody + " is not in the Friends timeline of " + ma,
                cursor != null && cursor.getCount() > 0);
        cursor.close();
    }

    @Test
    public void testPrivateNoteToMyAccount() {
        MyAccount ma = demoData.getPumpioConversationAccount();
        Actor accountActor = ma.getActor();

        String noteOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ-" + demoData.testRunUid;

        String username = "t131t@pumpity.net";
        Actor author = Actor.fromOid(accountActor.origin, OriginPumpio.ACCOUNT_PREFIX + username);
        author.setUsername(username);
        author.build();

        final String noteName = "For You only";
        AActivity activity = new DemoNoteInserter(accountActor).buildActivity(
                author,
                noteName, "Hello, this is a test Private note by your namesake from http://pumpity.net",
                null, noteOid, DownloadStatus.LOADED);
        final Note note = activity.getNote();
        note.via = "AnyOtherClient";
        note.audience().add(accountActor);
        note.setPublic(TriState.FALSE);
        final long noteId = new DataUpdater(ma).onActivity(activity).getNote().noteId;
        assertNotEquals("Note added", 0, noteId);
        assertNotEquals("Activity added", 0, activity.getId());

        assertEquals("Note should be private " + note, TriState.FALSE,
                MyQuery.noteIdToTriState(NoteTable.PUBLIC, noteId));
        assertEquals("Note name " + note, noteName, MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId));
        DemoNoteInserter.assertInteraction(activity, NotificationEventType.PRIVATE, TriState.TRUE);

        Audience audience = Audience.fromNoteId(accountActor.origin, noteId);
        assertNotEquals("No audience for " + activity, 0, audience.getNonSpecialActors().size());
        assertEquals("Recipient " + ma.getAccountName() + "; " + audience.getNonSpecialActors(),
                ma.getActorId(), audience.getFirstNonSpecial().actorId);
        assertEquals("Number of audience for " + activity, 1, audience.getNonSpecialActors().size());
        assertVisibility(audience, TriState.FALSE, false);
    }

    @Test
    public void noteFavoritedByOtherActor() {
        MyAccount ma = demoData.getPumpioConversationAccount();
        Actor accountActor = ma.getActor();

        String authorUsername = "anybody@pumpity.net";
        Actor author = Actor.fromOid(accountActor.origin, OriginPumpio.ACCOUNT_PREFIX + authorUsername);
        author.setUsername(authorUsername);
        author.build();

        AActivity activity = AActivity.newPartialNote(accountActor,
                author, "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED" +  demoData.testRunUid,
                13312697000L, DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContentPosted("This test note will be favorited by First Reader from http://pumpity.net");
        note.via = "SomeOtherClient";
        note.setPublic(TriState.TRUE);

        String otherUsername = "firstreader@identi.ca";
        Actor otherActor = Actor.fromOid(accountActor.origin, OriginPumpio.ACCOUNT_PREFIX + otherUsername);
        otherActor.setUsername(otherUsername);
        otherActor.build();
        AActivity likeActivity = AActivity.fromInner(otherActor, ActivityType.LIKE, activity);

        long noteId = new DataUpdater(ma).onActivity(likeActivity).getNote().noteId;
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

        Audience audience = Audience.fromNoteId(accountActor.origin, noteId);
        assertVisibility(audience, TriState.TRUE, false);

        // TODO: Below is actually a timeline query test, so maybe expand / move...
        Uri contentUri = myContext.timelines()
                .get(TimelineType.EVERYTHING, Actor.EMPTY, ma.getOrigin()).getUri();
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimelineSortOrder(TimelineType.EVERYTHING, false);
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
    public void testReplyNoteFavoritedByMyActor() {
        oneReplyNoteFavoritedByMyActor("_it1", true);
        oneReplyNoteFavoritedByMyActor("_it2", false);
        oneReplyNoteFavoritedByMyActor("_it2", true);
    }

    private void oneReplyNoteFavoritedByMyActor(String iterationId, boolean favorited) {
        MyAccount ma = demoData.getPumpioConversationAccount();
        Actor accountActor = ma.getActor();

        String authorUsername = "example@pumpity.net";
        Actor author = Actor.fromOid(accountActor.origin, OriginPumpio.ACCOUNT_PREFIX + authorUsername);
        author.setUsername(authorUsername);
        author.build();

        AActivity activity = AActivity.newPartialNote(accountActor, author,
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123" + iterationId + demoData.testRunUid,
                13312795000L, DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContentPosted("The test note by Example\n from the http://pumpity.net " +  iterationId);
        note.via = "UnknownClient";
        if (favorited) note.addFavoriteBy(accountActor, TriState.TRUE);

        String inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB" + iterationId  + demoData.testRunUid;
        AActivity inReplyTo = AActivity.newPartialNote(accountActor,
                Actor.fromOid(accountActor.origin,
                        "irtUser" +  iterationId + demoData.testRunUid)
                        .setUsername("irt" + authorUsername +  iterationId)
                        .build(),
                inReplyToOid, 0, DownloadStatus.UNKNOWN);
        note.setInReplyTo(inReplyTo);

        long noteId = new DataUpdater(ma).onActivity(activity).getNote().noteId;
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

        MyAccount ma = myContext.accounts().getFirstSucceededForOrigin(activity.getActor().origin);
        assertTrue("Account is valid " + ma, ma.isValid());
        long noteId = new DataUpdater(ma).onActivity(activity).getNote().noteId;
        assertNotEquals("Note added " + activity.getNote(), 0, noteId);
        assertNotEquals("Activity added " + activity, 0, activity.getId());

        DownloadData dd = DownloadData.getSingleAttachment(noteId);
        assertEquals("Image URI stored", activity.getNote().attachments.list.get(0).getUri(), dd.getUri());
    }

    @Test
    public void testUnsentNoteWithAttachment() {
        final String method = "testUnsentNoteWithAttachment";
        MyAccount ma = myContext.accounts().getFirstSucceeded();
        Actor accountActor = ma.getActor();
        AActivity activity = AActivity.newPartialNote(accountActor, accountActor, "",
                System.currentTimeMillis(), DownloadStatus.SENDING);
        activity.getNote().setContentPosted("Unsent note with an attachment " + demoData.testRunUid);
        activity.addAttachment(Attachment.fromUriAndMimeType(demoData.localImageTestUri,
                MyContentType.VIDEO.generalMimeType));
        new DataUpdater(ma).onActivity(activity);

        Note note1 = activity.getNote();
        assertNotEquals("Note added " + activity, 0, note1.noteId);
        assertNotEquals("Activity added " + activity, 0, activity.getId());
        assertEquals("Status of unsent note", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note1.noteId)));

        DownloadData dd = DownloadData.getSingleAttachment(note1.noteId);
        assertEquals("Image URI stored", note1.attachments.list.get(0).getUri(), dd.getUri());
        assertEquals("Local image immediately loaded " + dd, DownloadStatus.LOADED, dd.getStatus());

        DbUtils.waitMs(method, 1000);

        // Emulate receiving of note
        final String oid = "sentMsgOid" + demoData.testRunUid;
        AActivity activity2 = AActivity.newPartialNote(accountActor, activity.getAuthor(), oid,
                System.currentTimeMillis(), DownloadStatus.LOADED);
        activity2.getNote().setContentPosted("Just sent: " + note1.getContent());
        activity2.getNote().noteId = note1.noteId;
        activity2.addAttachment(Attachment.fromUri(demoData.image1Url));
        new DataUpdater(ma).onActivity(activity2);

        Note note2 = activity2.getNote();
        assertEquals("Row id didn't change", note1.noteId, note2.noteId);
        assertEquals("Note content updated", note2.getContent(),
                MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, note1.noteId));
        assertEquals("Status of loaded note", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note1.noteId)));

        DownloadData dd2 = DownloadData.getSingleAttachment(note2.noteId);
        assertEquals("New image URI stored", note2.attachments.list.get(0).getUri(), dd2.getUri());

        assertEquals("Not loaded yet. " + dd2, DownloadStatus.ABSENT, dd2.getStatus());
        AttachmentDownloaderTest.loadAndAssertStatusForRow(dd2, DownloadStatus.LOADED, false);
    }

    @Test
    public void testUsernameChanged() {
        MyAccount ma = demoData.getGnuSocialAccount();
        Actor accountActor = ma.getActor();
        String username = "peter" + demoData.testRunUid;
        Actor actor1 = new DemoNoteInserter(ma).buildActorFromOid("34804" + demoData.testRunUid);
        actor1.setUsername(username);
        actor1.setProfileUrl("https://" + demoData.gnusocialTestOriginName + ".example.com/");
        actor1.build();

        DataUpdater dataUpdater = new DataUpdater(ma);
        long actorId1 = dataUpdater.onActivity(accountActor.update(actor1)).getObjActor().actorId;
        assertTrue("Actor added", actorId1 != 0);
        assertEquals("username stored", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));

        Actor actor1partial = Actor.fromOid(actor1.origin, actor1.oid);
        assertTrue("Partially defined", actor1partial.isPartiallyDefined());
        long actorId1partial = dataUpdater.onActivity(accountActor.update(actor1partial)).getObjActor().actorId;
        assertEquals("Same Actor", actorId1, actorId1partial);
        assertEquals("Partially defined Actor shouldn't change Username", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));
        assertEquals("Partially defined Actor shouldn't change WebfingerId", actor1.getWebFingerId(),
                MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, actorId1));
        assertEquals("Partially defined Actor shouldn't change Real name", actor1.getRealName(),
                MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, actorId1));

        actor1.setUsername(actor1.getUsername() + "renamed");
        actor1.build();
        long actorId1Renamed = dataUpdater.onActivity(accountActor.update(actor1)).getObjActor().actorId;
        assertEquals("Same Actor renamed", actorId1, actorId1Renamed);
        assertEquals("Actor should not be renamed, if updatedDate didn't change", username,
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));

        actor1.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        long actorId2Renamed = dataUpdater.onActivity(accountActor.update(actor1)).getObjActor().actorId;
        assertEquals("Same Actor renamed", actorId1, actorId2Renamed);
        assertEquals("Same Actor renamed", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1));

        Actor actor2SameOldUsername = new DemoNoteInserter(ma).buildActorFromOid("34805"
                + demoData.testRunUid);
        actor2SameOldUsername.setUsername(username);
        actor2SameOldUsername.build();
        long actorId2 = dataUpdater.onActivity(accountActor.update(actor2SameOldUsername)).getObjActor().actorId;
        assertTrue("Other Actor with the same Actor name as old name of Actor", actorId1 != actorId2);
        assertEquals("Username stored", actor2SameOldUsername.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId2));

        Actor actor3SameNewUsername = new DemoNoteInserter(ma).buildActorFromOid("34806"
                + demoData.testRunUid);
        actor3SameNewUsername.setUsername(actor1.getUsername());
        actor3SameNewUsername.setProfileUrl("https://" + demoData.gnusocialTestOriginName + ".other.example.com/");
        actor3SameNewUsername.build();
        long actorId3 = dataUpdater.onActivity(accountActor.update(actor3SameNewUsername)).getObjActor().actorId;
        assertTrue("Actor added " + actor3SameNewUsername, actorId3 != 0);
        assertTrue("Other Actor with the same username as the new name of actor1, but different WebFingerId", actorId1 != actorId3);
        assertEquals("username stored for actorId=" + actorId3, actor3SameNewUsername.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId3));
    }

    @Test
    public void demoInsertGnuSocialActor() {
        MyAccount ma = demoData.getGnuSocialAccount();
        Actor actor1 = new DemoNoteInserter(ma).buildActorFromOid("34807" + demoData.testRunUid);
        updateActor(ma, actor1);

        Actor actor2 = Actor.fromOid(ma.getOrigin(), "98023493" + demoData.testRunUid)
                .setUsername("someuser" + demoData.testRunUid);
        updateActor(ma, actor2);
    }

    @Test
    public void addActivityPubActor() {
        MyAccount ma = demoData.getMyAccount(demoData.activityPubTestAccountName);
        Actor actor = Actor.fromOid(ma.getOrigin(),"https://example.com/users/ApTester" + demoData.testRunUid);
        updateActor(ma, actor);
    }

    private void updateActor(MyAccount ma, Actor actor) {
        Actor accountActor = ma.getActor();
        long id = new DataUpdater(ma).onActivity(accountActor.update(actor)).getObjActor().actorId;
        assertTrue("Actor added", id != 0);

        DemoNoteInserter.checkStoredActor(actor);

        assertEquals("Location", actor.location,
                MyQuery.actorIdToStringColumnValue(ActorTable.LOCATION, id));
        assertEquals("profile image URL", actor.getAvatarUrl(),
                MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, id));
        assertEquals("profile URL", actor.getProfileUrl(),
                MyQuery.actorIdToStringColumnValue(ActorTable.PROFILE_PAGE, id));
        assertEquals("Endpoints", actor.endpoints, ActorEndpoints.from(myContext, id).initialize());
        assertEquals("Homepage", actor.getHomepage(),
                MyQuery.actorIdToStringColumnValue(ActorTable.HOMEPAGE, id));
        assertEquals("Description", actor.getSummary(),
                MyQuery.actorIdToStringColumnValue(ActorTable.SUMMARY, id));
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
        final MyAccount ma = demoData.getPumpioConversationAccount();
        String buddyName = "buddy" +  demoData.testRunUid + "@example.com";
        String content = "@" + buddyName + " I'm replying to you in a note's body."
                + " Hope you will see this as a real reply!";
        addOneNote4testReplyInContent(ma, buddyName, content, true);

        addOneNote4testReplyInContent(ma, buddyName, "Oh, " + content, true);

        long actorId1 = MyQuery.webFingerIdToId(myContext, ma.getOriginId(), buddyName, false);
        assertEquals("Actor should have temp Oid", Actor.toTempOid(buddyName, ""),
                MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId1, 0));

        String realBuddyOid = "acc:" + buddyName;
        Actor actor = Actor.fromOid(ma.getOrigin(), realBuddyOid);
        actor.withUniqueName(buddyName);
        long actorId2 = new DataUpdater(ma).onActivity(ma.getActor().update(actor)).getObjActor().actorId;
        assertEquals(actorId1, actorId2);
        assertEquals("TempOid should be replaced with real", realBuddyOid,
                MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId1, 0));

        addOneNote4testReplyInContent(ma, buddyName, "<a href=\"http://example.com/a\">@" +
                buddyName + "</a>, this is an HTML <i>formatted</i> note", true);

        String buddyName3 = demoData.conversationAuthorThirdUniqueName;
        addOneNote4testReplyInContent(ma, buddyName3,
                "@" + buddyName3 + " I know you are already in our cache", true);

        String buddyName4 = ma.getActor().getUniqueName();
        addOneNote4testReplyInContent(ma, buddyName4,
                "Reply to myaccount @" + buddyName4 + " should add me as a recipient", true);
        addOneNote4testReplyInContent(ma, buddyName3,
                "Reply to myaccount @" + buddyName4 + " should not add other buddy as a recipient", false);

        String groupName1 = "gnutestgroup";
        addOneNote4testReplyInContent(ma, groupName1,
                "Sending a note to the !" + groupName1 + " group", false);
    }

    private void addOneNote4testReplyInContent(MyAccount ma, String buddyUniqueName, String content, boolean isReply) {

        String actorUniqueName = "somebody" + demoData.testRunUid + "@somewhere.net";
        Actor actor = Actor.fromOid(ma.getActor().origin, OriginPumpio.ACCOUNT_PREFIX + actorUniqueName);
        actor.withUniqueName(actorUniqueName);
        actor.setProfileUrl("https://somewhere.net/" + actorUniqueName);

        AActivity activityIn = AActivity.newPartialNote(ma.getActor(), actor, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        Note noteIn = activityIn.getNote();
        noteIn.setContentPosted(content);
        noteIn.via = "MyCoolClient";

        NoteEditorData.recreateAudience(activityIn);
        final AActivity activity = new DataUpdater(ma).onActivity(activityIn);
        final Note note = activity.getNote();
        assertTrue("Note was not added: " + activity, note.noteId != 0);

        Actor buddy = Actor.EMPTY;
        for (Actor recipient : activity.audience().getNonSpecialActors()) {
            assertFalse("Audience member is empty: " + recipient + ",\n" + note, recipient.isEmpty());
            if (recipient.getUniqueName().equals(buddyUniqueName)) {
                buddy = recipient;
                break;
            }
        }
        if (isReply) {
            assertNotEquals("'" + buddyUniqueName + "' should be a recipient " + activity.audience().getNonSpecialActors(),
                    Actor.EMPTY, buddy);
            assertNotEquals("'" + buddyUniqueName + "' is not added " + buddy, 0, buddy.actorId);
        } else {
            assertTrue("Note is a reply to '" + buddyUniqueName + "': " + note, buddy.isEmpty());
        }
    }

    @Test
    public void testGnuSocialMention() {
        MyAccount ma = demoData.getGnuSocialAccount();
        Actor accountActor = ma.getActor();
        MyAccount myMentionedAccount = demoData.getMyAccount(demoData.gnusocialTestAccount2Name);
        Actor myMentionedActor = myMentionedAccount.getActor();
        Actor author1 = Actor.fromOid(accountActor.origin, "sam" + demoData.testRunUid);
        author1.setUsername("samBrook");
        author1.build();

        String groupname = "gnutestgroup";

        AActivity activity1 = newLoadedNote(accountActor, author1,
                "@" + myMentionedActor.getUsername() + " I'm mentioning your another account" +
                        " and sending the content to !" + groupname + " group" +
                        " But Hello! is not a group name" +
                        " and!thisisnot also" +
                        " " + demoData.testRunUid);

        AActivity activity2 = AActivity.from(accountActor, ActivityType.UPDATE);
        activity2.setActor(author1);
        activity2.setActivity(activity1);
        NoteEditorData.recreateAudience(activity2);

        long noteId = new DataUpdater(ma).onActivity(activity2).getNote().noteId;
        assertTrue("Note should be added", noteId != 0);

        Audience audience = activity1.audience();
        assertEquals("Audience should contain two actors: " + audience, 2, audience.getNonSpecialActors().size());

        Optional<Actor> group = audience.getNonSpecialActors().stream().filter(a -> groupname.equals(a.getUsername())).findAny();
        assertTrue("Group should be in audience: " + audience, group.isPresent());
        assertEquals("Group type: " + group, GroupType.GENERIC, group.get().groupType);
        assertNotEquals("Group id: " + group, 0, group.get().actorId);

        Actor savedGroup = Actor.loadFromDatabase(MyContextHolder.get(), group.get().actorId, () -> Actor.EMPTY, false);
        assertEquals("Saved group: " + savedGroup, groupname, savedGroup.getUsername());
        assertEquals("Saved group type: " + savedGroup, GroupType.GENERIC, savedGroup.groupType);
    }

    @Test
    public void replyToOneOfMyActorsWithTheSameUsername() {
        MyAccount ma = demoData.getMyAccount(demoData.gnusocialTestAccount2Name);
        DataUpdater dataUpdater = new DataUpdater(ma);
        Actor accountActor = ma.getActor();

        Actor actorFromAnotherOrigin = demoData.getMyAccount(demoData.twitterTestAccountName).getActor();
        assertEquals(demoData.t131tUsername, actorFromAnotherOrigin.getUsername());
        Actor myAuthor1 = Actor.fromOid(accountActor.origin, actorFromAnotherOrigin.oid + "22");
        myAuthor1.setUsername(actorFromAnotherOrigin.getUsername());
        myAuthor1.setWebFingerId(actorFromAnotherOrigin.getWebFingerId());
        assertTrue("Should be unknown if it's mine" + myAuthor1, myAuthor1.user.isMyUser().unknown);
        myAuthor1.build();
        assertTrue("After build should be unknown if it's mine" + myAuthor1, myAuthor1.user.isMyUser().unknown);
        AActivity activity1 = newLoadedNote(accountActor, myAuthor1,
                "My account's first note from another Social Network " + demoData.testRunUid);
        assertTrue("Activity should be added", dataUpdater.onActivity(activity1).getId() != 0);
        assertTrue("Author should be mine " + activity1.getAuthor(), activity1.getAuthor().user.isMyUser().isTrue);

        Actor author2 = Actor.fromOid(accountActor.origin, "replier" + demoData.testRunUid);
        author2.setUsername("replier@anotherdoman.com");
        author2.build();
        AActivity activity2 = newLoadedNote(accountActor, author2,
                "@" + demoData.t131tUsername + " Replying to my user from another instance");
        activity2.getNote().setInReplyTo(activity1);
        assertTrue("Activity should be added", dataUpdater.onActivity(activity2).getId() != 0);

        assertEquals("Audience should contain one actor " + activity2.getNote().audience(),
                1, activity2.getNote().audience().getNonSpecialActors().size());
        assertEquals("Audience", myAuthor1, activity2.getNote().audience().getFirstNonSpecial());
        assertEquals("Notified actor", actorFromAnotherOrigin, activity2.getNotifiedActor());
    }

    private AActivity newLoadedNote(Actor accountActor, Actor author, String content) {
        AActivity activity1 = AActivity.newPartialNote(accountActor, author, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        Note note = activity1.getNote();
        note.setContentPosted(content);
        note.via = "AndStatus";
        return activity1;
    }

    @Test
    public void sendingNoteActivityPub() {
        MyAccount ma = demoData.getMyAccount(demoData.activityPubTestAccountName);
        DataUpdater dataUpdater = new DataUpdater(ma);
        Actor accountActor = ma.getActor();
        String content = "My note from ActivityPub " + demoData.testRunUid;

        AActivity activity0 = AActivity.newPartialNote(accountActor, accountActor, "",
                System.currentTimeMillis(), DownloadStatus.SENDING);
        activity0.getNote().setContentPosted(content);

        AActivity activity1 = dataUpdater.onActivity(activity0);
        Note note1 = activity1.getNote();
        assertTrue("Note should be added " + activity1, note1.noteId != 0);
        assertTrue("Activity should be added " + activity1, activity1.getId() != 0);
        assertEquals("Note " + note1, DownloadStatus.SENDING, note1.getStatus());

        Audience audience = Audience.fromNoteId(accountActor.origin, note1.noteId);
        assertVisibility(audience, TriState.UNKNOWN, false);

        // Response from a server
        AActivity activity2 = AActivity.from(accountActor, ActivityType.CREATE);
        activity2.setId(activity1.getId());
        activity2.setOid("https://" + demoData.activityPubMainHost + "/activities/" + MyLog.uniqueCurrentTimeMS());
        activity2.setUpdatedDate(MyLog.uniqueCurrentTimeMS());

        // No content in the response, just oid of the note
        Note note2 = Note.fromOriginAndOid(accountActor.origin,
                "https://" + demoData.activityPubMainHost + "/objects/" + MyLog.uniqueCurrentTimeMS(),
                DownloadStatus.UNKNOWN);
        activity2.setNote(note2);

        // This is what is done in org.andstatus.app.service.CommandExecutorOther.updateNote to link the notes
        activity2.setId(activity1.getId());
        activity2.getNote().noteId = note1.noteId;

        AActivity activity3 = dataUpdater.onActivity(activity2);
        Note note3 = activity3.getNote();
        assertEquals("The same note should be updated " + activity3, note1.noteId, note3.noteId);
        assertEquals("Note oid " + activity3, note2.oid, MyQuery.idToOid(myContext, OidEnum.NOTE_OID, note3.noteId, 0));
        assertTrue("Activity should be added " + activity3, activity3.getId() != 0);
        assertEquals("Note " + note3, DownloadStatus.SENT, note3.getStatus());
    }

    @Test
    public void noteToFollowersOnly() {
        MyAccount ma = demoData.getMyAccount(demoData.activityPubTestAccountName);
        Actor accountActor = ma.getActor();

        String authorUsername = "author101";
        Actor author = Actor.fromOid(accountActor.origin, "https://activitypub.org/users/" + authorUsername);
        author.setUsername(authorUsername);
        author.build();

        AActivity activity = AActivity.newPartialNote(accountActor,
                author, "https://activitypub.org/note/sdajklsdkiewwpdsldkfsdasdjWED" +  demoData.testRunUid,
                System.currentTimeMillis(), DownloadStatus.LOADED);
        Note note = activity.getNote();
        note.setContentPosted("This test note was sent to Followers only");
        note.via = "SomeApClient";
        note.audience().setPublic(TriState.FALSE);
        note.audience().setFollowers(true);

        long noteId = new DataUpdater(ma).onActivity(activity).getNote().noteId;
        assertNotEquals("Note added", 0, noteId);
        assertNotEquals("First activity added", 0, activity.getId());

        Audience audience = Audience.fromNoteId(accountActor.origin, noteId);
        assertVisibility(audience, TriState.FALSE, true);
    }
}