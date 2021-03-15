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
package org.andstatus.app.account

import android.accounts.Account
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.http.HttpConnectionData
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.junit.Assert

class DemoAccountInserter(private val myContext: MyContext) {
    private var firstAccountActorOid: String? = null
    fun insert() {
        addAccount(DemoData.demoData.pumpioTestAccountActorOid, DemoData.demoData.pumpioTestAccountName,
                "", OriginType.PUMPIO)
        addAccount(DemoData.demoData.twitterTestAccountActorOid, DemoData.demoData.twitterTestAccountName,
                "", OriginType.TWITTER)
        addAccount(DemoData.demoData.gnusocialTestAccountActorOid, DemoData.demoData.gnusocialTestAccountName,
                DemoData.demoData.gnusocialTestAccountAvatarUrl, OriginType.GNUSOCIAL)
        addAccount(DemoData.demoData.gnusocialTestAccount2ActorOid, DemoData.demoData.gnusocialTestAccount2Name,
                "", OriginType.GNUSOCIAL)
        addAccount(DemoData.demoData.mastodonTestAccountActorOid, DemoData.demoData.mastodonTestAccountName,
                DemoData.demoData.gnusocialTestAccountAvatarUrl, OriginType.MASTODON)
        addAccount(DemoData.demoData.conversationAccountActorOid, DemoData.demoData.conversationAccountName,
                DemoData.demoData.conversationAccountAvatarUrl, DemoData.demoData.conversationOriginType)
        addAccount(DemoData.demoData.conversationAccountSecondActorOid, DemoData.demoData.conversationAccountSecondName,
                "", DemoData.demoData.conversationOriginType)
        addAccount(DemoData.demoData.activityPubTestAccountActorOid, DemoData.demoData.activityPubTestAccountName,
                "", OriginType.ACTIVITYPUB)
    }

    private fun addAccount(actorOid: String, accountNameString: String, avatarUrl: String?, originType: OriginType): MyAccount {
        if (firstAccountActorOid == null) {
            firstAccountActorOid = actorOid
        }
        DemoData.demoData.checkDataPath()
        val accountName: AccountName = AccountName.fromAccountName(myContext, accountNameString)
        Assert.assertEquals("Account name created $accountName", accountNameString, accountName.name)
        MyLog.v(this, "Adding account $accountName")
        Assert.assertTrue("Name '$accountNameString' is valid for $originType", accountName.isValid)
        Assert.assertEquals("Origin for '$accountNameString' account created",
                accountName.origin.originType, originType)
        val accountActorId_existing = MyQuery.oidToId(myContext, OidEnum.ACTOR_OID,
                accountName.origin.id, actorOid)
        val actor: Actor = Actor.fromOid(accountName.origin, actorOid)
        actor.withUniqueName(accountName.getUniqueName())
        actor.setAvatarUrl(avatarUrl)
        if (!actor.isWebFingerIdValid() && UrlUtils.hostIsValid(actor.getIdHost())) {
            actor.setWebFingerId(actor.getUsername() + "@" + actor.getIdHost())
        }
        Assert.assertTrue("No WebfingerId $actor", actor.isWebFingerIdValid())
        if (actor.origin.originType === OriginType.ACTIVITYPUB) {
            val basePath = "https://" + actor.getConnectionHost() + "/users/" + actor.getUsername()
            actor.endpoints.add(ActorEndpointType.API_INBOX, "$basePath/inbox")
            actor.endpoints.add(ActorEndpointType.API_OUTBOX, "$basePath/outbox")
            actor.endpoints.add(ActorEndpointType.API_FOLLOWING, "$basePath/following")
            actor.endpoints.add(ActorEndpointType.API_FOLLOWERS, "$basePath/followers")
        }
        actor.setCreatedDate(MyLog.uniqueCurrentTimeMS())
        val ma = addAccountFromActor(actor, accountName)
        val accountActorId = ma.actorId
        val msg = "AccountUserId for '$accountNameString, (first: '$firstAccountActorOid')"
        if (accountActorId_existing == 0L && !actorOid.contains(firstAccountActorOid ?: "")) {
            Assert.assertTrue("$msg != 1", accountActorId != 1L)
        } else {
            Assert.assertTrue("$msg != 0", accountActorId != 0L)
        }
        Assert.assertTrue("Account $actorOid is persistent", ma.isValid)
        Assert.assertTrue("Account actorOid", ma.getActorOid().equals(actorOid, ignoreCase = true))
        Assert.assertEquals("No WebFingerId stored $actor",
                actor.getWebFingerId(), MyQuery.actorIdToWebfingerId(myContext, actor.actorId))
        Assert.assertEquals("Account is not successfully verified",
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified())
        assertAccountIsAddedToAccountManager(ma)
        Assert.assertEquals("Oid: " + ma.actor, actor.oid, ma.actor.oid)
        Assert.assertTrue("Should be fully defined: " + ma.actor, ma.actor.isFullyDefined())
        Assert.assertNotEquals(Timeline.EMPTY, getAutomaticallySyncableTimeline(myContext, ma))
        return ma
    }

