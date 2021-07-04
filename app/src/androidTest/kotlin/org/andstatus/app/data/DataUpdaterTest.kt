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
package org.andstatus.app.data

import android.content.Context
import android.provider.BaseColumns
import androidx.test.platform.app.InstrumentationRegistry
import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpoints
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.net.social.ConnectionGnuSocialTest
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.note.NoteEditorData
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginPumpio
import org.andstatus.app.service.AttachmentDownloaderTest
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SelectionAndArgs
import org.andstatus.app.util.TriState
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class DataUpdaterTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private val context: Context = myContext.context

    @Before
    fun setUp() {
        DemoData.demoData.checkDataPath()
    }

    @Test
    fun testFriends() {
        val ma: MyAccount = DemoData.demoData.getPumpioConversationAccount()
        val accountActor = ma.actor
        val noteOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB"
        DemoNoteInserter.Companion.deleteOldNote(accountActor.origin, noteOid)
        val executionContext = CommandExecutionContext(
                myContext, CommandData.Companion.newAccountCommand(CommandEnum.EMPTY, ma))
        val dataUpdater = DataUpdater(executionContext)
        val username = "somebody" + DemoData.demoData.testRunUid + "@identi.ca"
        val actorOid: String = OriginPumpio.Companion.ACCOUNT_PREFIX + username
        val somebody: Actor = Actor.Companion.fromOid(accountActor.origin, actorOid)
        somebody.setUsername(username)
        somebody.isMyFriend = TriState.FALSE
        somebody.setProfileUrl("http://identi.ca/somebody")
        somebody.build()
        dataUpdater.onActivity(accountActor.update(somebody))
        somebody.actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, accountActor.origin.id, actorOid)
        Assert.assertTrue("Actor $username added", somebody.actorId != 0L)
        DemoConversationInserter.Companion.assertIfActorIsMyFriend(somebody, false, ma)
        val activity: AActivity = AActivity.Companion.newPartialNote(accountActor, somebody, noteOid, System.currentTimeMillis(),
                DownloadStatus.LOADED)
        val note = activity.getNote()
        note.setContentPosted("The test note by Somebody at run " + DemoData.demoData.testRunUid)
        note.via = "MyCoolClient"
        note.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB"
        TestSuite.clearAssertions()
        val noteId = dataUpdater.onActivity(activity)?.getNote()?.noteId ?: 0
        Assert.assertNotEquals("Note added", 0, noteId)
        Assert.assertNotEquals("Activity added", 0, activity.getId())
        val data = TestSuite.getMyContextForTest().getAssertionData(DataUpdater.Companion.MSG_ASSERTION_KEY)
        Assert.assertTrue("Data put", data.nonEmpty)
        Assert.assertEquals("Note Oid", noteOid, data.values
                .getAsString(NoteTable.NOTE_OID))
        Assert.assertEquals("Note is loaded", DownloadStatus.LOADED,
                DownloadStatus.Companion.load(data.values.getAsInteger(NoteTable.NOTE_STATUS).toLong()))
        Assert.assertEquals("Note permalink before storage", note.url,
                data.values.getAsString(NoteTable.URL))
        Assert.assertEquals("Note permalink", note.url, accountActor.origin.getNotePermalink(noteId))
        Assert.assertEquals("Note stored as loaded", DownloadStatus.LOADED, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)))
        val authorId = MyQuery.noteIdToLongColumnValue(ActivityTable.AUTHOR_ID, noteId)
        Assert.assertEquals("Author of the note", somebody.actorId, authorId)
        var url = MyQuery.noteIdToStringColumnValue(NoteTable.URL, noteId)
        Assert.assertEquals("Url of the note", note.url, url)
        val senderId = MyQuery.noteIdToLongColumnValue(ActivityTable.ACTOR_ID, noteId)
        Assert.assertEquals("Sender of the note", somebody.actorId, senderId)
        url = MyQuery.actorIdToStringColumnValue(ActorTable.PROFILE_PAGE, senderId)
        Assert.assertEquals("Url of the author " + somebody.getUsername(), somebody.getProfileUrl(), url)
        Assert.assertEquals("Latest activity of $somebody", activity.getId(),
                MyQuery.actorIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, somebody.actorId))
        val contentUri = myContext.timelines[TimelineType.FRIENDS, ma.actor,  Origin.EMPTY].getUri()
        val sa = SelectionAndArgs()
        val sortOrder = ActivityTable.getTimelineSortOrder(TimelineType.FRIENDS, false)
        sa.addSelection(ActivityTable.ACTOR_ID + "=?", java.lang.Long.toString(somebody.actorId))
        val projection = arrayOf<String?>(BaseColumns._ID)
        var cursor = context.getContentResolver().query(contentUri, projection, sa.selection,
                sa.selectionArgs, sortOrder)  ?: throw IllegalStateException("No cursor of Friends timeline")
        Assert.assertEquals("Should be no notes of this actor in the Friends timeline", 0, cursor.getCount().toLong())
        cursor.close()
        somebody.isMyFriend = TriState.TRUE
        somebody.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
        dataUpdater.onActivity(accountActor.update(somebody))
        DemoConversationInserter.Companion.assertIfActorIsMyFriend(somebody, true, ma)
        cursor = context.getContentResolver().query(contentUri, projection, sa.selection,
                sa.selectionArgs, sortOrder) ?: throw IllegalStateException("No cursor")
        Assert.assertTrue("Note by actor=$somebody is not in the Friends timeline of $ma",
                cursor.count > 0)
        cursor.close()
    }

    @Test
    fun testPrivateNoteToMyAccount() {
        val ma: MyAccount = DemoData.demoData.getPumpioConversationAccount()
        val accountActor = ma.actor
        val noteOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ-" + DemoData.demoData.testRunUid
        val username = "t131t@pumpity.net"
        val author: Actor = Actor.Companion.fromOid(accountActor.origin, OriginPumpio.Companion.ACCOUNT_PREFIX + username)
        author.setUsername(username)
        author.build()
        val noteName = "For You only"
        val activity = DemoNoteInserter(accountActor).buildActivity(
                author,
                noteName, "Hello, this is a test Private note by your namesake from http://pumpity.net",
                null, noteOid, DownloadStatus.LOADED)
        val note = activity.getNote()
        note.via = "AnyOtherClient"
        note.audience().add(accountActor)
        note.audience().visibility = Visibility.PRIVATE
        val noteId = DataUpdater(ma).onActivity(activity)?.getNote()?.noteId ?: 0
        Assert.assertNotEquals("Note added", 0, noteId)
        Assert.assertNotEquals("Activity added", 0, activity.getId())
        Assert.assertEquals("Note should be private $note", Visibility.PRIVATE, Visibility.Companion.fromNoteId(noteId))
        Assert.assertEquals("Note name $note", noteName, MyQuery.noteIdToStringColumnValue(NoteTable.NAME, noteId))
        DemoNoteInserter.Companion.assertInteraction(activity, NotificationEventType.PRIVATE, TriState.TRUE)
        val audience: Audience = fromNoteId(accountActor.origin, noteId)
        Assert.assertNotEquals("No audience for $activity", 0, audience.getNonSpecialActors().size.toLong())
        Assert.assertEquals("Recipient " + ma.getAccountName() + "; " + audience.getNonSpecialActors(),
                ma.actorId, audience.getFirstNonSpecial().actorId)
        Assert.assertEquals("Number of audience for $activity", 1, audience.getNonSpecialActors().size.toLong())
        DemoNoteInserter.Companion.assertVisibility(audience, Visibility.PRIVATE)
    }

    @Test
    fun noteFavoritedByOtherActor() {
        val ma: MyAccount = DemoData.demoData.getPumpioConversationAccount()
        val accountActor = ma.actor
        val authorUsername = "anybody@pumpity.net"
        val author: Actor = Actor.Companion.fromOid(accountActor.origin, OriginPumpio.Companion.ACCOUNT_PREFIX + authorUsername)
        author.setUsername(authorUsername)
        author.build()
        val activity: AActivity = AActivity.Companion.newPartialNote(accountActor,
                author, "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED" + DemoData.demoData.testRunUid,
                13312697000L, DownloadStatus.LOADED)
        val note = activity.getNote()
        note.setContentPosted("This test note will be favorited by First Reader from http://pumpity.net")
        note.via = "SomeOtherClient"
        note.audience().visibility = Visibility.PUBLIC_AND_TO_FOLLOWERS
        val otherUsername = "firstreader@identi.ca"
        val otherActor: Actor = Actor.Companion.fromOid(accountActor.origin, OriginPumpio.Companion.ACCOUNT_PREFIX + otherUsername)
        otherActor.setUsername(otherUsername)
        otherActor.build()
        val likeActivity: AActivity = AActivity.Companion.fromInner(otherActor, ActivityType.LIKE, activity)
        val noteId = DataUpdater(ma).onActivity(likeActivity)?.getNote()?.noteId ?: 0
        Assert.assertNotEquals("Note added", 0, noteId)
        Assert.assertNotEquals("First activity added", 0, activity.getId())
        Assert.assertNotEquals("LIKE activity added", 0, likeActivity.getId())
        val stargazers = MyQuery.getStargazers(myContext.database, accountActor.origin, note.noteId)
        var favoritedByOtherActor = false
        for (actor in stargazers) {
            if (actor == accountActor) {
                Assert.fail("Note is favorited by my account $ma - $note")
            } else if (actor == author) {
                Assert.fail("Note is favorited by my author $note")
            }
            if (actor == otherActor) {
                favoritedByOtherActor = true
            } else {
                Assert.fail("Note is favorited by unexpected actor $actor - $note")
            }
        }
        Assert.assertEquals("Note is not favorited by $otherActor: $stargazers",
                true, favoritedByOtherActor)
        Assert.assertNotEquals("Note is favorited (by some my account)", TriState.TRUE,
                MyQuery.noteIdToTriState(NoteTable.FAVORITED, noteId))
        Assert.assertEquals("Activity is subscribed $likeActivity", TriState.UNKNOWN,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, likeActivity.getId()))
        DemoNoteInserter.Companion.assertInteraction(likeActivity, NotificationEventType.EMPTY, TriState.FALSE)
        Assert.assertEquals("Note is reblogged", TriState.UNKNOWN,
                MyQuery.noteIdToTriState(NoteTable.REBLOGGED, noteId))
        val audience: Audience = fromNoteId(accountActor.origin, noteId)
        DemoNoteInserter.Companion.assertVisibility(audience, Visibility.PUBLIC_AND_TO_FOLLOWERS)

        // TODO: Below is actually a timeline query test, so maybe expand / move...
        val contentUri = myContext.timelines[TimelineType.EVERYTHING, Actor.EMPTY, ma.origin].getUri()
        val sa = SelectionAndArgs()
        val sortOrder = ActivityTable.getTimelineSortOrder(TimelineType.EVERYTHING, false)
        sa.addSelection(NoteTable.NOTE_ID + " = ?", java.lang.Long.toString(noteId))
        val PROJECTION = arrayOf<String?>(
                ActivityTable.ACTIVITY_ID,
                ActivityTable.ACTOR_ID,
                ActivityTable.SUBSCRIBED,
                ActivityTable.INS_DATE,
                NoteTable.NOTE_ID,
                NoteTable.FAVORITED,
                NoteTable.UPDATED_DATE,
                ActivityTable.ACCOUNT_ID)
        val cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder) ?: throw IllegalStateException("No cursor")
        var noteFound = false
        while (cursor.moveToNext()) {
            Assert.assertEquals("Note with other id returned", noteId, DbUtils.getLong(cursor, NoteTable.NOTE_ID))
            noteFound = true
            Assert.assertNotEquals("Note is favorited", TriState.TRUE, DbUtils.getTriState(cursor, NoteTable.FAVORITED))
            Assert.assertNotEquals("Activity is subscribed", TriState.TRUE,
                    DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED))
        }
        cursor.close()
        Assert.assertTrue("Note is not in Everything timeline, noteId=$noteId", noteFound)
    }

    @Test
    fun testReplyNoteFavoritedByMyActor() {
        oneReplyNoteFavoritedByMyActor("_it1", true)
        oneReplyNoteFavoritedByMyActor("_it2", false)
        oneReplyNoteFavoritedByMyActor("_it2", true)
    }

    private fun oneReplyNoteFavoritedByMyActor(iterationId: String?, favorited: Boolean) {
        val ma: MyAccount = DemoData.demoData.getPumpioConversationAccount()
        val accountActor = ma.actor
        val authorUsername = "example@pumpity.net"
        val author: Actor = Actor.Companion.fromOid(accountActor.origin, OriginPumpio.Companion.ACCOUNT_PREFIX + authorUsername)
        author.setUsername(authorUsername)
        author.build()
        val activity: AActivity = AActivity.Companion.newPartialNote(accountActor, author,
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123" + iterationId + DemoData.demoData.testRunUid,
                13312795000L, DownloadStatus.LOADED)
        val note = activity.getNote()
        note.setContentPosted("The test note by Example\n from the http://pumpity.net $iterationId")
        note.via = "UnknownClient"
        if (favorited) note.addFavoriteBy(accountActor, TriState.TRUE)
        val inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB" + iterationId + DemoData.demoData.testRunUid
        val inReplyTo: AActivity = AActivity.Companion.newPartialNote(accountActor,
                Actor.Companion.fromOid(accountActor.origin,
                        "irtUser" + iterationId + DemoData.demoData.testRunUid)
                        .setUsername("irt$authorUsername$iterationId")
                        .build(),
                inReplyToOid, RelativeTime.DATETIME_MILLIS_NEVER, DownloadStatus.UNKNOWN)
        note.setInReplyTo(inReplyTo)
        val noteId = DataUpdater(ma).onActivity(activity)?.getNote()?.noteId ?: 0
        Assert.assertNotEquals("Note added " + activity.getNote(), 0, noteId)
        Assert.assertNotEquals("Activity added $accountActor", 0, activity.getId())
        if (!favorited) {
            Assert.assertNotEquals("In reply to note added " + inReplyTo.getNote(), 0, inReplyTo.getNote().noteId)
            Assert.assertNotEquals("In reply to activity added $inReplyTo", 0, inReplyTo.getId())
        }
        val stargazers = MyQuery.getStargazers(myContext.database, accountActor.origin, note.noteId)
        var favoritedByMe = false
        for (actor in stargazers) {
            if (actor == accountActor) {
                favoritedByMe = true
            } else if (actor == author) {
                Assert.fail("Note is favorited by my author $note")
            } else {
                Assert.fail("Note is favorited by unexpected actor $actor - $note")
            }
        }
        if (favorited) {
            Assert.assertEquals("""Note ${note.noteId} is not favorited by $accountActor: $stargazers
$activity""",
                    true, favoritedByMe)
            Assert.assertEquals("Note should be favorited (by some my account) $activity", TriState.TRUE,
                    MyQuery.noteIdToTriState(NoteTable.FAVORITED, noteId))
        }
        Assert.assertEquals("Activity is subscribed", TriState.UNKNOWN,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, activity.getId()))
        DemoNoteInserter.Companion.assertInteraction(activity, NotificationEventType.EMPTY, TriState.FALSE)
        Assert.assertEquals("Note is reblogged", TriState.UNKNOWN,
                MyQuery.noteIdToTriState(NoteTable.REBLOGGED, noteId))
        Assert.assertEquals("Note stored as loaded", DownloadStatus.LOADED, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, noteId)))
        val inReplyToId = MyQuery.oidToId(OidEnum.NOTE_OID, accountActor.origin.id,
                inReplyToOid)
        Assert.assertTrue("In reply to note added", inReplyToId != 0L)
        Assert.assertEquals("Note reply status is unknown", DownloadStatus.UNKNOWN, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, inReplyToId)))
    }

    @Test
    fun testNoteWithAttachment() {
        val activity: AActivity = ConnectionGnuSocialTest.getNoteWithAttachment(
                InstrumentationRegistry.getInstrumentation().context)
        val ma = myContext.accounts.getFirstPreferablySucceededForOrigin(activity.getActor().origin)
        Assert.assertTrue("Account is valid $ma", ma.isValid)
        val noteId = DataUpdater(ma).onActivity(activity)?.getNote()?.noteId ?: 0
        Assert.assertNotEquals("Note added " + activity.getNote(), 0, noteId)
        Assert.assertNotEquals("Activity added $activity", 0, activity.getId())
        val dd: DownloadData = DownloadData.Companion.getSingleAttachment(noteId)
        Assert.assertEquals("Image URI stored", activity.getNote().attachments.list[0].uri, dd.getUri())
    }

    @Test
    fun testUnsentNoteWithAttachment() {
        val method = "testUnsentNoteWithAttachment"
        val ma = myContext.accounts.getFirstSucceeded()
        val accountActor = ma.actor
        val activity: AActivity = AActivity.Companion.newPartialNote(accountActor, accountActor, "",
                System.currentTimeMillis(), DownloadStatus.SENDING)
        activity.getNote().setContentPosted("Unsent note with an attachment " + DemoData.demoData.testRunUid)
        activity.addAttachment(Attachment.Companion.fromUriAndMimeType(DemoData.demoData.localImageTestUri,
                MyContentType.VIDEO.generalMimeType))
        DataUpdater(ma).onActivity(activity)
        val note1 = activity.getNote()
        Assert.assertNotEquals("Note added $activity", 0, note1.noteId)
        Assert.assertNotEquals("Activity added $activity", 0, activity.getId())
        Assert.assertEquals("Status of unsent note", DownloadStatus.SENDING, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note1.noteId)))
        DbUtils.waitMs(method, 1000)

        val dd: DownloadData = DownloadData.Companion.getSingleAttachment(note1.noteId)
        Assert.assertEquals("Image URI stored", note1.attachments.list[0].uri, dd.getUri())
        Assert.assertEquals("Local image immediately loaded $dd", DownloadStatus.LOADED, dd.getStatus())

        // Emulate receiving of note
        val oid = "sentMsgOid" + DemoData.demoData.testRunUid
        val activity2: AActivity = AActivity.Companion.newPartialNote(accountActor, activity.getAuthor(), oid,
                System.currentTimeMillis(), DownloadStatus.LOADED)
        activity2.getNote().setContentPosted("Just sent: " + note1.content)
        activity2.getNote().noteId = note1.noteId
        activity2.addAttachment(Attachment.Companion.fromUri(DemoData.demoData.image1Url))
        DataUpdater(ma).onActivity(activity2)
        val note2 = activity2.getNote()
        Assert.assertEquals("Row id didn't change", note1.noteId, note2.noteId)
        Assert.assertEquals("Note content updated", note2.content,
                MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, note1.noteId))
        Assert.assertEquals("Status of loaded note", DownloadStatus.LOADED, DownloadStatus.Companion.load(
                MyQuery.noteIdToLongColumnValue(NoteTable.NOTE_STATUS, note1.noteId)))
        val dd2: DownloadData = DownloadData.Companion.getSingleAttachment(note2.noteId)
        Assert.assertEquals("New image URI stored", note2.attachments.list[0].uri, dd2.getUri())
        Assert.assertEquals("Not loaded yet. $dd2", DownloadStatus.ABSENT, dd2.getStatus())
        AttachmentDownloaderTest.loadAndAssertStatusForRow(method, dd2, DownloadStatus.LOADED, false)
    }

    @Test
    fun testUsernameChanged() {
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val accountActor = ma.actor
        val username = "peter" + DemoData.demoData.testRunUid
        val actor1 = DemoNoteInserter(ma).buildActorFromOid("34804" + DemoData.demoData.testRunUid)
        actor1.setUsername(username)
        actor1.setProfileUrl("https://" + DemoData.demoData.gnusocialTestOriginName + ".example.com/")
        actor1.build()
        val dataUpdater = DataUpdater(ma)
        val actorId1 = dataUpdater.onActivity(accountActor.update(actor1))?.getObjActor()?.actorId ?: 0
        Assert.assertTrue("Actor added", actorId1 != 0L)
        Assert.assertEquals("username stored", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1))
        val actor1partial: Actor = Actor.Companion.fromOid(actor1.origin, actor1.oid)
        Assert.assertFalse("Should be partially defined", actor1partial.isFullyDefined())
        val actorId1partial = dataUpdater.onActivity(accountActor.update(actor1partial))?.getObjActor()?.actorId ?: 0
        Assert.assertEquals("Same Actor", actorId1, actorId1partial)
        Assert.assertEquals("Partially defined Actor shouldn't change Username", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1))
        Assert.assertEquals("Partially defined Actor shouldn't change WebfingerId", actor1.getWebFingerId(),
                MyQuery.actorIdToStringColumnValue(ActorTable.WEBFINGER_ID, actorId1))
        Assert.assertEquals("Partially defined Actor shouldn't change Real name", actor1.getRealName(),
                MyQuery.actorIdToStringColumnValue(ActorTable.REAL_NAME, actorId1))
        actor1.setUsername(actor1.getUsername() + "renamed")
        actor1.build()
        val actorId1Renamed = dataUpdater.onActivity(accountActor.update(actor1))?.getObjActor()?.actorId ?: 0
        Assert.assertEquals("Same Actor renamed", actorId1, actorId1Renamed)
        Assert.assertEquals("Actor should not be renamed, if updatedDate didn't change", username,
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1))
        actor1.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
        val actorId2Renamed = dataUpdater.onActivity(accountActor.update(actor1))?.getObjActor()?.actorId ?: 0
        Assert.assertEquals("Same Actor renamed", actorId1, actorId2Renamed)
        Assert.assertEquals("Same Actor renamed", actor1.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId1))
        val actor2SameOldUsername = DemoNoteInserter(ma).buildActorFromOid("34805"
                + DemoData.demoData.testRunUid)
        actor2SameOldUsername.setUsername(username)
        actor2SameOldUsername.build()
        val actorId2 = dataUpdater.onActivity(accountActor.update(actor2SameOldUsername))?.getObjActor()?.actorId ?: 0
        Assert.assertTrue("Other Actor with the same Actor name as old name of Actor", actorId1 != actorId2)
        Assert.assertEquals("Username stored", actor2SameOldUsername.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId2))
        val actor3SameNewUsername = DemoNoteInserter(ma).buildActorFromOid("34806"
                + DemoData.demoData.testRunUid)
        actor3SameNewUsername.setUsername(actor1.getUsername())
        actor3SameNewUsername.setProfileUrl("https://" + DemoData.demoData.gnusocialTestOriginName + ".other.example.com/")
        actor3SameNewUsername.build()
        val actorId3 = dataUpdater.onActivity(accountActor.update(actor3SameNewUsername))?.getObjActor()?.actorId ?: 0
        Assert.assertTrue("Actor added $actor3SameNewUsername", actorId3 != 0L)
        Assert.assertTrue("Other Actor with the same username as the new name of actor1, but different WebFingerId", actorId1 != actorId3)
        Assert.assertEquals("username stored for actorId=$actorId3", actor3SameNewUsername.getUsername(),
                MyQuery.actorIdToStringColumnValue(ActorTable.USERNAME, actorId3))
    }

    @Test
    fun demoInsertGnuSocialActor() {
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val actor1 = DemoNoteInserter(ma).buildActorFromOid("34807" + DemoData.demoData.testRunUid)
        updateActor(ma, actor1)
        val actor2: Actor = Actor.Companion.fromOid(ma.origin, "98023493" + DemoData.demoData.testRunUid)
                .setUsername("someuser" + DemoData.demoData.testRunUid)
        updateActor(ma, actor2)
    }

    @Test
    fun addActivityPubActor() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.activityPubTestAccountName)
        val actor: Actor = Actor.Companion.fromOid(ma.origin, "https://example.com/users/ApTester" + DemoData.demoData.testRunUid)
        updateActor(ma, actor)
    }

    private fun updateActor(ma: MyAccount, actor: Actor) {
        val accountActor = ma.actor
        val id = DataUpdater(ma).onActivity(accountActor.update(actor))?.getObjActor()?.actorId ?: 0
        Assert.assertTrue("Actor added", id != 0L)
        DemoNoteInserter.Companion.checkStoredActor(actor)
        Assert.assertEquals("Location", actor.location,
                MyQuery.actorIdToStringColumnValue(ActorTable.LOCATION, id))
        Assert.assertEquals("profile image URL", actor.getAvatarUrl(),
                MyQuery.actorIdToStringColumnValue(ActorTable.AVATAR_URL, id))
        Assert.assertEquals("profile URL", actor.getProfileUrl(),
                MyQuery.actorIdToStringColumnValue(ActorTable.PROFILE_PAGE, id))
        Assert.assertEquals("Endpoints", actor.endpoints, ActorEndpoints.Companion.from(myContext, id).initialize())
        Assert.assertEquals("Homepage", actor.getHomepage(),
                MyQuery.actorIdToStringColumnValue(ActorTable.HOMEPAGE, id))
        Assert.assertEquals("Description", actor.getSummary(),
                MyQuery.actorIdToStringColumnValue(ActorTable.SUMMARY, id))
        Assert.assertEquals("Notes count", actor.notesCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.NOTES_COUNT, id))
        Assert.assertEquals("Favorites count", actor.favoritesCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.FAVORITES_COUNT, id))
        Assert.assertEquals("Following (friends) count", actor.followingCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.FOLLOWING_COUNT, id))
        Assert.assertEquals("Followers count", actor.followersCount,
                MyQuery.actorIdToLongColumnValue(ActorTable.FOLLOWERS_COUNT, id))
        Assert.assertEquals("Created at", actor.getCreatedDate(),
                MyQuery.actorIdToLongColumnValue(ActorTable.CREATED_DATE, id))
        Assert.assertEquals("Created at", actor.getUpdatedDate(),
                MyQuery.actorIdToLongColumnValue(ActorTable.UPDATED_DATE, id))
    }

    @Test
    fun testReplyInBody() {
        val ma: MyAccount = DemoData.demoData.getPumpioConversationAccount()
        val buddyName = "buddy" + DemoData.demoData.testRunUid + "@example.com"
        val content = ("@" + buddyName + " I'm replying to you in a note's body."
                + " Hope you will see this as a real reply!")
        addOneNote4testReplyInContent(ma, buddyName, content, true)
        addOneNote4testReplyInContent(ma, buddyName, "Oh, $content", true)
        val actorId1 = MyQuery.webFingerIdToId(myContext, ma.originId, buddyName, false)
        Assert.assertEquals("Actor should have temp Oid", Actor.Companion.toTempOid(buddyName, ""),
                MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId1, 0))
        val realBuddyOid = "acc:$buddyName"
        val actor: Actor = Actor.Companion.fromOid(ma.origin, realBuddyOid)
        actor.withUniqueName(buddyName)
        val actorId2 = DataUpdater(ma).onActivity(ma.actor.update(actor))?.getObjActor()?.actorId ?: 0
        Assert.assertEquals(actorId1, actorId2)
        Assert.assertEquals("TempOid should be replaced with real", realBuddyOid,
                MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId1, 0))
        addOneNote4testReplyInContent(ma, buddyName, "<a href=\"http://example.com/a\">@" +
                buddyName + "</a>, this is an HTML <i>formatted</i> note", true)
        val buddyName3: String = DemoData.demoData.conversationAuthorThirdUniqueName
        addOneNote4testReplyInContent(ma, buddyName3,
                "@$buddyName3 I know you are already in our cache", true)
        val buddyName4 = ma.actor.uniqueName
        addOneNote4testReplyInContent(ma, buddyName4,
                "Reply to myaccount @$buddyName4 should add me as a recipient", true)
        addOneNote4testReplyInContent(ma, buddyName3,
                "Reply to myaccount @$buddyName4 should not add other buddy as a recipient", false)
        val groupName1 = "gnutestgroup"
        addOneNote4testReplyInContent(ma, groupName1,
                "Sending a note to the !$groupName1 group", false)
    }

    private fun addOneNote4testReplyInContent(ma: MyAccount, buddyUniqueName: String?, content: String?, isReply: Boolean) {
        val actorUniqueName = "somebody" + DemoData.demoData.testRunUid + "@somewhere.net"
        val actor: Actor = Actor.Companion.fromOid(ma.origin, OriginPumpio.Companion.ACCOUNT_PREFIX + actorUniqueName)
        actor.withUniqueName(actorUniqueName)
        actor.setProfileUrl("https://somewhere.net/$actorUniqueName")
        val activityIn: AActivity = AActivity.Companion.newPartialNote(ma.actor, actor, System.nanoTime().toString(),
                System.currentTimeMillis(), DownloadStatus.LOADED)
        val noteIn = activityIn.getNote()
        noteIn.setContentPosted(content)
        noteIn.via = "MyCoolClient"
        NoteEditorData.Companion.recreateKnownAudience(activityIn)
        val activity = DataUpdater(ma).onActivity(activityIn) ?: AActivity.EMPTY
        val note = activity.getNote()
        Assert.assertTrue("Note was not added: $activity", note.noteId != 0L)
        var buddy: Actor = Actor.EMPTY
        for (recipient in activity.audience().getNonSpecialActors()) {
            Assert.assertFalse("Audience member is empty: $recipient,\n$note", recipient.isEmpty)
            if (recipient.uniqueName == buddyUniqueName) {
                buddy = recipient
                break
            }
        }
        if (isReply) {
            Assert.assertNotEquals("'" + buddyUniqueName + "' should be a recipient " + activity.audience().getNonSpecialActors(),
                    Actor.EMPTY, buddy)
            Assert.assertNotEquals("'$buddyUniqueName' is not added $buddy", 0, buddy.actorId)
        } else {
            Assert.assertTrue("Note is a reply to '$buddyUniqueName': $note", buddy.isEmpty)
        }
    }

    @Test
    fun testGnuSocialMention() {
        val ma: MyAccount = DemoData.demoData.getGnuSocialAccount()
        val accountActor = ma.actor
        val myMentionedAccount: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.gnusocialTestAccount2Name)
        val myMentionedActor = myMentionedAccount.actor
        val author1: Actor = Actor.Companion.fromOid(accountActor.origin, "sam" + DemoData.demoData.testRunUid)
        author1.setUsername("samBrook")
        author1.build()
        val groupname = "gnutestgroup"
        val activity1 = newLoadedNote(accountActor, author1,
                "@" + myMentionedActor.getUsername() + " I'm mentioning your another account" +
                        " and sending the content to !" + groupname + " group" +
                        " But Hello! is not a group name" +
                        " and!thisisnot also" +
                        " " + DemoData.demoData.testRunUid)
        val activity2: AActivity = AActivity.Companion.from(accountActor, ActivityType.UPDATE)
        activity2.setActor(author1)
        activity2.setActivity(activity1)
        NoteEditorData.Companion.recreateKnownAudience(activity2)
        val noteId = DataUpdater(ma).onActivity(activity2)?.getNote()?.noteId ?: 0
        Assert.assertTrue("Note should be added", noteId != 0L)
        val audience = activity1.audience()
        Assert.assertEquals("Audience should contain two actors: $audience", 2, audience.getNonSpecialActors().size.toLong())
        val group = audience.getNonSpecialActors().stream().filter { a: Actor -> groupname == a.getUsername() }.findAny()
        Assert.assertTrue("Group should be in audience: $audience", group.isPresent)
        Assert.assertEquals("Group type: $group", GroupType.GENERIC, group.get().groupType)
        Assert.assertNotEquals("Group id: $group", 0, group.get().actorId)
        val savedGroup: Actor = Actor.Companion.loadFromDatabase(myContext, group.get().actorId, { Actor.EMPTY }, false)
        Assert.assertEquals("Saved group: $savedGroup", groupname, savedGroup.getUsername())
        Assert.assertEquals("Saved group type: $savedGroup", GroupType.GENERIC, savedGroup.groupType)
    }

    @Test
    fun replyToOneOfMyActorsWithTheSameUsername() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.gnusocialTestAccount2Name)
        val dataUpdater = DataUpdater(ma)
        val accountActor = ma.actor
        val actorFromAnotherOrigin: Actor = DemoData.demoData.getMyAccount(DemoData.demoData.twitterTestAccountName).actor
        Assert.assertEquals(DemoData.demoData.t131tUsername, actorFromAnotherOrigin.getUsername())
        val myAuthor1: Actor = Actor.Companion.fromOid(accountActor.origin, actorFromAnotherOrigin.oid + "22")
        myAuthor1.setUsername(actorFromAnotherOrigin.getUsername())
        myAuthor1.setWebFingerId(actorFromAnotherOrigin.getWebFingerId())
        Assert.assertTrue("Should be unknown if it's mine$myAuthor1", myAuthor1.user.isMyUser().unknown)
        myAuthor1.build()
        Assert.assertTrue("After build should be unknown if it's mine$myAuthor1", myAuthor1.user.isMyUser().unknown)
        val activity1 = newLoadedNote(accountActor, myAuthor1,
                "My account's first note from another Social Network " + DemoData.demoData.testRunUid)
        Assert.assertTrue("Activity should be added", dataUpdater.onActivity(activity1)?.getId() != 0L)
        Assert.assertTrue("Author should be mine " + activity1.getAuthor(), activity1.getAuthor().user.isMyUser().isTrue)
        val author2: Actor = Actor.Companion.fromOid(accountActor.origin, "replier" + DemoData.demoData.testRunUid)
        author2.setUsername("replier@anotherdoman.com")
        author2.build()
        val activity2 = newLoadedNote(accountActor, author2,
                "@" + DemoData.demoData.t131tUsername + " Replying to my user from another instance")
        activity2.getNote().setInReplyTo(activity1)
        Assert.assertTrue("Activity should be added", dataUpdater.onActivity(activity2)?.getId() != 0L)
        Assert.assertEquals("Audience should contain one actor " + activity2.getNote().audience(),
                1, activity2.getNote().audience().getNonSpecialActors().size.toLong())
        Assert.assertEquals("Audience", myAuthor1, activity2.getNote().audience().getFirstNonSpecial())
        Assert.assertEquals("Notified actor $activity2", actorFromAnotherOrigin, activity2.getNotifiedActor())
    }

    private fun newLoadedNote(accountActor: Actor, author: Actor, content: String?): AActivity {
        val activity1: AActivity = AActivity.Companion.newPartialNote(accountActor, author, System.nanoTime().toString(),
                System.currentTimeMillis(), DownloadStatus.LOADED)
        val note = activity1.getNote()
        note.setContentPosted(content)
        note.via = "AndStatus"
        return activity1
    }

    @Test
    fun sendingNoteActivityPub() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.activityPubTestAccountName)
        val dataUpdater = DataUpdater(ma)
        val accountActor = ma.actor
        val content = "My note from ActivityPub " + DemoData.demoData.testRunUid
        val activity0: AActivity = AActivity.Companion.newPartialNote(accountActor, accountActor, "",
                System.currentTimeMillis(), DownloadStatus.SENDING)
        activity0.getNote().setContentPosted(content)
        val activity1 = dataUpdater.onActivity(activity0) ?: AActivity.EMPTY
        val note1 = activity1.getNote()
        Assert.assertTrue("Note should be added $activity1", note1.noteId != 0L)
        Assert.assertTrue("Activity should be added $activity1", activity1.getId() != 0L)
        Assert.assertEquals("Note $note1", DownloadStatus.SENDING, note1.getStatus())
        val audience: Audience = fromNoteId(accountActor.origin, note1.noteId)
        DemoNoteInserter.Companion.assertVisibility(audience, Visibility.PRIVATE)

        // Response from a server
        val activity2: AActivity = AActivity.Companion.from(accountActor, ActivityType.CREATE)
        activity2.setId(activity1.getId())
        activity2.setOid("https://" + DemoData.demoData.activityPubMainHost + "/activities/" + MyLog.uniqueCurrentTimeMS())
        activity2.setUpdatedDate(MyLog.uniqueCurrentTimeMS())

        // No content in the response, just oid of the note
        val note2: Note = Note.Companion.fromOriginAndOid(accountActor.origin,
                "https://" + DemoData.demoData.activityPubMainHost + "/objects/" + MyLog.uniqueCurrentTimeMS(),
                DownloadStatus.UNKNOWN)
        activity2.setNote(note2)

        // This is what is done in org.andstatus.app.service.CommandExecutorOther.updateNote to link the notes
        activity2.setId(activity1.getId())
        activity2.getNote().noteId = note1.noteId
        val activity3 = dataUpdater.onActivity(activity2) ?: AActivity.EMPTY
        val note3 = activity3.getNote()
        Assert.assertEquals("The same note should be updated $activity3", note1.noteId, note3.noteId)
        Assert.assertEquals("Note oid $activity3", note2.oid, MyQuery.idToOid(myContext, OidEnum.NOTE_OID, note3.noteId, 0))
        Assert.assertTrue("Activity should be added $activity3", activity3.getId() != 0L)
        Assert.assertEquals("Note $note3", DownloadStatus.SENT, note3.getStatus())
    }

    @Test
    fun noteToFollowersOnly() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.activityPubTestAccountName)
        val accountActor = ma.actor
        val authorUsername = "author101"
        val author: Actor = Actor.Companion.fromOid(accountActor.origin, "https://activitypub.org/users/$authorUsername")
        author.setUsername(authorUsername)
        author.build()
        val activity: AActivity = AActivity.Companion.newPartialNote(accountActor,
                author, "https://activitypub.org/note/sdajklsdkiewwpdsldkfsdasdjWED" + DemoData.demoData.testRunUid,
                System.currentTimeMillis(), DownloadStatus.LOADED)
        val note = activity.getNote()
        note.setContentPosted("This test note was sent to Followers only")
        note.via = "SomeApClient"
        note.audience().withVisibility(Visibility.TO_FOLLOWERS)
        val noteId = DataUpdater(ma).onActivity(activity)?.getNote()?.noteId ?: 0
        Assert.assertNotEquals("Note added", 0, noteId)
        Assert.assertNotEquals("First activity added", 0, activity.getId())
        val audience: Audience = fromNoteId(accountActor.origin, noteId)
        DemoNoteInserter.Companion.assertVisibility(audience, Visibility.TO_FOLLOWERS)
    }
}
