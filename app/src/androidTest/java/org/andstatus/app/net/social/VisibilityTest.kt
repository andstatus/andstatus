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

import org.junit.Assert
import org.junit.Test

class VisibilityTest {
    @Test
    fun testAddition() {
        for (value in Visibility.values()) {
            assertAdd(value, value, value)
            assertAdd(Visibility.PUBLIC_AND_TO_FOLLOWERS, Visibility.PUBLIC_AND_TO_FOLLOWERS, value)
            assertAdd(value, Visibility.UNKNOWN, value)
            if (value != Visibility.UNKNOWN && value != Visibility.NOT_PUBLIC_NEEDS_CLARIFICATION) {
                assertAdd(value, value, Visibility.PRIVATE)
                assertAdd(value, value, Visibility.NOT_PUBLIC_NEEDS_CLARIFICATION)
            }
        }
        assertAdd(Visibility.PUBLIC_AND_TO_FOLLOWERS, Visibility.TO_FOLLOWERS, Visibility.PUBLIC)
        assertAdd(Visibility.TO_FOLLOWERS, Visibility.PRIVATE, Visibility.TO_FOLLOWERS)
        assertAdd(Visibility.PUBLIC, Visibility.PRIVATE, Visibility.PUBLIC)
    }

    private fun assertAdd(expected: Visibility?, a: Visibility?, b: Visibility?) {
        Assert.assertEquals("Test: $a add $b", expected, a.add(b))
        Assert.assertEquals("Test: $b add $a", expected, b.add(a))
    }
}