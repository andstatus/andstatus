/*
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

package org.andstatus.app.account;

import android.text.TextUtils;

import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DemoAccountInserter {
    private MyContext myContext;
    private String firstAccountUserOid = null;

    public DemoAccountInserter(MyContext myContext) {
        this.myContext = myContext;
    }

    public void insert() {
        addAccount(DemoData.PUMPIO_TEST_ACCOUNT_USER_OID, DemoData.PUMPIO_TEST_ACCOUNT_NAME,
                "", OriginType.PUMPIO);
        addAccount(DemoData.TWITTER_TEST_ACCOUNT_USER_OID, DemoData.TWITTER_TEST_ACCOUNT_NAME,
                "", OriginType.TWITTER);
        addAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT_USER_OID, DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME,
                DemoData.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL, OriginType.GNUSOCIAL);
        addAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT2_USER_OID, DemoData.GNUSOCIAL_TEST_ACCOUNT2_NAME,
                "", OriginType.GNUSOCIAL);
        addAccount(DemoData.MASTODON_TEST_ACCOUNT_USER_OID, DemoData.MASTODON_TEST_ACCOUNT_NAME,
                DemoData.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL, OriginType.MASTODON);
        addAccount(DemoData.CONVERSATION_ACCOUNT_USER_OID, DemoData.CONVERSATION_ACCOUNT_NAME,
                DemoData.CONVERSATION_ACCOUNT_AVATAR_URL, DemoData.CONVERSATION_ORIGIN_TYPE);
        addAccount(DemoData.CONVERSATION_ACCOUNT2_USER_OID, DemoData.CONVERSATION_ACCOUNT2_NAME,
                "", DemoData.CONVERSATION_ORIGIN_TYPE);
    }

    private MyAccount addAccount(String userOid, String accountNameString, String avatarUrl, OriginType originType) {
        if (firstAccountUserOid == null) {
            firstAccountUserOid = userOid;
        }
        DemoData.checkDataPath();
        AccountName accountName = AccountName.fromAccountName(myContext, accountNameString);
        MyLog.v(this, "Adding account " + accountName);
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
        assertTrue("Account " + userOid + " is persistent", ma.isValid());
        assertTrue("Account UserOid", ma.getUserOid().equalsIgnoreCase(userOid));
        assertTrue("Account is successfully verified", ma.getCredentialsVerified() == CredentialsVerificationStatus.SUCCEEDED);

        assertAccountIsAddedToAccountManager(ma);

        MbUser mbUser2 = ma.toPartialUser();
        assertEquals("Oid: " + mbUser2, mbUser.oid, mbUser2.oid);
        assertEquals("Partially defined: " + mbUser2, true, mbUser2.isPartiallyDefined());

        return ma;
    }

    private void assertAccountIsAddedToAccountManager(MyAccount maExpected) {
        android.accounts.Account[] aa = PersistentAccounts.getAccounts(myContext.context());
        MyAccount ma = null;
        for (android.accounts.Account account : aa) {
            ma = MyAccount.Builder.fromAndroidAccount(myContext, account).getAccount();
            if (maExpected.getAccountName().equals(ma.getAccountName())) {
                break;
            }
        }
        assertEquals("MyAccount was not found in AccountManager among " + aa.length + " accounts.",
                maExpected, ma);
    }

    private MyAccount addAccountFromMbUser(MbUser mbUser) {
        Origin origin = myContext.persistentOrigins().fromId(mbUser.originId);
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(myContext, mbUser.getUserName() + "/" + origin.getName(), TriState.TRUE);
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.getUserName(), "sampleUserSecretFor" + mbUser.getUserName());
        } else {
            builder.setPassword("samplePasswordFor" + mbUser.getUserName());
        }
        assertTrue("Credentials of " + mbUser + " are present (origin name=" + origin.getName() + ")", builder.getAccount().getCredentialsPresent());
        try {
            builder.onCredentialsVerified(mbUser, null);
        } catch (ConnectionException e) {
            MyLog.e(this, e);
            fail(e.getMessage());
        }

        assertTrue("Account is persistent " + builder.getAccount(), builder.isPersistent());
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
        DemoConversationInserter.getUsers().put(mbUser.oid, mbUser);
        return ma;
    }

    public static void checkDefaultTimelinesForAccounts() {
        for (MyAccount myAccount : MyContextHolder.get().persistentAccounts().list()) {
            for (TimelineType timelineType : TimelineType.getDefaultMyAccountTimelineTypes()) {
                long count = 0;
                StringBuilder logMsg =new StringBuilder(myAccount.toString());
                I18n.appendWithSpace(logMsg, timelineType.toString());
                for (Timeline timeline : MyContextHolder.get().persistentTimelines().values()) {
                    if (timeline.getMyAccount().equals(myAccount) && timeline.getTimelineType().equals(timelineType)) {
                        count++;
                        I18n.appendWithSpace(logMsg, timeline.toString());
                    }
                }
                assertEquals(logMsg.toString(), 1, count);
            }
        }
    }

}
