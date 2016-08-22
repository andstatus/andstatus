package org.andstatus.app.data;

import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;

@Travis
public class MessageForAccountTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testAReply() {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME);
        assertTrue(ma.isValid());
        MessageInserter mi = new MessageInserter(ma);
        MbUser author1 = mi.getAccountMbUser();
        MbMessage msg1 = mi.buildMessage(author1, "My testing message", null, null, DownloadStatus.LOADED);
        long mgs1Id = mi.addMessage(msg1);
        
        MessageForAccount mfa = new MessageForAccount(mgs1Id, ma.getOriginId(), ma);
        assertTrue(mfa.isAuthor);
        assertTrue(mfa.isSender);
        assertTrue(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertFalse(mfa.mayBePrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        MbUser author2 = mi.buildUser();
        MbMessage replyTo1 = mi.buildMessage(author2, "@" + author1.getUserName()
                + " Replying to you", msg1, null, DownloadStatus.LOADED);
        replyTo1.setPublic(true);
        long replyTo1Id = mi.addMessage(replyTo1);
        
        mfa = new MessageForAccount(replyTo1Id, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertTrue(mfa.mayBePrivate);
        assertTrue(mfa.isTiedToThisAccount());
        assertTrue(mfa.hasPrivateAccess());
        
        
        MbUser author3 = mi.buildUser();
        MbMessage replyTo2 = mi.buildMessage(author3, "@" + author2.getUserName()
                + " Replying to the second author", replyTo1, null, DownloadStatus.LOADED);
        replyTo2.setPublic(true);
        long replyTo2Id = mi.addMessage(replyTo2);
        
        mfa = new MessageForAccount(replyTo2Id, ma.getOriginId(), ma);
        assertFalse(mfa.isAuthor);
        assertFalse(mfa.isSender);
        assertFalse(mfa.isSubscribed);
        assertFalse(mfa.isDirect());
        assertTrue(mfa.mayBePrivate);
        assertFalse(mfa.isTiedToThisAccount());
        assertFalse(mfa.hasPrivateAccess());
    }
}
