package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.junit.Before;
import org.junit.Test;

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
        MbMessage msg1 = mi.buildMessage(accountUser, "My testing message", null, null, DownloadStatus.LOADED);
        long mgs1Id = mi.onActivity(msg1.update(accountUser));
        
        MessageForAccount mfa = new MessageForAccount(mgs1Id, ma.getOriginId(), ma);
        assertTrue(mfa.isAuthor);
        assertTrue(mfa.isSender);
        assertTrue(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertFalse(mfa.mayBePrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        MbUser author2 = mi.buildUserFromOid("acct:a2." + DemoData.TESTRUN_UID + "@pump.example.com");
        MbMessage replyTo1 = mi.buildMessage(author2, "@" + accountUser.getUserName()
                + " Replying to you", msg1, null, DownloadStatus.LOADED);
        replyTo1.setPublic(true);
        long replyTo1Id = mi.onActivity(replyTo1.update(accountUser));
        
        mfa = new MessageForAccount(replyTo1Id, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertTrue(mfa.mayBePrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());

        MbUser author3 = mi.buildUserFromOid("acct:b3." + DemoData.TESTRUN_UID + "@pumpity.example.com");
        MbMessage replyTo2 = mi.buildMessage(author3, "@" + author2.getUserName()
                + " Replying to the second author", replyTo1, null, DownloadStatus.LOADED);
        replyTo2.setPublic(true);
        long replyTo2Id = mi.onActivity(replyTo2.update(accountUser));
        
        mfa = new MessageForAccount(replyTo2Id, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertTrue(mfa.mayBePrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);

        MbMessage reblogged1 = mi.buildMessage(author3, "@" + author2.getUserName()
                + " This reply is reblogged by anotherMan", replyTo1, null, DownloadStatus.LOADED);
        MbUser anotherMan = mi.buildUserFromOid("acct:c4." + DemoData.TESTRUN_UID + "@pump.example.com");
        anotherMan.setUserName("anotherMan" + DemoData.TESTRUN_UID);
        MbActivity activity = MbActivity.from(accountUser, MbActivityType.ANNOUNCE);
        activity.setActor(anotherMan);
        activity.setMessage(reblogged1);
        long reblogged1Id = mi.onActivity(activity);

        mfa = new MessageForAccount(reblogged1Id, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertTrue(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertTrue(mfa.mayBePrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
        assertFalse(mfa.reblogged);
    }
}
