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
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.TestSuite
import org.andstatus.app.origin.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountNameTest {
    private val myContext: MyContext = TestSuite.initializeWithAccounts(this)

    @Test
    fun preserveInputValues() {
        assertForOneOrigin(Origin.EMPTY)
        assertForOneOrigin(DemoData.demoData.getPumpioConversationOrigin())
        assertForOneOrigin(DemoData.demoData.getGnuSocialOrigin())
    }

    private fun assertForOneOrigin(origin: Origin) {
        assertInputValues(origin, "")
        assertInputValues(origin, "user1")
        assertInputValues(origin, "user1" + if (origin.hasHost()) "@" + origin.getHost() else "")
    }

    private fun assertInputValues(origin: Origin, uniqueName: String?) {
        val accountName: AccountName = AccountName.fromOriginAndUniqueName(origin, uniqueName)
        assertEquals(origin, accountName.origin)
        val expected = if (uniqueName.isNullOrEmpty()) "" else
            if (uniqueName.contains("@")) uniqueName else uniqueName +
                if (origin.hasHost()) "@" + origin.getHost() else ""
        assertEquals(expected, accountName.getUniqueName())
    }

    @Test
    fun testUniqueAccountName() {
        val accountName1: AccountName = AccountName.Companion.fromAccountName(myContext, "someTester/Pump.io")
        assertEquals(accountName1.toString(), accountName1.origin.name, "Pump.io")
        val accountName2: AccountName = AccountName.Companion.fromAccountName(myContext, "someTester/PumpioTest")
        assertEquals(accountName2.toString(), accountName2.origin.name, "PumpioTest")
    }

    @Test
    fun testNonIcannTld() {
        val origin = DemoData.demoData.getMyAccount(DemoData.demoData.activityPubTestAccountName).origin
        val nonIcannTld = "marek22k@social.dn42"
        val accountName: AccountName = AccountName.fromOriginAndUniqueName(origin, nonIcannTld)
        assertTrue(accountName.toString(), accountName.isValid)
    }
}
