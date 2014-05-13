package org.andstatus.app.data;

import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.net.OAuthClientKeysTest;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginTest;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

public class OriginsAndAccountsInserter extends InstrumentationTestCase {
    
    public void insert() throws NameNotFoundException, ConnectionException {
        Context context = TestSuite.getMyContextForTest().context();
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));

        OriginTest.createOneOrigin(OriginType.TWITTER, TestSuite.TWITTER_TEST_ORIGIN_NAME, TestSuite.TWITTER_TEST_ORIGIN_NAME + ".example.com", true, true);
        OriginTest.createOneOrigin(TestSuite.CONVERSATION_ORIGIN_TYPE, TestSuite.CONVERSATION_ORIGIN_NAME, TestSuite.CONVERSATION_ORIGIN_NAME + ".example.com", true, true);
        OriginTest.createOneOrigin(OriginType.STATUSNET, TestSuite.STATUSNET_TEST_ORIGIN_NAME, TestSuite.STATUSNET_TEST_ORIGIN_NAME + ".example.com", true, true);
        MyContextHolder.get().persistentOrigins().initialize();
        
        Origin pumpioOrigin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.CONVERSATION_ORIGIN_NAME);
        assertEquals("Origin for conversation created", pumpioOrigin.getOriginType(), TestSuite.CONVERSATION_ORIGIN_TYPE);
        OAuthClientKeysTest.insertTestKeys(pumpioOrigin);
        
        addAccount(pumpioOrigin, "acct:firstTestUser@identi.ca", "firstTestUser@identi.ca", "");
        addAccount(pumpioOrigin, "acct:t131t@identi.ca", "t131t@identi.ca", "");
        addAccount(pumpioOrigin, TestSuite.CONVERSATION_ACCOUNT_USER_OID, TestSuite.CONVERSATION_ACCOUNT_USERNAME, TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL);

        Origin twitterOrigin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.TWITTER_TEST_ORIGIN_NAME);
        assertEquals("Twitter test origin created", twitterOrigin.getOriginType(), OriginType.TWITTER);
        addAccount(twitterOrigin, TestSuite.TWITTER_TEST_ACCOUNT_USER_OID, TestSuite.TWITTER_TEST_ACCOUNT_USERNAME, "");
        
        Origin statusNetOrigin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.STATUSNET_TEST_ORIGIN_NAME);
        assertEquals("StatusNetOrigin created", statusNetOrigin.getOriginType(), OriginType.STATUSNET);
        addAccount(statusNetOrigin, TestSuite.STATUSNET_TEST_ACCOUNT_USER_OID, TestSuite.STATUSNET_TEST_ACCOUNT_USERNAME, "");
        
        MyPreferences.onPreferencesChanged();
        MyContextHolder.initialize(context, this);
        MyServiceManager.setServiceUnavailable();
        assertTrue("Context is ready", MyContextHolder.get().isReady());
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
    }
    
    private MyAccount addAccount(Origin origin, String userOid, String username, String avatarUrl) throws ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        long accountUserId_existing = MyProvider.oidToId(OidEnum.USER_OID, origin.getId(), userOid);
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), userOid);
        mbUser.userName = username;
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

    private MyAccount addAccountFromMbUser(MbUser mbUser) throws ConnectionException {
        waitForContextInitialization();
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(mbUser.originId);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(mbUser.userName + "/" + origin.getName(), TriState.TRUE);
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.userName, "sampleUserSecretFor" + mbUser.userName);
        } else {
            builder.setPassword("samplePasswordFor" + mbUser.userName);
        }
        assertTrue("Credentials of " + mbUser + " are present (origin name=" + origin.getName() + ")", builder.getAccount().getCredentialsPresent());
        builder.onCredentialsVerified(mbUser, null);
        waitForContextInitialization();

        assertTrue("Account is persistent", builder.isPersistent());
        MyAccount ma = builder.getAccount();
        assertEquals("Credentials of " + mbUser.userName + " successfully verified", 
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long userId = ma.getUserId();
        assertTrue("Account " + mbUser.userName + " has UserId", userId != 0);
        assertEquals("Account UserOid", ma.getUserOid(), mbUser.oid);
        String oid = MyProvider.idToOid(OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(oid)) {
            String message = "Couldn't find a User in the database for id=" + userId + " oid=" + mbUser.oid;
            MyLog.v(this, message);
            fail(message); 
        }
        assertEquals("User in the database for id=" + userId, 
                mbUser.oid,
                MyProvider.idToOid(OidEnum.USER_OID, userId, 0));
        assertEquals("Account name", mbUser.userName + "/" + origin.getName(), ma.getAccountName());
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getUserId());
        return ma;
    }

    private void waitForContextInitialization() throws ConnectionException {
        try {
            assertTrue(MyContextHolder.getBlocking(null, this).isReady());
        } catch (InterruptedException e) {
            throw new ConnectionException("Error at addAccountFromMbUser", e);
        }
    }
}
