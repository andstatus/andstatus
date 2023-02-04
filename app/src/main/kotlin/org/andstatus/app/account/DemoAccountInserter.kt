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
import org.andstatus.app.context.DemoData.Companion.demoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ConnectionFactory
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TriState
import org.andstatus.app.util.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

class DemoAccountInserter(private val myContext: MyContext) {
    private var firstAccountActorOid: String? = null
    fun insert() {
        addAccount(
            demoData.pumpioTestAccountActorOid, demoData.pumpioTestAccountName,
            "", OriginType.PUMPIO
        )
        addAccount(
            demoData.twitterTestAccountActorOid, demoData.twitterTestAccountName,
            "", OriginType.TWITTER
        )
        addAccount(
            demoData.gnusocialTestAccountActorOid, demoData.gnusocialTestAccountName,
            demoData.gnusocialTestAccountAvatarUrl, OriginType.GNUSOCIAL
        )
        addAccount(
            demoData.gnusocialTestAccount2ActorOid, demoData.gnusocialTestAccount2Name,
            "", OriginType.GNUSOCIAL
        )
        addAccount(
            demoData.mastodonTestAccountActorOid, demoData.mastodonTestAccountName,
            demoData.gnusocialTestAccountAvatarUrl, OriginType.MASTODON
        )
        addAccount(
            demoData.conversationAccountActorOid, demoData.conversationAccountName,
            demoData.conversationAccountAvatarUrl, demoData.conversationOriginType
        )
        addAccount(
            demoData.conversationAccountSecondActorOid, demoData.conversationAccountSecondName,
            "", demoData.conversationOriginType
        )
        addAccount(
            demoData.activityPubTestAccountActorOid, demoData.activityPubTestAccountName,
            demoData.activityPubTestAccountAvatarUrl, OriginType.ACTIVITYPUB
        )
    }

    private fun addAccount(
        actorOid: String,
        accountNameString: String,
        avatarUrl: String?,
        originType: OriginType
    ): MyAccount {
        if (firstAccountActorOid == null) {
            firstAccountActorOid = actorOid
        }
        demoData.checkDataPath()
        val accountName: AccountName = AccountName.fromAccountName(myContext, accountNameString)
        assertEquals("Account name created $accountName", accountNameString, accountName.name)
        MyLog.v(this, "Adding account $accountName")
        assertTrue("Name '$accountNameString' is valid for $originType", accountName.isValid)
        assertEquals(
            "Origin for '$accountNameString' account created",
            accountName.origin.originType, originType
        )
        val accountActorId_existing = MyQuery.oidToId(
            myContext, OidEnum.ACTOR_OID,
            accountName.origin.id, actorOid
        )
        val actor: Actor = Actor.fromOid(accountName.origin, actorOid)
        actor.withUniqueName(accountName.getUniqueName())
        actor.setAvatarUrl(avatarUrl)
        if (!actor.isWebFingerIdValid() && UrlUtils.hostIsValid(actor.getIdHost())) {
            actor.setWebFingerId(actor.getUsername() + "@" + actor.getIdHost())
        }
        assertTrue("No WebfingerId $actor", actor.isWebFingerIdValid())
        if (actor.origin.originType === OriginType.ACTIVITYPUB) {
            val basePath = "https://" + actor.getConnectionHost() + "/users/" + actor.getUsername()
            actor.endpoints.add(ActorEndpointType.API_INBOX, "$basePath/inbox")
            actor.endpoints.add(ActorEndpointType.API_OUTBOX, "$basePath/outbox")
            actor.endpoints.add(ActorEndpointType.API_FOLLOWING, "$basePath/following")
            actor.endpoints.add(ActorEndpointType.API_FOLLOWERS, "$basePath/followers")
        }
        actor.setCreatedDate(MyLog.uniqueCurrentTimeMS)
        val ma = addAccountFromActor(actor, accountName)
        val accountActorId = ma.actorId
        val msg = "AccountUserId for '$accountNameString, (first: '$firstAccountActorOid')"
        if (accountActorId_existing == 0L && !actorOid.contains(firstAccountActorOid ?: "")) {
            assertTrue("$msg != 1", accountActorId != 1L)
        } else {
            assertTrue("$msg != 0", accountActorId != 0L)
        }
        assertTrue("Account $actorOid is persistent", ma.isValid)
        assertTrue("Account actorOid", ma.actorOid.equals(actorOid, ignoreCase = true))
        assertEquals(
            "No WebFingerId stored $actor",
            actor.webFingerId, MyQuery.actorIdToWebfingerId(myContext, actor.actorId)
        )
        assertEquals(
            "Account is not successfully verified",
            AccessStatus.SUCCEEDED, ma.accessStatus
        )
        assertAccountIsAddedToAccountManager(ma)
        assertEquals("Oid: " + ma.actor, actor.oid, ma.actor.oid)
        assertTrue("Should be fully defined: " + ma.actor, ma.actor.isFullyDefined())
        assertNotEquals(Timeline.EMPTY, getAutomaticallySyncableTimeline(myContext, ma))
        return ma
    }

    private fun assertAccountIsAddedToAccountManager(maExpected: MyAccount) {
        val aa = AccountUtils.getCurrentAccounts(myContext.context)
        var ma: MyAccount = MyAccount.EMPTY
        for (account in aa) {
            ma = MyAccountBuilder.loadFromAndroidAccount(myContext, account).myAccount
            if (maExpected.accountName == ma.accountName) {
                break
            }
        }
        assertEquals(
            "MyAccount was not found in AccountManager among " + aa.size + " accounts.",
            maExpected, ma
        )
    }

