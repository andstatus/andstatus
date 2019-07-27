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

package org.andstatus.app.util;

import android.content.Context;

import org.andstatus.app.R;
import org.andstatus.app.context.TestSuite;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class StringUtilsTest2 {
    Context context = null;

    @Before
    public void setUp() throws Exception {
        context = TestSuite.initialize(this);
    }

    @Test
    public void test() {
        assertEquals("a", StringUtils.notNull("a"));
        assertEquals("", StringUtils.notNull(""));
        assertEquals("", StringUtils.notNull(null));
    }

    @Test
    public void testFormat() {
        // No error
        assertEquals("“Person” 的關注者", StringUtils.format(context, R.string.format_test_quotes, "Person"));
        assertEquals("“Person” 的關注者", String.format(context.getText(R.string.format_test_quotes).toString(), "Person"));

        // Error if not catch it
        assertEquals("“%1”的朋友 Person", StringUtils.format(context, R.string.format_test_quotes2, "Person"));
        String error = "";
        try {
            assertEquals("“Person”的朋友", String.format(context.getText(R.string.format_test_quotes2).toString(), "Person"));
        } catch (java.util.UnknownFormatConversionException e) {
            error = e.getMessage();
        }
        assertEquals("Conversion = '”'", error);

        String noContext = StringUtils.format(null, R.string.format_test_quotes2, "Person");
        assertThat(noContext, containsString("Error showing resourceId="));
        assertThat(noContext, containsString("Person"));

        String noContextAndParameter = StringUtils.format(null, R.string.format_test_quotes2);
        assertThat(noContextAndParameter, containsString("Error showing resourceId="));

        String noParameter = StringUtils.format(context, R.string.format_test_quotes2);
        assertEquals("“%1”的朋友", noParameter);

        String unneededParameter = StringUtils.format(context, R.string.app_name, "Person");
        assertEquals("AndStatus", unneededParameter);
    }
}