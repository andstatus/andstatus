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

import android.accounts.Account;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
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

import java.util.List;

import static org.andstatus.app.context.DemoData.demoData;
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
        addAccount(demoData.PUMPIO_TEST_ACCOUNT_USER_OID, demoData.PUMPIO_TEST_ACCOUNT_NAME,
                "", OriginType.PUMPIO);
        addAccount(demoData.TWITTER_TEST_ACCOUNT_USER_OID, demoData.TWITTER_TEST_ACCOUNT_NAME,
                "", OriginType.TWITTER);
        addAccount(demoData.GNUSOCIAL_TEST_ACCOUNT_USER_OID, demoData.GNUSOCIAL_TEST_ACCOUNT_NAME,
                demoData.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL, OriginType.GNUSOCIAL);
        addAccount(demoData.GNUSOCIAL_TEST_ACCOUNT2_USER_OID, demoData.GNUSOCIAL_TEST_ACCOUNT2_NAME,
                "", OriginType.GNUSOCIAL);
        addAccount(demoData.MASTODON_TEST_ACCOUNT_USER_OID, demoData.MASTODON_TEST_ACCOUNT_NAME,
                demoData.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL, OriginType.MASTODON);
        addAccount(demoData.CONVERSATION_ACCOUNT_USER_OID, demoData.CONVERSATION_ACCOUNT_NAME,
                demoData.CONVERSATION_ACCOUNT_AVATAR_URL, demoData.CONVERSATION_ORIGIN_TYPE);
        addAccount(demoData.CONVERSATION_ACCOUNT2_USER_OID, demoData.CONVERSATION_ACCOUNT2_NAME,
                "", demoData.CONVERSATION_ORIGIN_TYPE);
    }

    private MyAccount addAccount(String userOid, String accountNameString, String avatarUrl, OriginType originType) {
        if (firstAccountUserOid == null) {
            firstAccountUserOid = userOid;
        }
        demoData.checkDataPath();
        AccountName accountName = AccountName.fromAccountName(myContext, accountNameString);
        MyLog.v(this, "Adding account " + accountName);
        assertTrue("Name '" + accountNameString + "' is valid for " + originType, accountName.isValid());
        assertEquals("Origin for '" + accountNameString + "' account created", accountName.getOrigin().getOriginType(), originType);
        long accountUserId_existing = MyQuery.oidToId(myContext.getDatabase(), OidEnum.USER_OID,
                accountName.getOrigin().getId(), userOid);
        MbUser mbUser = MbUser.fromOriginAndUserOid(accountName.getOrigin(), userOid);
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
        List<Account> aa = PersistentAccounts.getAccounts(myContext.context());
        MyAccount ma = null;
        for (android.accounts.Account account : aa) {
            ma = MyAccount.Builder.fromAndroidAccount(myContext, account).getAccount();
            if (maExpected.getAccountName().equals(ma.getAccountName())) {
                break;
            }
        }
        assertEquals("MyAccount was not found in AccountManager among " + aa.size() + " accounts.",
                maExpected, ma);
    }

    private MyAccount addAccountFromMbUser(@NonNull MbUser mbUser) {
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(myContext,
                mbUser.getUserName() + "/" + mbUser.origin.getName(), TriState.TRUE);
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + mbUser.getUserName(),
                    "sampleUserSecretFor" + mbUser.getUserName());
        } else {
            builder.setPassword("samplePasswordFor" + mbUser.getUserName());
        }
        assertTrue("Credentials of " + mbUser + " are present (origin name=" + mbUser.origin.getName() + ")",
                builder.getAccount().getCredentialsPresent());
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
        assertEquals("Account name", mbUser.getUserName() + "/" + mbUser.origin.getName(), ma.getAccountName());
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getUserId());
        DemoConversationInserter.getUsers().put(mbUser.oid, mbUser);
        return ma;
    }

    public void checkDefaultTimelinesForAccounts() {
        for (MyAccount myAccount : MyContextHolder.get().persistentAccounts().list()) {
            for (TimelineType timelineType : TimelineType.getDefaultMyAccountTimelineTypes()) {
                if (!myAccount.getConnection().isApiSupported(timelineType.getConnectionApiRoutine())) continue;

                long count = 0;
                StringBuilder logMsg =new StringBuilder(myAccount.toString());
                I18n.appendWithSpace(logMsg, timelineType.toString());
                for (Timeline timeline : MyContextHolder.get().persistentTimelines().values()) {
                    if (timeline.getMyAccount().equals(myAccount) && timeline.getTimelineType().equals(timelineType)
                            && !timeline.hasSearchQuery()) {
                        count++;
                        I18n.appendWithSpace(logMsg, timeline.toString());
                    }
                }
                assertEquals(logMsg.toString(), 1, count);
            }
        }
    }

}
