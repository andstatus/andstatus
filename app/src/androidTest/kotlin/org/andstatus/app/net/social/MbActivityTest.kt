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
package org.andstatus.app.net.social

import org.andstatus.app.context.TestSuite
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MbActivityTest {

    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testEmpty() {
        val empty: AActivity = AActivity.Companion.EMPTY
        Assert.assertEquals(empty.toString(), true, empty.isEmpty)
        Assert.assertEquals(Actor.EMPTY, empty.accountActor)
        Assert.assertEquals(Actor.EMPTY, empty.getActor())
        Assert.assertEquals(Actor.EMPTY, empty.getAuthor())
        Assert.assertEquals(empty.toString(), Actor.EMPTY, empty.getObjActor())
        Assert.assertEquals(Note.Companion.EMPTY, empty.getNote())
        Assert.assertEquals(AActivity.Companion.EMPTY, empty.getActivity())
        Assert.assertEquals(AObjectType.EMPTY, empty.getObjectType())
    }
}
