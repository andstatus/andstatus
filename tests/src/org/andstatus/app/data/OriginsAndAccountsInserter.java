package org.andstatus.app.data;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.MyServiceManager;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionPumpio;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.OAuthClientKeysTest;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginTest;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

public class OriginsAndAccountsInserter extends InstrumentationTestCase {
    private Origin pumpioOrigin;
    
    public void insert() throws NameNotFoundException, ConnectionException {
        Context context = TestSuite.getMyContextForTest().context();
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        OriginTest.createOneOrigin(OriginType.TWITTER, TestSuite.TWITTER_TEST_ORIGIN_NAME, TestSuite.TWITTER_TEST_ORIGIN_NAME + ".example.com", true, true);
        OriginTest.createOneOrigin(TestSuite.CONVERSATION_ORIGIN_TYPE, TestSuite.CONVERSATION_ORIGIN_NAME, TestSuite.CONVERSATION_ORIGIN_NAME + ".example.com", true, true);
        MyContextHolder.get().persistentOrigins().initialize();

        Origin twitterOrigin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.TWITTER_TEST_ORIGIN_NAME);
        assertEquals("Twitter test origin created", twitterOrigin.getOriginType(), OriginType.TWITTER);
        
        pumpioOrigin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.CONVERSATION_ORIGIN_NAME);
        assertEquals("Origin for conversation created", pumpioOrigin.getOriginType(), TestSuite.CONVERSATION_ORIGIN_TYPE);
        
        OAuthClientKeysTest.insertTestKeys(pumpioOrigin);
        
        addPumpIoAccount("acct:firstTestUser@identi.ca", "");
        addPumpIoAccount("acct:t131t@identi.ca", "");
        addPumpIoAccount(TestSuite.CONVERSATION_ACCOUNT_USER_OID, TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL);

        MyPreferences.onPreferencesChanged();
        MyContextHolder.initialize(context, this);
        MyServiceManager.setServiceUnavailable();
        assertTrue("Context is ready", MyContextHolder.get().isReady());
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
    }
    
    private MyAccount addPumpIoAccount(String userOid, String avatarUrl) throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        long accountUserId_existing = MyProvider.oidToId(OidEnum.USER_OID, pumpioOrigin.getId(), userOid);
        MbUser mbUser = userFromPumpioOid(userOid);
        mbUser.avatarUrl = avatarUrl;
        MyAccount ma = addAccountFromMbUser(mbUser);
        long accountUserId = ma.getUserId();
        if (accountUserId_existing == 0 && !userOid.contains("firstTestUser")) {
            assertTrue("AccountUserId != 1", accountUserId != 1);
        } else {
            assertTrue("AccountUserId != 0", accountUserId != 0);
        }
        assertTrue("Account " + userOid + " is persistent", ma != null);
        assertTrue("Account UserOid", ma.getUserOid().equalsIgnoreCase(userOid));
        assertTrue("Account is successfully verified", ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED);
        return ma;
    }
    
    private MbUser userFromPumpioOid(String userOid) {
        ConnectionPumpio connection = new ConnectionPumpio();
        String userName = connection.userOidToUsername(userOid);
        MbUser mbUser = MbUser.fromOriginAndUserOid(pumpioOrigin.getId(), userOid);
        mbUser.userName = userName;
        mbUser.url = "http://" + connection.usernameToHost(userName)  + "/" + connection.usernameToNickname(userName);
        return mbUser;
    }

    private MyAccount addAccountFromMbUser(MbUser mbUser) throws ConnectionException {
        assertTrue(MyContextHolder.get().initialized());
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(mbUser.originId);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(mbUser.userName + "/" + origin.getName(), TriState.TRUE);
        builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.userName, "sampleUserSecretFor" + mbUser.userName);
        assertTrue("Credentials of " + mbUser.userName + " are present", builder.getAccount().getCredentialsPresent());
        builder.onCredentialsVerified(mbUser, null);
        assertTrue("Account is persistent", builder.isPersistent());
        MyAccount ma = builder.getAccount();
        assertEquals("Credentials of " + mbUser.userName + " successfully verified", 
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long userId = ma.getUserId();
        assertTrue("Account " + mbUser.userName + " has UserId", userId != 0);
        assertEquals("Account UserOid", ma.getUserOid(), mbUser.oid);
        assertEquals("User in the database for id=" + userId, 
                mbUser.oid,
                MyProvider.idToOid(OidEnum.USER_OID, userId, 0));
        assertEquals("Account name", mbUser.userName + "/" + origin.getName(), ma.getAccountName());
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getUserId());
        return ma;
    }
}
