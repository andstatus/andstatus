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
    public static final String THIS_MESSAGE_HAS_NEWLINE_PREPARED_FOR_VIEW =
            "This message<br>\nhas newline";
    public static final String THIS_MESSAGE_HAS_NEWLINE_HTML = "<p dir=\"ltr\">" +
            THIS_MESSAGE_HAS_NEWLINE_PREPARED_FOR_VIEW + "</p>";
    public static final String HTMLIFIED_STRING_PREPARED_FOR_VIEW = "@auser@example.com This is a link " +
            "<a href=\"https://example.com/page1.html#something\">https://example.com/page1.html#something</a><br>\n" +
            "The second line";
    public static final String HTMLIFIED_STRING = "<p dir=\"ltr\">" +
            HTMLIFIED_STRING_PREPARED_FOR_VIEW + "</p>";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initialize(this);
    }

    public void testPrepareForView() {
        assertEquals(THIS_MESSAGE_HAS_NEWLINE_PREPARED_FOR_VIEW,
                MyHtml.prepareForView(THIS_MESSAGE_HAS_NEWLINE_HTML));
        assertEquals(HTMLIFIED_STRING_PREPARED_FOR_VIEW, MyHtml.prepareForView(HTMLIFIED_STRING));
    }

    public void testHtmlify() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return; // It's a bit different for that versions...
        }
        String string1 = THIS_MESSAGE_HAS_NEWLINE;
        String string2 = THIS_MESSAGE_HAS_NEWLINE_HTML;
        assertEquals(string2, MyHtml.htmlify(string1));
        assertEquals(string2, MyHtml.htmlify(string2));
        assertEquals(HTMLIFIED_STRING,
                MyHtml.htmlify("@auser@example.com This is a link https://example.com/page1.html#something\nThe second line"));
    }

    public void testBodyToSearch() {
        final String text1 = "@somebody,  [This]         is'\n a\t; \"normalised {text}\""
                + "@user@domain.com, #<a href=\"#some\">AndStatus</a> (!gnusocial)";
        final String text2 = "@somebody,  [This]         is'\n a\t; \"normalised {text}\""
                + "@user@domain.com, #AndStatus (!gnusocial)";
        final String text3 = ",somebody,@somebody,This,is,a,normalised,text,user,@user@domain.com,AndStatus,#AndStatus,"
                + "gnusocial,!gnusocial,";
        assertEquals(text3, MyHtml.normalizeWordsForSearch(text2));
        assertEquals(text3.toLowerCase(), MyHtml.getBodyToSearch(text2));
        assertEquals(text3.toLowerCase(), MyHtml.getBodyToSearch(text1));
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
