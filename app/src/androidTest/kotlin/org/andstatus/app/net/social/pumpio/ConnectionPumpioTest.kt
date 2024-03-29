/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social.pumpio

import android.net.Uri
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.AObjectType
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Attachments
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtilsTest
import org.andstatus.app.util.UrlUtils
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.core.StringStartsWith
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URL
import java.util.*
import java.util.stream.Collectors
import kotlin.properties.Delegates

class ConnectionPumpioTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var connection: ConnectionPumpio by Delegates.notNull()
    private var originUrl: URL by Delegates.notNull()
    private var stub: ConnectionStub by Delegates.notNull()
    private var keyStored: String by Delegates.notNull()
    private var secretStored: String by Delegates.notNull()

    @Before
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        originUrl = UrlUtils.fromString("https://" + DemoData.demoData.pumpioMainHost)
            ?: throw IllegalStateException("No Url")
        stub = ConnectionStub.newFor(DemoData.demoData.conversationAccountName)
        connection = stub.connection as ConnectionPumpio
        val oauthHttp = connection.oauthHttpOrThrow
        val data = oauthHttp.data
        data.originUrl = originUrl
        oauthHttp.oauthClientKeys = OAuthClientKeys.fromConnectionData(data).also { keys ->
            keyStored = keys.getConsumerKey()
            secretStored = keys.getConsumerSecret()
            if (!keys.areKeysPresent()) {
                oauthHttp.oauthClientKeys?.setConsumerKeyAndSecret(
                    "keyForThetestGetTimeline",
                    "thisIsASecret02341"
                )
            }
        }
    }

    @After
    fun tearDown() {
        if (keyStored.isNotEmpty()) {
            stub.http.oauthClientKeys?.setConsumerKeyAndSecret(keyStored, secretStored)
        }
    }

    @Test
    fun testOidToObjectType() {
        val oids = arrayOf<String>(
            "https://identi.ca/api/activity/L4v5OL93RrabouQc9_QGfg",
            "https://identi.ca/api/comment/ibpUqhU1TGCE2yHNbUv54g",
            "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
            "https://identi.ca/obj/ibpcomment",
            "http://identi.ca/notice/95772390",
            "acct:t131t@identi.ca",
            "http://identi.ca/user/46155",
            "https://identi.ca/api/user/andstatus/followers",
            ConnectionPumpio.Companion.PUBLIC_COLLECTION_ID
        )
        val objectTypes = arrayOf<String>(
            "activity",
            "comment",
            "note",
            "unknown object type: https://identi.ca/obj/ibpcomment",
            "note",
            "person",
            "person",
            "collection",
            "collection"
        )
        for (ind in oids.indices) {
            val oid = oids[ind]
            val objectType = objectTypes[ind]
            Assert.assertEquals("Expecting'$oid' to be '$objectType'", objectType, connection.oidToObjectType(oid))
        }
    }

    @Test
    fun actorOidToHost() {
        val oids = arrayOf<String?>(
            "t131t@identi.ca",
            "acct:somebody@example.com",
            "https://identi.ca/api/note/nlF5jl1HQciIs_zP85EeYg",
            "example.com",
            "@somewhere.com"
        )
        val hosts = arrayOf<String?>(
            "identi.ca",
            "example.com",
            "",
            "",
            "somewhere.com"
        )
        for (ind in oids.indices) {
            Assert.assertEquals("Expecting '" + hosts[ind] + "'", hosts[ind], connection.actorOidToHost(oids[ind]))
        }
    }

    @Test
    fun testGetConnectionAndUrl() {
        val origin = connection.data.getOrigin()
        val actors = arrayOf<Actor>(
            Actor.Companion.fromOid(origin, "acct:t131t@" + DemoData.demoData.pumpioMainHost)
                .setWebFingerId("t131t@" + DemoData.demoData.pumpioMainHost),
            Actor.Companion.fromOid(origin, "somebody@" + DemoData.demoData.pumpioMainHost)
                .setWebFingerId("somebody@" + DemoData.demoData.pumpioMainHost)
        )
        val urls = arrayOf<String?>(
            originUrl.toString() + "/api/user/t131t/profile",
            originUrl.toString() + "/api/user/somebody/profile"
        )
        val hosts = arrayOf<String?>(DemoData.demoData.pumpioMainHost, DemoData.demoData.pumpioMainHost)
        for (ind in actors.indices) {
            val conu: ConnectionAndUrl = ConnectionAndUrl
                .fromActor(connection, ApiRoutineEnum.GET_ACTOR, actors[ind]).get()
            Assert.assertEquals("Expecting '" + urls[ind] + "'", Uri.parse(urls[ind]), conu.uri)
            Assert.assertEquals(
                "Expecting '" + hosts[ind] + "'", hosts[ind],
                conu.httpConnection.data.originUrl?.host
            )
        }
    }

    @Test
    fun testGetTimeline() {
        val sinceId = "https%3A%2F%2F" + originUrl.getHost() + "%2Fapi%2Factivity%2Ffrefq3232sf"
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_actor_t131t_inbox)
        val webFingerId = "t131t@" + originUrl.getHost()
        val actor1: Actor = Actor.Companion.fromOid(connection.data.getOrigin(), "acct:$webFingerId")
            .setWebFingerId(webFingerId)
        val timeline = connection.getTimeline(
            true, ApiRoutineEnum.HOME_TIMELINE,
            TimelinePosition.Companion.of(sinceId), TimelinePosition.Companion.EMPTY, 20, actor1
        ).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 6
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        var ind = 0
        Assert.assertEquals("Posting image", AObjectType.NOTE, timeline[ind]?.getObjectType())
        assertActivity0FromTimeline(timeline[ind])
        ind++
        var activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Is not FOLLOW $activity", ActivityType.FOLLOW, activity.type)
        Assert.assertEquals("Actor", "acct:jpope@io.jpope.org", activity.getActor().oid)
        Assert.assertEquals("Actor is not my friend", TriState.TRUE, activity.getActor().isMyFriend)
        Assert.assertEquals("Activity Object", AObjectType.ACTOR, activity.getObjectType())
        var objActor = activity.getObjActor()
        Assert.assertEquals("objActor followed", "acct:atalsta@microca.st", objActor.oid)
        Assert.assertEquals("WebFinger ID", "atalsta@microca.st", objActor.webFingerId)
        Assert.assertEquals("Actor is my friend", TriState.FALSE, objActor.isMyFriend)
        ind++
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Is not FOLLOW $activity", ActivityType.FOLLOW, activity.type)
        Assert.assertEquals("Actor", AObjectType.ACTOR, activity.getObjectType())
        objActor = activity.getObjActor()
        Assert.assertEquals("Url of the actor", "https://identi.ca/t131t", activity.getActor().getProfileUrl())
        Assert.assertEquals("WebFinger ID", "t131t@identi.ca", activity.getActor().webFingerId)
        Assert.assertEquals("Actor is not my friend", TriState.TRUE, objActor.isMyFriend)
        Assert.assertEquals("Url of objActor", "https://fmrl.me/grdryn", objActor.getProfileUrl())
        ind++
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Is not LIKE $activity", ActivityType.LIKE, activity.type)
        Assert.assertEquals("Actor $activity", "acct:jpope@io.jpope.org", activity.getActor().oid)
        Assert.assertEquals(
            "Activity updated $activity",
            TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 22, 20, 25),
            TestSuite.utcTime(activity.getUpdatedDate())
        )
        var note = activity.getNote()
        Assert.assertEquals("Author $activity", "acct:lostson@fmrl.me", activity.getAuthor().oid)
        Assert.assertTrue(
            "Has a non special recipient " + note.audience().getRecipients(),
            note.audience().getNonSpecialActors().isEmpty()
        )
        Assert.assertEquals("Note oid $note", "https://fmrl.me/api/note/Dp-njbPQSiOfdclSOuAuFw", note.oid)
        Assert.assertEquals("Url of the note $note", "https://fmrl.me/lostson/note/Dp-njbPQSiOfdclSOuAuFw", note.url)
        MatcherAssert.assertThat(
            "Note body $note",
            note.content,
            StringStartsWith.startsWith("My new <b>Firefox</b> OS phone arrived today")
        )
        Assert.assertEquals(
            "Note updated $note",
            TestSuite.utcTime(2013, Calendar.SEPTEMBER, 20, 20, 4, 22),
            TestSuite.utcTime(note.updatedDate)
        )
        ind++
        note = timeline[ind]?.getNote() ?: throw IllegalStateException("No activity")
        Assert.assertTrue("Have a recipient", note.audience().hasNonSpecial())
        Assert.assertEquals("Directed to yvolk", "acct:yvolk@identi.ca", note.audience().getFirstNonSpecial().oid)
        ind++
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        note = activity.getNote()
        Assert.assertEquals(TriState.UNKNOWN, activity.isSubscribedByMe())
        Assert.assertTrue("Is a reply", note.inReplyTo.nonEmpty)
        Assert.assertEquals(
            "Is not a reply to this actor $activity", "jankusanagi@identi.ca",
            note.inReplyTo.getAuthor().uniqueName
        )
        Assert.assertEquals(TriState.UNKNOWN, note.inReplyTo.isSubscribedByMe())
    }

    private fun assertActivity0FromTimeline(activity: AActivity?) {
        val note = activity?.getNote() ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Note name $note", "Wheel Stand", note.getName())
        MatcherAssert.assertThat(
            "Note body $note",
            note.content,
            StringStartsWith.startsWith("Wow! Fantastic wheel stand at #DragWeek2013 today.")
        )
        Assert.assertEquals(
            "Note updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
            TestSuite.utcTime(2013, Calendar.SEPTEMBER, 13, 1, 8, 38),
            TestSuite.utcTime(activity.getUpdatedDate())
        )
        val actor = activity.getActor()
        assertJpopeActor(actor, false)
        Assert.assertEquals("Actor is an Author", actor, activity.getAuthor())
        Assert.assertNotEquals("Is a Reblog $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertEquals(
            "Favorited by me $activity",
            TriState.UNKNOWN,
            activity.getNote().getFavoritedBy(activity.accountActor)
        )
        val audience = note.audience()
        Assert.assertEquals(
            "Should be Public for now." +
                " Followers in cc aren't recognized yet as a Followers collection..." +
                "\n$activity", Visibility.PUBLIC, audience.visibility
        )
        Assert.assertFalse("Shouldn't be to Followers. We shouldn't know this yet?! $audience", audience.isFollowers())
        MatcherAssert.assertThat(
            audience.getRecipients().toString(),
            audience.getNonSpecialActors().stream().map { obj: Actor -> obj.getUsername() }
                .collect(Collectors.toList()),
            Matchers.containsInAnyOrder("user/jpope/followers")
        )
        val executionContext = CommandExecutionContext(
            myContext,
            CommandData.Companion.newTimelineCommand(
                CommandEnum.GET_TIMELINE,
                stub.data.getMyAccount(),
                TimelineType.HOME
            )
        )
        DataUpdater(executionContext).onActivity(activity)
        val actorStored: Actor = Actor.Companion.loadFromDatabase(
            stub.data.getOrigin().myContext, actor.actorId,
            { Actor.EMPTY }, false
        )
        assertJpopeActor(actorStored, true)
        val noteStored: Note = Note.Companion.loadContentById(myContext, note.noteId)
        val audienceStored = noteStored.audience()
        Assert.assertEquals(
            "Should be Public with Followers $audienceStored",
            Visibility.PUBLIC_AND_TO_FOLLOWERS, audienceStored.visibility
        )
        Assert.assertTrue(
            "Should be to Followers $audienceStored,\n" +
                "Audience before storage: $audience", audienceStored.isFollowers()
        )
        MatcherAssert.assertThat(
            audienceStored.getRecipients().toString(),
            audienceStored.getNonSpecialActors(),
            Matchers.`is`(Matchers.empty())
        )

        val conversationId = MyQuery.noteIdToLongColumnValue(NoteTable.CONVERSATION_ID, noteStored.noteId)
        val noteLinked: Note = Note.Companion.loadContentById(myContext, conversationId)
        Assert.assertEquals(
            "Note linked to wrong conversation $noteStored\nLinked to $conversationId, $noteLinked",
            noteStored.noteId, conversationId
        )
    }

    private fun assertJpopeActor(actor: Actor, stored: Boolean) {
        Assert.assertEquals("Sender's oid", "acct:jpope@io.jpope.org", actor.oid)
        Assert.assertEquals("Sender's username", "jpope", actor.getUsername())
        Assert.assertEquals("Sender's unique name in Origin", "jpope@io.jpope.org", actor.uniqueName)
        Assert.assertEquals("Sender's Display name", "jpope", actor.getRealName())
        Assert.assertEquals(
            "Sender's profile image URL", "https://io.jpope.org/uploads/jpope/2013/7/8/LPyLPw_thumb.png",
            actor.getAvatarUrl()
        )
        Assert.assertEquals("Sender's profile URL", "https://io.jpope.org/jpope", actor.getProfileUrl())
        Assert.assertEquals("Sender's Homepage", "https://io.jpope.org/jpope", actor.getHomepage())
        Assert.assertEquals("Sender's WebFinger ID", "jpope@io.jpope.org", actor.webFingerId)
        Assert.assertEquals("Description", "Does the Pope shit in the woods?", actor.getSummary())
        Assert.assertEquals("Notes count", 0, actor.notesCount)
        Assert.assertEquals("Favorites count", 0, actor.favoritesCount)
        Assert.assertEquals("Following (friends) count", 0, actor.followingCount)
        Assert.assertEquals("Followers count", 0, actor.followersCount)
        Assert.assertEquals("Location", "/dev/null", actor.location)
        Assert.assertEquals(
            "Created at",
            if (stored) RelativeTime.SOME_TIME_AGO else RelativeTime.DATETIME_MILLIS_NEVER,
            actor.getCreatedDate()
        )
        Assert.assertEquals(
            "Updated at", TestSuite.utcTime(2013, Calendar.SEPTEMBER, 12, 17, 10, 44),
            TestSuite.utcTime(actor.getUpdatedDate())
        )
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_INBOX, "https://io.jpope.org/api/user/jpope/inbox", actor)
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_PROFILE, "https://io.jpope.org/api/user/jpope/profile", actor)
        UriUtilsTest.assertEndpoint(
            ActorEndpointType.API_FOLLOWING,
            "https://io.jpope.org/api/user/jpope/following",
            actor
        )
        UriUtilsTest.assertEndpoint(
            ActorEndpointType.API_FOLLOWERS,
            "https://io.jpope.org/api/user/jpope/followers",
            actor
        )
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_LIKED, "https://io.jpope.org/api/user/jpope/favorites", actor)
    }

    @Test
    fun testGetFriends() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_actor_t131t_following)
        Assert.assertTrue(connection.hasApiEndpoint(ApiRoutineEnum.GET_FRIENDS))
        Assert.assertTrue(connection.hasApiEndpoint(ApiRoutineEnum.GET_FRIENDS_IDS))
        val webFingerId = "t131t@" + originUrl.getHost()
        val actor: Actor = Actor.Companion.fromOid(connection.data.getOrigin(), "acct:$webFingerId")
            .setWebFingerId(webFingerId)
        val actors = connection.getFriends(actor).get()
        Assert.assertNotNull("List of actors, who " + actor.getUniqueNameWithOrigin() + " is following", actors)
        val size = 5
        Assert.assertEquals("Response for t131t", size.toLong(), actors.size.toLong())
        Assert.assertEquals("Does the Pope shit in the woods?", actors[1].getSummary())
        Assert.assertEquals("gitorious", actors[2].getUsername())
        Assert.assertEquals("gitorious@identi.ca", actors[2].uniqueName)
        Assert.assertEquals("acct:ken@coding.example", actors[3].oid)
        Assert.assertEquals("Yuri Volkov", actors[4].getRealName())
    }

    @Test
    fun testReply() {
        val name = "To Peter"
        val contentPartToLookup = "Do you think it's true?"
        val content = "@peter $contentPartToLookup"
        val inReplyToOid = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv"
        val note: Note = Note.Companion.fromOriginAndOid(stub.data.getOrigin(), "", DownloadStatus.SENDING)
            .setName(name)
            .setContentPosted(content)
            .setInReplyTo(
                AActivity.Companion.newPartialNote(
                    stub.data.getMyAccount().actor,
                    Actor.EMPTY, inReplyToOid, RelativeTime.DATETIME_MILLIS_NEVER, DownloadStatus.UNKNOWN
                )
                    .setOid(inReplyToOid)
            )
        connection.updateNote(note)
        val result = stub.http.waitForPostContaining(contentPartToLookup)
        val jso = result.request.postParams.get().getJSONObject("object")
        Assert.assertEquals("Note name", name, MyHtml.htmlToPlainText(jso.getString("displayName")))
        Assert.assertEquals("Note content", content, MyHtml.htmlToPlainText(jso.getString("content")))
        Assert.assertEquals("Reply is comment", PObjectType.COMMENT.id, jso.getString("objectType"))
        Assert.assertTrue("InReplyTo is present", jso.has("inReplyTo"))
        val inReplyToObject = jso.getJSONObject("inReplyTo")
        Assert.assertEquals("Id of the in reply to object", inReplyToOid, inReplyToObject.getString("id"))
    }

    @Test
    fun testUpdateStatus() {
        val name = ""
        val content = "Testing the application..."
        val note: Note = Note.Companion.fromOriginAndOid(stub.data.getOrigin(), "", DownloadStatus.SENDING)
            .setName(name).setContentPosted(content)
        val tryActivity = connection.updateNote(note)
        val jsoActivity = stub.http.getLatestPostedJSONObject() ?: throw IllegalStateException("No activity")
        Assert.assertTrue(
            "Object present $jsoActivity\nResults: ${stub.http.getResults()}",
            jsoActivity.has("object")
        )
        val jso = jsoActivity.getJSONObject("object")
        Assert.assertEquals("Note name", name, MyHtml.htmlToPlainText(JsonUtils.optString(jso, "displayName")))
        Assert.assertEquals("Note content", content, MyHtml.htmlToPlainText(jso.getString("content")))
        Assert.assertEquals("Note without reply is a note", PObjectType.NOTE.id, jso.getString("objectType"))
        val toArray = jsoActivity.optJSONArray("to") ?: JSONArray()
        Assert.assertEquals("Only public recipient expected $jsoActivity", 1, toArray.length().toLong())
        val recipient = toArray[0] as JSONObject
        Assert.assertEquals(
            "Only public recipient expected $jsoActivity", ConnectionPumpio.PUBLIC_COLLECTION_ID,
            recipient.getString("id")
        )
        Assert.assertFalse("InReplyTo is not present $jsoActivity", jso.has("inReplyTo"))
    }

    @Test
    fun testReblog() {
        val rebloggedId = "https://identi.ca/api/note/94893FsdsdfFdgtjuk38ErKv"
        connection.announce(rebloggedId)
        val activity = stub.http.getLatestPostedJSONObject() ?: throw IllegalStateException("No activity")
        Assert.assertTrue("Object present", activity.has("object"))
        val obj = activity.getJSONObject("object")
        Assert.assertEquals("Sharing a note", PObjectType.NOTE.id, obj.getString("objectType"))
        Assert.assertFalse("Nothing in 'to'", activity.has("to"))
        Assert.assertFalse("No followers in CC", activity.has("cc"))
    }

    @Test
    fun testUndoFollowActor() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.unfollow_pumpio)
        val actorOid = "acct:evan@e14n.com"
        val activity = connection.follow(actorOid, false).get()
        Assert.assertEquals("Not unfollow action", ActivityType.UNDO_FOLLOW, activity.type)
        val objActor = activity.getObjActor()
        Assert.assertTrue("objActor is present", objActor.nonEmpty)
        Assert.assertEquals("Actor", "acct:t131t@pump1.example.com", activity.getActor().oid)
        Assert.assertEquals("Object of action", actorOid, objActor.oid)
    }

    @Test
    fun testParseDate() {
        val stringDate = "Wed Nov 27 09:27:01 -0300 2013"
        Assert.assertEquals("Bad date shouldn't throw ($stringDate)", 0, connection.parseDate(stringDate))
    }

    @Test
    fun testDestroyStatus() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_delete_comment_response)
        Assert.assertTrue(
            "Success", connection.deleteNote(
                "https://" + DemoData.demoData.pumpioMainHost
                    + "/api/comment/xf0WjLeEQSlyi8jwHJ0ttre"
            ).get()
        )
        val tried = connection.deleteNote("")
        Assert.assertTrue(tried.isFailure)
        MatcherAssert.assertThat(tried.cause, Matchers.isA(IllegalArgumentException::class.java))
    }

    @Test
    fun testPostWithImage() {
        // TODO: There should be 3 responses, just like for Video
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_activity_with_image)
        val note: Note = Note.Companion.fromOriginAndOid(stub.data.getOrigin(), "", DownloadStatus.SENDING)
            .setContentPosted("Test post note with media")
            .withAttachments(
                Attachments().add(
                    Attachment.Companion.fromUriAndMimeType(
                        DemoData.demoData.localImageTestUri,
                        MyContentType.IMAGE.generalMimeType
                    )
                )
            )
        val activity = connection.updateNote(note)
    }

    @Test
    fun testPostWithVideo() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_activity_with_video_response1)
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_activity_with_video_response2)
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_activity_with_video_response3)
        val name = "Note - Testing Video attachments in #AndStatus"
        val content = "<p dir=\"ltr\">Video attachment is here</p>"
        val note: Note = Note.Companion.fromOriginAndOid(stub.data.getOrigin(), "", DownloadStatus.SENDING)
            .setName(name).setContentPosted(content)
            .withAttachments(
                Attachments().add(
                    Attachment.Companion.fromUriAndMimeType(
                        DemoData.demoData.localVideoTestUri,
                        MyContentType.VIDEO.generalMimeType
                    )
                )
            )
        val activity = connection.updateNote(note).get()
        Assert.assertEquals("Responses counter " + stub.http, 3, stub.http.responsesCounter.toLong())
        val note2 = activity.getNote()
        Assert.assertEquals("Note name $activity", name, note2.getName())
        Assert.assertEquals("Note content $activity", content, note2.content)
        Assert.assertEquals("Should have an attachment $activity", false, note2.attachments.isEmpty)
        val attachment = note2.attachments.list[0]
        Assert.assertEquals("Video attachment $activity", MyContentType.VIDEO, attachment.contentType)
        Assert.assertEquals("Video content type $activity", "video/mp4", attachment.mimeType)
        Assert.assertEquals(
            "Video uri $activity",
            "https://identi.ca/uploads/andstatus/2018/4/11/7CmQmw.mp4", attachment.uri.toString()
        )
    }

    private fun privateGetNoteWithAttachment(uniqueUid: Boolean): Note {
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_activity_with_image)
        var note: Note = connection.getNote("https://io.jpope.org/api/activity/w9wME-JVQw2GQe6POK7FSQ").get().getNote()
        if (uniqueUid) {
            note = note.withNewOid(note.oid + "_" + DemoData.demoData.testRunUid)
        }
        Assert.assertNotNull("note returned", note)
        Assert.assertEquals("has attachment", 1, note.attachments.size.toLong())
        val attachment: Attachment =
            Attachment.Companion.fromUri("https://io.jpope.org/uploads/jpope/2014/8/18/m1o1bw.jpg")
        Assert.assertEquals("attachment", attachment, note.attachments.list[0])
        Assert.assertEquals("Body text", "<p>Hanging out up in the mountains.</p>\n", note.content)
        return note
    }

    @Test
    fun getNoteWithAttachment() {
        privateGetNoteWithAttachment(true)
    }

    @Test
    fun getNoteWithReplies() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.pumpio_note_self)
        val noteOid = "https://identi.ca/api/note/Z-x96Q8rTHSxTthYYULRHA"
        val activity = connection.getNote(noteOid).get()
        val note = activity.getNote()
        Assert.assertNotNull("note returned", note)
        Assert.assertEquals("Note oid", noteOid, note.oid)
        Assert.assertEquals("Number of replies", 2, note.replies.size.toLong())
        val reply = note.replies[0].getNote()
        Assert.assertEquals("Reply oid", "https://identi.ca/api/comment/cJdi4cGWQT-Z9Rn3mjr5Bw", reply.oid)
        Assert.assertEquals("Is not a Reply $activity", noteOid, reply.inReplyTo.getNote().oid)
    }

    @Test
    fun testActorOidToUsername() {
        val connection = ConnectionPumpio()
        val oid = DemoData.demoData.conversationAuthorSecondActorOid
        Assert.assertEquals("Username from $oid", "second", connection.actorOidToUsername(oid))
    }

    @Test
    fun testGetFriendsTypeActor() {
        // TODO:
    }

}
