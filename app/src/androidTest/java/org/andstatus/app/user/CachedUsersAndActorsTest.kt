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
package org.andstatus.app.user

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class CachedUsersAndActorsTest {
    @Before
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun test() {
        val users: CachedUsersAndActors =  MyContextHolder.myContextHolder.getNow().users()
        Assert.assertTrue(users.toString(), users.size() > 4)
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().fromName(DemoData.demoData.conversationOriginName)
        Assert.assertEquals(DemoData.demoData.conversationOriginName, origin.name)
        val actor: Actor = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountSecondName).actor
        Assert.assertEquals(users.toString(), true, users.isMeOrMyFriend(actor))
        Assert.assertEquals(users.toString(), false, users.isMeOrMyFriend(Actor.EMPTY))
    }
}