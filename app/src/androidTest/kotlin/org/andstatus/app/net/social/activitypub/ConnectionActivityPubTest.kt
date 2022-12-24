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
package org.andstatus.app.net.social.activitypub

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils.getOrElseRecover
import org.andstatus.app.util.UriUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringStartsWith
import org.hamcrest.text.IsEmptyString
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.function.Consumer
import kotlin.properties.Delegates

class ConnectionActivityPubTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var stub: ConnectionStub by Delegates.notNull()
    var pawooActorOid: String = "https://pawoo.net/users/pawooAndStatusTester"
    var pawooNoteOid: String = "https://pawoo.net/users/pawooAndStatusTester/statuses/101727836012435643"

    @Before
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        stub = ConnectionStub.newFor(DemoData.demoData.activityPubTestAccountName)
    }

    @Test
    fun getTimeline() {
        val sinceId = ""
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_inbox_pleroma)
        val actorForTimeline: Actor =
            Actor.Companion.fromOid(stub.data.getOrigin(), VerifyCredentialsActivityPubTest.ACTOR_OID)
                .withUniqueName(VerifyCredentialsActivityPubTest.UNIQUE_NAME_IN_ORIGIN)
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox")
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.HOME_TIMELINE,
            TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actorForTimeline
        ).get()
        Assert.assertNotNull("timeline returned", timeline)
        assertEquals("Number of items in the Timeline", 5, timeline.size().toLong())
        var activity = timeline[4] ?: throw IllegalStateException("No activity")
        assertEquals("Creating a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        assertEquals(
            "Note oid $note",
            "https://pleroma.site/objects/34ab2ec5-4307-4e0b-94d6-a789d4da1240",
            note.oid
        )
        assertEquals(
            "Conversation oid $note", "https://pleroma.site/contexts/c62ba280-2a11-473e-8bd1-9435e9dc83ae",
            note.conversationOid
        )
        assertEquals("Note name $note", "", note.getName())
        MatcherAssert.assertThat(
            "Note body $note",
            note.content,
            StringStartsWith.startsWith("We could successfully create an account")
        )
        assertEquals(
            "Activity updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
            TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
            TestSuite.utcTime(activity.getUpdatedDate()).toString()
        )
        assertEquals(
            "Note updated at " + TestSuite.utcTime(note.updatedDate),
            TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
            TestSuite.utcTime(note.updatedDate).toString()
        )
        val actor = activity.getActor()
        assertEquals("Actor's oid $activity", "https://pleroma.site/users/ActivityPubTester", actor.oid)
        assertEquals("Actor's Webfinger $activity", "", actor.getWebFingerId())
        assertEquals("Actor is an Author", actor, activity.getAuthor())
        assertEquals("Should be Create $activity", ActivityType.CREATE, activity.type)
        assertEquals(
            "Favorited by me $activity",
            TriState.UNKNOWN,
            activity.getNote().getFavoritedBy(activity.accountActor)
        )
        activity = timeline[3] ?: throw IllegalStateException("No activity")
        assertEquals("Is not FOLLOW $activity", ActivityType.FOLLOW, activity.type)
        assertEquals("Actor", "https://pleroma.site/users/ActivityPubTester", activity.getActor().oid)
        assertEquals("Actor is my friend", TriState.UNKNOWN, activity.getActor().isMyFriend)
        assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType())
        var objActor = activity.getObjActor()
        assertEquals("objActor followed", "https://pleroma.site/users/AndStatus", objActor.oid)
        assertEquals("Actor is my friend", TriState.UNKNOWN, objActor.isMyFriend)
        for (ind in 0..2) {
            activity = timeline[ind] ?: throw IllegalStateException("No activity")
            assertEquals("Is not UPDATE $activity", ActivityType.UPDATE, activity.type)
            assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType())
            objActor = activity.getObjActor()
            assertEquals("Actor is my friend", TriState.UNKNOWN, objActor.isMyFriend)
            assertEquals("Url of objActor", "https://pleroma.site/users/AndStatus", objActor.getProfileUrl())
            assertEquals("WebFinger ID", "andstatus@pleroma.site", objActor.getWebFingerId())
            assertEquals(
                "Avatar",
                "https://pleroma.site/media/c5f60f06-6620-46b6-b676-f9f4571b518e/bfa1745b8c221225cc6551805d9eaa8bebe5f36fc1856b4924bcfda5d620334d.png",
                objActor.getAvatarUrl()
            )
        }
    }

    @Test
    fun getNotesByActor() {
        val ACTOR_OID2 = "https://pleroma.site/users/kaniini"
        val sinceId = ""
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_outbox_pleroma)
        val actorForTimeline: Actor = Actor.Companion.fromOid(stub.data.getOrigin(), ACTOR_OID2)
            .withUniqueName(VerifyCredentialsActivityPubTest.UNIQUE_NAME_IN_ORIGIN)
        actorForTimeline.endpoints.add(ActorEndpointType.API_OUTBOX, "$ACTOR_OID2/outbox")
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.ACTOR_TIMELINE,
            TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actorForTimeline
        ).get()
        Assert.assertNotNull("timeline returned", timeline)
        assertEquals("Number of items in the Timeline", 10, timeline.size().toLong())
        val activity = timeline[2] ?: throw IllegalStateException("No activity")
        assertEquals("Announcing $activity", ActivityType.ANNOUNCE, activity.type)
        assertEquals("Announcing a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        assertEquals("Note oid $note", "https://lgbtq.cool/users/abby/statuses/101702144808655868", note.oid)
        val actor = activity.getActor()
        assertEquals("Actor's oid $activity", ACTOR_OID2, actor.oid)
        assertEquals("Author is unknown", Actor.EMPTY, activity.getAuthor())
    }

    @Test
    fun noteFromPawooNet() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_note_from_pawoo_net_pleroma)
        val activity8 = stub.connection.getNote(pawooNoteOid).get()
        assertEquals("Updating $activity8", ActivityType.UPDATE, activity8.type)
        assertEquals("Acting on a Note $activity8", AObjectType.NOTE, activity8.getObjectType())
        val note8 = activity8.getNote()
        assertEquals("Note oid $note8", pawooNoteOid, note8.oid)
        val author = activity8.getAuthor()
        assertEquals("Author's oid $activity8", pawooActorOid, author.oid)
        assertEquals("Actor is author", author, activity8.getActor())
        MatcherAssert.assertThat(
            "Note body $note8", note8.content,
            CoreMatchers.containsString("how two attached images may look like")
        )
        assertEquals(
            "Note updated at " + TestSuite.utcTime(note8.updatedDate),
            TestSuite.utcTime(2019, Calendar.MARCH, 10, 18, 46, 31).toString(),
            TestSuite.utcTime(note8.updatedDate).toString()
        )
    }

    @Test
    fun getTimeline2() {
        val sinceId = ""
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_inbox_pleroma_2)
        val actorForTimeline: Actor =
            Actor.Companion.fromOid(stub.data.getOrigin(), VerifyCredentialsActivityPubTest.ACTOR_OID)
                .withUniqueName(VerifyCredentialsActivityPubTest.UNIQUE_NAME_IN_ORIGIN)
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox")
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.HOME_TIMELINE,
            TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actorForTimeline
        ).get()
        Assert.assertNotNull("timeline returned", timeline)
        assertEquals("Number of items in the Timeline", 10, timeline.size().toLong())
        val activity8 = timeline[8] ?: throw IllegalStateException("No activity")
        assertEquals("Creating $activity8", ActivityType.CREATE, activity8.type)
        assertEquals("Acting on a Note $activity8", AObjectType.NOTE, activity8.getObjectType())
        val note8 = activity8.getNote()
        assertEquals("Note oid $note8", pawooNoteOid, note8.oid)
        val author = activity8.getAuthor()
        assertEquals("Author's oid $activity8", pawooActorOid, author.oid)
        assertEquals("Actor is author", author, activity8.getActor())
        MatcherAssert.assertThat(
            "Note summary should be absent $note8",
            note8.summary,
            CoreMatchers.`is`(IsEmptyString.emptyString())
        )
        MatcherAssert.assertThat(
            "Note body $note8", note8.content,
            CoreMatchers.containsString("how two attached images may look like")
        )
        assertEquals(
            "Note updated at " + TestSuite.utcTime(note8.updatedDate),
            TestSuite.utcTime(2019, Calendar.MARCH, 10, 18, 46, 31).toString(),
            TestSuite.utcTime(note8.updatedDate).toString()
        )
        assertEquals("Media attachments " + note8.attachments, 2, note8.attachments.size.toLong())
        val attachment0 = note8.attachments.list[0]
        assertEquals("Content type", MyContentType.IMAGE, attachment0.contentType)
        assertEquals(
            "Media URI",
            UriUtils.fromString("https://img.pawoo.net/media_attachments/files/013/102/220/original/b70c78bee2bf7c99.jpg"),
            attachment0.uri
        )
        val attachment1 = note8.attachments.list[1]
        assertEquals("Content type", MyContentType.IMAGE, attachment1.contentType)
        assertEquals(
            "Media URI",
            UriUtils.fromString("https://img.pawoo.net/media_attachments/files/013/102/261/original/104659a0cd852f39.jpg"),
            attachment1.uri
        )
        val activity9 = timeline[9] ?: throw IllegalStateException("No activity")
        assertEquals("Creating a Note $activity9", AObjectType.NOTE, activity9.getObjectType())
        val note9 = activity9.getNote()
        assertEquals(
            "Prev timeline position $activity9",
            "https://pleroma.site/users/AndStatus/inbox?max_id=9gOowwftJe67DBVQum",
            activity9.getPrevTimelinePosition().getPosition()
        )
        assertEquals(
            "Next timeline position $activity9",
            "https://pleroma.site/users/AndStatus/inbox?max_id=9gmg7FVl50VMgeCw0u",
            activity9.getNextTimelinePosition().getPosition()
        )
        assertEquals(
            "Activity oid $activity9",
            "https://pleroma.site/activities/0f74296c-0f8c-43e2-a250-692f3e61c9c3",
            activity9.getOid()
        )
        assertEquals(
            "Note oid $note9",
            "https://pleroma.site/objects/78bcd5dd-c1ee-4ac1-b2e0-206a508e60e9",
            note9.oid
        )
        assertEquals(
            "Conversation oid $note9",
            "https://pleroma.site/contexts/cebf1c4d-f7f2-46a5-8025-fd8bd9cde1ab",
            note9.conversationOid
        )
        assertEquals("Note name $note9", "", note9.getName())
        MatcherAssert.assertThat(
            "Note body $note9", note9.content,
            CoreMatchers.`is`("@pawooandstatustester@pawoo.net We are implementing conversation retrieval via #ActivityPub")
        )
        assertEquals(
            "Activity updated at " + TestSuite.utcTime(activity9.getUpdatedDate()),
            TestSuite.utcTime(2019, Calendar.MARCH, 15, 4, 38, 48).toString(),
            TestSuite.utcTime(activity9.getUpdatedDate()).toString()
        )
        assertEquals(
            "Note updated at " + TestSuite.utcTime(note9.updatedDate),
            TestSuite.utcTime(2019, Calendar.MARCH, 15, 4, 38, 48).toString(),
            TestSuite.utcTime(note9.updatedDate).toString()
        )
        val actor9 = activity9.getActor()
        assertEquals("Actor's oid $activity9", VerifyCredentialsActivityPubTest.ACTOR_OID, actor9.oid)
        assertEquals("Actor's Webfinger $activity9", "", actor9.getWebFingerId())
        assertEquals("Actor is an Author", actor9, activity9.getAuthor())
        assertEquals("Should be Create $activity9", ActivityType.CREATE, activity9.type)
        assertEquals(
            "Favorited by me $activity9",
            TriState.UNKNOWN,
            activity9.getNote().getFavoritedBy(activity9.accountActor)
        )
    }

    @Test
    fun testGetFriends() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_friends_pleroma)
        val actor: Actor =
            Actor.Companion.fromOid(stub.data.getOrigin(), "https://pleroma.site/users/ActivityPubTester")
        actor.endpoints.add(ActorEndpointType.API_FOLLOWING, "https://pleroma.site/users/ActivityPubTester/following")
        val page = stub.connection.getFriendsOrFollowers(
            ApiRoutineEnum.GET_FRIENDS,
            TimelinePosition.Companion.EMPTY, actor
        ).get()
        assertEquals(
            "Number of actors, " +
                "who " + actor.getUniqueNameWithOrigin() + " is following " + page, 1, page.size().toLong()
        )
        assertEquals("https://pleroma.site/users/AndStatus", page[0]?.oid)
    }

    @Test
    fun testGetNoteWithAudience() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_with_audience_pleroma)
        val noteOid = "https://pleroma.site/objects/032e7c06-48aa-4cc9-b84a-0a36a24a7779"
        val activity = stub.connection.getNote(noteOid).get()
        assertEquals("Creating $activity", ActivityType.CREATE, activity.type)
        assertEquals("Acting on a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        assertEquals("Note oid $note", noteOid, note.oid)
        val author = activity.getAuthor()
        assertEquals("Author's oid $activity", VerifyCredentialsActivityPubTest.ACTOR_OID, author.oid)
        assertEquals("Actor is author", author, activity.getActor())
        MatcherAssert.assertThat(
            "Note body $note", note.content,
            CoreMatchers.containsString("Testing public reply to Conversation participants")
        )
        assertEquals(
            "Note updated at " + TestSuite.utcTime(note.updatedDate),
            TestSuite.utcTime(2019, Calendar.MARCH, 31, 11, 39, 54).toString(),
            TestSuite.utcTime(note.updatedDate).toString()
        )
        Assert.assertTrue("Note should be sensitive $note", note.isSensitive())
        val audience = activity.audience()
        assertEquals("Visibility of $activity", Visibility.PUBLIC, audience.visibility)
        val oids = Arrays.asList(
            "https://pleroma.site/users/kaniini",
            "https://pawoo.net/users/pawooAndStatusTester",
            "https://pleroma.site/users/ActivityPubTester",
            "https://pleroma.site/users/AndStatus/followers"
        )
        oids.forEach(Consumer { oid: String? ->
            Assert.assertTrue(
                "Audience should contain $oid\n $activity\n $audience",
                audience.containsOid(oid)
            )
        })
        val executionContext = CommandExecutionContext(
            myContext,
            CommandData.Companion.newTimelineCommand(
                CommandEnum.UPDATE_NOTE,
                stub.data.getMyAccount(),
                TimelineType.SENT
            )
        )
        DataUpdater(executionContext).onActivity(activity)
        val noteStored: Note = Note.Companion.loadContentById(stub.data.getOrigin().myContext, note.noteId)
        val audienceStored = noteStored.audience()
        oids.forEach(Consumer { oid: String? ->
            Assert.assertTrue(
                "Audience should contain $oid\n $activity\n $audienceStored",
                audienceStored.containsOid(oid)
            )
        })
        Assert.assertTrue("Audience of $activity\n $audienceStored", audienceStored.hasNonSpecial())
        Assert.assertTrue("Note should be sensitive $noteStored", noteStored.isSensitive())
    }

    @Test
    fun getNoteWithAttachment() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_with_attachment_pleroma)
        val noteOid = "https://queer.hacktivis.me/objects/afc8092f-d25e-40a5-9dfe-5a067fb2e67d"
        val activity = stub.connection.getNote(noteOid).get()
        assertEquals("Updating $activity", ActivityType.UPDATE, activity.type)
        assertEquals("Acting on a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        assertEquals("Note oid $note", noteOid, note.oid)
        val author = activity.getAuthor()
        assertEquals("Author's oid $activity", "https://queer.hacktivis.me/users/AndStatus", author.oid)
        MatcherAssert.assertThat("Note name $note", note.getName(), CoreMatchers.`is`("TestPost003"))
        MatcherAssert.assertThat("Note summary $note", note.summary, CoreMatchers.`is`("TestPost003Subject"))
        MatcherAssert.assertThat("Note body $note", note.content, CoreMatchers.`is`("With attachment"))
        assertEquals(
            "Note updated at " + TestSuite.utcTime(note.updatedDate),
            TestSuite.utcTime(2019, Calendar.NOVEMBER, 10, 10, 44, 37).toString(),
            TestSuite.utcTime(note.updatedDate).toString()
        )
        Assert.assertFalse("Note is sensitive $note", note.isSensitive())
        val audience = activity.audience()
        assertEquals("Visibility of $activity", Visibility.PUBLIC, audience.visibility)
        val oids = Arrays.asList(
            "https://queer.hacktivis.me/users/AndStatus/followers"
        )
        oids.forEach { oid: String? ->
            Assert.assertTrue("Audience should contain $oid\n $activity\n $audience", audience.containsOid(oid))
        }
        val attachments = activity.getNote().attachments
        Assert.assertTrue("Attachments of $activity", attachments.nonEmpty)
        val executionContext = CommandExecutionContext(
            myContext,
            CommandData.Companion.newTimelineCommand(
                CommandEnum.UPDATE_MEDIA,
                stub.data.getMyAccount(),
                TimelineType.SENT
            )
        )
        DataUpdater(executionContext).onActivity(activity)
        val attachmentsStored: Attachments = Attachments.Companion.newLoaded(myContext, activity.getNote().noteId)
        Assert.assertTrue(
            "Attachments should be stored of $activity\n $attachmentsStored\n",
            attachmentsStored.nonEmpty
        )
        assertEquals(
            "Attachment stored of $activity\n $attachmentsStored\n",
            attachments.list, attachmentsStored.list
        )
        val noteStored: Note = Note.Companion.loadContentById(stub.data.getOrigin().myContext, note.noteId)
        val audienceStored = noteStored.audience()
        oids.forEach(Consumer { oid: String? ->
            Assert.assertTrue(
                "Audience should contain $oid\n $activity\n $audienceStored",
                audienceStored.containsOid(oid)
            )
        })
        Assert.assertTrue("Audience of $activity\n $audienceStored", audienceStored.hasNonSpecial())
        Assert.assertFalse("Note is sensitive $noteStored", noteStored.isSensitive())
    }

    @Test
    fun getActor() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_get_actor)
        val actorOid = "https://pleroma.site/users/ActivityPubTester"
        val partial: Actor = Actor.Companion.fromOid(stub.data.getOrigin(), actorOid)
        val received = stub.connection.getActor(partial).get()
        assertEquals("Actor's oid $received", actorOid, received.oid)
        MatcherAssert.assertThat("Note name $received", received.getUsername(), CoreMatchers.`is`("ActivityPubTester"))
        val executionContext = CommandExecutionContext(
            myContext,
            CommandData.Companion.newActorCommandAtOrigin(
                CommandEnum.GET_ACTOR, partial, "", stub.data.getOrigin()
            )
        )
        val activity = executionContext.getMyAccount().actor.update(received)
        DataUpdater(executionContext).onActivity(activity)
        val stored: Actor = Actor.Companion.load(executionContext.myContext, received.actorId, true, { Actor.EMPTY })
        assertEquals("Actor's oid $stored", actorOid, stored.oid)
    }

    @Test
    fun postNoteIdInLocationReturned() {
        val activityId = stub.data.getOriginUrl()!!.toExternalForm() + "/activities/3237932"
        stub.http.addLocation(activityId)
        val note = Note.fromOriginAndOid(stub.data.getOrigin(), null, DownloadStatus.SENDING).apply {
            setContent("Posting via ActivityPub C2S", TextMediaType.PLAIN)
        }
        val activity: AActivity = stub.connection.updateNote(note).getOrElseRecover {
            throw AssertionError("Should be a success: $it")
        }
        assertEquals(activityId, activity.getOid())
        assertEquals(ActivityType.UNKNOWN, activity.type)
    }
}
