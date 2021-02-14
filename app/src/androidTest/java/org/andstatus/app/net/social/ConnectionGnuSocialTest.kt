/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.TextMediaType
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyUrlSpan
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtilsTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.*

class ConnectionGnuSocialTest {
    private var mock: ConnectionMock? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        mock = ConnectionMock.Companion.newFor(DemoData.Companion.demoData.gnusocialTestAccountName)
    }

    @Test
    @Throws(IOException::class)
    fun testGetPublicTimeline() {
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_home)
        val accountActor: Actor = DemoData.Companion.demoData.getAccountActorByOid(DemoData.Companion.demoData.gnusocialTestAccountActorOid)
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.PUBLIC_TIMELINE,
                TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 3
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        var ind = 0
        Assert.assertEquals("Posting note", AObjectType.NOTE, timeline[ind].objectType)
        var activity = timeline[ind]
        Assert.assertEquals("Activity oid", "2663077", activity.oid)
        Assert.assertEquals("Note Oid", "2663077", activity.note.oid)
        Assert.assertEquals("conversationOid", "2218650", activity.note.conversationOid)
        Assert.assertEquals("Favorited $activity", TriState.TRUE, activity.note.getFavoritedBy(activity.accountActor))
        var author = activity.author
        Assert.assertEquals("Oid", "116387", author.oid)
        Assert.assertEquals("Username", "aru", author.username)
        Assert.assertEquals("WebFinger ID", "aru@status.vinilox.eu", author.webFingerId)
        Assert.assertEquals("Display name", "aru", author.realName)
        Assert.assertEquals("Description", "Manjaro user, student of physics and metalhead. Excuse my english ( ͡° ͜ʖ ͡°)", author.summary)
        Assert.assertEquals("Location", "Spain", author.location)
        Assert.assertEquals("Profile URL", "https://status.vinilox.eu/aru", author.profileUrl)
        Assert.assertEquals("Homepage", "", author.homepage)
        Assert.assertEquals("Avatar URL", "http://quitter.se/avatar/116387-48-20140609172839.png", author.avatarUrl)
        UriUtilsTest.Companion.assertEndpoint(ActorEndpointType.BANNER, "", author)
        Assert.assertEquals("Notes count", 523, author.notesCount)
        Assert.assertEquals("Favorites count", 11, author.favoritesCount)
        Assert.assertEquals("Following (friends) count", 23, author.followingCount)
        Assert.assertEquals("Followers count", 21, author.followersCount)
        Assert.assertEquals("Created at", mock.connection.parseDate("Sun Feb 09 22:33:42 +0100 2014"), author.createdDate)
        Assert.assertEquals("Updated at", 0, author.updatedDate)
        ind++
        activity = timeline[ind]
        author = activity.author
        Assert.assertEquals("Activity oid", "2664346", activity.oid)
        Assert.assertEquals("Note Oid", "2664346", activity.note.oid)
        Assert.assertEquals("conversationOid", "2218650", activity.note.conversationOid)
        Assert.assertEquals("Should have a recipient $activity", 1, activity.audience().nonSpecialActors.size.toLong())
        Assert.assertNotEquals("Is a reblog", ActivityType.ANNOUNCE, activity.type)
        val inReplyTo = activity.note.inReplyTo
        Assert.assertTrue("Is a reply", inReplyTo.nonEmpty())
        Assert.assertEquals("Reply to the note id", "2663833", inReplyTo.note.oid)
        Assert.assertEquals("Reply to the note by actorOid", "114973", inReplyTo.actor.oid)
        Assert.assertEquals("Updated date should be 0 for inReplyTo note", RelativeTime.DATETIME_MILLIS_NEVER,
                inReplyTo.note.updatedDate)
        Assert.assertEquals("Updated date should be 0 for inReplyTo activity", 0, inReplyTo.updatedDate)
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.note.getFavoritedBy(activity.accountActor))
        val startsWith = "@<span class=\"vcard\">"
        Assert.assertEquals("Body of this note starts with", startsWith, activity.note.content.substring(0, startsWith.length))
        Assert.assertEquals("Username", "andstatus", author.username)
        Assert.assertEquals("Display name", "AndStatus@quitter.se", author.realName)
        UriUtilsTest.Companion.assertEndpoint(ActorEndpointType.BANNER, "https://quitter.se/file/3fd65c6088ea02dc3a5ded9798a865a8ff5425b13878da35ad894cd084d015fc.png", author)
        ind++
        activity = timeline[ind]
        author = activity.author
        Assert.assertEquals("conversationOid", "2218650", activity.note.conversationOid)
        Assert.assertEquals("Should be public", Visibility.PUBLIC_AND_TO_FOLLOWERS, activity.note.audience().visibility)
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.note.getFavoritedBy(activity.accountActor))
        Assert.assertEquals("MyAccount", accountActor.oid, activity.accountActor.oid)
        Assert.assertEquals("Actor", author.oid, activity.actor.oid)
        Assert.assertEquals("Oid", "114973", author.oid)
        Assert.assertEquals("Username", "mmn", author.username)
        Assert.assertEquals("WebFinger ID", "mmn@social.umeahackerspace.se", author.webFingerId)
        Assert.assertEquals("Display name", "mmn", author.realName)
        Assert.assertEquals("Description", "", author.summary)
        Assert.assertEquals("Location", "Umeå, Sweden", author.location)
        Assert.assertEquals("Profile URL", "https://social.umeahackerspace.se/mmn", author.profileUrl)
        Assert.assertEquals("Homepage", "http://blog.mmn-o.se/", author.homepage)
        Assert.assertEquals("Avatar URL", "http://quitter.se/avatar/114973-48-20140702161520.jpeg", author.avatarUrl)
        UriUtilsTest.Companion.assertEndpoint(ActorEndpointType.BANNER, "", author)
        Assert.assertEquals("Notes count", 1889, author.notesCount)
        Assert.assertEquals("Favorites count", 31, author.favoritesCount)
        Assert.assertEquals("Following (friends) count", 17, author.followingCount)
        Assert.assertEquals("Followers count", 31, author.followersCount)
        Assert.assertEquals("Created at", mock.connection.parseDate("Wed Aug 14 10:05:28 +0200 2013"), author.createdDate)
        Assert.assertEquals("Updated at", 0, author.updatedDate)
    }

    @Test
    @Throws(IOException::class)
    fun testSearch() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_home_timeline)
        val timeline = mock.connection.searchNotes(true,
                TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20, DemoData.Companion.demoData.globalPublicNoteText).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 4
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
    }

    @Test
    @Throws(IOException::class)
    fun testPostWithMedia() {
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_note_with_attachment)
        val note: Note = Note.Companion.fromOriginAndOid(mock.getData().origin, "", DownloadStatus.SENDING)
                .setContentPosted("Test post note with media")
                .withAttachments(Attachments().add(Attachment.Companion.fromUri(DemoData.Companion.demoData.localImageTestUri)))
        val activity = mock.connection.updateNote(note).get()
        Assert.assertEquals("Note returned",
                privateGetNoteWithAttachment(false).getNote(), activity.note)
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithAttachment() {
        privateGetNoteWithAttachment(true)
    }

    @Throws(IOException::class)
    private fun privateGetNoteWithAttachment(uniqueUid: Boolean): AActivity? {
        val NOTE_OID = "2215662"
        // Originally downloaded from https://quitter.se/api/statuses/show.json?id=2215662
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_note_with_attachment)
        val activity = mock.connection.getNote(NOTE_OID).get()
        if (uniqueUid) {
            activity.setNote(activity.note.withNewOid(activity.note.oid + "_" + DemoData.Companion.demoData.testRunUid))
        }
        Assert.assertNotNull("note returned", activity)
        Assert.assertEquals("conversationOid", "1956322", activity.note.conversationOid)
        Assert.assertEquals("Author", "mcscx", activity.author.username)
        Assert.assertEquals("null Homepage (url) should be treated as blank", "", activity.author.homepage)
        Assert.assertEquals("has attachment", 1, activity.note.attachments.size().toLong())
        val attachment: Attachment = Attachment.Companion.fromUri("https://quitter.se/file/mcscx-20131110T222250-427wlgn.png")
        Assert.assertEquals("attachment", attachment, activity.note.attachments.list[0])
        return activity
    }

    @Test
    @Throws(IOException::class)
    fun testReblog() {
        val NOTE_OID = "10341561"
        mock.addResponse(org.andstatus.app.tests.R.raw.loadaverage_repost_response)
        val activity = mock.connection.announce(NOTE_OID).get()
        Assert.assertEquals(ActivityType.ANNOUNCE, activity.type)
        val note = activity.note
        Assert.assertEquals("Note oid$note", NOTE_OID, note.oid)
        Assert.assertEquals("conversationOid", "9118253", note.conversationOid)
        Assert.assertEquals(1, mock.getHttpMock().requestsCounter.toLong())
        val result = mock.getHttpMock().results[0]
        Assert.assertTrue("URL doesn't contain note oid: " + result.url, result.url.contains(NOTE_OID))
        Assert.assertEquals("Activity oid; $activity", "10341833", activity.oid)
        Assert.assertEquals("Actor; $activity", "andstatus@loadaverage.org", activity.actor.webFingerId)
        Assert.assertEquals("Author; $activity", "igor@herds.eu", activity.author.webFingerId)
    }

    @Test
    fun testFavoritingActivity() {
        val contentOfFavoritedNote = "the favorited note"
        val activity1 = getFavoritingActivity("10238",
                "somebody favorited something by anotheractor: $contentOfFavoritedNote",
                contentOfFavoritedNote)
        val activity2 = getFavoritingActivity("10239",
                "anotherman favorited something by anotheractor: $contentOfFavoritedNote",
                contentOfFavoritedNote)
        Assert.assertEquals("Should have the same content", activity1.note.content, activity2.note.content)
        val content2 = "a status by somebody@somewhere.org"
        getFavoritingActivity("10240", "oneman favourited $content2", content2)
    }

    private fun getFavoritingActivity(favoritingOid: String?, favoritingContent: String?, likedContent: String?): AActivity {
        val accountActor: Actor = DemoData.Companion.demoData.getAccountActorByOid(DemoData.Companion.demoData.gnusocialTestAccountActorOid)
        val actorOid = favoritingOid + "1"
        val actor: Actor = Actor.Companion.fromOid(accountActor.origin, actorOid)
        val favoritingUpdateDate = System.currentTimeMillis() - 1000000
        val activityIn: AActivity = AActivity.Companion.newPartialNote(accountActor, actor, favoritingOid, favoritingUpdateDate,
                DownloadStatus.LOADED)
        activityIn.note.setContentPosted(favoritingContent)
        val activity: AActivity = ConnectionTwitterGnuSocial.Companion.createLikeActivity(activityIn)
        Assert.assertEquals("Should become LIKE activity $activityIn", ActivityType.LIKE, activity.type)
        val note = activity.note
        Assert.assertEquals("Should strip favoriting prefix $activityIn", likedContent, note.content)
        Assert.assertEquals("Note updatedDate should be 1 $activity", RelativeTime.SOME_TIME_AGO, note.updatedDate)
        return activity
    }

    @Test
    @Throws(IOException::class)
    fun testFavoritingActivityInTimeline() {
        mock.addResponse(org.andstatus.app.tests.R.raw.loadaverage_favoriting_activity)
        val accountActor: Actor = DemoData.Companion.demoData.getAccountActorByOid(DemoData.Companion.demoData.gnusocialTestAccountActorOid)
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.SEARCH_NOTES,
                TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 2, timeline.size().toLong())
        var ind = 0
        var activity = timeline[ind]
        Assert.assertEquals("Posting a note $activity", AObjectType.NOTE, activity.objectType)
        Assert.assertEquals("Should be UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Activity oid", "12940131", activity.oid)
        Assert.assertEquals("Note Oid", "12940131", activity.note.oid)
        Assert.assertEquals("conversationOid", "10538185", activity.note.conversationOid)
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.note.getFavoritedBy(activity.accountActor))
        Assert.assertEquals("Actor Oid", "379323", activity.actor.oid)
        Assert.assertEquals("Actor Username", "colegota", activity.actor.username)
        Assert.assertEquals("Author Oid", "379323", activity.author.oid)
        Assert.assertEquals("Author Username", "colegota", activity.author.username)
        val contentPrefix = "@<a href=\"https://linuxinthenight.com/user/1\" class"
        Assert.assertTrue("Content $activity", activity.note.content.startsWith(contentPrefix))
        ind++
        activity = timeline[ind]
        Assert.assertEquals("Should be LIKE $activity", ActivityType.LIKE, activity.type)
        Assert.assertEquals("Activity oid", "12942571", activity.oid)
        Assert.assertEquals("Actor Oid", "347578", activity.actor.oid)
        Assert.assertEquals("Actor Username", "fanta", activity.actor.username)
        Assert.assertEquals("Author Oid", "379323", activity.author.oid)
        Assert.assertEquals("Note Oid", "12940131", activity.note.oid)
        Assert.assertTrue("Should not have a recipient " + activity.audience(), !activity.audience().hasNonSpecial())
        Assert.assertTrue("Content $activity", activity.note.content.startsWith(contentPrefix))
        Assert.assertTrue("inReplyTo should be empty $activity", activity.note.inReplyTo.isEmpty)
        Assert.assertEquals("Updated date should be 1 for favorited note", RelativeTime.SOME_TIME_AGO,
                activity.note.updatedDate)
        Assert.assertEquals("Activity updated at " + TestSuite.utcTime(activity.updatedDate),
                TestSuite.utcTime(2018, Calendar.JUNE, 1, 17, 4, 57),
                TestSuite.utcTime(activity.updatedDate))
    }

    @Test
    @Throws(IOException::class)
    fun testMentionsInHtml() {
        oneHtmlMentionsTest("1iceloops123", "14044206",
                org.andstatus.app.tests.R.raw.loadaverage_note_with_mentions, 6)
        oneHtmlMentionsTest("andstatus", "14043873",
                org.andstatus.app.tests.R.raw.loadaverage_note_with_mentions2, 5)
        val activity = oneHtmlMentionsTest("andstatus", "13421701",
                org.andstatus.app.tests.R.raw.loadaverage_note_with_mentions3, 1)
        val spannable = SpanUtil.textToSpannable(activity.getNote().content, TextMediaType.HTML,
                activity.audience())
        val spans = spannable.getSpans(0, spannable.length - 1, MyUrlSpan::class.java)
        Assert.assertEquals("""
    Link to hashtag ${Arrays.toString(spans)}
    $activity
    """.trimIndent(), TimelineType.SEARCH,
                Arrays.stream(spans).filter { span: MyUrlSpan? -> span.getURL().contains("/search/%23Hubzilla") }.findAny()
                        .orElse(MyUrlSpan.Companion.EMPTY).data.timeline.timelineType)
    }

    @Throws(IOException::class)
    private fun oneHtmlMentionsTest(actorUsername: String?, noteOid: String?, responseResourceId: Int, numberOfMembers: Int): AActivity? {
        mock.addResponse(responseResourceId)
        val activity = mock.connection.getNote(noteOid).get()
        Assert.assertEquals("Received a note $activity", AObjectType.NOTE, activity.objectType)
        Assert.assertEquals("Should be UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Note Oid", noteOid, activity.note.oid)
        Assert.assertEquals("Actor Username", actorUsername, activity.actor.username)
        Assert.assertEquals("Author should be Actor", activity.actor, activity.author)
        Assert.assertTrue("inReplyTo should not be empty $activity", activity.note.inReplyTo.nonEmpty())
        activity.note.updatedDate = MyLog.uniqueCurrentTimeMS()
        activity.setUpdatedNow(0)
        val executionContext = CommandExecutionContext(
                MyContextHolder.Companion.myContextHolder.getNow(), CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, mock.getData().myAccount, 123))
        DataUpdater(executionContext).onActivity(activity)
        assertAudience(activity, activity.audience(), numberOfMembers)
        val storedAudience: Audience = Audience.Companion.load(activity.note.origin, activity.note.noteId, Optional.empty())
        assertAudience(activity, storedAudience, numberOfMembers)
        return activity
    }

    private fun assertAudience(activity: AActivity?, audience: Audience?, numberOfMembers: Int) {
        val actors = audience.getNonSpecialActors()
        Assert.assertEquals("Wrong number of audience members $audience\n$activity", numberOfMembers.toLong(), actors.size.toLong())
        Assert.assertEquals("All recipients should have valid usernames $audience\n$activity", Actor.Companion.EMPTY,
                actors.stream().filter { actor: Actor? -> !actor.isUsernameValid() }.findAny().orElse(Actor.Companion.EMPTY))
        Assert.assertEquals("All recipients should have id $audience\n$activity", Actor.Companion.EMPTY,
                actors.stream().filter { actor: Actor? -> actor.actorId == 0L }.findAny().orElse(Actor.Companion.EMPTY))
        Assert.assertEquals("All recipients should be nonEmpty $audience\n$activity", Actor.Companion.EMPTY,
                actors.stream()
                        .filter { obj: Actor? -> obj.isEmpty() }.findAny().orElse(Actor.Companion.EMPTY))
    }

    companion object {
        @Throws(Exception::class)
        fun getNoteWithAttachment(context: Context?): AActivity? {
            val test = ConnectionGnuSocialTest()
            test.setUp()
            return test.privateGetNoteWithAttachment(true)
        }
    }
}