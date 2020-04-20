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

import org.junit.Test;

import static org.andstatus.app.net.social.Visibility.NOT_PUBLIC_NEEDS_CLARIFICATION;
import static org.andstatus.app.net.social.Visibility.PRIVATE;
import static org.andstatus.app.net.social.Visibility.PUBLIC;
import static org.andstatus.app.net.social.Visibility.PUBLIC_AND_TO_FOLLOWERS;
import static org.andstatus.app.net.social.Visibility.TO_FOLLOWERS;
import static org.andstatus.app.net.social.Visibility.UNKNOWN;
import static org.junit.Assert.assertEquals;

public class VisibilityTest {

    @Test
    public void testAddition() {
        for (Visibility value : Visibility.values()) {
            assertAdd(value, value, value);
            assertAdd(PUBLIC_AND_TO_FOLLOWERS, PUBLIC_AND_TO_FOLLOWERS, value);
            assertAdd(value, UNKNOWN, value);
            if (value != UNKNOWN && value != NOT_PUBLIC_NEEDS_CLARIFICATION) {
                assertAdd(value, value, PRIVATE);
                assertAdd(value, value, NOT_PUBLIC_NEEDS_CLARIFICATION);
            }
        }
        assertAdd(PUBLIC_AND_TO_FOLLOWERS, TO_FOLLOWERS, PUBLIC);
        assertAdd(TO_FOLLOWERS, PRIVATE, TO_FOLLOWERS);
        assertAdd(PUBLIC, PRIVATE, PUBLIC);
    }

    private void assertAdd(Visibility expected, Visibility a, Visibility b) {
        assertEquals("Test: " + a + " add " + b, expected, a.add(b));
        assertEquals("Test: " + b + " add " + a, expected, b.add(a));
    }
}
