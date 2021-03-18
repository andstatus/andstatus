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
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.MyContentType
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.net.social.ConnectionMock
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringStartsWith
import org.hamcrest.text.IsEmptyString
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier

class ConnectionActivityPubTest {
    private var mock: ConnectionMock? = null
    var pawooActorOid: String? = "https://pawoo.net/users/pawooAndStatusTester"
    var pawooNoteOid: String? = "https://pawoo.net/users/pawooAndStatusTester/statuses/101727836012435643"
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        mock = ConnectionMock.Companion.newFor(DemoData.demoData.activityPubTestAccountName)
    }

    @Test
    @Throws(IOException::class)
    fun getTimeline() {
        val sinceId = ""
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma)
        val actorForTimeline: Actor = Actor.Companion.fromOid(mock.getData().origin, VerifyCredentialsActivityPubTest.Companion.ACTOR_OID)
                .withUniqueName(VerifyCredentialsActivityPubTest.Companion.UNIQUE_NAME_IN_ORIGIN)
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox")
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actorForTimeline).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 5, timeline.size().toLong())
        var activity = timeline[4]
        Assert.assertEquals("Creating a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        Assert.assertEquals("Note oid $note", "https://pleroma.site/objects/34ab2ec5-4307-4e0b-94d6-a789d4da1240", note.oid)
        Assert.assertEquals("Conversation oid $note", "https://pleroma.site/contexts/c62ba280-2a11-473e-8bd1-9435e9dc83ae",
                note.conversationOid)
        Assert.assertEquals("Note name $note", "", note.name)
        MatcherAssert.assertThat("Note body $note", note.content, StringStartsWith.startsWith("We could successfully create an account"))
        Assert.assertEquals("Activity updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
                TestSuite.utcTime(activity.getUpdatedDate()).toString())
        Assert.assertEquals("Note updated at " + TestSuite.utcTime(note.updatedDate),
                TestSuite.utcTime(2019, Calendar.FEBRUARY, 10, 17, 37, 25).toString(),
                TestSuite.utcTime(note.updatedDate).toString())
        val actor = activity.getActor()
        Assert.assertEquals("Actor's oid $activity", "https://pleroma.site/users/ActivityPubTester", actor.oid)
        Assert.assertEquals("Actor's Webfinger $activity", "", actor.getWebFingerId())
        Assert.assertEquals("Actor is an Author", actor, activity.getAuthor())
        Assert.assertEquals("Should be Create $activity", ActivityType.CREATE, activity.type)
        Assert.assertEquals("Favorited by me $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        activity = timeline[3]
        Assert.assertEquals("Is not FOLLOW $activity", ActivityType.FOLLOW, activity.type)
        Assert.assertEquals("Actor", "https://pleroma.site/users/ActivityPubTester", activity.getActor().oid)
        Assert.assertEquals("Actor is my friend", TriState.UNKNOWN, activity.getActor().isMyFriend)
        Assert.assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType())
        var objActor = activity.getObjActor()
        Assert.assertEquals("objActor followed", "https://pleroma.site/users/AndStatus", objActor.oid)
        Assert.assertEquals("Actor is my friend", TriState.UNKNOWN, objActor.isMyFriend)
        for (ind in 0..2) {
            activity = timeline[ind]
            Assert.assertEquals("Is not UPDATE $activity", ActivityType.UPDATE, activity.type)
            Assert.assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType())
            objActor = activity.getObjActor()
            Assert.assertEquals("Actor is my friend", TriState.UNKNOWN, objActor.isMyFriend)
            Assert.assertEquals("Url of objActor", "https://pleroma.site/users/AndStatus", objActor.profileUrl)
            Assert.assertEquals("WebFinger ID", "andstatus@pleroma.site", objActor.getWebFingerId())
            Assert.assertEquals("Avatar", "https://pleroma.site/media/c5f60f06-6620-46b6-b676-f9f4571b518e/bfa1745b8c221225cc6551805d9eaa8bebe5f36fc1856b4924bcfda5d620334d.png",
                    objActor.avatarUrl)
        }
    }

    @Test
    @Throws(IOException::class)
    fun getNotesByActor() {
        val ACTOR_OID2 = "https://pleroma.site/users/kaniini"
        val sinceId = ""
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_outbox_pleroma)
        val actorForTimeline: Actor = Actor.Companion.fromOid(mock.getData().origin, ACTOR_OID2)
                .withUniqueName(VerifyCredentialsActivityPubTest.Companion.UNIQUE_NAME_IN_ORIGIN)
        actorForTimeline.endpoints.add(ActorEndpointType.API_OUTBOX, "$ACTOR_OID2/outbox")
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.ACTOR_TIMELINE,
                TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actorForTimeline).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 10, timeline.size().toLong())
        val activity = timeline[2]
        Assert.assertEquals("Announcing $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertEquals("Announcing a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        Assert.assertEquals("Note oid $note", "https://lgbtq.cool/users/abby/statuses/101702144808655868", note.oid)
        val actor = activity.getActor()
        Assert.assertEquals("Actor's oid $activity", ACTOR_OID2, actor.oid)
        Assert.assertEquals("Author is unknown", Actor.EMPTY, activity.getAuthor())
    }

    @Test
    @Throws(IOException::class)
    fun noteFromPawooNet() {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_note_from_pawoo_net_pleroma)
        val activity8 = mock.connection.getNote(pawooNoteOid).get()
        Assert.assertEquals("Updating $activity8", ActivityType.UPDATE, activity8.type)
        Assert.assertEquals("Acting on a Note $activity8", AObjectType.NOTE, activity8.getObjectType())
        val note8 = activity8.note
        Assert.assertEquals("Note oid $note8", pawooNoteOid, note8.oid)
        val author = activity8.author
        Assert.assertEquals("Author's oid $activity8", pawooActorOid, author.oid)
        Assert.assertEquals("Actor is author", author, activity8.actor)
        MatcherAssert.assertThat("Note body $note8", note8.content,
                CoreMatchers.containsString("how two attached images may look like"))
        Assert.assertEquals("Note updated at " + TestSuite.utcTime(note8.updatedDate),
                TestSuite.utcTime(2019, Calendar.MARCH, 10, 18, 46, 31).toString(),
                TestSuite.utcTime(note8.updatedDate).toString())
    }

    @Test
    @Throws(IOException::class)
    fun getTimeline2() {
        val sinceId = ""
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_inbox_pleroma_2)
        val actorForTimeline: Actor = Actor.Companion.fromOid(mock.getData().origin, VerifyCredentialsActivityPubTest.Companion.ACTOR_OID)
                .withUniqueName(VerifyCredentialsActivityPubTest.Companion.UNIQUE_NAME_IN_ORIGIN)
        actorForTimeline.endpoints.add(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox")
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actorForTimeline).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 10, timeline.size().toLong())
        val activity8 = timeline[8]
        Assert.assertEquals("Creating $activity8", ActivityType.CREATE, activity8.type)
        Assert.assertEquals("Acting on a Note $activity8", AObjectType.NOTE, activity8.getObjectType())
        val note8 = activity8.note
        Assert.assertEquals("Note oid $note8", pawooNoteOid, note8.oid)
        val author = activity8.author
        Assert.assertEquals("Author's oid $activity8", pawooActorOid, author.oid)
        Assert.assertEquals("Actor is author", author, activity8.actor)
        MatcherAssert.assertThat("Note summary should be absent $note8", note8.summary, CoreMatchers.`is`(IsEmptyString.emptyString()))
        MatcherAssert.assertThat("Note body $note8", note8.content,
                CoreMatchers.containsString("how two attached images may look like"))
        Assert.assertEquals("Note updated at " + TestSuite.utcTime(note8.updatedDate),
                TestSuite.utcTime(2019, Calendar.MARCH, 10, 18, 46, 31).toString(),
                TestSuite.utcTime(note8.updatedDate).toString())
        Assert.assertEquals("Media attachments " + note8.attachments, 2, note8.attachments.size().toLong())
        val attachment0 = note8.attachments.list[0]
        Assert.assertEquals("Content type", MyContentType.IMAGE, attachment0.contentType)
        Assert.assertEquals("Media URI", UriUtils.fromString("https://img.pawoo.net/media_attachments/files/013/102/220/original/b70c78bee2bf7c99.jpg"),
                attachment0.getUri())
        val attachment1 = note8.attachments.list[1]
        Assert.assertEquals("Content type", MyContentType.IMAGE, attachment1.contentType)
        Assert.assertEquals("Media URI", UriUtils.fromString("https://img.pawoo.net/media_attachments/files/013/102/261/original/104659a0cd852f39.jpg"),
                attachment1.getUri())
        val activity9 = timeline[9]
        Assert.assertEquals("Creating a Note $activity9", AObjectType.NOTE, activity9.getObjectType())
        val note9 = activity9.note
        Assert.assertEquals("Prev timeline position $activity9",
                "https://pleroma.site/users/AndStatus/inbox?max_id=9gOowwftJe67DBVQum",
                activity9.prevTimelinePosition.position)
        Assert.assertEquals("Next timeline position $activity9",
                "https://pleroma.site/users/AndStatus/inbox?max_id=9gmg7FVl50VMgeCw0u",
                activity9.nextTimelinePosition.position)
        Assert.assertEquals("Activity oid $activity9",
                "https://pleroma.site/activities/0f74296c-0f8c-43e2-a250-692f3e61c9c3",
                activity9.oid)
        Assert.assertEquals("Note oid $note9", "https://pleroma.site/objects/78bcd5dd-c1ee-4ac1-b2e0-206a508e60e9", note9.oid)
        Assert.assertEquals("Conversation oid $note9", "https://pleroma.site/contexts/cebf1c4d-f7f2-46a5-8025-fd8bd9cde1ab", note9.conversationOid)
        Assert.assertEquals("Note name $note9", "", note9.name)
        MatcherAssert.assertThat("Note body $note9", note9.content,
                CoreMatchers.`is`("@pawooandstatustester@pawoo.net We are implementing conversation retrieval via #ActivityPub"))
        Assert.assertEquals("Activity updated at " + TestSuite.utcTime(activity9.getUpdatedDate()),
                TestSuite.utcTime(2019, Calendar.MARCH, 15, 4, 38, 48).toString(),
                TestSuite.utcTime(activity9.getUpdatedDate()).toString())
        Assert.assertEquals("Note updated at " + TestSuite.utcTime(note9.updatedDate),
                TestSuite.utcTime(2019, Calendar.MARCH, 15, 4, 38, 48).toString(),
                TestSuite.utcTime(note9.updatedDate).toString())
        val actor9 = activity9.actor
        Assert.assertEquals("Actor's oid $activity9", VerifyCredentialsActivityPubTest.Companion.ACTOR_OID, actor9.oid)
        Assert.assertEquals("Actor's Webfinger $activity9", "", actor9.getWebFingerId())
        Assert.assertEquals("Actor is an Author", actor9, activity9.author)
        Assert.assertEquals("Should be Create $activity9", ActivityType.CREATE, activity9.type)
        Assert.assertEquals("Favorited by me $activity9", TriState.UNKNOWN, activity9.note.getFavoritedBy(activity9.accountActor))
    }

    @Test
    @Throws(IOException::class)
    fun testGetFriends() {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_friends_pleroma)
        val actor: Actor = Actor.Companion.fromOid(mock.getData().origin, "https://pleroma.site/users/ActivityPubTester")
        actor.endpoints.add(ActorEndpointType.API_FOLLOWING, "https://pleroma.site/users/ActivityPubTester/following")
        val page = mock.connection.getFriendsOrFollowers(ApiRoutineEnum.GET_FRIENDS,
                TimelinePosition.Companion.EMPTY, actor).get()
        Assert.assertEquals("Number of actors, " +
                "who " + actor.uniqueNameWithOrigin + " is following " + page, 1, page.size().toLong())
        Assert.assertEquals("https://pleroma.site/users/AndStatus", page[0].oid)
    }

    @Test
    @Throws(IOException::class)
    fun testGetNoteWithAudience() {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_with_audience_pleroma)
        val noteOid = "https://pleroma.site/objects/032e7c06-48aa-4cc9-b84a-0a36a24a7779"
        val activity = mock.connection.getNote(noteOid).get()
        Assert.assertEquals("Creating $activity", ActivityType.CREATE, activity.type)
        Assert.assertEquals("Acting on a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        Assert.assertEquals("Note oid $note", noteOid, note.oid)
        val author = activity.getAuthor()
        Assert.assertEquals("Author's oid $activity", VerifyCredentialsActivityPubTest.Companion.ACTOR_OID, author.oid)
        Assert.assertEquals("Actor is author", author, activity.getActor())
        MatcherAssert.assertThat("Note body $note", note.content,
                CoreMatchers.containsString("Testing public reply to Conversation participants"))
        Assert.assertEquals("Note updated at " + TestSuite.utcTime(note.updatedDate),
                TestSuite.utcTime(2019, Calendar.MARCH, 31, 11, 39, 54).toString(),
                TestSuite.utcTime(note.updatedDate).toString())
        Assert.assertTrue("Note should be sensitive $note", note.isSensitive)
        val audience = activity.audience()
        Assert.assertEquals("Visibility of $activity", Visibility.PUBLIC, audience.visibility)
        val oids = Arrays.asList(
                "https://pleroma.site/users/kaniini",
                "https://pawoo.net/users/pawooAndStatusTester",
                "https://pleroma.site/users/ActivityPubTester",
                "https://pleroma.site/users/AndStatus/followers")
        oids.forEach(Consumer { oid: String? -> Assert.assertTrue("Audience should contain $oid\n $activity\n $audience", audience.containsOid(oid)) })
        val executionContext = CommandExecutionContext(
                 MyContextHolder.myContextHolder.getNow(),
                CommandData.Companion.newTimelineCommand(CommandEnum.UPDATE_NOTE, mock.getData().myAccount, TimelineType.SENT))
        DataUpdater(executionContext).onActivity(activity)
        val noteStored: Note = Note.Companion.loadContentById(mock.getData().origin.myContext, note.noteId)
        val audienceStored = noteStored.audience()
        oids.forEach(Consumer { oid: String? -> Assert.assertTrue("Audience should contain $oid\n $activity\n $audienceStored", audienceStored.containsOid(oid)) })
        Assert.assertTrue("Audience of $activity\n $audienceStored", audienceStored.hasNonSpecial())
        Assert.assertTrue("Note should be sensitive $noteStored", noteStored.isSensitive)
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithAttachment() {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_with_attachment_pleroma)
        val noteOid = "https://queer.hacktivis.me/objects/afc8092f-d25e-40a5-9dfe-5a067fb2e67d"
        val activity = mock.connection.getNote(noteOid).get()
        Assert.assertEquals("Updating $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Acting on a Note $activity", AObjectType.NOTE, activity.getObjectType())
        val note = activity.getNote()
        Assert.assertEquals("Note oid $note", noteOid, note.oid)
        val author = activity.getAuthor()
        Assert.assertEquals("Author's oid $activity", "https://queer.hacktivis.me/users/AndStatus", author.oid)
        MatcherAssert.assertThat("Note name $note", note.name, CoreMatchers.`is`("TestPost003"))
        MatcherAssert.assertThat("Note summary $note", note.summary, CoreMatchers.`is`("TestPost003Subject"))
        MatcherAssert.assertThat("Note body $note", note.content, CoreMatchers.`is`("With attachment"))
        Assert.assertEquals("Note updated at " + TestSuite.utcTime(note.updatedDate),
                TestSuite.utcTime(2019, Calendar.NOVEMBER, 10, 10, 44, 37).toString(),
                TestSuite.utcTime(note.updatedDate).toString())
        Assert.assertFalse("Note is sensitive $note", note.isSensitive)
        val audience = activity.audience()
        Assert.assertEquals("Visibility of $activity", Visibility.PUBLIC, audience.visibility)
        val oids = Arrays.asList(
                "https://queer.hacktivis.me/users/AndStatus/followers"
        )
        oids.forEach(Consumer { oid: String? -> Assert.assertTrue("Audience should contain $oid\n $activity\n $audience", audience.containsOid(oid)) })
        val attachments = activity.getNote().attachments
        Assert.assertTrue("Attachments of $activity", attachments.nonEmpty)
        val executionContext = CommandExecutionContext(
                 MyContextHolder.myContextHolder.getNow(),
                CommandData.Companion.newTimelineCommand(CommandEnum.UPDATE_NOTE, mock.getData().myAccount, TimelineType.SENT))
        DataUpdater(executionContext).onActivity(activity)
        val attachmentsStored: Attachments = Attachments.Companion.load( MyContextHolder.myContextHolder.getNow(), activity.getNote().noteId)
        Assert.assertTrue("Attachments should be stored of $activity\n $attachmentsStored\n",
                attachmentsStored.nonEmpty)
        Assert.assertEquals("Attachment stored of $activity\n $attachmentsStored\n",
                attachments.list, attachmentsStored.list)
        val noteStored: Note = Note.Companion.loadContentById(mock.getData().origin.myContext, note.noteId)
        val audienceStored = noteStored.audience()
        oids.forEach(Consumer { oid: String? -> Assert.assertTrue("Audience should contain $oid\n $activity\n $audienceStored", audienceStored.containsOid(oid)) })
        Assert.assertTrue("Audience of $activity\n $audienceStored", audienceStored.hasNonSpecial())
        Assert.assertFalse("Note is sensitive $noteStored", noteStored.isSensitive)
    }

    @Test
    @Throws(IOException::class)
    fun getActor() {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_get_actor)
        val actorOid = "https://pleroma.site/users/ActivityPubTester"
        val partial: Actor = Actor.Companion.fromOid(mock.getData().origin, actorOid)
        val received = mock.connection.getActor(partial).get()
        Assert.assertEquals("Actor's oid $received", actorOid, received.oid)
        MatcherAssert.assertThat("Note name $received", received.getUsername(), CoreMatchers.`is`("ActivityPubTester"))
        val executionContext = CommandExecutionContext(
                 MyContextHolder.myContextHolder.getNow(),
                CommandData.Companion.newActorCommandAtOrigin(
                        CommandEnum.GET_ACTOR, partial, "", mock.getData().origin))
        val activity = executionContext.myAccount.actor.update(received)
        DataUpdater(executionContext).onActivity(activity)
        val stored: Actor = Actor.Companion.load(executionContext.myContext, received.actorId, true, Supplier<Actor> { Actor.EMPTY })
        Assert.assertEquals("Actor's oid $stored", actorOid, stored.oid)
    }
}