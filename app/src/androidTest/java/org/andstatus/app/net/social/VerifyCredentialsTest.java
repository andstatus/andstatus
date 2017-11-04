/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.support.test.InstrumentationRegistry;
import android.text.TextUtils;

import org.andstatus.app.account.AccountDataReaderEmpty;
import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.http.HttpConnectionMock;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.RawResourceUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerifyCredentialsTest {
    private Connection connection;
    private HttpConnectionMock httpConnection;

    private String keyStored;
    private String secretStored;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);

        TestSuite.setHttpConnectionMockClass(HttpConnectionMock.class);
        OriginConnectionData connectionData = OriginConnectionData.fromAccountName(AccountName.fromOriginAndUserName(
                MyContextHolder.get().persistentOrigins().fromName(demoData.TWITTER_TEST_ORIGIN_NAME),
                demoData.TWITTER_TEST_ACCOUNT_USERNAME),
                TriState.UNKNOWN);
        connectionData.setAccountUser(demoData.getAccountUserByOid(demoData.TWITTER_TEST_ACCOUNT_USER_OID));
        connectionData.setDataReader(new AccountDataReaderEmpty());
        connection = connectionData.newConnection();
        httpConnection = (HttpConnectionMock) connection.http;

        httpConnection.data.originUrl = UrlUtils.fromString("https://twitter.com");
        httpConnection.data.oauthClientKeys = OAuthClientKeys.fromConnectionData(httpConnection.data);
        keyStored = httpConnection.data.oauthClientKeys.getConsumerKey();
        secretStored = httpConnection.data.oauthClientKeys.getConsumerSecret();

        if (!httpConnection.data.oauthClientKeys.areKeysPresent()) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232");
        }
        TestSuite.setHttpConnectionMockClass(null);
    }

    @After
    public void tearDown() throws Exception {
        if (!TextUtils.isEmpty(keyStored)) {
            httpConnection.data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);        
        }
    }

    @Test
    public void testVerifyCredentials() throws IOException {
        String jso = RawResourceUtils.getString(InstrumentationRegistry.getInstrumentation().getContext(),
                org.andstatus.app.tests.R.raw.verify_credentials_twitter);
        httpConnection.setResponse(jso);

        MbUser mbUser = connection.verifyCredentials();
        assertEquals("User's oid is user oid of this account", demoData.TWITTER_TEST_ACCOUNT_USER_OID, mbUser.oid);

        Origin origin = MyContextHolder.get().persistentOrigins().firstOfType(OriginType.TWITTER);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(
                MyContextHolder.get(), demoData.TWITTER_TEST_ACCOUNT_NAME +
                "/" + origin.getName(), TriState.TRUE);
        builder.onCredentialsVerified(mbUser, null);
        assertTrue("Account is persistent", builder.isPersistent());
        long userId = builder.getAccount().getUserId();
        assertTrue("Account " + mbUser.getUserName() + " has UserId", userId != 0);
        assertEquals("Account UserOid", builder.getAccount().getUserOid(), mbUser.oid);
        assertEquals("User in the database for id=" + userId,
                mbUser.oid,
                MyQuery.idToOid(OidEnum.USER_OID, userId, 0));

        String msgOid = "383296535213002752";
        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, origin.getId(), msgOid) ;
        assertTrue("Message not found", msgId != 0);
        long userIdM = MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, msgId);
        assertEquals("Message not by " + mbUser.getUserName() + " found", userId, userIdM);

        assertEquals("Message permalink at twitter",
                "https://" + origin.fixUriforPermalink(UriUtils.fromUrl(origin.getUrl())).getHost()
                        + "/"
                        + builder.getAccount().getUsername() + "/status/" + msgOid,
                origin.messagePermalink(msgId));
    }
}
