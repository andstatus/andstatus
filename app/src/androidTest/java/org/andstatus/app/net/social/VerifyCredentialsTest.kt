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

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
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
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.*

class VerifyCredentialsTest {
    private var connection: Connection? = null
    private var mock: ConnectionMock? = null
    private var keyStored: String? = null
    private var secretStored: String? = null
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
        mock = ConnectionMock.Companion.newFor(DemoData.demoData.twitterTestAccountName)
        connection = mock.connection
        val data = mock.getHttp().data
        data.originUrl = UrlUtils.fromString("https://twitter.com")
        data.oauthClientKeys = OAuthClientKeys.Companion.fromConnectionData(data)
        keyStored = data.oauthClientKeys.consumerKey
        secretStored = data.oauthClientKeys.consumerSecret
        if (!data.oauthClientKeys.areKeysPresent()) {
            data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232")
        }
    }

    @After
    fun tearDown() {
        if (!keyStored.isNullOrEmpty()) {
            mock.getHttp().data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored)
        }
    }

    @Test
    @Throws(IOException::class)
    fun testVerifyCredentials() {
        mock.addResponse(org.andstatus.app.tests.R.raw.verify_credentials_twitter)
        val actor = connection.verifyCredentials(Optional.empty()).get()
        Assert.assertEquals("Actor's oid is actorOid of this account", DemoData.demoData.twitterTestAccountActorOid, actor.oid)
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().firstOfType(OriginType.TWITTER)
        val builder: MyAccount.Builder = MyAccount.Builder.Companion.fromAccountName(mock.getData().accountName)
        builder.onCredentialsVerified(actor)
        Assert.assertTrue("Account is persistent", builder.isPersistent)
        val actorId = builder.account.actorId
        Assert.assertTrue("Account " + actor.getUsername() + " has ActorId", actorId != 0L)
        Assert.assertEquals("Account actorOid", builder.account.actorOid, actor.oid)
        Assert.assertEquals("Actor in the database for id=$actorId",
                actor.oid,
                MyQuery.idToOid( MyContextHolder.myContextHolder.getNow(), OidEnum.ACTOR_OID, actorId, 0))
        val noteOid = "383296535213002752"
        val noteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.id, noteOid)
        Assert.assertTrue("Note not found", noteId != 0L)
        val actorIdM = MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, noteId)
        Assert.assertEquals("Note not by " + actor.getUsername() + " found", actorId, actorIdM)
        Assert.assertEquals("Note permalink at twitter",
                "https://" + origin.fixUriForPermalink(UriUtils.fromUrl(origin.url)).host
                        + "/"
                        + builder.account.username + "/status/" + noteOid,
                origin.getNotePermalink(noteId))
    }
}