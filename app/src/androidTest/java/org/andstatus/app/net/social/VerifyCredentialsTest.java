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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.http.OAuthClientKeys;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerifyCredentialsTest {
    private Connection connection;
    private ConnectionMock mock;

    private String keyStored;
    private String secretStored;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);

        mock = ConnectionMock.newFor(demoData.twitterTestAccountName);
        connection = mock.connection;

        HttpConnectionData data = mock.getHttp().data;
        data.originUrl = UrlUtils.fromString("https://twitter.com");
        data.oauthClientKeys = OAuthClientKeys.fromConnectionData(data);
        keyStored = data.oauthClientKeys.getConsumerKey();
        secretStored = data.oauthClientKeys.getConsumerSecret();

        if (!data.oauthClientKeys.areKeysPresent()) {
            data.oauthClientKeys.setConsumerKeyAndSecret("keyForGetTimelineForTw", "thisIsASecret341232");
        }
    }

    @After
    public void tearDown() {
        if (!StringUtil.isEmpty(keyStored)) {
            mock.getHttp().data.oauthClientKeys.setConsumerKeyAndSecret(keyStored, secretStored);
        }
    }

    @Test
    public void testVerifyCredentials() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.verify_credentials_twitter);

        Actor actor = connection.verifyCredentials(Optional.empty());
        assertEquals("Actor's oid is actorOid of this account", demoData.twitterTestAccountActorOid, actor.oid);

        Origin origin = MyContextHolder.get().origins().firstOfType(OriginType.TWITTER);
        MyAccount.Builder builder = MyAccount.Builder.fromAccountName(mock.getData().getAccountName());
        builder.onCredentialsVerified(actor, null);
        assertTrue("Account is persistent", builder.isPersistent());
        long actorId = builder.getAccount().getActorId();
        assertTrue("Account " + actor.getUsername() + " has ActorId", actorId != 0);
        assertEquals("Account actorOid", builder.getAccount().getActorOid(), actor.oid);
        assertEquals("Actor in the database for id=" + actorId,
                actor.oid,
                MyQuery.idToOid(MyContextHolder.get(), OidEnum.ACTOR_OID, actorId, 0));

        String noteOid = "383296535213002752";
        long noteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(), noteOid) ;
        assertTrue("Note not found", noteId != 0);
        long actorIdM = MyQuery.noteIdToActorId(NoteTable.AUTHOR_ID, noteId);
        assertEquals("Note not by " + actor.getUsername() + " found", actorId, actorIdM);

        assertEquals("Note permalink at twitter",
                "https://" + origin.fixUriForPermalink(UriUtils.fromUrl(origin.getUrl())).getHost()
                        + "/"
                        + builder.getAccount().getUsername() + "/status/" + noteOid,
                origin.getNotePermalink(noteId));
    }
}
