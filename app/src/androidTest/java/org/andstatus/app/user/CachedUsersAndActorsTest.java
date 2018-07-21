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

package org.andstatus.app.user;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CachedUsersAndActorsTest {
    @Before
    public void setUp() {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void test() {
        CachedUsersAndActors users = MyContextHolder.get().users();
        assertTrue(users.toString(), users.size() > 4);

        Origin origin = MyContextHolder.get().origins().fromName(demoData.conversationOriginName);
        assertEquals(demoData.conversationOriginName, origin.getName());

        Actor actor = demoData.getMyAccount(demoData.conversationAccount2Name).getActor();
        assertEquals(users.toString(), true, users.isMeOrMyFriend(actor));
        assertEquals(users.toString(), false, users.isMeOrMyFriend(Actor.EMPTY));
    }

}
