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
import org.andstatus.app.context.MyContext
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
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates

class ConnectionGnuSocialTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var mock: ConnectionMock by Delegates.notNull()

    @Before
    fun setUp() {
        mock = ConnectionMock.newFor(DemoData.demoData.gnusocialTestAccountName)
    }

    @Test
    fun testGetPublicTimeline() {
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_home)
        val accountActor: Actor = DemoData.demoData.getAccountActorByOid(DemoData.demoData.gnusocialTestAccountActorOid)
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.PUBLIC_TIMELINE,
                TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 3
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        var ind = 0
        Assert.assertEquals("Posting note", AObjectType.NOTE, timeline[ind]?.getObjectType())
        var activity = timeline[ind]  ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Activity oid", "2663077", activity.getOid())
        Assert.assertEquals("Note Oid", "2663077", activity.getNote().oid)
        Assert.assertEquals("conversationOid", "2218650", activity.getNote().conversationOid)
        Assert.assertEquals("Favorited $activity", TriState.TRUE, activity.getNote().getFavoritedBy(activity.accountActor))
        var author = activity.getAuthor()
        Assert.assertEquals("Oid", "116387", author.oid)
        Assert.assertEquals("Username", "aru", author.getUsername())
        Assert.assertEquals("WebFinger ID", "aru@status.vinilox.eu", author.getWebFingerId())
        Assert.assertEquals("Display name", "aru", author.getRealName())
        Assert.assertEquals("Description", "Manjaro user, student of physics and metalhead. Excuse my english ( ͡° ͜ʖ ͡°)", author.getSummary())
        Assert.assertEquals("Location", "Spain", author.location)
        Assert.assertEquals("Profile URL", "https://status.vinilox.eu/aru", author.getProfileUrl())
        Assert.assertEquals("Homepage", "", author.getHomepage())
        Assert.assertEquals("Avatar URL", "http://quitter.se/avatar/116387-48-20140609172839.png", author.getAvatarUrl())
        UriUtilsTest.assertEndpoint(ActorEndpointType.BANNER, "", author)
        Assert.assertEquals("Notes count", 523, author.notesCount)
        Assert.assertEquals("Favorites count", 11, author.favoritesCount)
        Assert.assertEquals("Following (friends) count", 23, author.followingCount)
        Assert.assertEquals("Followers count", 21, author.followersCount)
        Assert.assertEquals("Created at", mock.connection.parseDate("Sun Feb 09 22:33:42 +0100 2014"), author.getCreatedDate())
        Assert.assertEquals("Updated at", 0, author.getUpdatedDate())
        ind++
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        author = activity.getAuthor()
        Assert.assertEquals("Activity oid", "2664346", activity.getOid())
        Assert.assertEquals("Note Oid", "2664346", activity.getNote().oid)
        Assert.assertEquals("conversationOid", "2218650", activity.getNote().conversationOid)
        Assert.assertEquals("Should have a recipient $activity", 1, activity.audience().getNonSpecialActors().size.toLong())
        Assert.assertNotEquals("Is a reblog", ActivityType.ANNOUNCE, activity.type)
        val inReplyTo = activity.getNote().getInReplyTo()
        Assert.assertTrue("Is a reply", inReplyTo.nonEmpty)
        Assert.assertEquals("Reply to the note id", "2663833", inReplyTo.getNote().oid)
        Assert.assertEquals("Reply to the note by actorOid", "114973", inReplyTo.getActor().oid)
        Assert.assertEquals("Updated date should be 0 for inReplyTo note", RelativeTime.DATETIME_MILLIS_NEVER,
                inReplyTo.getNote().updatedDate)
        Assert.assertEquals("Updated date should be 0 for inReplyTo activity", 0, inReplyTo.getUpdatedDate())
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        val startsWith = "@<span class=\"vcard\">"
        Assert.assertEquals("Body of this note starts with", startsWith, activity.getNote().content.substring(0, startsWith.length))
        Assert.assertEquals("Username", "andstatus", author.getUsername())
        Assert.assertEquals("Display name", "AndStatus@quitter.se", author.getRealName())
        UriUtilsTest.assertEndpoint(ActorEndpointType.BANNER, "https://quitter.se/file/3fd65c6088ea02dc3a5ded9798a865a8ff5425b13878da35ad894cd084d015fc.png", author)
        ind++
        activity = timeline[ind] ?: throw IllegalStateException("No activity")
        author = activity.getAuthor()
        Assert.assertEquals("conversationOid", "2218650", activity.getNote().conversationOid)
        Assert.assertEquals("Should be public", Visibility.PUBLIC_AND_TO_FOLLOWERS, activity.getNote().audience().visibility)
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        Assert.assertEquals("MyAccount", accountActor.oid, activity.accountActor.oid)
        Assert.assertEquals("Actor", author.oid, activity.getActor().oid)
        Assert.assertEquals("Oid", "114973", author.oid)
        Assert.assertEquals("Username", "mmn", author.getUsername())
        Assert.assertEquals("WebFinger ID", "mmn@social.umeahackerspace.se", author.getWebFingerId())
        Assert.assertEquals("Display name", "mmn", author.getRealName())
        Assert.assertEquals("Description", "", author.getSummary())
        Assert.assertEquals("Location", "Umeå, Sweden", author.location)
        Assert.assertEquals("Profile URL", "https://social.umeahackerspace.se/mmn", author.getProfileUrl())
        Assert.assertEquals("Homepage", "http://blog.mmn-o.se/", author.getHomepage())
        Assert.assertEquals("Avatar URL", "http://quitter.se/avatar/114973-48-20140702161520.jpeg", author.getAvatarUrl())
        UriUtilsTest.assertEndpoint(ActorEndpointType.BANNER, "", author)
        Assert.assertEquals("Notes count", 1889, author.notesCount)
        Assert.assertEquals("Favorites count", 31, author.favoritesCount)
        Assert.assertEquals("Following (friends) count", 17, author.followingCount)
        Assert.assertEquals("Followers count", 31, author.followersCount)
        Assert.assertEquals("Created at", mock.connection.parseDate("Wed Aug 14 10:05:28 +0200 2013"), author.getCreatedDate())
        Assert.assertEquals("Updated at", 0, author.getUpdatedDate())
    }

    @Test
    fun testSearch() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_home_timeline)
        val timeline = mock.connection.searchNotes(true,
                TimelinePosition.Companion.EMPTY, TimelinePosition.Companion.EMPTY, 20, DemoData.demoData.globalPublicNoteText).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 4
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
    }

    @Test
    fun testPostWithMedia() {
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_note_with_attachment)
        val note: Note = Note.Companion.fromOriginAndOid(mock.getData().getOrigin(), "", DownloadStatus.SENDING)
                .setContentPosted("Test post note with media")
                .withAttachments(Attachments().add(Attachment.Companion.fromUri(DemoData.demoData.localImageTestUri)))
        val activity = mock.connection.updateNote(note).get()
        Assert.assertEquals("Note returned",
                privateGetNoteWithAttachment(false).getNote(), activity.getNote())
    }

    @Test
    fun getNoteWithAttachment() {
        privateGetNoteWithAttachment(true)
    }

    private fun privateGetNoteWithAttachment(uniqueUid: Boolean): AActivity {
        val NOTE_OID = "2215662"
        // Originally downloaded from https://quitter.se/api/statuses/show.json?id=2215662
        mock.addResponse(org.andstatus.app.tests.R.raw.quitter_note_with_attachment)
        val activity = mock.connection.getNote(NOTE_OID).get()
        if (uniqueUid) {
            activity.setNote(activity.getNote().withNewOid(activity.getNote().oid + "_" + DemoData.demoData.testRunUid))
        }
        Assert.assertNotNull("note returned", activity)
        Assert.assertEquals("conversationOid", "1956322", activity.getNote().conversationOid)
        Assert.assertEquals("Author", "mcscx", activity.getAuthor().getUsername())
        Assert.assertEquals("null Homepage (url) should be treated as blank", "", activity.getAuthor().getHomepage())
        Assert.assertEquals("has attachment", 1, activity.getNote().attachments.size().toLong())
        val attachment: Attachment = Attachment.Companion.fromUri("https://quitter.se/file/mcscx-20131110T222250-427wlgn.png")
        Assert.assertEquals("attachment", attachment, activity.getNote().attachments.list[0])
        return activity
    }

    @Test
    fun testReblog() {
        val NOTE_OID = "10341561"
        mock.addResponse(org.andstatus.app.tests.R.raw.loadaverage_repost_response)
        val activity = mock.connection.announce(NOTE_OID).get()
        Assert.assertEquals(ActivityType.ANNOUNCE, activity.type)
        val note = activity.getNote()
        Assert.assertEquals("Note oid$note", NOTE_OID, note.oid)
        Assert.assertEquals("conversationOid", "9118253", note.conversationOid)
        Assert.assertEquals(1, mock.getHttpMock().getRequestsCounter())
        val result = mock.getHttpMock().getResults()[0]
        assertThat(result.url?.toExternalForm() ?: "", containsString(NOTE_OID))
        Assert.assertEquals("Activity oid; $activity", "10341833", activity.getOid())
        Assert.assertEquals("Actor; $activity", "andstatus@loadaverage.org", activity.getActor().getWebFingerId())
        Assert.assertEquals("Author; $activity", "igor@herds.eu", activity.getAuthor().getWebFingerId())
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
        Assert.assertEquals("Should have the same content", activity1.getNote().content, activity2.getNote().content)
        val content2 = "a status by somebody@somewhere.org"
        getFavoritingActivity("10240", "oneman favourited $content2", content2)
    }

    private fun getFavoritingActivity(favoritingOid: String?, favoritingContent: String?, likedContent: String?): AActivity {
        val accountActor: Actor = DemoData.demoData.getAccountActorByOid(DemoData.demoData.gnusocialTestAccountActorOid)
        val actorOid = favoritingOid + "1"
        val actor: Actor = Actor.Companion.fromOid(accountActor.origin, actorOid)
        val favoritingUpdateDate = System.currentTimeMillis() - 1000000
        val activityIn: AActivity = AActivity.Companion.newPartialNote(accountActor, actor, favoritingOid, favoritingUpdateDate,
                DownloadStatus.LOADED)
        activityIn.getNote().setContentPosted(favoritingContent)
        val activity: AActivity = ConnectionTwitterGnuSocial.Companion.createLikeActivity(activityIn)
        Assert.assertEquals("Should become LIKE activity $activityIn", ActivityType.LIKE, activity.type)
        val note = activity.getNote()
        Assert.assertEquals("Should strip favoriting prefix $activityIn", likedContent, note.content)
        Assert.assertEquals("Note updatedDate should be 1 $activity", RelativeTime.SOME_TIME_AGO, note.updatedDate)
        return activity
    }

    @Test
    fun testFavoritingActivityInTimeline() {
        mock.addResponse(org.andstatus.app.tests.R.raw.loadaverage_favoriting_activity)
        val accountActor: Actor = DemoData.demoData.getAccountActorByOid(DemoData.demoData.gnusocialTestAccountActorOid)
        val timeline = mock.connection.getTimeline(true, ApiRoutineEnum.SEARCH_NOTES,
                TimelinePosition.Companion.of("2656388"), TimelinePosition.Companion.EMPTY, 20, accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        Assert.assertEquals("Number of items in the Timeline", 2, timeline.size().toLong())
        var ind = 0
        var activity = timeline[ind] ?: throw IllegalStateException("No activity")
        Assert.assertEquals("Posting a note $activity", AObjectType.NOTE, activity.getObjectType())
        Assert.assertEquals("Should be UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Activity oid", "12940131", activity.getOid())
        Assert.assertEquals("Note Oid", "12940131", activity.getNote().oid)
        Assert.assertEquals("conversationOid", "10538185", activity.getNote().conversationOid)
        Assert.assertEquals("Favorited $activity", TriState.UNKNOWN, activity.getNote().getFavoritedBy(activity.accountActor))
        Assert.assertEquals("Actor Oid", "379323", activity.getActor().oid)
        Assert.assertEquals("Actor Username", "colegota", activity.getActor().getUsername())
        Assert.assertEquals("Author Oid", "379323", activity.getAuthor().oid)
        Assert.assertEquals("Author Username", "colegota", activity.getAuthor().getUsername())
        val contentPrefix = "@<a href=\"https://linuxinthenight.com/user/1\" class"
        Assert.assertTrue("Content $activity", activity.getNote().content.startsWith(contentPrefix))
        ind++
        activity = timeline[ind] ?: AActivity.EMPTY
        Assert.assertEquals("Should be LIKE $activity", ActivityType.LIKE, activity.type)
        Assert.assertEquals("Activity oid", "12942571", activity.getOid())
        Assert.assertEquals("Actor Oid", "347578", activity.getActor().oid)
        Assert.assertEquals("Actor Username", "fanta", activity.getActor().getUsername())
        Assert.assertEquals("Author Oid", "379323", activity.getAuthor().oid)
        Assert.assertEquals("Note Oid", "12940131", activity.getNote().oid)
        Assert.assertTrue("Should not have a recipient " + activity.audience(), !activity.audience().hasNonSpecial())
        Assert.assertTrue("Content $activity", activity.getNote().content.startsWith(contentPrefix))
        Assert.assertTrue("inReplyTo should be empty $activity", activity.getNote().getInReplyTo().isEmpty)
        Assert.assertEquals("Updated date should be 1 for favorited note", RelativeTime.SOME_TIME_AGO,
                activity.getNote().updatedDate)
        Assert.assertEquals("Activity updated at " + TestSuite.utcTime(activity.getUpdatedDate()),
                TestSuite.utcTime(2018, Calendar.JUNE, 1, 17, 4, 57),
                TestSuite.utcTime(activity.getUpdatedDate()))
    }

    @Test
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
        Assert.assertEquals("Link to hashtag ${Arrays.toString(spans)}\n$activity", TimelineType.SEARCH,
                Arrays.stream(spans).filter { span: MyUrlSpan -> span.getURL()?.contains("/search/%23Hubzilla") == true }.findAny()
                        .orElse(MyUrlSpan.Companion.EMPTY).data.getTimeline().timelineType)
    }

    private fun oneHtmlMentionsTest(actorUsername: String, noteOid: String, responseResourceId: Int, numberOfMembers: Int): AActivity {
        mock.addResponse(responseResourceId)
        val activity = mock.connection.getNote(noteOid).get()
        Assert.assertEquals("Received a note $activity", AObjectType.NOTE, activity.getObjectType())
        Assert.assertEquals("Should be UPDATE $activity", ActivityType.UPDATE, activity.type)
        Assert.assertEquals("Note Oid", noteOid, activity.getNote().oid)
        Assert.assertEquals("Actor Username", actorUsername, activity.getActor().getUsername())
        Assert.assertEquals("Author should be Actor", activity.getActor(), activity.getAuthor())
        Assert.assertTrue("inReplyTo should not be empty $activity", activity.getNote().getInReplyTo().nonEmpty)
        activity.getNote().updatedDate = MyLog.uniqueCurrentTimeMS()
        activity.setUpdatedNow(0)
        val executionContext = CommandExecutionContext(
                 myContext, CommandData.Companion.newItemCommand(CommandEnum.GET_NOTE, mock.getData().getMyAccount(), 123))
        DataUpdater(executionContext).onActivity(activity)
        assertAudience(activity, activity.audience(), numberOfMembers)
        val storedAudience: Audience = Audience.Companion.load(activity.getNote().origin, activity.getNote().noteId, Optional.empty())
        assertAudience(activity, storedAudience, numberOfMembers)
        return activity
    }

    private fun assertAudience(activity: AActivity, audience: Audience, numberOfMembers: Int) {
        val actors = audience.getNonSpecialActors()
        Assert.assertEquals("Wrong number of audience members $audience\n$activity", numberOfMembers.toLong(), actors.size.toLong())
        Assert.assertEquals("All recipients should have valid usernames $audience\n$activity", Actor.EMPTY,
                actors.stream().filter { actor: Actor -> !actor.isUsernameValid() }.findAny().orElse(Actor.EMPTY))
        Assert.assertEquals("All recipients should have id $audience\n$activity", Actor.EMPTY,
                actors.stream().filter { actor: Actor -> actor.actorId == 0L }.findAny().orElse(Actor.EMPTY))
        Assert.assertEquals("All recipients should be nonEmpty $audience\n$activity", Actor.EMPTY,
                actors.stream()
                        .filter { obj: Actor -> obj.isEmpty }.findAny().orElse(Actor.EMPTY))
    }

    companion object {
        fun getNoteWithAttachment(context: Context): AActivity {
            val test = ConnectionGnuSocialTest()
            test.setUp()
            return test.privateGetNoteWithAttachment(true)
        }
    }
}
