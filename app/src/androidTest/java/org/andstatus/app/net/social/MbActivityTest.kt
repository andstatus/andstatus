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
    @Throws(Exception::class)
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testEmpty() {
        val empty: AActivity = AActivity.Companion.EMPTY
        Assert.assertEquals(empty.toString(), true, empty.isEmpty)
        Assert.assertEquals(Actor.Companion.EMPTY, empty.accountActor)
        Assert.assertEquals(Actor.Companion.EMPTY, empty.actor)
        Assert.assertEquals(Actor.Companion.EMPTY, empty.author)
        Assert.assertEquals(empty.toString(), Actor.Companion.EMPTY, empty.objActor)
        Assert.assertEquals(Note.Companion.EMPTY, empty.note)
        Assert.assertEquals(AActivity.Companion.EMPTY, empty.activity)
        Assert.assertEquals(AObjectType.EMPTY, empty.objectType)
    }
}