    private fun addAccountFromActor(actor: Actor, accountName: AccountName): MyAccount {
        val builder1: MyAccountBuilder = MyAccountBuilder.fromAccountName(accountName).setOAuth(true)
        if (actor.origin.isOAuthDefault() || actor.origin.canChangeOAuth()) {
            insertTestClientKeys(builder1.myAccount)
        }
        val builder: MyAccountBuilder = MyAccountBuilder.fromAccountName(accountName).setOAuth(true)
        if (builder.myAccount.isOAuth) {
            builder.setAccessTokenWithSecret(
                "sampleAccessTokenFor" + actor.uniqueName,
                "sampleAccessSecretFor" + actor.uniqueName
            )
        } else {
            builder.setPassword("samplePasswordFor" + actor.uniqueName)
        }
        assertTrue(
            "Credentials of " + actor + " are present, account: " + builder.myAccount,
            builder.myAccount.getCredentialsPresent()
        )
        val tryMyAccount = builder.onCredentialsVerified(actor)
            .map { it.myAccount }
        assertTrue("Success $tryMyAccount", tryMyAccount.isSuccess)
        val ma = tryMyAccount.get()
        assertTrue("Account is persistent $ma", builder.isPersistent())
        assertEquals(
            "Credentials of " + actor.getUniqueNameWithOrigin() + " successfully verified",
            AccessStatus.SUCCEEDED, ma.accessStatus
        )
        val actorId = ma.actorId
        assertTrue("Account " + actor.getUniqueNameWithOrigin() + " has ActorId", actorId != 0L)
        assertEquals("Account actorOid", ma.actorOid, actor.oid)
        val oid = MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId, 0)
        if (oid.isEmpty()) {
            val message = "Couldn't find an Actor in the database for id=" + actorId + " oid=" + actor.oid
            MyLog.v(this, message)
            fail(message)
        }
        assertEquals(
            "Actor in the database for id=$actorId",
            actor.oid,
            MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId, 0)
        )
        assertEquals(
            "Account name calculated",
            (if (actor.origin.shouldHaveUrl()) actor.getUsername() + "@" +
                actor.origin.getAccountNameHost() else actor.uniqueName) +
                AccountName.ORIGIN_SEPARATOR +
                actor.origin.getOriginInAccountName(accountName.host), ma.accountName
        )
        assertEquals("Account name provided", accountName.name, ma.accountName)
        val existingAndroidAccount = AccountUtils.getExistingAndroidAccount(accountName)
        assertEquals(
            "Android account name", accountName.name,
            existingAndroidAccount.map { a: Account -> a.name }.getOrElse("(not found)")
        )
        assertEquals("User should be known as this actor $actor", actor.uniqueName, actor.user.getKnownAs())
        assertEquals("User is not mine $actor", TriState.TRUE, actor.user.isMyUser)
        assertNotEquals("User is not added $actor", 0, actor.user.userId)
        MyLog.v(this, ma.accountName + " added, id=" + ma.actorId)
        return ma
    }

    private fun insertTestClientKeys(myAccount: MyAccount) {
        val connection = ConnectionFactory.fromMyAccount(myAccount, TriState.UNKNOWN)
        val httpData = connection.http.data
        if (!UrlUtils.hasHost(httpData.originUrl)) {
            httpData.originUrl = UrlUtils.fromString("https://" + myAccount.actor.getConnectionHost())
        }
        val keys1: OAuthClientKeys = OAuthClientKeys.fromConnectionData(httpData)
        if (!keys1.areKeysPresent()) {
            val consumerKey = "testConsumerKey" + java.lang.Long.toString(System.nanoTime())
            val consumerSecret = "testConsumerSecret" + java.lang.Long.toString(System.nanoTime())
            keys1.setConsumerKeyAndSecret(consumerKey, consumerSecret)
            val keys2: OAuthClientKeys = OAuthClientKeys.fromConnectionData(httpData)
            assertEquals("Keys are loaded for $myAccount", true, keys2.areKeysPresent())
            assertEquals(consumerKey, keys2.getConsumerKey())
            assertEquals(consumerSecret, keys2.getConsumerSecret())
        }
    }

    companion object {
        fun getAutomaticallySyncableTimeline(myContext: MyContext, myAccount: MyAccount): Timeline {
            val timelineToSync = myContext.timelines
                .filter(false, TriState.FALSE, TimelineType.UNKNOWN, myAccount.actor, Origin.EMPTY)
                .filter { obj: Timeline -> obj.isSyncedAutomatically }.findFirst().orElse(Timeline.EMPTY)
            assertTrue(
                "No syncable automatically timeline for $myAccount" +
                    "\n${myContext.timelines.values()}", timelineToSync.isSyncableAutomatically
            )
            return timelineToSync
        }

        fun assertDefaultTimelinesForAccounts() {
            for (myAccount in myContextHolder.getNow().accounts.get()) {
                for (timelineType in myAccount.defaultTimelineTypes) {
                    if (!myAccount.connection.hasApiEndpoint(timelineType.connectionApiRoutine)) continue
                    var count: Long = 0
                    val logMsg: StringBuilder = StringBuilder(myAccount.toString())
                    MyStringBuilder.appendWithSpace(logMsg, timelineType.toString())
                    for (timeline in myContextHolder.getNow().timelines.values()) {
                        if (timeline.actorId == myAccount.actorId && timeline.timelineType == timelineType &&
                            !timeline.hasSearchQuery
                        ) {
                            count++
                            MyStringBuilder.appendWithSpace(logMsg, timeline.toString())
                        }
                    }
                    assertEquals(
                        "$logMsg\n${myContextHolder.getNow().timelines.values()}",
                        1, count
                    )
                }
            }
        }
    }
}
