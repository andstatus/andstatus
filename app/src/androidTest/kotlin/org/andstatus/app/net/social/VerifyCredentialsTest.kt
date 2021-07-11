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
package org.andstatus.app.net.social

import org.andstatus.app.account.MyAccountBuilder
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.OidEnum
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.http.OAuthClientKeys
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.UriUtils
import org.andstatus.app.util.UrlUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates

class VerifyCredentialsTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)
    private var connection: Connection by Delegates.notNull()
    private var stub: ConnectionStub by Delegates.notNull()
    private var keyStored: String? = null
    private var secretStored: String? = null

    @Before
    fun setUp() {
        stub = ConnectionStub.newFor(DemoData.demoData.twitterTestAccountName)
        connection = stub.connection
        val data = stub.getHttp().data
        data.originUrl = UrlUtils.fromString("https://twitter.com")
        data.oauthClientKeys = OAuthClientKeys.Companion.fromConnectionData(data)
        keyStored = data.oauthClientKeys?.getConsumerKey()
        secretStored = data.oauthClientKeys?.getConsumerSecret()
        if (data.oauthClientKeys?.areKeysPresent() == false) {
            data.oauthClientKeys?.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232")
        }
    }

    @After
    fun tearDown() {
        if (!keyStored.isNullOrEmpty()) {
            stub.getHttp().data.oauthClientKeys?.setConsumerKeyAndSecret(keyStored, secretStored)
        }
    }

    @Test
    fun testVerifyCredentials() {
        stub.addResponse(org.andstatus.app.test.R.raw.verify_credentials_twitter)
        val actor = connection.verifyCredentials(Optional.empty()).get()
        assertEquals("Actor's oid is actorOid of this account", DemoData.demoData.twitterTestAccountActorOid, actor.oid)
        val origin: Origin =  myContext.origins.firstOfType(OriginType.TWITTER)
        val builder: MyAccountBuilder = MyAccountBuilder.Companion.fromAccountName(stub.getData().getAccountName())
        builder.onCredentialsVerified(actor).onFailure { e -> AssertionError("Failed: $e" ) }
        assertTrue("Account is persistent", builder.isPersistent())
        val actorId = builder.myAccount.actorId
        assertTrue("Account " + actor.getUsername() + " has ActorId", actorId != 0L)
        assertEquals("Account actorOid", builder.myAccount.getActorOid(), actor.oid)
        assertEquals("Actor in the database for id=$actorId",
                actor.oid,
                MyQuery.idToOid( myContext, OidEnum.ACTOR_OID, actorId, 0))
        val noteOid = "383296535213002752"
        val noteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, noteOid)
        assertTrue("Note not found", noteId != 0L)
        val actorIdM = MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, noteId)
        assertEquals("Note not by " + actor.getUsername() + " found", actorId, actorIdM)
        assertEquals("Note permalink at twitter",
                "https://" + origin.fixUriForPermalink(UriUtils.fromUrl(origin.url)).host
                        + "/"
                        + builder.myAccount.username + "/status/" + noteOid,
                origin.getNotePermalink(noteId))
    }
}