    private fun assertAccountIsAddedToAccountManager(maExpected: MyAccount) {
        val aa = AccountUtils.getCurrentAccounts(myContext.context())
        var ma: MyAccount = MyAccount.EMPTY
        for (account in aa) {
            ma = MyAccount.Builder.loadFromAndroidAccount(myContext, account).getAccount()
            if (maExpected.getAccountName() == ma.getAccountName()) {
                break
            }
        }
        Assert.assertEquals("MyAccount was not found in AccountManager among " + aa.size + " accounts.",
                maExpected, ma)
    }

    private fun addAccountFromActor(actor: Actor, accountName: AccountName): MyAccount {
        val builder1: MyAccount.Builder = MyAccount.Builder.fromAccountName(accountName).setOAuth(true)
        if (actor.origin.isOAuthDefault() || actor.origin.canChangeOAuth()) {
            insertTestClientKeys(builder1.getAccount())
        }
        val builder: MyAccount.Builder = MyAccount.Builder.fromAccountName(accountName).setOAuth(true)
        if (builder.getAccount().isOAuth()) {
            builder.setUserTokenWithSecret("sampleUserTokenFor" + actor.uniqueName,
                    "sampleUserSecretFor" + actor.uniqueName)
        } else {
            builder.setPassword("samplePasswordFor" + actor.uniqueName)
        }
        Assert.assertTrue("Credentials of " + actor + " are present, account: " + builder.getAccount(),
                builder.getAccount().getCredentialsPresent())
        val tryMyAccount = builder.onCredentialsVerified(actor)
                .map { it.getAccount() }
        Assert.assertTrue("Success $tryMyAccount", tryMyAccount.isSuccess)
        val ma = tryMyAccount.get()
        Assert.assertTrue("Account is persistent $ma", builder.isPersistent())
        Assert.assertEquals("Credentials of " + actor.getUniqueNameWithOrigin() + " successfully verified",
                CredentialsVerificationStatus.SUCCEEDED, ma.getCredentialsVerified())
        val actorId = ma.actorId
        Assert.assertTrue("Account " + actor.getUniqueNameWithOrigin() + " has ActorId", actorId != 0L)
        Assert.assertEquals("Account actorOid", ma.getActorOid(), actor.oid)
        val oid = MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId, 0)
        if (oid.isEmpty()) {
            val message = "Couldn't find an Actor in the database for id=" + actorId + " oid=" + actor.oid
            MyLog.v(this, message)
            Assert.fail(message)
        }
        Assert.assertEquals("Actor in the database for id=$actorId",
                actor.oid,
                MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId, 0))
        Assert.assertEquals("Account name calculated",
                (if (actor.origin.shouldHaveUrl()) actor.getUsername() + "@" +
                        actor.origin.getAccountNameHost() else actor.uniqueName) +
                        AccountName.ORIGIN_SEPARATOR +
                        actor.origin.getOriginInAccountName(accountName.host), ma.getAccountName())
        Assert.assertEquals("Account name provided", accountName.name, ma.getAccountName())
        val existingAndroidAccount = AccountUtils.getExistingAndroidAccount(accountName)
        Assert.assertEquals("Android account name", accountName.name,
                existingAndroidAccount.map { a: Account -> a.name }.getOrElse("(not found)"))
        Assert.assertEquals("User should be known as this actor $actor", actor.uniqueName, actor.user.getKnownAs())
        Assert.assertEquals("User is not mine $actor", TriState.TRUE, actor.user.isMyUser())
        Assert.assertNotEquals("User is not added $actor", 0, actor.user.userId)
        MyLog.v(this, ma.getAccountName() + " added, id=" + ma.actorId)
        return ma
    }

    private fun insertTestClientKeys(myAccount: MyAccount) {
        val connectionData: HttpConnectionData = HttpConnectionData.fromAccountConnectionData(
                AccountConnectionData.fromMyAccount(myAccount, TriState.UNKNOWN)
        )
        if (!UrlUtils.hasHost(connectionData.originUrl)) {
            connectionData.originUrl = UrlUtils.fromString("https://" + myAccount.actor.getConnectionHost())
        }
        val keys1: OAuthClientKeys = OAuthClientKeys.fromConnectionData(connectionData)
        if (!keys1.areKeysPresent()) {
            val consumerKey = "testConsumerKey" + java.lang.Long.toString(System.nanoTime())
            val consumerSecret = "testConsumerSecret" + java.lang.Long.toString(System.nanoTime())
            keys1.setConsumerKeyAndSecret(consumerKey, consumerSecret)
            val keys2: OAuthClientKeys = OAuthClientKeys.fromConnectionData(connectionData)
            Assert.assertEquals("Keys are loaded for $myAccount", true, keys2.areKeysPresent())
            Assert.assertEquals(consumerKey, keys2.getConsumerKey())
            Assert.assertEquals(consumerSecret, keys2.getConsumerKey())
        }
    }

    companion object {
        fun getAutomaticallySyncableTimeline(myContext: MyContext, myAccount: MyAccount): Timeline {
            val timelineToSync = myContext.timelines()
                    .filter(false, TriState.FALSE, TimelineType.UNKNOWN, myAccount.actor,  Origin.EMPTY)
                    .filter { obj: Timeline -> obj.isSyncedAutomatically() }.findFirst().orElse(Timeline.EMPTY)
            Assert.assertTrue("""
    No syncable automatically timeline for $myAccount
    ${myContext.timelines().values()}
    """.trimIndent(), timelineToSync.isSyncableAutomatically())
            return timelineToSync
        }

        fun assertDefaultTimelinesForAccounts() {
            for (myAccount in  MyContextHolder.myContextHolder.getNow().accounts().get()) {
                for (timelineType in myAccount.actor.getDefaultMyAccountTimelineTypes()) {
                    if (!myAccount.getConnection().hasApiEndpoint(timelineType.connectionApiRoutine)) continue
                    var count: Long = 0
                    val logMsg: StringBuilder = StringBuilder(myAccount.toString())
                    MyStringBuilder.appendWithSpace(logMsg, timelineType.toString())
                    for (timeline in  MyContextHolder.myContextHolder.getNow().timelines().values()) {
                        if (timeline.getActorId() == myAccount.actorId && timeline.timelineType == timelineType &&
                                !timeline.hasSearchQuery()) {
                            count++
                            MyStringBuilder.appendWithSpace(logMsg, timeline.toString())
                        }
                    }
                    Assert.assertEquals("""
    $logMsg
    ${ MyContextHolder.myContextHolder.getNow().timelines().values()}
    """.trimIndent(), 1, count)
                }
            }
        }
    }
}