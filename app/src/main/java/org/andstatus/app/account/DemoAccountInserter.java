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

import org.andstatus.app.account.MyAccount.CredentialsVerificationStatus;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DemoConversationInserter;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.util.List;

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
        addAccount(demoData.conversationAccount2ActorOid, demoData.conversationAccount2Name,
                "", demoData.conversationOriginType);
    }

    private MyAccount addAccount(String actorOid, String accountNameString, String avatarUrl, OriginType originType) {
        if (firstAccountActorOid == null) {
            firstAccountActorOid = actorOid;
        }
        demoData.checkDataPath();
        AccountName accountName = AccountName.fromAccountName(myContext, accountNameString);
        MyLog.v(this, "Adding account " + accountName);
        assertTrue("Name '" + accountNameString + "' is valid for " + originType, accountName.isValid);
        assertEquals("Origin for '" + accountNameString + "' account created",
                accountName.getOrigin().getOriginType(), originType);
        long accountActorId_existing = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID,
                accountName.getOrigin().getId(), actorOid);
        Actor actor = Actor.fromOriginAndActorOid(accountName.getOrigin(), actorOid);
        actor.setUsername(accountName.getUsername());
        actor.avatarUrl = avatarUrl;
        if (!actor.isWebFingerIdValid() && UrlUtils.hasHost(actor.origin.getUrl())) {
            actor.setWebFingerId(actor.getUsername() + "@" + actor.origin.getHost());
        }
        assertTrue("No WebfingerId " + actor, actor.isWebFingerIdValid());
        actor.setCreatedDate(MyLog.uniqueCurrentTimeMS());
        MyAccount ma = addAccountFromActor(actor);
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
                actor.getWebFingerId(), MyQuery.actorIdToWebfingerId(actor.actorId));
        assertEquals("Account is not successfully verified",
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        assertAccountIsAddedToAccountManager(ma);

        assertEquals("Oid: " + ma.getActor(), actor.oid, ma.getActor().oid);
        assertEquals("Partially defined: " + ma.getActor(), false, ma.getActor().isPartiallyDefined());

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
        List<Account> aa = MyAccounts.getAccounts(myContext.context());
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

    private MyAccount addAccountFromActor(@NonNull Actor actor) {
        MyAccount.Builder builder = MyAccount.Builder.newOrExistingFromAccountName(myContext,
                actor.getUsername() + "/" + actor.origin.getName(), TriState.TRUE);
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + actor.getUsername(),
                    "sampleUserSecretFor" + actor.getUsername());
        } else {
            builder.setPassword("samplePasswordFor" + actor.getUsername());
        }
        assertTrue("Credentials of " + actor + " are present (origin name=" + actor.origin.getName() + ")",
                builder.getAccount().getCredentialsPresent());
        try {
            builder.onCredentialsVerified(actor, null);
        } catch (ConnectionException e) {
            MyLog.e(this, e);
            fail(e.getMessage());
        }

        assertTrue("Account is persistent " + builder.getAccount(), builder.isPersistent());
        MyAccount ma = builder.getAccount();
        assertEquals("Credentials of " + actor.getUsername() + " successfully verified",
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified());
        long actorId = ma.getActorId();
        assertTrue("Account " + actor.getUsername() + " has ActorId", actorId != 0);
        assertEquals("Account actorOid", ma.getActorOid(), actor.oid);
        String oid = MyQuery.idToOid(myContext.getDatabase(), OidEnum.ACTOR_OID, actorId, 0);
        if (StringUtils.isEmpty(oid)) {
            String message = "Couldn't find an Actor in the database for id=" + actorId + " oid=" + actor.oid;
            MyLog.v(this, message);
            fail(message);
        }
        assertEquals("Actor in the database for id=" + actorId,
                actor.oid,
                MyQuery.idToOid(myContext.getDatabase(), OidEnum.ACTOR_OID, actorId, 0));
        assertEquals("Account name", actor.getUsername() + "/" + actor.origin.getName(), ma.getAccountName());
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.getActorId());
        DemoConversationInserter.getActors().put(actor.oid, actor);
        return ma;
    }

    public void checkDefaultTimelinesForAccounts() {
        for (MyAccount myAccount : MyContextHolder.get().accounts().get()) {
            for (TimelineType timelineType : TimelineType.getDefaultMyAccountTimelineTypes()) {
                if (!myAccount.getConnection().isApiSupported(timelineType.getConnectionApiRoutine())) continue;

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
