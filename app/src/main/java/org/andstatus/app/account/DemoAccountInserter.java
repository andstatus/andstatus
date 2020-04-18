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

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.util.List;

import io.vavr.control.Try;

import static org.andstatus.app.account.AccountName.ORIGIN_SEPARATOR;
import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DemoAccountInserter {
    private MyContext myContext;
    private String firstAccountActorOid = null;

    public DemoAccountInserter(MyContext myContext) {
        this.myContext = myContext;
    }

    public void insert() {
        addAccount(demoData.pumpioTestAccountActorOid, demoData.pumpioTestAccountName,
                "", OriginType.PUMPIO);
        addAccount(demoData.twitterTestAccountActorOid, demoData.twitterTestAccountName,
                "", OriginType.TWITTER);
        addAccount(demoData.gnusocialTestAccountActorOid, demoData.gnusocialTestAccountName,
                demoData.gnusocialTestAccountAvatarUrl, OriginType.GNUSOCIAL);
        addAccount(demoData.gnusocialTestAccount2ActorOid, demoData.gnusocialTestAccount2Name,
                "", OriginType.GNUSOCIAL);
        addAccount(demoData.mastodonTestAccountActorOid, demoData.mastodonTestAccountName,
                demoData.gnusocialTestAccountAvatarUrl, OriginType.MASTODON);
        addAccount(demoData.conversationAccountActorOid, demoData.conversationAccountName,
                demoData.conversationAccountAvatarUrl, demoData.conversationOriginType);
        addAccount(demoData.conversationAccountSecondActorOid, demoData.conversationAccountSecondName,
                "", demoData.conversationOriginType);
        addAccount(demoData.activityPubTestAccountActorOid, demoData.activityPubTestAccountName,
                "", OriginType.ACTIVITYPUB);
    }

    private MyAccount addAccount(String actorOid, String accountNameString, String avatarUrl, OriginType originType) {
        if (firstAccountActorOid == null) {
            firstAccountActorOid = actorOid;
        }
        demoData.checkDataPath();
        AccountName accountName = AccountName.fromAccountName(myContext, accountNameString);
        assertEquals("Account name created " + accountName, accountNameString, accountName.getName());
        MyLog.v(this, "Adding account " + accountName);
        assertTrue("Name '" + accountNameString + "' is valid for " + originType, accountName.isValid);
        assertEquals("Origin for '" + accountNameString + "' account created",
                accountName.getOrigin().getOriginType(), originType);
        long accountActorId_existing = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID,
                accountName.getOrigin().getId(), actorOid);
        Actor actor = Actor.fromOid(accountName.getOrigin(), actorOid);
        actor.withUniqueName(accountName.getUniqueName());
        actor.setAvatarUrl(avatarUrl);
        if (!actor.isWebFingerIdValid() && UrlUtils.hostIsValid(actor.getIdHost())) {
            actor.setWebFingerId(actor.getUsername() + "@" + actor.getIdHost());
        }
        assertTrue("No WebfingerId " + actor, actor.isWebFingerIdValid());
        if (actor.origin.getOriginType() == OriginType.ACTIVITYPUB) {
            String basePath = "https://" + actor.getConnectionHost() + "/users/" + actor.getUsername();
            actor.endpoints.add(ActorEndpointType.API_INBOX, basePath + "/inbox");
            actor.endpoints.add(ActorEndpointType.API_OUTBOX, basePath + "/outbox");
            actor.endpoints.add(ActorEndpointType.API_FOLLOWING, basePath + "/following");
            actor.endpoints.add(ActorEndpointType.API_FOLLOWERS, basePath + "/followers");
        }
        actor.setCreatedDate(MyLog.uniqueCurrentTimeMS());
        MyAccount ma = addAccountFromActor(actor, accountName);

        long accountActorId = ma.getActorId();
        String msg = "AccountUserId for '" + accountNameString + ", (first: '" + firstAccountActorOid + "')";
        if (accountActorId_existing == 0 && !actorOid.contains(firstAccountActorOid)) {
            assertTrue(msg + " != 1", accountActorId != 1);
        } else {
            assertTrue(msg + " != 0", accountActorId != 0);
        }
        assertTrue("Account " + actorOid + " is persistent", ma.isValid());
        assertTrue("Account actorOid", ma.getActorOid().equalsIgnoreCase(actorOid));
        assertEquals("No WebFingerId stored " + actor,
                actor.getWebFingerId(), MyQuery.actorIdToWebfingerId(myContext, actor.actorId));
        assertEquals("Account is not successfully verified",
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        assertAccountIsAddedToAccountManager(ma);

        assertEquals("Oid: " + ma.getActor(), actor.oid, ma.getActor().oid);
        assertTrue("Should be fully defined: " + ma.getActor(), ma.getActor().isFullyDefined());

        assertNotEquals(Timeline.EMPTY, getAutomaticallySyncableTimeline(myContext, ma));
        return ma;
    }

    @NonNull
    public static Timeline getAutomaticallySyncableTimeline(MyContext myContext, MyAccount myAccount) {
        Timeline timelineToSync = myContext.timelines()
                .filter(false, TriState.FALSE, TimelineType.UNKNOWN, myAccount.getActor(), Origin.EMPTY)
                .filter(Timeline::isSyncedAutomatically).findFirst().orElse(Timeline.EMPTY);
        assertTrue("No syncable automatically timeline for " + myAccount + "\n"
                + myContext.timelines().values(), timelineToSync.isSyncableAutomatically());
        return timelineToSync;
    }

    private void assertAccountIsAddedToAccountManager(MyAccount maExpected) {
        List<Account> aa = AccountUtils.getCurrentAccounts(myContext.context());
        MyAccount ma = null;
        for (android.accounts.Account account : aa) {
            ma = MyAccount.Builder.loadFromAndroidAccount(myContext, account).getAccount();
            if (maExpected.getAccountName().equals(ma.getAccountName())) {
                break;
            }
        }
        assertEquals("MyAccount was not found in AccountManager among " + aa.size() + " accounts.",
                maExpected, ma);
    }

    private MyAccount addAccountFromActor(@NonNull Actor actor, AccountName accountName) {
        MyAccount.Builder builder1 = MyAccount.Builder.fromAccountName(accountName).setOAuth(true);
        if (actor.origin.isOAuthDefault() || actor.origin.canChangeOAuth()) {
            insertTestClientKeys(builder1.getAccount());
        }

        MyAccount.Builder builder = MyAccount.Builder.fromAccountName(accountName).setOAuth(true);
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + actor.getUniqueName(),
                    "sampleUserSecretFor" + actor.getUniqueName());
        } else {
            builder.setPassword("samplePasswordFor" + actor.getUniqueName());
        }
        assertTrue("Credentials of " + actor + " are present, account: " + builder.getAccount(),
        builder.getAccount().getCredentialsPresent());
        Try<MyAccount> tryMyAccount =  builder.onCredentialsVerified(actor).map(MyAccount.Builder::getAccount);
        assertTrue("Success " + tryMyAccount, tryMyAccount.isSuccess());

        MyAccount ma = tryMyAccount.get();
        assertTrue("Account is persistent " + ma, builder.isPersistent());
        assertEquals("Credentials of " + actor.getUniqueNameWithOrigin() + " successfully verified",
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long actorId = ma.getActorId();
        assertTrue("Account " + actor.getUniqueNameWithOrigin() + " has ActorId", actorId != 0);
        assertEquals("Account actorOid", ma.getActorOid(), actor.oid);
        String oid = MyQuery.idToOid(myContext.getDatabase(), OidEnum.ACTOR_OID, actorId, 0);
        if (StringUtil.isEmpty(oid)) {
            String message = "Couldn't find an Actor in the database for id=" + actorId + " oid=" + actor.oid;
            MyLog.v(this, message);
            fail(message);
        }
        assertEquals("Actor in the database for id=" + actorId,
                actor.oid,
                MyQuery.idToOid(myContext.getDatabase(), OidEnum.ACTOR_OID, actorId, 0));
        assertEquals("Account name calculated",
                (actor.origin.shouldHaveUrl()
                        ? actor.getUsername() + "@" + actor.origin.getAccountNameHost()
                        : actor.getUniqueName()) +
                ORIGIN_SEPARATOR +
                actor.origin.getOriginInAccountName(accountName.host), ma.getAccountName());
        assertEquals("Account name provided", accountName.getName(), ma.getAccountName());
        Try<Account> existingAndroidAccount = AccountUtils.getExistingAndroidAccount(accountName);
        assertEquals("Android account name", accountName.getName(),
                existingAndroidAccount.map(a -> a.name).getOrElse("(not found)"));

        assertEquals("User should be known as this actor " + actor, actor.getUniqueName(), actor.user.getKnownAs());
        assertEquals("User is not mine " + actor, TriState.TRUE, actor.user.isMyUser());
        assertNotEquals("User is not added " + actor, 0, actor.user.userId);

        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getActorId());
        return ma;
    }

    private void insertTestClientKeys(MyAccount myAccount) {
        HttpConnectionData connectionData = HttpConnectionData.fromConnectionData(
            AccountConnectionData.fromMyAccount(myAccount, TriState.UNKNOWN)
        );
        if (!UrlUtils.hasHost(connectionData.originUrl)) {
            connectionData.originUrl = UrlUtils.fromString("https://" + myAccount.getActor().getConnectionHost());
        }
        OAuthClientKeys keys1 = OAuthClientKeys.fromConnectionData(connectionData);
        if (!keys1.areKeysPresent()) {
            final String consumerKey = "testConsumerKey" + Long.toString(System.nanoTime());
            final String consumerSecret = "testConsumerSecret" + Long.toString(System.nanoTime());
            keys1.setConsumerKeyAndSecret(consumerKey, consumerSecret);

            OAuthClientKeys keys2 = OAuthClientKeys.fromConnectionData(connectionData);
            assertEquals("Keys are loaded for " + myAccount, true, keys2.areKeysPresent());
            assertEquals(consumerKey, keys2.getConsumerKey());
            assertEquals(consumerSecret, keys2.getConsumerSecret());
        }
    }

    public static void assertDefaultTimelinesForAccounts() {
        for (MyAccount myAccount : MyContextHolder.get().accounts().get()) {
            for (TimelineType timelineType : myAccount.getActor().getDefaultMyAccountTimelineTypes()) {
                if (!myAccount.getConnection().hasApiEndpoint(timelineType.getConnectionApiRoutine())) continue;

                long count = 0;
                StringBuilder logMsg =new StringBuilder(myAccount.toString());
                MyStringBuilder.appendWithSpace(logMsg, timelineType.toString());
                for (Timeline timeline : MyContextHolder.get().timelines().values()) {
                    if (timeline.getActorId() == myAccount.getActorId()
                            && timeline.getTimelineType().equals(timelineType)
                            && !timeline.hasSearchQuery()) {
                        count++;
                        MyStringBuilder.appendWithSpace(logMsg, timeline.toString());
                    }
                }
                assertEquals(logMsg.toString() + "\n" + MyContextHolder.get().timelines().values(), 1, count);
            }
        }
    }

}
