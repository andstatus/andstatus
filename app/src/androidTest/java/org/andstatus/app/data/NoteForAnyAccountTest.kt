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
        NoteContextMenuData dataActivity1 = new NoteContextMenuData(nfaActivity1, ma);
        assertTrue(dataActivity1.isAuthor);
        assertTrue(dataActivity1.isActor);
        assertTrue(dataActivity1.isSubscribed);
        assertEquals(Visibility.PUBLIC, nfaActivity1.visibility);
        assertTrue(dataActivity1.isTiedToThisAccount());
        assertTrue(dataActivity1.hasPrivateAccess());
        
        Actor author2 = mi.buildActorFromOid("acct:a2." + demoData.testRunUid + "@pump.example.com");
        final AActivity replyTo1 = mi.buildActivity(author2, "", "@" + accountActor.getUsername()
                + " Replying to you privately", activity1, null, DownloadStatus.LOADED);
        replyTo1.getNote().audience().setVisibility(Visibility.PRIVATE);
        mi.onActivity(replyTo1);

        NoteForAnyAccount nfaReplyTo1 = new NoteForAnyAccount(myContextHolder.getNow(),
                replyTo1.getId(), replyTo1.getNote().noteId);
        NoteContextMenuData dataReplyTo1 = new NoteContextMenuData(nfaReplyTo1, ma);
        assertFalse(dataReplyTo1.isAuthor);
        assertFalse(dataReplyTo1.isActor);
        assertEquals(Visibility.PRIVATE, nfaReplyTo1.visibility);
        assertTrue(dataReplyTo1.isTiedToThisAccount());
        assertTrue(dataReplyTo1.hasPrivateAccess());

        Actor author3 = mi.buildActorFromOid("acct:b3." + demoData.testRunUid + "@pumpity.example.com");
        AActivity reply2 = mi.buildActivity(author3, "", "@" + author2.getUsername()
                + " Replying publicly to the second author", replyTo1, null, DownloadStatus.LOADED);
        reply2.getNote().audience().setVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS);
        mi.onActivity(reply2);

        NoteForAnyAccount nfaReply2 = new NoteForAnyAccount(myContextHolder.getNow(),
                0, reply2.getNote().noteId);
        NoteContextMenuData dataReply2 = new NoteContextMenuData(nfaReply2, ma);
        assertFalse(dataReply2.isAuthor);
        assertFalse(dataReply2.isActor);
        assertEquals(Visibility.PUBLIC_AND_TO_FOLLOWERS, nfaReply2.visibility);
        assertFalse(dataReply2.isTiedToThisAccount());
        assertFalse(dataReply2.hasPrivateAccess());
        assertFalse(dataReply2.reblogged);

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
        NoteContextMenuData dataReblogged1 = new NoteContextMenuData(nfaReblogged1, ma);
        assertFalse(dataReblogged1.isAuthor);
        assertFalse(dataReblogged1.isActor);
        assertFalse(dataReblogged1.isSubscribed);
        assertEquals(Visibility.PRIVATE, nfaReblogged1.visibility);
        assertFalse(dataReblogged1.isTiedToThisAccount());
        assertFalse(dataReblogged1.hasPrivateAccess());
        assertFalse(dataReblogged1.reblogged);
    }
}
