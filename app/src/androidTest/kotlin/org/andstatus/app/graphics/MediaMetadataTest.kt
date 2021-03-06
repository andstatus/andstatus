/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.graphics

import org.andstatus.app.context.DemoData
import org.andstatus.app.context.TestSuite
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MediaMetadataTest {
    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testFromFilePath() {
        val path = DemoData.demoData.localVideoTestUri.toString()
        MediaMetadata.Companion.fromFilePath(path).let {
            Assert.assertEquals(
                "For path '$path' returned: " +
                        (if (it.isFailure) MyLog.getStackTrace(it.cause).replace("\n", " ")
                        else it.toString()),
                180, it.getOrElse(MediaMetadata.EMPTY).height.toLong()
            )
        }
    }
}
