package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteForAnyAccountTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testAReply() {
        MyAccount ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(ma.isValid());
        DemoNoteInserter mi = new DemoNoteInserter(ma);
        Actor accountActor = ma.getActor();
        AActivity activity1 = mi.buildActivity(accountActor, "", "My testing note", null,
                null, DownloadStatus.LOADED);
        mi.onActivity(activity1);

        NoteForAnyAccount nfaActivity1 = new NoteForAnyAccount(myContextHolder.getNow(),
                activity1.getId(), activity1.getNote().noteId);
        AccountToNote atnActivity1 = new AccountToNote(nfaActivity1, ma);
        assertTrue(atnActivity1.isAuthor);
        assertTrue(atnActivity1.isActor);
        assertTrue(atnActivity1.isSubscribed);
        assertEquals(Visibility.PUBLIC, nfaActivity1.visibility);
        assertTrue(atnActivity1.isTiedToThisAccount());
        assertTrue(atnActivity1.hasPrivateAccess());
        
        Actor author2 = mi.buildActorFromOid("acct:a2." + demoData.testRunUid + "@pump.example.com");
        final AActivity replyTo1 = mi.buildActivity(author2, "", "@" + accountActor.getUsername()
                + " Replying to you privately", activity1, null, DownloadStatus.LOADED);
        replyTo1.getNote().audience().setVisibility(Visibility.PRIVATE);
        mi.onActivity(replyTo1);

        NoteForAnyAccount nfaReplyTo1 = new NoteForAnyAccount(myContextHolder.getNow(),
                replyTo1.getId(), replyTo1.getNote().noteId);
        AccountToNote atnReplyTo1 = new AccountToNote(nfaReplyTo1, ma);
        assertFalse(atnReplyTo1.isAuthor);
        assertFalse(atnReplyTo1.isActor);
        assertEquals(Visibility.PRIVATE, nfaReplyTo1.visibility);
        assertTrue(atnReplyTo1.isTiedToThisAccount());
        assertTrue(atnReplyTo1.hasPrivateAccess());

        Actor author3 = mi.buildActorFromOid("acct:b3." + demoData.testRunUid + "@pumpity.example.com");
        AActivity reply2 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " Replying publicly to the second author", replyTo1, null, DownloadStatus.LOADED);
        reply2.getNote().audience().setVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS);
        mi.onActivity(reply2);

        NoteForAnyAccount nfaReply2 = new NoteForAnyAccount(myContextHolder.getNow(),
                0, reply2.getNote().noteId);
        AccountToNote atnReply2 = new AccountToNote(nfaReply2, ma);
        assertFalse(atnReply2.isAuthor);
        assertFalse(atnReply2.isActor);
        assertEquals(Visibility.PUBLIC_AND_TO_FOLLOWERS, nfaReply2.visibility);
        assertFalse(atnReply2.isTiedToThisAccount());
        assertFalse(atnReply2.hasPrivateAccess());
        assertFalse(atnReply2.reblogged);

        AActivity reblogged1 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED);
        Actor anotherMan = mi.buildActorFromOid("acct:c4." + demoData.testRunUid + "@pump.example.com")
                .setUsername("anotherMan" + demoData.testRunUid).build();
        AActivity reblog1 = AActivity.from(accountActor, ActivityType.ANNOUNCE);
        reblog1.setActor(anotherMan);
        reblog1.setActivity(reblogged1);
        reblog1.setOid(MyLog.uniqueDateTimeFormatted());
        reblog1.setUpdatedNow(0);
        mi.onActivity(reblog1);

        NoteForAnyAccount nfaReblogged1 = new NoteForAnyAccount(myContextHolder.getNow(),
                0, reblogged1.getNote().noteId);
        AccountToNote atnReblogged1 = new AccountToNote(nfaReblogged1, ma);
        assertFalse(atnReblogged1.isAuthor);
        assertFalse(atnReblogged1.isActor);
        assertFalse(atnReblogged1.isSubscribed);
        assertEquals(Visibility.PRIVATE, nfaReblogged1.visibility);
        assertFalse(atnReblogged1.isTiedToThisAccount());
        assertFalse(atnReblogged1.hasPrivateAccess());
        assertFalse(atnReblogged1.reblogged);
    }
}
