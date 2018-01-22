package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.util.TriState.FALSE;
import static org.andstatus.app.util.TriState.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageForAccountTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testAReply() {
        MyAccount ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        DemoMessageInserter mi = new DemoMessageInserter(ma);
        MbUser accountUser = ma.toPartialUser();
        MbActivity activity1 = mi.buildActivity(accountUser, "My testing message", null,
                null, DownloadStatus.LOADED);
        mi.onActivity(activity1);
        
        MessageForAccount mfa = new MessageForAccount(ma.getOrigin(), activity1.getId(), activity1.getMessage().msgId, ma);
        assertTrue(mfa.isAuthor);
        assertTrue(mfa.isActor);
        assertTrue(mfa.isSubscribed);
        assertEquals(UNKNOWN, mfa.isPrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        MbUser author2 = mi.buildUserFromOid("acct:a2." + demoData.TESTRUN_UID + "@pump.example.com");
        final MbActivity replyTo1 = mi.buildActivity(author2, "@" + accountUser.getUserName()
                + " Replying to you", activity1, null, DownloadStatus.LOADED);
        replyTo1.getMessage().setPrivate(FALSE);
        mi.onActivity(replyTo1);
        
        mfa = new MessageForAccount(ma.getOrigin(), replyTo1.getId(), replyTo1.getMessage().msgId, ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isActor);
        assertFalse(mfa.isPrivate());
        assertEquals(FALSE, mfa.isPrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());

        MbUser author3 = mi.buildUserFromOid("acct:b3." + demoData.TESTRUN_UID + "@pumpity.example.com");
        MbActivity replyTo2 = mi.buildActivity(author3, "@" + author2.getUserName()
                + " Replying to the second author", replyTo1, null, DownloadStatus.LOADED);
        replyTo2.getMessage().setPrivate(FALSE);
        mi.onActivity(replyTo2);
        
        mfa = new MessageForAccount(ma.getOrigin(), 0, replyTo2.getMessage().msgId, ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isActor);
        assertFalse(mfa.isPrivate());
        assertEquals(FALSE, mfa.isPrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);

        MbActivity reblogged1 = mi.buildActivity(author3, "@" + author2.getUserName()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED);
        MbUser anotherMan = mi.buildUserFromOid("acct:c4." + demoData.TESTRUN_UID + "@pump.example.com");
        anotherMan.setUserName("anotherMan" + demoData.TESTRUN_UID);
        MbActivity activity4 = MbActivity.from(accountUser, MbActivityType.ANNOUNCE);
        activity4.setActor(anotherMan);
        activity4.setActivity(reblogged1);
        activity4.setTimelinePosition(MyLog.uniqueDateTimeFormatted());
        activity4.setUpdatedDate(System.currentTimeMillis());
        mi.onActivity(activity4);

        mfa = new MessageForAccount(ma.getOrigin(), 0, reblogged1.getMessage().msgId, ma);
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
