/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline.meta

import org.andstatus.app.context.ActivityTest
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.context.TestSuite
import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class ManageTimelinesTest : ActivityTest<ManageTimelines>() {

    override fun getActivityClass(): Class<ManageTimelines> {
        return ManageTimelines::class.java
    }

    @Before
    fun setUp() {
        TestSuite.initializeWithData(this)
    }

    @Test
    fun testActivityOpened() {
        val expectedCount: Int = myContextHolder.getNow().timelines.values().size
        TestSuite.waitForListLoaded(activity, expectedCount)
        Assert.assertTrue(
            "Timelines shown: " + activity.getListAdapter().count,
            activity.getListAdapter().count == expectedCount
        )
    }
}
