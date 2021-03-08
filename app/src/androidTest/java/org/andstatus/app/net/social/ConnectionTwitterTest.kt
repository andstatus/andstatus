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
package org.andstatus.app.net.social

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.CommandExecutionContext
import org.andstatus.app.util.MyHtmlTest
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UriUtilsTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.*

class ConnectionTwitterTest {
    private var connection: Connection? = null
    private var mock: ConnectionMock? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        mock = ConnectionMock.Companion.newFor(DemoData.Companion.demoData.twitterTestAccountName)
        connection = mock.connection
        val data = mock.getHttp().data
        data.oauthClientKeys = OAuthClientKeys.Companion.fromConnectionData(data)
        if (!data.oauthClientKeys.areKeysPresent()) {
            data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232")
        }
    }

    @Test
    @Throws(IOException::class)
    fun testGetTimeline() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_home_timeline)
        val timeline = connection.getTimeline(true, ApiRoutineEnum.HOME_TIMELINE,
                TimelinePosition.Companion.of("380925803053449216"), TimelinePosition.Companion.EMPTY, 20,
                connection.getData().accountActor).get()
        Assert.assertNotNull("timeline returned", timeline)
        val size = 4
        Assert.assertEquals("Number of items in the Timeline", size.toLong(), timeline.size().toLong())
        var ind = 0
        var activity = timeline[ind]
        var note = activity.note
        val hostName: String = DemoData.Companion.demoData.twitterTestHostWithoutApiDot
        Assert.assertEquals("Posting note", AObjectType.NOTE, activity.objectType)
        Assert.assertEquals("Activity oid", "381172771428257792", activity.oid)
        Assert.assertEquals("Note Oid", "381172771428257792", note.oid)
        Assert.assertEquals("MyAccount", connection.getData().accountActor, activity.accountActor)
        Assert.assertEquals("Favorited $activity", TriState.TRUE, note.getFavoritedBy(activity.accountActor))
        Assert.assertEquals("Counters $activity", 232, note.likesCount)
        Assert.assertEquals("Counters $activity", 408, note.reblogsCount)
        Assert.assertEquals("Counters $activity", 0, note.repliesCount)
        val author = activity.author
        Assert.assertEquals("Oid", "221452291", author.oid)
        Assert.assertEquals("Username", "Know", author.username)
        Assert.assertEquals("WebFinger ID", "know@$hostName", author.webFingerId)
        Assert.assertEquals("Display name", "Just so you Know", author.realName)
        Assert.assertEquals("Description", "Unimportant facts you'll never need to know. Legally responsible publisher: @FUN", author.summary)
        Assert.assertEquals("Location", "Library of Congress", author.location)
        Assert.assertEquals("Profile URL", "https://$hostName/Know", author.profileUrl)
        Assert.assertEquals("Homepage", "http://t.co/4TzphfU9qt", author.homepage)
        Assert.assertEquals("Avatar URL", "https://si0.twimg.com/profile_images/378800000411110038/a8b7eced4dc43374e7ae21112ff749b6_normal.jpeg", author.avatarUrl)
        UriUtilsTest.Companion.assertEndpoint(ActorEndpointType.BANNER, "https://pbs.twimg.com/profile_banners/221452291/1377270845", author)
        Assert.assertEquals("Notes count", 1592, author.notesCount)
        Assert.assertEquals("Favorites count", 163, author.favoritesCount)
        Assert.assertEquals("Following (friends) count", 151, author.followingCount)
        Assert.assertEquals("Followers count", 1878136, author.followersCount)
        Assert.assertEquals("Created at", connection.parseDate("Tue Nov 30 18:17:25 +0000 2010"), author.createdDate)
        Assert.assertEquals("Updated at", 0, author.updatedDate)
        Assert.assertEquals("Actor is author", author.oid, activity.actor.oid)
        ind++
        activity = timeline[ind]
        note = activity.note
        Assert.assertTrue("Note is loaded", note.status == DownloadStatus.LOADED)
        Assert.assertEquals("Should have a recipient $activity", 1, note.audience().nonSpecialActors.size.toLong())
        Assert.assertNotEquals("Is a Reblog $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertTrue("Is a reply", note.inReplyTo.nonEmpty)
        Assert.assertEquals("Reply to the note id", "17176774678", note.inReplyTo.note.oid)
        Assert.assertEquals("Reply to the note by actorOid", DemoData.Companion.demoData.twitterTestAccountActorOid, note.inReplyTo.author.oid)
        Assert.assertTrue("Reply status is unknown", note.inReplyTo.note.status == DownloadStatus.UNKNOWN)
        Assert.assertEquals("Favorited by me $activity", TriState.UNKNOWN, activity.note.getFavoritedBy(activity.accountActor))
        var startsWith = "@t131t"
        Assert.assertEquals("Body of this note starts with", startsWith, note.content.substring(0, startsWith.length))
        ind++
        activity = timeline[ind]
        note = activity.note
        MatcherAssert.assertThat("Should not have non special recipients", note.audience().nonSpecialActors, Matchers.`is`(Matchers.empty()))
        Assert.assertEquals("Is not a Reblog $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertTrue("Is not a reply", note.inReplyTo.isEmpty)
        Assert.assertEquals("Reblog of the note id", "315088751183409153", note.oid)
        Assert.assertEquals("Author of reblogged note oid", "442756884", activity.author.oid)
        Assert.assertEquals("Reblog id", "383295679507869696", activity.oid)
        Assert.assertEquals("Reblogger oid", "111911542", activity.actor.oid)
        Assert.assertEquals("Favorited by me $activity", TriState.UNKNOWN, activity.note.getFavoritedBy(activity.accountActor))
        startsWith = "This AndStatus application"
        Assert.assertEquals("Body of reblogged note starts with", startsWith,
                note.content.substring(0, startsWith.length))
        var date = TestSuite.utcTime(2013, Calendar.SEPTEMBER, 26, 18, 23, 5)
        Assert.assertEquals("Reblogged at Thu Sep 26 18:23:05 +0000 2013 ($date) $activity", date,
                TestSuite.utcTime(activity.updatedDate))
        date = TestSuite.utcTime(2013, Calendar.MARCH, 22, 13, 13, 7)
        Assert.assertEquals("Reblogged note created at Fri Mar 22 13:13:07 +0000 2013 ($date)$note",
                date, TestSuite.utcTime(note.updatedDate))
        ind++
        activity = timeline[ind]
        note = activity.note
        MatcherAssert.assertThat("Should not have non special recipients", note.audience().nonSpecialActors, Matchers.`is`(Matchers.empty()))
        Assert.assertNotEquals("Is a Reblog $activity", ActivityType.ANNOUNCE, activity.type)
        Assert.assertTrue("Is not a reply", note.inReplyTo.isEmpty)
        Assert.assertEquals("Favorited by me $activity", TriState.UNKNOWN, activity.note.getFavoritedBy(activity.accountActor))
        Assert.assertEquals("Author's oid is actor oid of this account",
                connection.getData().accountActor.oid, activity.author.oid)
        startsWith = "And this is"
        Assert.assertEquals("Body of this note starts with", startsWith, note.content.substring(0, startsWith.length))
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithAttachment() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_media)
        val note = connection.getNote("503799441900314624").get().note
        Assert.assertFalse("note returned", note.isEmpty)
        Assert.assertEquals("Should have an attachment $note", 1, note.attachments.size().toLong())
        Assert.assertEquals("attachment", Attachment.Companion.fromUri("https://pbs.twimg.com/media/Bv3a7EsCAAIgigY.jpg"),
                note.attachments.list[0])
        Assert.assertNotSame("attachment", Attachment.Companion.fromUri("https://pbs.twimg.com/media/Bv4a7EsCAAIgigY.jpg"),
                note.attachments.list[0])
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithTwoAttachments() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_two_attachments)
        val note = connection.getNote("1198619196260790272").get().note
        Assert.assertFalse("note returned $note", note.isEmpty)
        Assert.assertEquals("Body of this note $note", "Test uploading two images via #AndStatus https://t.co/lJn9QBpWyn",
                note.content)
        Assert.assertEquals("Should have two attachments $note", 2, note.attachments.size().toLong())
        Assert.assertEquals("attachment", Attachment.Companion.fromUri("https://pbs.twimg.com/media/EKJZzZPWoAICygS.jpg"),
                note.attachments.list[0])
        Assert.assertEquals("attachment", Attachment.Companion.fromUri("https://pbs.twimg.com/media/EKJZzkYWsAELO-o.jpg"),
                note.attachments.list[1])
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithAnimatedGif() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_animated_gif)
        val activity = connection.getNote("1271153637457367042").get()
        val note = activity.note
        Assert.assertFalse("note returned", note.isEmpty)
        Assert.assertEquals("Should have two attachments $note", 2, note.attachments.size().toLong())
        val attachment0 = note.attachments.list[0]
        Assert.assertEquals("attachment 0 $note", Attachment.Companion.fromUriAndMimeType(
                "https://video.twimg.com/tweet_video/EaQLf5eXkAIhL7_.mp4", "video/mp4"),
                attachment0)
        val attachment1 = note.attachments.list[1]
        Assert.assertEquals("attachment 1 $note", Attachment.Companion.fromUri("https://pbs.twimg.com/tweet_video_thumb/EaQLf5eXkAIhL7_.jpg"),
                attachment1)
        Assert.assertEquals("attachment 1 should be a preview $note", attachment0, attachment1.previewOf)
        addAsGetNote(activity)
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithEscapedHtmlTag() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_escaped_html_tag)
        val body = "Update: Streckensperrung zw. Berliner Tor &lt;&gt; Bergedorf. Ersatzverkehr mit Bussen und Taxis " +
                "Störungsdauer bis ca. 10 Uhr. #hvv #sbahnhh"
        val activity = connection.getNote("834306097003581440").get()
        Assert.assertEquals("No note returned $activity", AObjectType.NOTE, activity.objectType)
        val note = activity.note
        Assert.assertEquals("Body of this note", body, note.content)
        Assert.assertEquals("Body of this note", ",update,streckensperrung,zw,berliner,tor,bergedorf,ersatzverkehr,mit,bussen," +
                "und,taxis,störungsdauer,bis,ca,10,uhr,hvv,#hvv,sbahnhh,#sbahnhh,", note.contentToSearch)
        addAsGetNote(activity)
    }

    private fun addAsGetNote(activity: AActivity?) {
        val ma: MyAccount = DemoData.Companion.demoData.getMyAccount(connection.getData().accountName.toString())
        val executionContext = CommandExecutionContext(
                 MyContextHolder.myContextHolder.getNow(), CommandData.Companion.newAccountCommand(CommandEnum.GET_NOTE, ma))
        DataUpdater(executionContext).onActivity(activity)
        Assert.assertNotEquals("Note was not added $activity", 0, activity.getNote().noteId)
        Assert.assertNotEquals("Activity was not added $activity", 0, activity.getId())
    }

    @Test
    @Throws(IOException::class)
    fun getNoteWithEscapedChars() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_note_with_escaped_chars)
        val contentToSearch = ",testing,if,and,what,is,escaped,in,a,tweet," +
                "1,less-than,sign,and,escaped,&lt," +
                "2,greater-than,sign,and,escaped,&gt," +
                "3,ampersand,&,and,escaped,&amp," +
                "4,apostrophe," +
                "5,br,html,tag,br,/,and,without,/,br,"
        val activity = connection.getNote("1070738478198071296").get()
        Assert.assertEquals("No note returned $activity", AObjectType.NOTE, activity.objectType)
        val note = activity.note
        Assert.assertEquals("Body of this note", MyHtmlTest.Companion.twitterBodyHtml, note.content)
        Assert.assertEquals("""
    Body to post is wrong. Try to type:
    ${MyHtmlTest.Companion.twitterBodyTypedPlain}
    
    """.trimIndent(),
                MyHtmlTest.Companion.twitterBodyToPost, note.contentToPost)
        Assert.assertEquals("Content to Search of this note", contentToSearch, note.contentToSearch)
        addAsGetNote(activity)
    }

    @Test
    @Throws(IOException::class)
    fun follow() {
        mock.addResponse(org.andstatus.app.tests.R.raw.twitter_follow)
        val actorOid = "96340134"
        val activity = connection.follow(actorOid, true).get()
        Assert.assertEquals("No actor returned $activity", AObjectType.ACTOR, activity.objectType)
        val friend = activity.objActor
        Assert.assertEquals("Wrong username returned $activity", "LPirro93", friend.username)
        val ma: MyAccount = DemoData.Companion.demoData.getMyAccount(connection.getData().accountName.toString())
        val friend2: Actor = Actor.Companion.fromId(ma.origin, 123)
        val executionContext = CommandExecutionContext(
                 MyContextHolder.myContextHolder.getNow(), CommandData.Companion.actOnActorCommand(CommandEnum.FOLLOW, ma, friend2, ""))
        DataUpdater(executionContext).onActivity(activity)
        val friendId = MyQuery.oidToId( MyContextHolder.myContextHolder.getNow(), OidEnum.ACTOR_OID, ma.originId, actorOid)
        Assert.assertNotEquals("Followed Actor was not added $activity", 0, friendId)
        Assert.assertNotEquals("Activity was not added $activity", 0, activity.id)
    }
}