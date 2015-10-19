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

package org.andstatus.app.util;

import android.test.InstrumentationTestCase;

public class I18nTest extends InstrumentationTestCase {
    public void testTrimTextAt() {
        // Length of this string is 20 chars:
        String text1 = "Text 'to be trimmed.";
        assertEquals(text1, I18n.trimTextAt(text1, 20));
        assertEquals(text1, I18n.trimTextAt(text1, 21));
        assertEquals(text1.substring(0, 19), I18n.trimTextAt(text1, 19));
        assertEquals(text1.substring(0, 11) + "…", I18n.trimTextAt(text1, 18));
        assertEquals(text1.substring(0, 8) + "…", I18n.trimTextAt(text1, 11));
        assertEquals(text1.substring(0, 1) + "…", I18n.trimTextAt(text1, 2));
        assertEquals(text1.substring(0, 1), I18n.trimTextAt(text1, 1));
        assertEquals("", I18n.trimTextAt(text1, 0));
        assertEquals("", I18n.trimTextAt(text1, -1));
    }

    public void testLocaleToLanguageAndCountry() {
        assertEquals("pt", I18n.localeToLanguage("pt-rPT"));
        assertEquals("PT", I18n.localeToCountry("pt-rPT"));
        assertEquals("pt", I18n.localeToLanguage("pt"));
        assertEquals("", I18n.localeToCountry("pt"));
    }
}
