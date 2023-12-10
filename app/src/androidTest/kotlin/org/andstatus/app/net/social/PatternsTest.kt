/*
 * Copyright (c) 2023 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.junit.Assert.assertEquals
import org.junit.Test

class PatternsTest {
    @Test
    fun testIsWebFingerIdValid() {
        check("", false)
        check("someUser.", false)
        check("someUser ", false)
        check("some.user", false)
        check("some.user@example.com", true)
        check("so+me.user@example.com", true)
        check("some.us+er@example.com", false)
        check("t131t@identi.ca/PumpIo", false)
        check("t131t@identi.ca", true)
        check("some@example.com.", false)
        check("some@user", false)
        check("someuser@gs.kawa-kun.com", true)
        check("AndStatus@datamost.com", true)
        check("someuser@social.1", false)
        check("someuser@social.r", false)
        check("someuser@social.ru", true)
        check("someuser@social.1d", true)
        check("someuser@social.d1", true)
        check("someuser@social.1d1", true)
        check("someuser@social.12", false)
        check("marek22k@social.dn42", true)
        check("someuser@social.1234", false)
    }

    private fun check(username: String?, valid: Boolean) {
        assertEquals(
            "Username '" + username + "' " + if (valid) "is valid" else "invalid", valid,
            Actor.Companion.isWebFingerIdValid(username)
        )
    }
}
