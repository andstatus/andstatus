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

package org.andstatus.app.net.social.activitypub;

import org.andstatus.app.account.AccountName;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.UriUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.util.UriUtilsTest.assertEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerifyCredentialsActivityPubTest {
    final static String ACTOR_OID = "https://pleroma.site/users/AndStatus";
    final static String UNIQUE_NAME_IN_ORIGIN = "AndStatus@pleroma.site";
    private ConnectionMock mock;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
        Origin origin = myContextHolder.getNow().origins().fromName(demoData.activityPubTestOriginName);
        AccountName accountName = AccountName.fromOriginAndUniqueName(origin, UNIQUE_NAME_IN_ORIGIN);
        mock = ConnectionMock.newFor(MyAccount.Builder.fromAccountName(accountName).getAccount());
    }

    @Test
    public void verifyCredentials() throws IOException {
        mock.addResponse(org.andstatus.app.tests.R.raw.activitypub_whoami_pleroma);

        Actor actor = mock.connection.verifyCredentials(UriUtils.toDownloadableOptional(ACTOR_OID)).get();
        assertEquals("Actor's oid is actorOid of this account", ACTOR_OID, actor.oid);

        MyAccount.Builder builder = MyAccount.Builder.fromAccountName(mock.getData().getAccountName());
        builder.onCredentialsVerified(actor);
        assertTrue("Account is persistent", builder.isPersistent());
        long actorId = builder.getAccount().getActorId();
        assertTrue("Account " + actor.getUsername() + " has ActorId", actorId != 0);
        assertEquals("Account actorOid", "https://pleroma.site/users/AndStatus" , actor.oid);
        assertEquals("Actor in the database for id=" + actorId,
                actor.oid,
                MyQuery.idToOid(myContextHolder.getNow(), OidEnum.ACTOR_OID, actorId, 0));
        assertActor(actor);

        Actor stored = Actor.loadFromDatabase(mock.getData().getOrigin().myContext, actorId, () -> Actor.EMPTY, false);
        assertActor(stored);
    }

    private void assertActor(Actor actor) {
        assertEquals("Username", "AndStatus", actor.getUsername());
        assertEquals("Name", "AndStatus", actor.getRealName());
        assertEquals("Summary", "AndStatus - Open Source multiple accounts client for multiple social networks for Android<br><a href=\"http://andstatus.org/\">http://andstatus.org/</a>", actor.getSummary());

        assertEndpoint(ActorEndpointType.API_INBOX, "https://pleroma.site/users/AndStatus/inbox", actor);
        assertEndpoint(ActorEndpointType.API_OUTBOX, "https://pleroma.site/users/AndStatus/outbox", actor);
        assertEndpoint(ActorEndpointType.API_PROFILE, "https://pleroma.site/users/AndStatus", actor);
        assertEndpoint(ActorEndpointType.API_FOLLOWING, "https://pleroma.site/users/AndStatus/following", actor);
        assertEndpoint(ActorEndpointType.API_FOLLOWERS, "https://pleroma.site/users/AndStatus/followers", actor);
        assertEndpoint(ActorEndpointType.API_SHARED_INBOX, "https://pleroma.site/inbox", actor);
        assertEndpoint(ActorEndpointType.BANNER, "https://pleroma.site/images/banner.png", actor);

        assertEquals("Avatar URL", "https://pleroma.site/media/c5f60f06-6620-46b6-b676-f9f4571b518e/bfa1745b8c221225cc6551805d9eaa8bebe5f36fc1856b4924bcfda5d620334d.png",
                actor.getAvatarUrl());
        assertEquals("Profile URL", "https://pleroma.site/users/AndStatus", actor.getProfileUrl());
    }

}
