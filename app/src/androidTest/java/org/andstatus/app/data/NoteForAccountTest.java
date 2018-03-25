package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.util.TriState.FALSE;
import static org.andstatus.app.util.TriState.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteForAccountTest {

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
        
        NoteForAccount mfa = new NoteForAccount(ma.getOrigin(), activity1.getId(), activity1.getNote().noteId, ma);
        assertTrue(mfa.isAuthor);
        assertTrue(mfa.isActor);
        assertTrue(mfa.isSubscribed);
        assertEquals(UNKNOWN, mfa.isPrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        Actor author2 = mi.buildActorFromOid("acct:a2." + demoData.testRunUid + "@pump.example.com");
        final AActivity replyTo1 = mi.buildActivity(author2, "", "@" + accountActor.getUsername()
                + " Replying to you", activity1, null, DownloadStatus.LOADED);
        replyTo1.getNote().setPrivate(FALSE);
        mi.onActivity(replyTo1);
        
        mfa = new NoteForAccount(ma.getOrigin(), replyTo1.getId(), replyTo1.getNote().noteId, ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isActor);
        assertFalse(mfa.isPrivate());
        assertEquals(FALSE, mfa.isPrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());

        Actor author3 = mi.buildActorFromOid("acct:b3." + demoData.testRunUid + "@pumpity.example.com");
        AActivity replyTo2 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " Replying to the second author", replyTo1, null, DownloadStatus.LOADED);
        replyTo2.getNote().setPrivate(FALSE);
        mi.onActivity(replyTo2);
        
        mfa = new NoteForAccount(ma.getOrigin(), 0, replyTo2.getNote().noteId, ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isActor);
        assertFalse(mfa.isPrivate());
        assertEquals(FALSE, mfa.isPrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);

        AActivity reblogged1 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED);
        Actor anotherMan = mi.buildActorFromOid("acct:c4." + demoData.testRunUid + "@pump.example.com");
        anotherMan.setUsername("anotherMan" + demoData.testRunUid);
        AActivity activity4 = AActivity.from(accountActor, ActivityType.ANNOUNCE);
        activity4.setActor(anotherMan);
        activity4.setActivity(reblogged1);
        activity4.setTimelinePosition(MyLog.uniqueDateTimeFormatted());
        activity4.setUpdatedDate(System.currentTimeMillis());
        mi.onActivity(activity4);

        mfa = new NoteForAccount(ma.getOrigin(), 0, reblogged1.getNote().noteId, ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isActor);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isPrivate());
        assertEquals(UNKNOWN, mfa.isPrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);
    }
}
