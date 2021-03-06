/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.NoteForAnyAccount
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.net.social.ConnectionStub.Companion
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.function.Consumer
import kotlin.properties.Delegates

class ConnectionMastodonTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var stub: ConnectionStub by Delegates.notNull()
    private var accountActor: Actor = Actor.EMPTY

    @Before
    fun setUp() {
        stub = Companion.newFor(DemoData.demoData.mastodonTestAccountName)
        accountActor = stub.getData().getAccountActor()
    }

    @Test
    fun testGetHomeTimeline() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_home_timeline)
        val timeline = stub.connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 1
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        val ind = 0
        val activity = timeline[ind] ?: throw IllegalStateException("No activity")
        val note = activity.getNote()
        Assert.assertEquals("Activity oid", "22", activity.getOid())
        Assert.assertEquals("Note Oid", "22", note.oid)
        Assert.assertEquals("Account unknown $activity", true,  myContext.accounts
                .fromActorOfSameOrigin(activity.accountActor).isValid)
        Assert.assertEquals("Is not a note $activity", AObjectType.NOTE, activity.getObjectType())
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, note.getFavoritedBy(activity.accountActor))
        Assert.assertEquals("Counters $activity", 678, note.getLikesCount())
        Assert.assertEquals("Counters $activity", 234, note.getReblogsCount())
        Assert.assertEquals("Counters $activity", 11, note.getRepliesCount())
        DemoNoteInserter.Companion.assertVisibility(note.audience(), Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val actor = activity.getActor()
        val stringDate = "2017-04-16T11:13:12.133Z"
        val parsedDate = stub.connection.parseDate(stringDate)
        Assert.assertEquals("Parsing $stringDate", 4, (Date(parsedDate).month + 1).toLong())
        Assert.assertEquals("Created at", parsedDate, actor.getCreatedDate())
        Assert.assertTrue("Actor is partially defined $actor", actor.isFullyDefined())
        Assert.assertEquals("Actor Oid", "37", actor.oid)
        Assert.assertEquals("Username", "t131t1", actor.getUsername())
        Assert.assertEquals("Note Oid $activity", "22", note.oid)
        Assert.assertEquals("Note url$activity", "https://neumastodon.com/@t131t1/22", note.url)
        Assert.assertEquals("Name", "", note.getName())
        Assert.assertEquals("Summary", "This is a test spoiler", note.summary)
        Assert.assertEquals("Body", "<p>I&apos;m figuring out how to work with Mastodon</p>", note.content)
        Assert.assertEquals("Note application", "Web", note.via)
        Assert.assertEquals("Media attachments", 2, note.attachments.size().toLong())
        val attachment = note.attachments.list[0]
        Assert.assertEquals("Content type", MyContentType.IMAGE, attachment.contentType)
        Assert.assertEquals("Media URI", UriUtils.fromString("https://files.neumastodon.com/media_attachments/files/000/306/223/original/e678f956970a585b.png?1492832537"),
                attachment.uri)
        timeline.items.forEach(Consumer { act: AActivity -> act.setUpdatedNow(0) })
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME))
        DataUpdater(executionContext).onActivity(activity)
    }

    @Test
    fun testGetPrivateNotes() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_private_notes)
        val timeline = stub.connection.getTimeline(true, ApiRoutineEnum.PRIVATE_NOTES,
                TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 4
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        val activity3 = timeline[3] ?: throw IllegalStateException("No activity")
        val note3 = activity3.getNote()
        Assert.assertEquals("Activity oid", "104114771989428879", activity3.getOid())
        Assert.assertEquals("Account unknown $activity3", true,  myContext.accounts
                .fromActorOfSameOrigin(activity3.accountActor).isValid)
        Assert.assertEquals("Is not a note $activity3", AObjectType.NOTE, activity3.getObjectType())
        Assert.assertEquals("Favorited $activity3", TriState.UNKNOWN, note3.getFavoritedBy(activity3.accountActor))
        DemoNoteInserter.Companion.assertVisibility(note3.audience(), Visibility.PRIVATE)
        Assert.assertTrue("Audience: " + note3.audience(), note3.audience().containsOid("886798"))
        Assert.assertEquals("Audience: " + note3.audience(), "lanodan@queer.hacktivis.me",
                note3.audience().getRecipients().stream()
                        .filter { actor: Actor -> actor.getUsername() == "lanodan" }
                        .findAny()
                        .map { obj: Actor -> obj.getWebFingerId() }
                        .orElse("(not found)"))
        val actor3 = activity3.getActor()
        val stringDate = "2016-10-14T08:05:36.581Z"
        val parsedDate = stub.connection.parseDate(stringDate)
        Assert.assertEquals("Parsing $stringDate", 10, (Date(parsedDate).month + 1).toLong())
        Assert.assertEquals("Created at", parsedDate, actor3.getCreatedDate())
        Assert.assertTrue("Actor is partially defined $actor3", actor3.isFullyDefined())
        Assert.assertEquals("Actor Oid", "5962", actor3.oid)
        Assert.assertEquals("Username", "AndStatus", actor3.getUsername())
        Assert.assertEquals("Note Oid", "104114771989428879", note3.oid)
        Assert.assertEquals("Note url$activity3", "https://mastodon.social/@AndStatus/104114771989428879", note3.url)
        Assert.assertEquals("Name", "", note3.getName())
        Assert.assertEquals("Summary", "", note3.summary)
        MatcherAssert.assertThat("Body", note3.content, CoreMatchers.containsString("Will monitor there"))
        Assert.assertEquals("Note application", "Web", note3.via)
        Assert.assertEquals("Media attachments", 0, note3.attachments.size().toLong())
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.PRIVATE))
        timeline.items.forEach(Consumer { act: AActivity ->
            act.setUpdatedNow(0)
            DataUpdater(executionContext).onActivity(act)
        })
    }

    @Test
    fun testIncomingVisibility() {
        val response = RawResourceUtils.getString(org.andstatus.app.test.R.raw.mastodon_home_timeline)
        oneVisibility(response, Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val pattern = "\"visibility\": \"public\""
        oneVisibility(response.replace(pattern, "\"visibility\": \"unlisted\""), Visibility.PUBLIC_AND_TO_FOLLOWERS)
        oneVisibility(response.replace(pattern, "\"visibility\": \"private\""), Visibility.TO_FOLLOWERS)
        oneVisibility(response.replace(pattern, "\"visibility\": \"direct\""), Visibility.PRIVATE)
    }

    private fun oneVisibility(stringResponse: String, visibility: Visibility) {
        stub.getHttpStub().addResponse(stringResponse)
        val timeline = stub.connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        DemoNoteInserter.Companion.assertVisibility(timeline[0]!!.getNote().audience(), visibility)
    }

    @Test
    fun testGetConversation() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_get_conversation)
        val timeline = stub.connection.getConversation("5596683").get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 5, timeline.size.toLong())
    }

    @Test
    fun testGetNotifications() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_notifications)
        val timeline = stub.connection.getTimeline(true, ApiRoutineEnum.NOTIFICATIONS_TIMELINE,
                TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 20, timeline.size().toLong())
        var ind = 0
        var activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Activity oid", "2667058", activity.getOid())
        Assert.assertEquals("Note Oid", "4729037", activity.getNote().oid)
        Assert.assertEquals("Is not a Reblog $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertEquals("Is not an activity", AObjectType.ACTIVITY, activity.getObjectType())
        var actor = activity.getActor()
        Assert.assertEquals("Actor's Oid", "15451", actor.oid)
        Assert.assertEquals("Actor's username", "Chaosphere", actor.getUsername())
        Assert.assertEquals("WebfingerId", "chaosphere@mastodon.social", actor.getWebFingerId())
        Assert.assertEquals("Author's username$activity", "AndStatus", activity.getAuthor().getUsername())
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        ind = 2
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Activity oid", "2674022", activity.getOid())
        Assert.assertEquals("Note Oid", "4729037", activity.getNote().oid)
        Assert.assertEquals("Is not an activity $activity", AObjectType.ACTIVITY, activity.getObjectType())
        Assert.assertEquals("Is not LIKE $activity", ActivityType.LIKE, activity.type)
        MatcherAssert.assertThat(activity.getNote().content, CoreMatchers.`is`("<p>IT infrastructure of modern church</p>"))
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        Assert.assertEquals("Author's username", "AndStatus", activity.getAuthor().getUsername())
        actor = activity.getActor()
        Assert.assertEquals("Actor's Oid", "48790", actor.oid)
        Assert.assertEquals("Actor's Username", "vfrmedia", actor.getUsername())
        Assert.assertEquals("WebfingerId", "vfrmedia@social.tchncs.de", actor.getWebFingerId())
        ind = 17
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Is not FOLLOW $activity", ActivityType.FOLLOW, activity.type)
        Assert.assertEquals("Is not an ACTOR", AObjectType.ACTOR, activity.getObjectType())
        actor = activity.getActor()
        Assert.assertEquals("Actor's Oid", "24853", actor.oid)
        Assert.assertEquals("Username", "resir014", actor.getUsername())
        Assert.assertEquals("WebfingerId", "resir014@icosahedron.website", actor.getWebFingerId())
        val objActor = activity.getObjActor()
        Assert.assertEquals("Not following me$activity", accountActor.oid, objActor.oid)
        ind = 19
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Is not UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType())
        MatcherAssert.assertThat(activity.getNote().content, CoreMatchers.containsString("universe of Mastodon"))
        actor = activity.getActor()
        Assert.assertEquals("Actor's Oid", "119218", actor.oid)
        Assert.assertEquals("Username", "izwx6502", actor.getUsername())
        Assert.assertEquals("WebfingerId", "izwx6502@mstdn.jp", actor.getWebFingerId())
    }

    @Test
    fun testGetActor() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_get_actor)
        val actor = stub.connection.getActor(Actor.Companion.fromOid(accountActor.origin, "5962")).get()
        Assert.assertTrue(actor.toString(), actor.nonEmpty)
        Assert.assertEquals("Actor's Oid", "5962", actor.oid)
        Assert.assertEquals("Username", "AndStatus", actor.getUsername())
        Assert.assertEquals("WebfingerId", "andstatus@mastodon.social", actor.getWebFingerId())
        MatcherAssert.assertThat("Bio", actor.getSummary(), CoreMatchers.containsString("multiple Social networks"))
        MatcherAssert.assertThat("Fields appended", actor.getSummary(), CoreMatchers.containsString("Website: "))
        MatcherAssert.assertThat("Fields appended", actor.getSummary(), CoreMatchers.containsString("FAQ: "))
        MatcherAssert.assertThat("Fields appended", actor.getSummary(), CoreMatchers.containsString("GitHub: "))
    }

    @Test
    fun mentionsInANote() {
        mentionsInANoteOneLoad(1)
        mentionsInANoteOneLoad(2)
        mentionsInANoteOneLoad(3)
    }

    fun mentionsInANoteOneLoad(iteration: Int) {
        MyLog.i("mentionsInANote$iteration", "started")
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_get_note)
        val activity = stub.connection.getNote("101064848262880936").get()
        Assert.assertEquals("Is not UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType())
        val actor = activity.getActor()
        Assert.assertEquals("Actor's Oid", "32", actor.oid)
        Assert.assertEquals("Username", "somePettter", actor.getUsername())
        Assert.assertEquals("WebfingerId", "somepettter@social.umeahackerspace.se", actor.getWebFingerId())
        val note = activity.getNote()
        MatcherAssert.assertThat(note.content, CoreMatchers.containsString("CW should properly"))
        activity.setUpdatedNow(0)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, ma, 123))
        DataUpdater(executionContext).onActivity(activity)
        assertOneRecipient(activity, "AndStatus", "https://mastodon.example.com/@AndStatus",
                "andstatus@" + accountActor.origin.getHost())
        assertOneRecipient(activity, "qwertystop", "https://wandering.shop/@qwertystop",
                "qwertystop@wandering.shop")
        DemoNoteInserter.Companion.assertVisibility(activity.getNote().audience(), Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val audience: Audience = fromNoteId(accountActor.origin, activity.getNote().noteId)
        DemoNoteInserter.Companion.assertVisibility(audience, Visibility.PUBLIC_AND_TO_FOLLOWERS)
    }

    private fun assertOneRecipient(activity: AActivity, username: String?, profileUrl: String?, webFingerId: String?) {
        val audience = activity.getNote().audience()
        val actor = audience.getNonSpecialActors().stream()
                .filter { a: Actor -> a.getUsername() == username }.findAny().orElse(Actor.EMPTY)
        Assert.assertTrue("$username should be mentioned: $activity", actor.nonEmpty)
        Assert.assertEquals("Mentioned user: $activity", profileUrl, actor.getProfileUrl())
        Assert.assertEquals("Mentioned user: $activity", webFingerId, actor.getWebFingerId())
    }

    @Test
    fun reblog() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_get_reblog)
        val activity = stub.connection.getNote("101100271392454703").get()
        Assert.assertEquals("Is not ANNOUNCE $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertEquals("Is not an Activity", AObjectType.ACTIVITY, activity.getObjectType())
        val actor = activity.getActor()
        Assert.assertEquals("Actor's Oid", "153111", actor.oid)
        Assert.assertEquals("Username", "ZeniorXV", actor.getUsername())
        Assert.assertEquals("WebfingerId", "zeniorxv@mastodon.social", actor.getWebFingerId())
        val note = activity.getNote()
        MatcherAssert.assertThat(note.content, CoreMatchers.containsString("car of the future"))
        val author = activity.getAuthor()
        Assert.assertEquals("Author's Oid", "159379", author.oid)
        Assert.assertEquals("Username", "bjoern", author.getUsername())
        Assert.assertEquals("WebfingerId", "bjoern@mastodon.social", author.getWebFingerId())
        activity.setUpdatedNow(0)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, ma, 123))
        DataUpdater(executionContext).onActivity(activity)
        Assert.assertNotEquals("Activity wasn't saved $activity", 0, activity.getId())
        Assert.assertNotEquals("Reblogged note wasn't saved $activity", 0, activity.getNote().noteId)
    }

    @Test
    fun tootWithVideoAttachment() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_video)
        assertOneTootWithVideo("263975",
                "https://mastodon.social/media_proxy/11640109/original",
                "https://mastodon.social/media_proxy/11640109/small")
    }

    @Test
    fun originalTootWithVideoAttachment() {
        stub.addResponse(org.andstatus.app.test.R.raw.mastodon_video_original)
        assertOneTootWithVideo("10496",
                "https://mastodont.cat/system/media_attachments/files/000/684/914/original/7424effb937d991c.mp4?1550739268",
                "https://mastodont.cat/system/media_attachments/files/000/684/914/small/7424effb937d991c.png?1550739268")
    }

    private fun assertOneTootWithVideo(actorOid: String?, videoUri: String?, previewUri: String?) {
        val timeline = stub.connection.getTimeline(true, ApiRoutineEnum.ACTOR_TIMELINE,
                TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20,
                Actor.Companion.fromOid(stub.getData().getOrigin(), actorOid)).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 1, timeline.size().toLong())
        val activity = timeline[0] ?: throw IllegalStateException("No activity")
        val note = activity.getNote()
        Assert.assertEquals("Media attachments " + note.attachments, 2, note.attachments.size().toLong())
        val video = note.attachments.list[0]
        Assert.assertEquals("Content type", MyContentType.VIDEO, video.contentType)
        Assert.assertEquals("Media URI", UriUtils.fromString(videoUri),
                video.uri)
        val preview = note.attachments.list[1]
        Assert.assertEquals("Content type", MyContentType.IMAGE, preview.contentType)
        Assert.assertEquals("Media URI", UriUtils.fromString(previewUri),
                preview.uri)
        Assert.assertEquals("Preview of", preview.previewOf, video)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_CONVERSATION, ma, 123))
        DataUpdater(executionContext).onActivity(activity)
        val downloads: List<DownloadData> = DownloadData.Companion.fromNoteId( myContext, note.noteId)
        Assert.assertEquals("Saved downloads $downloads", 2, downloads.size.toLong())
        val dPreview = downloads.stream().filter { d: DownloadData -> d.getContentType().isImage() }.findAny().orElse(DownloadData.Companion.EMPTY)
        Assert.assertEquals("Preview URL $downloads", preview.uri, dPreview.getUri())
        Assert.assertEquals("Preview $downloads", 0, dPreview.getDownloadNumber())
        val dVideo = downloads.stream().filter { d: DownloadData -> d.getContentType() == MyContentType.VIDEO }
                .findAny().orElse(DownloadData.Companion.EMPTY)
        Assert.assertNotEquals("Video URL not saved $downloads", 0, dVideo.getDownloadId())
        Assert.assertEquals("Preview $downloads", dVideo.getDownloadId(), dPreview.getPreviewOfDownloadId())
        Assert.assertEquals("Video URL $downloads", video.uri, dVideo.getUri())
        Assert.assertEquals("Video $downloads", 1, dVideo.getDownloadNumber())
        val nfa = NoteForAnyAccount(myContext, activity.getId(), activity.getNote().noteId)
        Assert.assertEquals(preview.uri, nfa.downloads.getFirstForTimeline().getUri())
        Assert.assertEquals(MyContentType.IMAGE, nfa.downloads.getFirstForTimeline().getContentType())
        Assert.assertEquals(dVideo.getDownloadId(), nfa.downloads.getFirstForTimeline().getPreviewOfDownloadId())
        Assert.assertEquals(video.uri, nfa.downloads.getFirstToShare().getUri())
        Assert.assertEquals(MyContentType.VIDEO, nfa.downloads.getFirstToShare().getContentType())
    }
}
