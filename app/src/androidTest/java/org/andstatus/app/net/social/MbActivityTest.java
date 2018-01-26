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
        AActivity empty = AActivity.EMPTY;
        assertEquals(empty.toString(), true, empty.isEmpty());
        assertEquals(Actor.EMPTY, empty.accountActor);
        assertEquals(Actor.EMPTY, empty.getActor());
        assertEquals(Actor.EMPTY, empty.getAuthor());
        assertEquals(empty.toString(), Actor.EMPTY, empty.getObjActor());
        assertEquals(Note.EMPTY, empty.getMessage());
        assertEquals(AActivity.EMPTY, empty.getActivity());
        assertEquals(AObjectType.EMPTY, empty.getObjectType());
    }
}
