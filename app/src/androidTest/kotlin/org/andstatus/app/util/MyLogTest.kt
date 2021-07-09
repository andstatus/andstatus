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
package org.andstatus.app.util

import android.util.Log
import org.andstatus.app.context.TestSuite
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MyLogTest {

    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testObjTag() {
        var tag: Any? = this
        Assert.assertEquals("MyLogTest", MyStringBuilder.Companion.objToTag(tag))
        tag = this.javaClass
        Assert.assertEquals("MyLogTest", MyStringBuilder.Companion.objToTag(tag))
        tag = "Other tag"
        Assert.assertEquals(tag.toString(), MyStringBuilder.Companion.objToTag(tag))
        tag = null
        Assert.assertEquals("(null)", MyStringBuilder.Companion.objToTag(tag))
    }

    @Test
    fun testLogFilename() {
        val method = "testLogFilename"
        val isLogEnabled = MyLog.isLogToFileEnabled()
        MyLog.setLogToFile(true)
        Assert.assertFalse(MyLog.getLogFilename().isEmpty())
        MyLog.v(this, method)
        val file = MyLog.getFileInLogDir(MyLog.getLogFilename(), true)  ?: throw IllegalStateException("No file")
        Assert.assertTrue(file.exists())
        MyLog.setLogToFile(false)
        Assert.assertTrue(MyLog.getLogFilename().isEmpty())
        Assert.assertTrue(file.delete())
        MyLog.v(this, method)
        Assert.assertEquals("", MyLog.getLogFilename())
        Assert.assertFalse(file.exists())
        if (isLogEnabled) {
            MyLog.setLogToFile(true)
        }
    }

    @Test
    fun testUniqueDateTimeFormatted() {
        var string1: String? = ""
        var string2: String? = ""
        for (ind in 0..19) {
            val time1 = MyLog.uniqueCurrentTimeMS()
            string1 = MyLog.uniqueDateTimeFormatted()
            val time2 = MyLog.uniqueCurrentTimeMS()
            string2 = MyLog.uniqueDateTimeFormatted()
            Assert.assertTrue("Time:$time1", time2 > time1)
            Assert.assertFalse(string1, string1.contains("SSS"))
            Assert.assertFalse(string1, string1.contains("HH"))
            Assert.assertTrue(string1, string1.contains("-"))
            Assert.assertFalse(string1, string1 == string2)
        }
        MyLog.v("testUniqueDateTimeFormatted", "$string1 $string2")
    }

    private class LazyClass {
        override fun toString(): String {
            lazyTest = this.javaClass.simpleName
            return "from" + this.javaClass.simpleName
        }
    }

    @Test
    fun testLazyLogging() {
        val level1 = MyLog.getMinLogLevel()
        try {
            MyLog.setMinLogLevel(Log.DEBUG)
            val unchanged = "unchanged"
            lazyTest = unchanged
            MyLog.v(this) { lazyTest = "modified"; "" }
            Assert.assertEquals(unchanged, lazyTest)
            val lazyObject = LazyClass()
            MyLog.v(this) { "LazyObject: $lazyObject" }
            Assert.assertEquals(unchanged, lazyTest)
            MyLog.setMinLogLevel(Log.VERBOSE)
            val modified = "modified"
            MyLog.v(this) { lazyTest = "modified"; "" }
            Assert.assertEquals(modified, lazyTest)
            MyLog.v(this) { "LazyObject: $lazyObject" }
            Assert.assertEquals(LazyClass::class.java.simpleName, lazyTest)
        } finally {
            level1.onSuccess { obj: Int -> MyLog.setMinLogLevel(obj) }
        }
    }

    @Test
    fun testGetStackTrace() {
        val st: String = MyLog.getStackTrace(Exception("TheTest"))
        assertThat(st, CoreMatchers.containsString("testGetStackTrace"))
        assertThat(st, CoreMatchers.containsString("\n"))
        val stOneLine = st.replace("\n"," ")
        assertThat(stOneLine, not(CoreMatchers.containsString("\n")))
    }

    companion object {
        @Volatile
        private var lazyTest: String = ""
    }
}
