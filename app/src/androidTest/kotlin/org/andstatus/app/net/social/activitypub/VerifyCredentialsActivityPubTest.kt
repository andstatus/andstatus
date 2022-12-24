/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social.activitypub

import org.andstatus.app.account.AccountName
import org.andstatus.app.account.MyAccountBuilder
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.ActorEndpointType
import org.andstatus.app.net.social.ConnectionStub
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UriUtilsTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.properties.Delegates

class VerifyCredentialsActivityPubTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var stub: ConnectionStub by Delegates.notNull()

    @Before
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        val origin: Origin = myContext.origins.fromName(DemoData.demoData.activityPubTestOriginName)
        val accountName: AccountName = AccountName.Companion.fromOriginAndUniqueName(origin, UNIQUE_NAME_IN_ORIGIN)
        stub = ConnectionStub.newFor(MyAccountBuilder.Companion.fromAccountName(accountName).myAccount)
    }

    @Test
    fun verifyCredentials() {
        stub.http.addResponse(org.andstatus.app.test.R.raw.activitypub_whoami_pleroma)
        val actor = stub.connection.verifyCredentials(UriUtils.toDownloadableOptional(ACTOR_OID)).get()
        Assert.assertEquals("Actor's oid is actorOid of this account", ACTOR_OID, actor.oid)
        val builder: MyAccountBuilder = MyAccountBuilder.Companion.fromAccountName(stub.data.getAccountName())
        builder.onCredentialsVerified(actor)
        Assert.assertTrue("Account is persistent", builder.isPersistent())
        val actorId = builder.myAccount.actorId
        Assert.assertTrue("Account " + actor.getUsername() + " has ActorId", actorId != 0L)
        Assert.assertEquals("Account actorOid", "https://pleroma.site/users/AndStatus", actor.oid)
        Assert.assertEquals(
            "Actor in the database for id=$actorId",
            actor.oid,
            MyQuery.idToOid(myContext, OidEnum.ACTOR_OID, actorId, 0)
        )
        assertActor(actor)
        val stored: Actor =
            Actor.Companion.loadFromDatabase(stub.data.getOrigin().myContext, actorId, { Actor.EMPTY }, false)
        assertActor(stored)
    }

    private fun assertActor(actor: Actor) {
        Assert.assertEquals("Username", "AndStatus", actor.getUsername())
        Assert.assertEquals("Name", "AndStatus", actor.getRealName())
        Assert.assertEquals(
            "Summary",
            "AndStatus - Open Source multiple accounts client for multiple social networks for Android<br><a href=\"http://andstatus.org/\">http://andstatus.org/</a>",
            actor.getSummary()
        )
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox", actor)
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_OUTBOX, "https://pleroma.site/users/AndStatus/outbox", actor)
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_PROFILE, "https://pleroma.site/users/AndStatus", actor)
        UriUtilsTest.assertEndpoint(
            ActorEndpointType.API_FOLLOWING,
            "https://pleroma.site/users/AndStatus/following",
            actor
        )
        UriUtilsTest.assertEndpoint(
            ActorEndpointType.API_FOLLOWERS,
            "https://pleroma.site/users/AndStatus/followers",
            actor
        )
        UriUtilsTest.assertEndpoint(ActorEndpointType.API_SHARED_INBOX, "https://pleroma.site/inbox", actor)
        UriUtilsTest.assertEndpoint(ActorEndpointType.BANNER, "https://pleroma.site/images/banner.png", actor)
        Assert.assertEquals(
            "Avatar URL",
            "https://pleroma.site/media/c5f60f06-6620-46b6-b676-f9f4571b518e/bfa1745b8c221225cc6551805d9eaa8bebe5f36fc1856b4924bcfda5d620334d.png",
            actor.getAvatarUrl()
        )
        Assert.assertEquals("Profile URL", "https://pleroma.site/users/AndStatus", actor.getProfileUrl())
    }

    companion object {
        val ACTOR_OID: String = "https://pleroma.site/users/AndStatus"
        val UNIQUE_NAME_IN_ORIGIN: String = "AndStatus@pleroma.site"
    }
}
