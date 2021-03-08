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
package org.andstatus.app.account

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.origin.Origin
import org.junit.Assert
import org.junit.Before
import org.junit.Test

import kotlin.Throws

class AccountNameTest {
    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initializeWithAccounts(this)
    }

    @Test
    fun preserveInputValues() {
        assertForOneOrigin( Origin.EMPTY)
        assertForOneOrigin(DemoData.demoData.getPumpioConversationOrigin())
        assertForOneOrigin(DemoData.demoData.getGnuSocialOrigin())
    }

    fun assertForOneOrigin(origin: Origin?) {
        assertInputValues(origin, "")
        assertInputValues(origin, "user1")
        assertInputValues(origin, "user1" + if (origin.hasHost()) "@" + origin.getHost() else "")
    }

    fun assertInputValues(origin: Origin?, uniqueName: String?) {
        val accountName: AccountName = AccountName.Companion.fromOriginAndUniqueName(origin, uniqueName)
        Assert.assertEquals(origin, accountName.origin)
        val expected = if (uniqueName.isNullOrEmpty()) "" else if (uniqueName.contains("@")) uniqueName else uniqueName + if (origin.hasHost()) "@" + origin.getHost() else ""
        Assert.assertEquals(expected, accountName.uniqueName)
    }

    @Test
    fun testUniqueAccountName() {
        val accountName1: AccountName = AccountName.Companion.fromAccountName( MyContextHolder.myContextHolder.getNow(), "someTester/Pump.io")
        Assert.assertEquals(accountName1.toString(), accountName1.origin.name, "Pump.io")
        val accountName2: AccountName = AccountName.Companion.fromAccountName( MyContextHolder.myContextHolder.getNow(), "someTester/PumpioTest")
        Assert.assertEquals(accountName2.toString(), accountName2.origin.name, "PumpioTest")
    }
}