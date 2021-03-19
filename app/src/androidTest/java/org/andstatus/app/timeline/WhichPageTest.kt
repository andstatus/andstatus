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
package org.andstatus.app.timeline

import android.os.Bundle
import org.andstatus.app.IntentExtra
import org.andstatus.app.timeline.WhichPage
import org.junit.Assert
import org.junit.Test

/**
 * @author yvolk@yurivolkov.com
 */
class WhichPageTest {
    @Test
    fun testSaveLoad() {
        assertOne(WhichPage.CURRENT)
        assertOne(WhichPage.YOUNGER)
        assertOne(WhichPage.YOUNGEST)
        assertOne(WhichPage.OLDER)
        assertOne(WhichPage.EMPTY)
        var args: Bundle? = null
        Assert.assertEquals(WhichPage.EMPTY, WhichPage.Companion.load(args))
        args = Bundle()
        Assert.assertEquals(WhichPage.EMPTY, WhichPage.Companion.load(args))
        args.putString(IntentExtra.WHICH_PAGE.key, "234")
        Assert.assertEquals(WhichPage.EMPTY, WhichPage.Companion.load(args))
    }

    private fun assertOne(whichPage: WhichPage?) {
        val args = whichPage.toBundle()
        Assert.assertEquals(whichPage, WhichPage.Companion.load(args))
    }
}