/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.junit.Assert
import org.junit.Test

class I18nTest {
    @Test
    fun testTrimTextAt() {
        // Length of this string is 20 chars:
        val text1 = "Text 'to be trimmed."
        Assert.assertEquals(text1, I18n.trimTextAt(text1, 20))
        Assert.assertEquals(text1, I18n.trimTextAt(text1, 21))
        Assert.assertEquals(text1.substring(0, 19), I18n.trimTextAt(text1, 19))
        Assert.assertEquals(text1.substring(0, 11) + "…", I18n.trimTextAt(text1, 18))
        Assert.assertEquals(text1.substring(0, 8) + "…", I18n.trimTextAt(text1, 11))
        Assert.assertEquals(text1.substring(0, 1) + "…", I18n.trimTextAt(text1, 2))
        Assert.assertEquals(text1.substring(0, 1), I18n.trimTextAt(text1, 1))
        Assert.assertEquals("", I18n.trimTextAt(text1, 0))
        Assert.assertEquals("", I18n.trimTextAt(text1, -1))
    }

    @Test
    fun testLocaleToLanguageAndCountry() {
        Assert.assertEquals("pt", I18n.localeToLanguage("pt-rPT"))
        Assert.assertEquals("PT", I18n.localeToCountry("pt-rPT"))
        Assert.assertEquals("pt", I18n.localeToLanguage("pt"))
        Assert.assertEquals("", I18n.localeToCountry("pt"))
    }

    @Test
    fun testFormatBytes() {
        Assert.assertEquals("0", I18n.formatBytes(0))
        Assert.assertEquals("1B", I18n.formatBytes(1))
        Assert.assertEquals("9837B", I18n.formatBytes(9837))
        Assert.assertEquals("11KB", I18n.formatBytes(11286))
        Assert.assertEquals("18MB", I18n.formatBytes(19112286))
        Assert.assertEquals("33MB", I18n.formatBytes(34578432L))
        Assert.assertEquals("70GB", I18n.formatBytes(75334578432L))
    }
}