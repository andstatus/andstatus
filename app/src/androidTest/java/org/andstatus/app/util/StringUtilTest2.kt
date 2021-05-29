/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.util

import android.content.Context
import org.andstatus.app.R
import org.andstatus.app.context.TestSuite
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.properties.Delegates

class StringUtilTest2 {
    var context: Context by Delegates.notNull()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        context = TestSuite.initialize(this)
    }

    @Test
    fun test() {
        Assert.assertEquals("a", StringUtil.notNull("a"))
        Assert.assertEquals("", StringUtil.notNull(""))
        Assert.assertEquals("", StringUtil.notNull(null))
    }

    @Test
    fun testFormat() {
        // No error
        Assert.assertEquals("“Person” 的關注者", StringUtil.format(context, R.string.format_test_quotes, "Person"))
        Assert.assertEquals("“Person” 的關注者", String.format(context.getText(R.string.format_test_quotes).toString(), "Person"))

        // Error if not catch it
        Assert.assertEquals("“%1”的朋友 Person", StringUtil.format(context, R.string.format_test_quotes2, "Person"))
        var error = ""
        try {
            Assert.assertEquals("“Person”的朋友", String.format(context.getText(R.string.format_test_quotes2).toString(), "Person"))
        } catch (e: UnknownFormatConversionException) {
            error = e.message ?: ""
        }
        Assert.assertEquals("Conversion = '”'", error)
        val noContext = StringUtil.format(null as Context?, R.string.format_test_quotes2, "Person")
        MatcherAssert.assertThat(noContext, CoreMatchers.containsString("Error no context resourceId="))
        MatcherAssert.assertThat(noContext, CoreMatchers.containsString("Person"))
        val noContextAndParameter = StringUtil.format(null as Context?, R.string.format_test_quotes2)
        MatcherAssert.assertThat(noContextAndParameter, CoreMatchers.containsString("Error no context resourceId="))
        val noParameter = StringUtil.format(context, R.string.format_test_quotes2)
        Assert.assertEquals("“%1”的朋友", noParameter)
        val unneededParameter = StringUtil.format(context, R.string.app_name, "Person")
        Assert.assertEquals("AndStatus", unneededParameter)
        val invalidResource = StringUtil.format(context, -12, "Person")
        Assert.assertEquals("Error formatting resourceId=-12 Person", invalidResource)
        val noFormat = StringUtil.format(null, "Person")
        Assert.assertEquals("(no format) Person", noFormat)
    }
}
