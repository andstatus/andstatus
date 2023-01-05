package org.andstatus.app.data

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.util.MyLog
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteForAnyAccountTest {
    @Before
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
    }

    @Test
    fun testAReply() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        assertTrue(ma.isValid)
        val ni = DemoNoteInserter(ma)
        val accountActor = ma.actor
        val activity1 = ni.buildActivity(
            accountActor, "", "My testing note", null,
            null, DownloadStatus.LOADED
        )
        ni.onActivity(activity1)
        val nfaActivity1 = NoteForAnyAccount(
            myContextHolder.getNow(),
            activity1.id, activity1.getNote().noteId
        )
        val dataActivity1 = NoteContextMenuData(nfaActivity1, ma)
        assertTrue(dataActivity1.isAuthor)
        assertTrue(dataActivity1.isActor)
        assertTrue(dataActivity1.isSubscribed)
        assertEquals(Visibility.PUBLIC_AND_TO_FOLLOWERS, nfaActivity1.visibility)
        assertTrue(dataActivity1.isConversationParticipant)
        assertTrue(dataActivity1.isTiedToThisAccount())
        assertTrue(dataActivity1.hasPrivateAccess())

        val author2 = ni.buildActorFromOid("acct:a2." + DemoData.demoData.testRunUid + "@pump.example.com")
        val replyTo1 = ni.buildActivity(
            author2, "", "@" + accountActor.getUsername()
                + " Replying to you privately", activity1, null, DownloadStatus.LOADED
        )
        replyTo1.getNote().audience().visibility = Visibility.PRIVATE
        ni.onActivity(replyTo1)
        val nfaReplyTo1 = NoteForAnyAccount(
            myContextHolder.getNow(),
            replyTo1.id, replyTo1.getNote().noteId
        )
        val dataReplyTo1 = NoteContextMenuData(nfaReplyTo1, ma)
        assertFalse(dataReplyTo1.isAuthor)
        assertFalse(dataReplyTo1.isActor)
        assertEquals(Visibility.PRIVATE, nfaReplyTo1.visibility)
        assertTrue(dataReplyTo1.isConversationParticipant)
        assertTrue(dataReplyTo1.isTiedToThisAccount())
        assertTrue(dataReplyTo1.hasPrivateAccess())

        val author3 = ni.buildActorFromOid("acct:b3." + DemoData.demoData.testRunUid + "@pumpity.example.com")
        val reply2 = ni.buildActivity(
            author3, "", "@" + author2.getUsername()
                + " Replying publicly to the second author", replyTo1, null, DownloadStatus.LOADED
        )
        reply2.getNote().audience().visibility = Visibility.PUBLIC_AND_TO_FOLLOWERS
        ni.onActivity(reply2)
        val nfaReply2 = NoteForAnyAccount(
            myContextHolder.getNow(),
            0, reply2.getNote().noteId
        )
        assertThat(
            nfaReply2.conversationParticipants.map { it.author.actor },
            containsInAnyOrder(accountActor, author2, author3)
        )
        val dataReply2 = NoteContextMenuData(nfaReply2, ma)
        assertFalse(dataReply2.isAuthor)
        assertFalse(dataReply2.isActor)
        assertEquals(Visibility.PUBLIC_AND_TO_FOLLOWERS, nfaReply2.visibility)
        assertTrue(dataReply2.isConversationParticipant)
        assertTrue(dataReply2.isTiedToThisAccount())
        assertFalse(dataReply2.hasPrivateAccess())
        assertFalse(dataReply2.reblogged)

        val reblogged1 = ni.buildActivity(
            author3, "", "@" + author2.getUsername()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED
        )
        val anotherMan = ni.buildActorFromOid("acct:c4." + DemoData.demoData.testRunUid + "@pump.example.com")
            .setUsername("anotherMan" + DemoData.demoData.testRunUid).build()
        val reblog1: AActivity = AActivity.Companion.from(accountActor, ActivityType.ANNOUNCE)
        reblog1.setActor(anotherMan)
        reblog1.setActivity(reblogged1)
        reblog1.setOid(MyLog.uniqueDateTimeFormatted())
        reblog1.setUpdatedNow(0)
        ni.onActivity(reblog1)
        val nfaReblogged1 = NoteForAnyAccount(
            myContextHolder.getNow(),
            0, reblogged1.getNote().noteId
        )
        val dataReblogged1 = NoteContextMenuData(nfaReblogged1, ma)
        assertFalse(dataReblogged1.isAuthor)
        assertFalse(dataReblogged1.isActor)
        assertFalse(dataReblogged1.isSubscribed)
        assertEquals(Visibility.PRIVATE, nfaReblogged1.visibility)
        assertTrue(dataReblogged1.isConversationParticipant)
        assertTrue(dataReblogged1.isTiedToThisAccount())
        assertFalse(dataReblogged1.hasPrivateAccess())
        assertFalse(dataReblogged1.reblogged)
    }
}
