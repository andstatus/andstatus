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

import org.andstatus.app.context.TestSuite;

public class MyHtmlTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void testHtmlify() {
        String string1 = "This message\nhas newline";
        String string2 = "This message<br />has newline";
        assertEquals(string2, MyHtml.htmlify(string1));
        assertEquals(string2, MyHtml.htmlify(string2));
    }

    public void testHasHtmlMarkup() {
        String string1 = "This message\nhas newline";
        String string2 = "This message<br />has newline";
        assertFalse(MyHtml.hasHtmlMarkup(string1));
        assertTrue(MyHtml.hasHtmlMarkup(string2));
    }

    public void testFromHtml() {
        String string = "";
        String expected = "This message\nhas \nnewline";
        
        string = "This message<br >has <br>newline ";
        assertEquals(expected, MyHtml.fromHtml(string));
        string = "This message<br />has <p>newline</p>";
        assertEquals(expected, MyHtml.fromHtml(string));
        assertEquals(expected, MyHtml.fromHtml(expected));
        string = "This <a href='#as'>message</a><br />has <br><br>newline";
        assertEquals(expected, MyHtml.fromHtml(string));
        string = "<p>This <a href='#as'>message</a></p><br />has <p>newline</p>";
        assertEquals(expected, MyHtml.fromHtml(string));
        string = "<p>This <a href='#as'>message</a></p>has <p>newline</p>";
        assertEquals(expected, MyHtml.fromHtml(string));
        string = "<p>This <a href='#as'>message</a></p>   has <p>newline</p> ";
        assertEquals(expected, MyHtml.fromHtml(string));
    }
}
