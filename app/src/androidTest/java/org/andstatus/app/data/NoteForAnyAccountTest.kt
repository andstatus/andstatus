package org.andstatus.app.data

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class NoteForAnyAccountTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun testAReply() {
        val ma: MyAccount = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(ma.isValid)
        val mi = DemoNoteInserter(ma)
        val accountActor = ma.actor
        val activity1 = mi.buildActivity(accountActor, "", "My testing note", null,
                null, DownloadStatus.LOADED)
        mi.onActivity(activity1)
        val nfaActivity1 = NoteForAnyAccount( MyContextHolder.myContextHolder.getNow(),
                activity1.getId(), activity1.getNote().noteId)
        val dataActivity1 = NoteContextMenuData(nfaActivity1, ma)
        Assert.assertTrue(dataActivity1.isAuthor)
        Assert.assertTrue(dataActivity1.isActor)
        Assert.assertTrue(dataActivity1.isSubscribed)
        Assert.assertEquals(Visibility.PUBLIC, nfaActivity1.visibility)
        Assert.assertTrue(dataActivity1.isTiedToThisAccount())
        Assert.assertTrue(dataActivity1.hasPrivateAccess())
        val author2 = mi.buildActorFromOid("acct:a2." + DemoData.demoData.testRunUid + "@pump.example.com")
        val replyTo1 = mi.buildActivity(author2, "", "@" + accountActor.getUsername()
                + " Replying to you privately", activity1, null, DownloadStatus.LOADED)
        replyTo1.getNote().audience().visibility = Visibility.PRIVATE
        mi.onActivity(replyTo1)
        val nfaReplyTo1 = NoteForAnyAccount( MyContextHolder.myContextHolder.getNow(),
                replyTo1.getId(), replyTo1.getNote().noteId)
        val dataReplyTo1 = NoteContextMenuData(nfaReplyTo1, ma)
        Assert.assertFalse(dataReplyTo1.isAuthor)
        Assert.assertFalse(dataReplyTo1.isActor)
        Assert.assertEquals(Visibility.PRIVATE, nfaReplyTo1.visibility)
        Assert.assertTrue(dataReplyTo1.isTiedToThisAccount())
        Assert.assertTrue(dataReplyTo1.hasPrivateAccess())
        val author3 = mi.buildActorFromOid("acct:b3." + DemoData.demoData.testRunUid + "@pumpity.example.com")
        val reply2 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " Replying publicly to the second author", replyTo1, null, DownloadStatus.LOADED)
        reply2.getNote().audience().visibility = Visibility.PUBLIC_AND_TO_FOLLOWERS
        mi.onActivity(reply2)
        val nfaReply2 = NoteForAnyAccount( MyContextHolder.myContextHolder.getNow(),
                0, reply2.getNote().noteId)
        val dataReply2 = NoteContextMenuData(nfaReply2, ma)
        Assert.assertFalse(dataReply2.isAuthor)
        Assert.assertFalse(dataReply2.isActor)
        Assert.assertEquals(Visibility.PUBLIC_AND_TO_FOLLOWERS, nfaReply2.visibility)
        Assert.assertFalse(dataReply2.isTiedToThisAccount())
        Assert.assertFalse(dataReply2.hasPrivateAccess())
        Assert.assertFalse(dataReply2.reblogged)
        val reblogged1 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED)
        val anotherMan = mi.buildActorFromOid("acct:c4." + DemoData.demoData.testRunUid + "@pump.example.com")
                .setUsername("anotherMan" + DemoData.demoData.testRunUid).build()
        val reblog1: AActivity = AActivity.Companion.from(accountActor, ActivityType.ANNOUNCE)
        reblog1.setActor(anotherMan)
        reblog1.setActivity(reblogged1)
        reblog1.setOid(MyLog.uniqueDateTimeFormatted())
        reblog1.setUpdatedNow(0)
        mi.onActivity(reblog1)
        val nfaReblogged1 = NoteForAnyAccount( MyContextHolder.myContextHolder.getNow(),
                0, reblogged1.getNote().noteId)
        val dataReblogged1 = NoteContextMenuData(nfaReblogged1, ma)
        Assert.assertFalse(dataReblogged1.isAuthor)
        Assert.assertFalse(dataReblogged1.isActor)
        Assert.assertFalse(dataReblogged1.isSubscribed)
        Assert.assertEquals(Visibility.PRIVATE, nfaReblogged1.visibility)
        Assert.assertFalse(dataReblogged1.isTiedToThisAccount())
        Assert.assertFalse(dataReblogged1.hasPrivateAccess())
        Assert.assertFalse(dataReblogged1.reblogged)
    }
}