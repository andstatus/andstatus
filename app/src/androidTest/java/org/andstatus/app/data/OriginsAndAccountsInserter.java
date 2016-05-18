/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import android.content.pm.PackageManager.NameNotFoundException;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContextForTest;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.SslModeEnum;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginTest;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

public class OriginsAndAccountsInserter extends InstrumentationTestCase {
    MyContextForTest myContext;
    private static String firstAccountUserOid = null;
    
    public OriginsAndAccountsInserter(MyContextForTest myContextForTest) {
        myContext = myContextForTest;
    }

    public void insert() throws NameNotFoundException, ConnectionException {
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        addOrigins();
        addAccounts();
        MyPreferences.onPreferencesChanged();
        MyContextHolder.initialize(null, this);
        MyServiceManager.setServiceUnavailable();
        assertTrue("Context is ready", MyContextHolder.get().isReady());
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
    }

    private void addOrigins() {
        OriginTest.createOneOrigin(OriginType.TWITTER, TestSuite.TWITTER_TEST_ORIGIN_NAME,
                TestSuite.getTestOriginHost(TestSuite.TWITTER_TEST_ORIGIN_NAME),
                true, SslModeEnum.SECURE, false, true, true);
        OriginTest.createOneOrigin(OriginType.PUMPIO,
                TestSuite.PUMPIO_ORIGIN_NAME,
                TestSuite.getTestOriginHost(TestSuite.PUMPIO_ORIGIN_NAME),
                true, SslModeEnum.SECURE, true, true, true);
        OriginTest.createOneOrigin(OriginType.GNUSOCIAL, TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME,
                TestSuite.getTestOriginHost(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME),
                true, SslModeEnum.SECURE, true, true, true);
        String additionalOriginName = TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME + "ins";
        OriginTest.createOneOrigin(OriginType.GNUSOCIAL, additionalOriginName,
                TestSuite.getTestOriginHost(additionalOriginName),
                true, SslModeEnum.INSECURE, true, false, true);
        myContext.persistentOrigins().initialize();
    }

    private void addAccounts() throws ConnectionException {
        addAccount(TestSuite.PUMPIO_TEST_ACCOUNT_USER_OID, TestSuite.PUMPIO_TEST_ACCOUNT_NAME,
                "", OriginType.PUMPIO);
        addAccount(TestSuite.TWITTER_TEST_ACCOUNT_USER_OID, TestSuite.TWITTER_TEST_ACCOUNT_NAME,
                "", OriginType.TWITTER);
        addAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT_USER_OID, TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME,
                TestSuite.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL, OriginType.GNUSOCIAL);
        addAccount(TestSuite.GNUSOCIAL_TEST_ACCOUNT2_USER_OID, TestSuite.GNUSOCIAL_TEST_ACCOUNT2_NAME,
                "", OriginType.GNUSOCIAL);
        addAccount(TestSuite.CONVERSATION_ACCOUNT_USER_OID, TestSuite.CONVERSATION_ACCOUNT_NAME,
                TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL, TestSuite.CONVERSATION_ORIGIN_TYPE);
        addAccount(TestSuite.CONVERSATION_ACCOUNT2_USER_OID, TestSuite.CONVERSATION_ACCOUNT2_NAME,
                "", TestSuite.CONVERSATION_ORIGIN_TYPE);
    }

    private MyAccount addAccount(String userOid, String accountNameString, String avatarUrl, OriginType originType) throws ConnectionException {
        if (firstAccountUserOid == null) {
            firstAccountUserOid = userOid;
        }
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
        AccountName accountName = AccountName.fromAccountName(myContext, accountNameString);
        assertTrue("Name '" + accountNameString + "' is valid for " + originType, accountName.isValid());
        assertEquals("Origin for '" + accountNameString + "' account created", accountName.getOrigin().getOriginType(), originType);
        long accountUserId_existing = MyQuery.oidToId(myContext.getDatabase(), OidEnum.USER_OID,
                accountName.getOrigin().getId(), userOid);
        MbUser mbUser = MbUser.fromOriginAndUserOid(accountName.getOrigin().getId(), userOid);
        mbUser.setUserName(accountName.getUsername());
        mbUser.avatarUrl = avatarUrl;
        MyAccount ma = addAccountFromMbUser(mbUser);
        long accountUserId = ma.getUserId();
        String msg = "AccountUserId for '" + accountNameString + ", (first: '" + firstAccountUserOid + "')";
        if (accountUserId_existing == 0 && !userOid.contains(firstAccountUserOid)) {
            assertTrue(msg + " != 1", accountUserId != 1);
        } else {
            assertTrue(msg + " != 0", accountUserId != 0);
        }
        assertTrue("Account " + userOid + " is persistent", ma != null);
        assertTrue("Account UserOid", ma.getUserOid().equalsIgnoreCase(userOid));
        assertTrue("Account is successfully verified", ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED);
        return ma;
    }

    private MyAccount addAccountFromMbUser(MbUser mbUser) throws ConnectionException {
        Origin origin = myContext.persistentOrigins().fromId(mbUser.originId);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(myContext, mbUser.getUserName() + "/" + origin.getName(), TriState.TRUE);
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.getUserName(), "sampleUserSecretFor" + mbUser.getUserName());
        } else {
            builder.setPassword("samplePasswordFor" + mbUser.getUserName());
        }
        assertTrue("Credentials of " + mbUser + " are present (origin name=" + origin.getName() + ")", builder.getAccount().getCredentialsPresent());
        builder.onCredentialsVerified(mbUser, null);

        assertTrue("Account is persistent", builder.isPersistent());
        MyAccount ma = builder.getAccount();
        assertEquals("Credentials of " + mbUser.getUserName() + " successfully verified", 
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long userId = ma.getUserId();
        assertTrue("Account " + mbUser.getUserName() + " has UserId", userId != 0);
        assertEquals("Account UserOid", ma.getUserOid(), mbUser.oid);
        String oid = MyQuery.idToOid(myContext.getDatabase(), OidEnum.USER_OID, userId, 0);
        if (TextUtils.isEmpty(oid)) {
            String message = "Couldn't find a User in the database for id=" + userId + " oid=" + mbUser.oid;
            MyLog.v(this, message);
            fail(message); 
        }
        assertEquals("User in the database for id=" + userId, 
                mbUser.oid,
                MyQuery.idToOid(myContext.getDatabase(), OidEnum.USER_OID, userId, 0));
        assertEquals("Account name", mbUser.getUserName() + "/" + origin.getName(), ma.getAccountName());
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getUserId());
        ConversationInserter.users.put(mbUser.oid, mbUser);
        return ma;
    }
}
