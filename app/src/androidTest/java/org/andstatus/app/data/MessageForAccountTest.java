package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbUser;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.util.TriState.FALSE;
import static org.andstatus.app.util.TriState.TRUE;
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
        MyAccount ma = DemoData.getMyAccount(DemoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        DemoMessageInserter mi = new DemoMessageInserter(ma);
        MbUser accountUser = ma.toPartialUser();
        MbActivity msg1 = mi.buildActivity(accountUser, "My testing message", null, null, DownloadStatus.LOADED);
        mi.onActivity(msg1);
        
        MessageForAccount mfa = new MessageForAccount(msg1.getMessage().msgId, ma.getOriginId(), ma);
        assertTrue(mfa.isAuthor);
        assertTrue(mfa.isSender);
        assertTrue(mfa.isSubscribed);
        assertEquals(UNKNOWN, mfa.isPrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        MbUser author2 = mi.buildUserFromOid("acct:a2." + DemoData.TESTRUN_UID + "@pump.example.com");
        final MbActivity replyTo1 = mi.buildActivity(author2, "@" + accountUser.getUserName()
                + " Replying to you", msg1, null, DownloadStatus.LOADED);
        replyTo1.getMessage().setPrivate(FALSE);
        mi.onActivity(replyTo1);
        
        mfa = new MessageForAccount(replyTo1.getMessage().msgId, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isPrivate());
        assertEquals(FALSE, mfa.isPrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());

        MbUser author3 = mi.buildUserFromOid("acct:b3." + DemoData.TESTRUN_UID + "@pumpity.example.com");
        MbActivity replyTo2 = mi.buildActivity(author3, "@" + author2.getUserName()
                + " Replying to the second author", replyTo1, null, DownloadStatus.LOADED);
        replyTo2.getMessage().setPrivate(FALSE);
        mi.onActivity(replyTo2);
        
        mfa = new MessageForAccount(replyTo2.getMessage().msgId, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isPrivate());
        assertEquals(FALSE, mfa.isPrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);

        MbActivity reblogged1 = mi.buildActivity(author3, "@" + author2.getUserName()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED);
        MbUser anotherMan = mi.buildUserFromOid("acct:c4." + DemoData.TESTRUN_UID + "@pump.example.com");
        anotherMan.setUserName("anotherMan" + DemoData.TESTRUN_UID);
        MbActivity activity = MbActivity.from(accountUser, MbActivityType.ANNOUNCE);
        activity.setActor(anotherMan);
        activity.setActivity(reblogged1);
        mi.onActivity(activity);

        mfa = new MessageForAccount(reblogged1.getMessage().msgId, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertTrue(mfa.isSubscribed);
        assertFalse(mfa.isPrivate());
        assertEquals(UNKNOWN, mfa.isPrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);
    }
}
