package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.util.TriState.TRUE;
import static org.andstatus.app.util.TriState.UNKNOWN;
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

        NoteForAnyAccount noteForAnyAccount = new NoteForAnyAccount(MyContextHolder.get(),
                activity1.getId(), activity1.getNote().noteId);
        AccountToNote mfa = new AccountToNote(noteForAnyAccount, ma);
        assertTrue(mfa.isAuthor);
        assertTrue(mfa.isActor);
        assertTrue(mfa.isSubscribed);
        assertEquals(UNKNOWN, noteForAnyAccount.isPublic);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        Actor author2 = mi.buildActorFromOid("acct:a2." + demoData.testRunUid + "@pump.example.com");
        final AActivity replyTo1 = mi.buildActivity(author2, "", "@" + accountActor.getUsername()
                + " Replying to you", activity1, null, DownloadStatus.LOADED);
        replyTo1.getNote().setPublic(TRUE);
        mi.onActivity(replyTo1);

        NoteForAnyAccount noteForAnyAccount2 = new NoteForAnyAccount(MyContextHolder.get(),
                replyTo1.getId(), replyTo1.getNote().noteId);
        AccountToNote atn2 = new AccountToNote(noteForAnyAccount2, ma);
        assertFalse(atn2.isAuthor);
        assertFalse(atn2.isActor);
        assertEquals(TRUE, noteForAnyAccount2.isPublic);
        assertTrue(atn2.isTiedToThisAccount());
        assertTrue(atn2.hasPrivateAccess());

        Actor author3 = mi.buildActorFromOid("acct:b3." + demoData.testRunUid + "@pumpity.example.com");
        AActivity replyTo2 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " Replying to the second author", replyTo1, null, DownloadStatus.LOADED);
        replyTo2.getNote().setPublic(TRUE);
        mi.onActivity(replyTo2);

        NoteForAnyAccount noteForAnyAccount3 = new NoteForAnyAccount(MyContextHolder.get(),
                0, replyTo2.getNote().noteId);
        AccountToNote atn3 = new AccountToNote(noteForAnyAccount3, ma);
        assertFalse(atn3.isAuthor);
        assertFalse(atn3.isActor);
        assertEquals(TRUE, noteForAnyAccount3.isPublic);
        assertFalse(atn3.isTiedToThisAccount());
        assertFalse(atn3.hasPrivateAccess());
        assertFalse(atn3.reblogged);

        AActivity reblogged1 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED);
        Actor anotherMan = mi.buildActorFromOid("acct:c4." + demoData.testRunUid + "@pump.example.com")
                .setUsername("anotherMan" + demoData.testRunUid).build();
        AActivity activity4 = AActivity.from(accountActor, ActivityType.ANNOUNCE);
        activity4.setActor(anotherMan);
        activity4.setActivity(reblogged1);
        activity4.setTimelinePosition(MyLog.uniqueDateTimeFormatted());
        activity4.setUpdatedNow(0);
        mi.onActivity(activity4);

        NoteForAnyAccount noteForAnyAccount4 = new NoteForAnyAccount(MyContextHolder.get(),
                0, reblogged1.getNote().noteId);
        AccountToNote atn4 = new AccountToNote(noteForAnyAccount4, ma);
        assertFalse(atn4.isAuthor);
        assertFalse(atn4.isActor);
        assertFalse(atn4.isSubscribed);
        assertEquals(UNKNOWN, noteForAnyAccount4.isPublic);
        assertFalse(atn4.isTiedToThisAccount());
        assertFalse(atn4.hasPrivateAccess());
        assertFalse(atn4.reblogged);
    }
}
