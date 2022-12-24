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
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DemoNoteInserter
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.GroupMembership
import org.andstatus.app.data.MyContentType
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.NoteForAnyAccount
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.net.social.ConnectionStub.Companion
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.service.CommandExecutorOther
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtils
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*
import java.util.function.Consumer

class ConnectionMastodonTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private val stub: ConnectionStub = Companion.newFor(DemoData.demoData.mastodonTestAccountName)
    private val accountActor: Actor = stub.data.getAccountActor()

    @Test
    fun testGetHomeTimeline() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_home_timeline)
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.HOME_TIMELINE,
            TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor
        ).get()
        assertNotNull("timeline returned", timeline)
        val size = 1
        assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        val ind = 0
        val activity = timeline[ind] ?: throw IllegalStateException("No activity")
        val note = activity.getNote()
        assertEquals("Activity oid", "22", activity.getOid())
        assertEquals("Note Oid", "22", note.oid)
        assertEquals(
            "Account unknown $activity", true, myContext.accounts
                .fromActorOfSameOrigin(activity.accountActor).isValid
        )
        assertEquals("Is not a note $activity", AObjectType.NOTE, activity.getObjectType())
        assertEquals("Favorited $activity", TriState.UNKNOWN, note.getFavoritedBy(activity.accountActor))
        assertEquals("Counters $activity", 678, note.getLikesCount())
        assertEquals("Counters $activity", 234, note.getReblogsCount())
        assertEquals("Counters $activity", 11, note.getRepliesCount())
        DemoNoteInserter.Companion.assertVisibility(note.audience(), Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val actor = activity.getActor()
        val stringDate = "2017-04-16T11:13:12.133Z"
        val parsedDate = stub.connection.parseDate(stringDate)
        assertEquals("Parsing $stringDate", 4, (Date(parsedDate).month + 1).toLong())
        assertEquals("Created at", parsedDate, actor.getCreatedDate())
        assertTrue("Actor is partially defined $actor", actor.isFullyDefined())
        assertEquals("Actor Oid", "37", actor.oid)
        assertEquals("Username", "t131t1", actor.getUsername())
        assertEquals("Note Oid $activity", "22", note.oid)
        assertEquals("Note url$activity", "https://neumastodon.com/@t131t1/22", note.url)
        assertEquals("Name", "", note.getName())
        assertEquals("Summary", "This is a test spoiler", note.summary)
        assertEquals("Body", "<p>I&apos;m figuring out how to work with Mastodon</p>", note.content)
        assertEquals("Note application", "Web", note.via)
        assertEquals("Media attachments", 2, note.attachments.size.toLong())
        val attachment = note.attachments.list[0]
        assertEquals("Content type", MyContentType.IMAGE, attachment.contentType)
        assertEquals(
            "Media URI",
            UriUtils.fromString("https://files.neumastodon.com/media_attachments/files/000/306/223/original/e678f956970a585b.png?1492832537"),
            attachment.uri
        )
        timeline.items.forEach(Consumer { act: AActivity -> act.setUpdatedNow(0) })
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.HOME)
        )
        DataUpdater(executionContext).onActivity(activity)
    }

    @Test
    fun testGetPrivateNotes() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_private_notes)
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.PRIVATE_NOTES,
            TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20, accountActor
        ).get()
        assertNotNull("timeline returned", timeline)
        val size = 4
        assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        val activity3 = timeline[3] ?: throw IllegalStateException("No activity")
        val note3 = activity3.getNote()
        assertEquals("Activity oid", "104114771989428879", activity3.getOid())
        assertEquals(
            "Account unknown $activity3", true, myContext.accounts
                .fromActorOfSameOrigin(activity3.accountActor).isValid
        )
        assertEquals("Is not a note $activity3", AObjectType.NOTE, activity3.getObjectType())
        assertEquals("Favorited $activity3", TriState.UNKNOWN, note3.getFavoritedBy(activity3.accountActor))
        DemoNoteInserter.Companion.assertVisibility(note3.audience(), Visibility.PRIVATE)
        assertTrue("Audience: " + note3.audience(), note3.audience().containsOid("886798"))
        assertEquals("Audience: " + note3.audience(), "lanodan@queer.hacktivis.me",
            note3.audience().getRecipients().stream()
                .filter { actor: Actor -> actor.getUsername() == "lanodan" }
                .findAny()
                .map { obj: Actor -> obj.getWebFingerId() }
                .orElse("(not found)"))
        val actor3 = activity3.getActor()
        val stringDate = "2016-10-14T08:05:36.581Z"
        val parsedDate = stub.connection.parseDate(stringDate)
        assertEquals("Parsing $stringDate", 10, (Date(parsedDate).month + 1).toLong())
        assertEquals("Created at", parsedDate, actor3.getCreatedDate())
        assertTrue("Actor is partially defined $actor3", actor3.isFullyDefined())
        assertEquals("Actor Oid", "5962", actor3.oid)
        assertEquals("Username", "AndStatus", actor3.getUsername())
        assertEquals("Note Oid", "104114771989428879", note3.oid)
        assertEquals("Note url$activity3", "https://mastodon.social/@AndStatus/104114771989428879", note3.url)
        assertEquals("Name", "", note3.getName())
        assertEquals("Summary", "", note3.summary)
        MatcherAssert.assertThat("Body", note3.content, CoreMatchers.containsString("Will monitor there"))
        assertEquals("Note application", "Web", note3.via)
        assertEquals("Media attachments", 0, note3.attachments.size.toLong())
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newTimelineCommand(CommandEnum.GET_TIMELINE, ma, TimelineType.PRIVATE)
        )
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
        stub.http.addResponse(stringResponse)
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.HOME_TIMELINE,
            TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor
        ).get()
        DemoNoteInserter.Companion.assertVisibility(timeline[0]!!.getNote().audience(), visibility)
    }

    @Test
    fun testGetConversation() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_get_conversation)
        val timeline = stub.connection.getConversation("5596683").get()
        assertNotNull("timeline returned", timeline)
        assertEquals("Number of items in the Timeline", 5, timeline.size.toLong())
    }

    @Test
    fun testGetNotifications() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_notifications)
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.NOTIFICATIONS_TIMELINE,
            TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20, accountActor
        ).get()
        assertNotNull("timeline returned", timeline)
        assertEquals("Number of items in the Timeline", 20, timeline.size().toLong())
        var ind = 0
        var activity = timeline[ind] ?: throw IllegalStateException("No activity")
        assertEquals("Activity oid", "2667058", activity.getOid())
        assertEquals("Note Oid", "4729037", activity.getNote().oid)
        assertEquals("Is not a Reblog $activity", ActivityType.ANNOUNCE, activity.type)
        assertEquals("Is not an activity", AObjectType.ACTIVITY, activity.getObjectType())
        var actor = activity.getActor()
        assertEquals("Actor's Oid", "15451", actor.oid)
        assertEquals("Actor's username", "Chaosphere", actor.getUsername())
        assertEquals("WebfingerId", "chaosphere@mastodon.social", actor.getWebFingerId())
        assertEquals("Author's username$activity", "AndStatus", activity.getAuthor().getUsername())
        assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        ind = 2
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        assertEquals("Activity oid", "2674022", activity.getOid())
        assertEquals("Note Oid", "4729037", activity.getNote().oid)
        assertEquals("Is not an activity $activity", AObjectType.ACTIVITY, activity.getObjectType())
        assertEquals("Is not LIKE $activity", ActivityType.LIKE, activity.type)
        MatcherAssert.assertThat(
            activity.getNote().content,
            CoreMatchers.`is`("<p>IT infrastructure of modern church</p>")
        )
        assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        assertEquals("Author's username", "AndStatus", activity.getAuthor().getUsername())
        actor = activity.getActor()
        assertEquals("Actor's Oid", "48790", actor.oid)
        assertEquals("Actor's Username", "vfrmedia", actor.getUsername())
        assertEquals("WebfingerId", "vfrmedia@social.tchncs.de", actor.getWebFingerId())
        ind = 17
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        assertEquals("Is not FOLLOW $activity", ActivityType.FOLLOW, activity.type)
        assertEquals("Is not an ACTOR", AObjectType.ACTOR, activity.getObjectType())
        actor = activity.getActor()
        assertEquals("Actor's Oid", "24853", actor.oid)
        assertEquals("Username", "resir014", actor.getUsername())
        assertEquals("WebfingerId", "resir014@icosahedron.website", actor.getWebFingerId())
        val objActor = activity.getObjActor()
        assertEquals("Not following me$activity", accountActor.oid, objActor.oid)
        ind = 19
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        assertEquals("Is not UPDATE $activity", ActivityType.UPDATE, activity.type)
        assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType())
        MatcherAssert.assertThat(activity.getNote().content, CoreMatchers.containsString("universe of Mastodon"))
        actor = activity.getActor()
        assertEquals("Actor's Oid", "119218", actor.oid)
        assertEquals("Username", "izwx6502", actor.getUsername())
        assertEquals("WebfingerId", "izwx6502@mstdn.jp", actor.getWebFingerId())
    }

    @Test
    fun testGetActor() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_get_actor)
        val actor = stub.connection.getActor(Actor.Companion.fromOid(accountActor.origin, "5962")).get()
        assertTrue(actor.toString(), actor.nonEmpty)
        assertEquals("Actor's Oid", "5962", actor.oid)
        assertEquals("Username", "AndStatus", actor.getUsername())
        assertEquals("WebfingerId", "andstatus@mastodon.social", actor.getWebFingerId())
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
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_get_note)
        val activity = stub.connection.getNote("101064848262880936").get()
        assertEquals("Is not UPDATE $activity", ActivityType.UPDATE, activity.type)
        assertEquals("Is not a note", AObjectType.NOTE, activity.getObjectType())
        val actor = activity.getActor()
        assertEquals("Actor's Oid", "32", actor.oid)
        assertEquals("Username", "somePettter", actor.getUsername())
        assertEquals("WebfingerId", "somepettter@social.umeahackerspace.se", actor.getWebFingerId())
        val note = activity.getNote()
        MatcherAssert.assertThat(note.content, CoreMatchers.containsString("CW should properly"))
        activity.setUpdatedNow(0)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, ma, 123)
        )
        DataUpdater(executionContext).onActivity(activity)
        assertOneRecipient(
            activity, "AndStatus", "https://mastodon.example.com/@AndStatus",
            "andstatus@" + accountActor.origin.getHost()
        )
        assertOneRecipient(
            activity, "qwertystop", "https://wandering.shop/@qwertystop",
            "qwertystop@wandering.shop"
        )
        DemoNoteInserter.Companion.assertVisibility(activity.getNote().audience(), Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val audience: Audience = fromNoteId(accountActor.origin, activity.getNote().noteId)
        DemoNoteInserter.Companion.assertVisibility(audience, Visibility.PUBLIC_AND_TO_FOLLOWERS)
    }

    private fun assertOneRecipient(activity: AActivity, username: String?, profileUrl: String?, webFingerId: String?) {
        val audience = activity.getNote().audience()
        val actor = audience.getNonSpecialActors().stream()
            .filter { a: Actor -> a.getUsername() == username }.findAny().orElse(Actor.EMPTY)
        assertTrue("$username should be mentioned: $activity", actor.nonEmpty)
        assertEquals("Mentioned user: $activity", profileUrl, actor.getProfileUrl())
        assertEquals("Mentioned user: $activity", webFingerId, actor.getWebFingerId())
    }

    @Test
    fun reblog() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_get_reblog)
        val activity = stub.connection.getNote("101100271392454703").get()
        assertEquals("Is not ANNOUNCE $activity", ActivityType.ANNOUNCE, activity.type)
        assertEquals("Is not an Activity", AObjectType.ACTIVITY, activity.getObjectType())
        val actor = activity.getActor()
        assertEquals("Actor's Oid", "153111", actor.oid)
        assertEquals("Username", "ZeniorXV", actor.getUsername())
        assertEquals("WebfingerId", "zeniorxv@mastodon.social", actor.getWebFingerId())
        val note = activity.getNote()
        MatcherAssert.assertThat(note.content, CoreMatchers.containsString("car of the future"))
        val author = activity.getAuthor()
        assertEquals("Author's Oid", "159379", author.oid)
        assertEquals("Username", "bjoern", author.getUsername())
        assertEquals("WebfingerId", "bjoern@mastodon.social", author.getWebFingerId())
        activity.setUpdatedNow(0)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, ma, 123)
        )
        DataUpdater(executionContext).onActivity(activity)
        assertNotEquals("Activity wasn't saved $activity", 0, activity.getId())
        assertNotEquals("Reblogged note wasn't saved $activity", 0, activity.getNote().noteId)
    }

    @Test
    fun tootWithVideoAttachment() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_video)
        assertOneTootWithVideo(
            "263975",
            "https://mastodon.social/media_proxy/11640109/original",
            "https://mastodon.social/media_proxy/11640109/small"
        )
    }

    @Test
    fun originalTootWithVideoAttachment() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_video_original)
        assertOneTootWithVideo(
            "10496",
            "https://mastodont.cat/system/media_attachments/files/000/684/914/original/7424effb937d991c.mp4?1550739268",
            "https://mastodont.cat/system/media_attachments/files/000/684/914/small/7424effb937d991c.png?1550739268"
        )
    }

    private fun assertOneTootWithVideo(actorOid: String?, videoUri: String?, previewUri: String?) {
        val timeline = stub.connection.getTimeline(
            true, ApiRoutineEnum.ACTOR_TIMELINE,
            TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20,
            Actor.Companion.fromOid(stub.data.getOrigin(), actorOid)
        ).get()
        assertNotNull("timeline returned", timeline)
        assertEquals("Number of items in the Timeline", 1, timeline.size().toLong())
        val activity = timeline[0] ?: throw IllegalStateException("No activity")
        val note = activity.getNote()
        assertEquals("Media attachments " + note.attachments, 2, note.attachments.size.toLong())
        val video = note.attachments.list[0]
        assertEquals("Content type", MyContentType.VIDEO, video.contentType)
        assertEquals(
            "Media URI", UriUtils.fromString(videoUri),
            video.uri
        )
        val preview = note.attachments.list[1]
        assertEquals("Content type", MyContentType.IMAGE, preview.contentType)
        assertEquals(
            "Media URI", UriUtils.fromString(previewUri),
            preview.uri
        )
        assertEquals("Preview of", preview.previewOf, video)
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.mastodonTestAccountName)
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_CONVERSATION, ma, 123)
        )
        DataUpdater(executionContext).onActivity(activity)
        val downloads: List<DownloadData> = DownloadData.Companion.fromNoteId(myContext, note.noteId)
        assertEquals("Saved downloads $downloads", 2, downloads.size.toLong())
        val dPreview = downloads.stream().filter { d: DownloadData -> d.getContentType().isImage() }.findAny()
            .orElse(DownloadData.Companion.EMPTY)
        assertEquals("Preview URL $downloads", preview.uri, dPreview.getUri())
        assertEquals("Preview $downloads", 0, dPreview.getDownloadNumber())
        val dVideo = downloads.stream().filter { d: DownloadData -> d.getContentType() == MyContentType.VIDEO }
            .findAny().orElse(DownloadData.Companion.EMPTY)
        assertNotEquals("Video URL not saved $downloads", 0, dVideo.downloadId)
        assertEquals("Preview $downloads", dVideo.downloadId, dPreview.getPreviewOfDownloadId())
        assertEquals("Video URL $downloads", video.uri, dVideo.getUri())
        assertEquals("Video $downloads", 1, dVideo.getDownloadNumber())
        val nfa = NoteForAnyAccount(myContext, activity.getId(), activity.getNote().noteId)
        assertEquals(preview.uri, nfa.downloads.getFirstForTimeline().getUri())
        assertEquals(MyContentType.IMAGE, nfa.downloads.getFirstForTimeline().getContentType())
        assertEquals(dVideo.downloadId, nfa.downloads.getFirstForTimeline().getPreviewOfDownloadId())
        assertEquals(video.uri, nfa.downloads.getFirstToShare().getUri())
        assertEquals(MyContentType.VIDEO, nfa.downloads.getFirstToShare().getContentType())
    }

    @Test
    fun testGetListsOfUser() {
        val listsOfUser = getListsOfUser()
        val andstatusCombined = listsOfUser.find { it.oid.endsWith("13578") }
        assertNotNull("Should contain list with id=13578: $listsOfUser", andstatusCombined)
        getListMembers(andstatusCombined!!)
    }

    private fun getListsOfUser(): List<Actor> {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_lists_of_user)
        val expectedOid = ConnectionMastodon.MASTODON_LIST_OID_PREFIX + "19919"
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newActorCommand(CommandEnum.GET_LISTS, accountActor, null)
        )

        CommandExecutorOther(executionContext).getListsOfUser(accountActor) { lists ->
            assertEquals("Should be not empty", 2, lists.size)
            val list0: Actor = lists[0]
            assertEquals("$list0", GroupType.LIST_MEMBERS, list0.groupType)
            assertEquals("oid $list0", expectedOid, list0.oid)
            assertEquals("username $list0", expectedOid, list0.getUsername())
            assertEquals("$list0", "AndStatus in Fediverse", list0.getRealName())
            assertEquals(
                "$list0", expectedOid + "@" + accountActor.origin.url?.host, list0.getWebFingerId()
            )
            assertEquals("Parent should be the account actor", accountActor, list0.getParent())
            assertTrue("Should be fully defined: $list0", list0.isFullyDefined())
        }.also {
            assertTrue("Should be success: $it", it.isSuccess)
        }

        val members = GroupMembership.getSingleGroupMemberIds(myContext, accountActor.actorId, GroupType.LISTS)
            .map { Actor.load(myContext, it, true, Actor::EMPTY) }
        assertEquals("Should be 2 members $members", 2, members.size)
        val storedList = members.firstOrNull() { expectedOid == it.oid }
        assertNotNull("$expectedOid should be a member, $members", storedList)
        assertEquals("$expectedOid should be a group, $members", GroupType.LIST_MEMBERS, storedList?.groupType)
        return members
    }

    private fun getListMembers(group: Actor) {
        stub.http.addResponse(org.andstatus.app.test.R.raw.mastodon_list_members)
        val expectedOid = "179473"
        val executionContext = CommandExecutionContext(
            myContext, CommandData.Companion.newActorCommand(CommandEnum.GET_LIST_MEMBERS, group, null)
        )

        CommandExecutorOther(executionContext).getListMembers(group) { members ->
            assertEquals("Should be not empty $members", 3, members.size)
            val member0: Actor = members[0]
            assertFalse("Member should not be a group: $member0", member0.groupType.isGroupLike)
            assertEquals("oid $member0", expectedOid, member0.oid)
            assertEquals("username $member0", "AndStatus", member0.getUsername())
            assertEquals("$member0", "AndStatus@mstdn.io", member0.getRealName())
            assertEquals("$member0", "andstatus@mstdn.io", member0.getWebFingerId())
            assertEquals("Should be no Parent", Actor.EMPTY, member0.getParent())
            assertTrue("Should be fully defined: $member0", member0.isFullyDefined())
        }.also {
            assertTrue("Should be success: $it", it.isSuccess)
        }

        val members = MyQuery.getLongs(myContext, GroupMembership.selectGroupMemberIds(group))
            .map { Actor.load(myContext, it, true, Actor::EMPTY) }
        assertEquals("Should be 3 members $members", 3, members.size)
        val storedMember = members.firstOrNull() { expectedOid == it.oid }
        assertNotNull("$expectedOid should be a member, $members", storedMember)
        assertFalse("Member should not be a group: $storedMember", storedMember!!.groupType.isGroupLike)
    }

}
