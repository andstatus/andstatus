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

package org.andstatus.app.net.social;

import org.andstatus.app.context.TestSuite;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MbActivityTest {
    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
    }

    @Test
    public void testEmpty() {
        MbActivity empty = MbActivity.EMPTY;
        assertEquals(empty.toString(), true, empty.isEmpty());
        assertEquals(MbUser.EMPTY, empty.accountUser);
        assertEquals(MbUser.EMPTY, empty.getActor());
        assertEquals(MbUser.EMPTY, empty.getAuthor());
        assertEquals(empty.toString(), MbUser.EMPTY, empty.getUser());
        assertEquals(MbMessage.EMPTY, empty.getMessage());
        assertEquals(MbActivity.EMPTY, empty.getActivity());
        assertEquals(MbObjectType.EMPTY, empty.getObjectType());
    }
}
