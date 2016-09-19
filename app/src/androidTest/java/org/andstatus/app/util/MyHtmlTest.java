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

import android.os.Build;
import android.test.InstrumentationTestCase;

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.context.Travis;

@Travis
public class MyHtmlTest extends InstrumentationTestCase {

    public static final String THIS_MESSAGE_HAS_NEWLINE = "This message\nhas newline";
    public static final String THIS_MESSAGE_HAS_NEWLINE_HTML = "<p dir=\"ltr\">This message<br>\nhas newline</p>\n";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void testHtmlify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return; // It's a bit different for that versions...
        }
        String string1 = THIS_MESSAGE_HAS_NEWLINE;
        String string2 = THIS_MESSAGE_HAS_NEWLINE_HTML;
        assertEquals(string2, MyHtml.htmlify(string1));
        assertEquals(string2, MyHtml.htmlify(string2));
        assertEquals("<p dir=\"ltr\">@auser@example.com This is a link " +
                "<a href=\"https://example.com/page1.html#something\">https://example.com/page1.html#something</a><br>\n" +
                        "The second line</p>\n",
                MyHtml.htmlify("@auser@example.com This is a link https://example.com/page1.html#something\nThe second line"));
    }

    public void testHasHtmlMarkup() {
        assertFalse(MyHtml.hasHtmlMarkup(THIS_MESSAGE_HAS_NEWLINE));
        assertTrue(MyHtml.hasHtmlMarkup(THIS_MESSAGE_HAS_NEWLINE_HTML));
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
