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

import org.andstatus.app.account.MyAccount
import org.andstatus.app.actor.GroupType
import org.andstatus.app.context.DemoData
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Attachment
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TriState
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import java.util.*

class DemoConversationInserter {
    private var iteration = 0
    private var ma: MyAccount? = null
    private var accountActor: Actor? = Actor.Companion.EMPTY
    private var bodySuffix: String? = ""
    fun insertConversation(bodySuffixIn: String?) {
        bodySuffix = if (bodySuffixIn.isNullOrEmpty()) "" else " $bodySuffixIn"
        iteration = DemoData.demoData.conversationIterationCounter.incrementAndGet()
        ma = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(DemoData.demoData.conversationAccountName + " exists", ma.isValid)
        accountActor = ma.actor
        insertAndTestConversation()
    }

    private fun insertAndTestConversation() {
        Assert.assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, DemoData.demoData.conversationOriginType)
        val author2 = buildActorFromOid(DemoData.demoData.conversationAuthorSecondActorOid)
        author2.setAvatarUrl("http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png")
        val author3 = buildActorFromOid(DemoData.demoData.conversationAuthorThirdActorOid)
        author3.setRealName("John Smith")
        author3.withUniqueName(DemoData.demoData.conversationAuthorThirdUniqueName)
        author3.setHomepage("http://johnsmith.com/welcome")
        author3.setCreatedDate(GregorianCalendar(2011, 5, 12).timeInMillis)
        author3.setSummary("I am an ordinary guy, interested in computer science")
        author3.setAvatarUrl("http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif")
        author3.build()
        val author4 = buildActorFromOid("acct:fourthWithoutAvatar@pump.example.com")
        author4.setRealName("Real Fourth")
        val minus1 = buildActivity(author2, "", "Older one note", null, null)
        val selected = buildActivity(getAuthor1(), "", "Selected note from Home timeline", minus1,
                if (iteration == 1) DemoData.demoData.conversationEntryNoteOid else null)
        selected.setSubscribedByMe(TriState.TRUE)
        val reply1 = buildActivity(author3, "The first reply", "Reply 1 to selected<br />" +
                "&gt;&gt; Greater than<br />" +
                "&lt;&lt; Less than, let&apos;s try!",
                selected, null)
        author3.isMyFriend = TriState.TRUE
        val reply1Copy = buildActivity(accountActor,
                Actor.Companion.fromOid(reply1.accountActor.origin, reply1.getAuthor().oid),
                "", "", AActivity.Companion.EMPTY,
                reply1.getNote().oid, DownloadStatus.UNKNOWN)
        val reply12 = buildActivity(author2, "", "Reply 12 to 1 in Replies", reply1Copy, null)
        reply1.getNote().replies.add(reply12)
        val privateReply2 = buildActivity(author2, "Private reply", "Reply 2 to selected is private",
                selected, null).withVisibility(Visibility.PRIVATE)
        addActivity(privateReply2)
        DemoNoteInserter.Companion.assertStoredVisibility(privateReply2, Visibility.PRIVATE)
        DemoNoteInserter.Companion.assertInteraction(privateReply2, NotificationEventType.EMPTY, TriState.FALSE)
        Assert.assertEquals("Should be subscribed $selected", TriState.TRUE,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, selected.getId()))
        DemoNoteInserter.Companion.assertInteraction(selected, NotificationEventType.HOME, TriState.TRUE)
        val reply3 = buildActivity(getAuthor1(), "Another note title",
                "Reply 3 to selected by the same author", selected, null)
        reply3.addAttachment(
                Attachment.Companion.fromUri("http://www.publicdomainpictures.net/pictures/100000/nahled/broadcasting-tower-14081029181fC.jpg"))
        addActivity(reply3)
        addActivity(reply1)
        DemoNoteInserter.Companion.assertInteraction(reply1, NotificationEventType.EMPTY, TriState.FALSE)
        addActivity(privateReply2)
        val reply4 = buildActivity(author4, "", "Reply 4 to Reply 1 other author", reply1, null)
        addActivity(reply4)
        DemoNoteInserter.Companion.increaseUpdateDate(reply4)
        addActivity(reply4.withVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS))
        DemoNoteInserter.Companion.assertStoredVisibility(reply4, Visibility.PUBLIC_AND_TO_FOLLOWERS)
        assertIfActorIsMyFriend(author3, true, ma)
        val MENTIONS_NOTE_BODY = """@fourthWithoutAvatar@pump.example.com Reply 5 to Reply 4
@${author3.getUsername()} @unknownUser@example.com"""
        val reply5 = buildActivity(author2, "", MENTIONS_NOTE_BODY, reply4,
                if (iteration == 1) DemoData.demoData.conversationMentionsNoteOid else null)
        addActivity(reply5)
        if (iteration == 1) {
            Assert.assertEquals(reply5.toString(), DemoData.demoData.conversationMentionsNoteOid, reply5.getNote().oid)
        }
        MatcherAssert.assertThat("""
    The user '${author3.getUsername()}' should be a recipient
    ${reply5.getNote()}
    """.trimIndent(),
                reply5.getNote().audience().nonSpecialActors, CoreMatchers.hasItem(author3))
        MatcherAssert.assertThat("""
    The user '${author2.getUsername()}' should not be a recipient
    ${reply5.getNote()}
    """.trimIndent(),
                reply5.getNote().audience().nonSpecialActors, CoreMatchers.not<Iterable<in Actor?>?>(CoreMatchers.hasItem(author2)))
        val reblogger1 = buildActorFromOid("acct:reblogger@" + DemoData.demoData.pumpioMainHost)
        reblogger1.setAvatarUrl("http://www.large-icons.com/stock-icons/free-large-android/48x48/dog-robot.gif")
        val reblogOf5 = buildActivity(reblogger1, ActivityType.ANNOUNCE)
        reblogOf5.setNote(reply5.getNote().shallowCopy())
        reblogOf5.setSubscribedByMe(TriState.TRUE)
        addActivity(reblogOf5)
        val reply6 = buildActivity(author3, "", "Reply 6 to Reply 4 - the second", reply4, null)
        reply6.getNote().addFavoriteBy(accountActor, TriState.TRUE)
        addActivity(reply6)
        val likeOf6 = buildActivity(author2, ActivityType.LIKE)
        likeOf6.setNote(reply6.getNote().shallowCopy())
        addActivity(likeOf6)
        val reply7 = buildActivity(getAuthor1(), "", "Reply 7 to Reply 2 is about "
                + DemoData.demoData.publicNoteText + " and something else", privateReply2, null)
                .withVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS)
        addActivity(reply7)
        DemoNoteInserter.Companion.assertStoredVisibility(reply7, Visibility.PUBLIC_AND_TO_FOLLOWERS)
        val reply8 = buildActivity(author4, "", "<b>Reply 8</b> to Reply 7", reply7, null)
        val reblogOfNewActivity8 = buildActivity(author3, ActivityType.ANNOUNCE)
        reblogOfNewActivity8.setActivity(reply8)
        addActivity(reblogOfNewActivity8)
        val reply9 = buildActivity(author2, "", "Reply 9 to Reply 7", reply7, null)
        reply9.setSubscribedByMe(TriState.TRUE)
        reply9.addAttachment(Attachment.Companion.fromUri(
                "http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg"))
        addActivity(reply9)
        val duplicateOfReply9 = buildActivity(author4, "", "A duplicate of " + reply9.getNote().content,
                null, null)
        duplicateOfReply9.setSubscribedByMe(TriState.TRUE)
        addActivity(duplicateOfReply9)
        val myLikeOf9: AActivity = AActivity.Companion.from(accountActor, ActivityType.LIKE)
        myLikeOf9.setActor(accountActor)
        myLikeOf9.setNote(reply9.getNote().shallowCopy())
        addActivity(myLikeOf9)

        // Note downloaded by another account
        val ma2: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountSecondName)
        author3.isMyFriend = TriState.TRUE
        author3.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
        val reply10 = buildActivity(ma2.actor, author3, "", "Reply 10 to Reply 8", reply8,
                null, DownloadStatus.LOADED)
        Assert.assertEquals("The third is a note Author", author3, reply10.getAuthor())
        addActivity(reply10)
        author3.isMyFriend = TriState.UNKNOWN
        author3.setUpdatedDate(MyLog.uniqueCurrentTimeMS())
        assertIfActorIsMyFriend(author3, true, ma2)
        val anonymousReply = buildActivity(Actor.Companion.EMPTY, "", "Anonymous reply to Reply 10", reply10, null)
        addActivity(anonymousReply)
        val reply11 = buildActivity(author2, "", "Reply 11 to Reply 7, " + DemoData.demoData.globalPublicNoteText
                + " text", reply7, null)
                .withVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS)
        addActivity(reply11)
        DemoNoteInserter.Companion.assertStoredVisibility(reply11, Visibility.PUBLIC_AND_TO_FOLLOWERS)
        DemoNoteInserter.Companion.assertInteraction(reply11, NotificationEventType.EMPTY, TriState.FALSE)
        val myReply13 = buildActivity(accountActor, "", "My reply 13 to Reply 2", privateReply2, null)
        val reply14 = buildActivity(author3, "", "Publicly reply to my note 13", myReply13, null)
                .withVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS)
        addActivity(reply14)
        DemoNoteInserter.Companion.assertStoredVisibility(reply14, Visibility.PUBLIC_AND_TO_FOLLOWERS)
        DemoNoteInserter.Companion.assertInteraction(reply14, NotificationEventType.MENTION, TriState.TRUE)
        val reblogOf14 = buildActivity(author2, ActivityType.ANNOUNCE)
        reblogOf14.setActivity(reply14)
        addActivity(reblogOf14)
        DemoNoteInserter.Companion.assertInteraction(reblogOf14, NotificationEventType.MENTION, TriState.TRUE)

        // Note: We cannot publicly Reblog private note. Yet
        val reblogOfMyPrivate13 = buildActivity(author3, ActivityType.ANNOUNCE)
        reblogOfMyPrivate13.setActivity(myReply13)
        addActivity(reblogOfMyPrivate13)
        DemoNoteInserter.Companion.assertInteraction(reblogOfMyPrivate13, NotificationEventType.ANNOUNCE, TriState.TRUE)
        val mentionOfAuthor3 = buildActivity(reblogger1, "",
                "@" + author3.getUsername() + " mention in reply to 4",
                reply4, if (iteration == 1) DemoData.demoData.conversationMentionOfAuthor3Oid else null)
        addActivity(mentionOfAuthor3)
        val followAuthor3 = buildActivity(author2, ActivityType.FOLLOW)
        followAuthor3.setObjActor(author3)
        addActivity(followAuthor3)
        DemoNoteInserter.Companion.assertInteraction(followAuthor3, NotificationEventType.EMPTY, TriState.FALSE)
        val notLoadedActor: Actor = Actor.Companion.fromOid(accountActor.origin, "acct:notloaded@someother.host."
                + DemoData.demoData.testOriginParentHost)
        val notLoaded1: AActivity = newPartialNote(accountActor, notLoadedActor, MyLog.uniqueDateTimeFormatted())
        val reply15 = buildActivity(author4, "", "Reply 15 to not loaded 1", notLoaded1, null)
        addActivity(reply15)
        val followsMe1 = buildActivity(getAuthor1(), ActivityType.FOLLOW)
        followsMe1.setObjActor(accountActor)
        addActivity(followsMe1)
        DemoNoteInserter.Companion.assertInteraction(followsMe1, NotificationEventType.FOLLOW, TriState.TRUE)
        val reply16 = buildActivity(author2, "", "<a href='" + author4.getProfileUrl() + "'>" +
                "@" + author4.getUsername() + "</a> Reply 16 to Reply 15", reply15, null)
        addActivity(reply16)
        val followsMe3 = DemoNoteInserter(accountActor).buildActivity(author3, ActivityType.FOLLOW, "")
        followsMe3.setObjActor(accountActor)
        DemoNoteInserter.Companion.onActivityS(followsMe3)
        val followsAuthor4 = DemoNoteInserter(accountActor).buildActivity(author3, ActivityType.FOLLOW, "")
        followsAuthor4.setObjActor(author4)
        DemoNoteInserter.Companion.onActivityS(followsAuthor4)
        val myReply17 = buildActivity(accountActor, "Name of reply 17", "My reply 17 to public Reply 14", reply14, null)
        addActivity(myReply17)
        DemoNoteInserter.Companion.assertStoredVisibility(myReply17, Visibility.PUBLIC_AND_TO_FOLLOWERS)
    }

    private fun getAuthor1(): Actor? {
        val author1 = buildActorFromOid(DemoData.demoData.conversationEntryAuthorOid)
        author1.setAvatarUrl("https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png")
        return author1
    }

    private fun buildActorFromOid(actorOid: String?): Actor? {
        return DemoNoteInserter(accountActor).buildActorFromOid(actorOid)
    }

    private fun buildActivity(actor: Actor?, type: ActivityType?): AActivity? {
        return DemoNoteInserter(accountActor).buildActivity(actor, type, "")
    }

    private fun buildActivity(author: Actor?, name: String?, content: String?, inReplyTo: AActivity?, noteOidIn: String?): AActivity? {
        return buildActivity(accountActor, author, name, content, inReplyTo, noteOidIn, DownloadStatus.LOADED)
    }

    private fun buildActivity(accountActor: Actor?, author: Actor?, name: String?, content: String?, inReplyTo: AActivity?,
                              noteOidIn: String?, status: DownloadStatus?): AActivity? {
        return DemoNoteInserter(accountActor).buildActivity(author, name,
                if (content.isNullOrEmpty()) "" else content + (if (inReplyTo != null) " it$iteration" else "") + bodySuffix,
                inReplyTo, noteOidIn, status)
    }

    private fun addActivity(activity: AActivity?) {
        DemoNoteInserter.Companion.onActivityS(activity)
    }

    companion object {
        fun assertIfActorIsMyFriend(actor: Actor?, isFriendOf: Boolean, ma: MyAccount?) {
            val actualIsFriend: Boolean = GroupMembership.Companion.isGroupMember(ma.actor, GroupType.FRIENDS, actor.actorId)
            Assert.assertEquals("Actor $actor is a friend of $ma", isFriendOf, actualIsFriend)
        }
    }
}