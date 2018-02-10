/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.origin.Origin;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PersistentAccountsTest {
    @Before
    public void setUp() {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void test() {
        PersistentAccounts accounts = MyContextHolder.get().accounts();

        assertNotEquals(accounts.toString(), MyAccount.EMPTY, accounts.fromWebFingerId(demoData.pumpioTestAccountUsername));
        assertNotEquals(accounts.toString(), MyAccount.EMPTY, accounts.fromWebFingerId(demoData.conversationAccount2Username));
        assertEquals(accounts.toString(), MyAccount.EMPTY, accounts.fromWebFingerId(demoData.conversationAuthorSecondUsername));

        Origin origin = MyContextHolder.get().origins().fromName(demoData.conversationOriginName);
        assertEquals(demoData.conversationOriginName, origin.getName());

        long actorId = MyQuery.oidToId(OidEnum.ACTOR_OID, origin.getId(), demoData.conversationAccount2ActorOid);
        assertEquals(accounts.toString(), accounts.isMeOrMyFriend(actorId), true);
        assertEquals(accounts.toString(), accounts.isMeOrMyFriend(-1), false);
    }

}
