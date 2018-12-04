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

import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.TextMediaType;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MyHtmlTest {

    private static final String SAMPLE1 = "This note\nhas newline";
    private static final String SAMPLE1_FOR_VIEW = "This note<br>\nhas newline";
    private static final String SAMPLE1_HTML = "<p dir=\"ltr\">" + SAMPLE1_FOR_VIEW + "</p>\n";

    public static final String SAMPLE2_PLAIN = "@auser@example.com This is a link " +
            "https://example.com/page1.html#something\nThe second line";
    private static final String SAMPLE2_FOR_VIEW = "@auser@example.com This is a link " +
            "<a href=\"https://example.com/page1.html#something\">https://example.com/page1.html#something</a><br>\n" +
            "The second line";
    private static final String SAMPLE2_HTML1 = "<p dir=\"ltr\">" + SAMPLE2_FOR_VIEW + "</p>";
    private static final String SAMPLE2_HTML2 = SAMPLE2_HTML1 + "\n";

    @Before
    public void setUp() throws Exception {
        TestSuite.initialize(this);
    }

    @Test
    public void testPrepareForView() {
        assertEquals(SAMPLE1_FOR_VIEW, MyHtml.prepareForView(SAMPLE1_HTML));
        assertEquals(SAMPLE2_FOR_VIEW, MyHtml.prepareForView(SAMPLE2_HTML1));
        assertEquals(SAMPLE2_FOR_VIEW, MyHtml.prepareForView(SAMPLE2_HTML2));
    }

    @Test
    public void testHtmlify() {
        assertEquals(SAMPLE1_HTML, MyHtml.htmlify(SAMPLE1));
        assertEquals(SAMPLE2_HTML2, MyHtml.htmlify(SAMPLE2_PLAIN));
    }

    @Test
    public void testHasHtmlMarkup() {
        assertFalse(MyHtml.hasHtmlMarkup(SAMPLE1));
        assertTrue(MyHtml.hasHtmlMarkup(SAMPLE1_HTML));
    }

    @Test
    public void testBodyToSearch() {
        final String text1 = "@somebody,  [This]  .       is'\n a\t; \"normalised {text}\""
                + "@user@domain.com, #<a href=\"#some\">AndStatus</a>. (!gnusocial)";
        final String text2 = "@somebody,  [This]  .       is'\n a\t; \"normalised {text}\""
                + "@user@domain.com, #AndStatus. (!gnusocial)";
        final String result1 = ",somebody,@somebody,This,is,a,normalised,text,user,@user@domain.com,AndStatus,#AndStatus,"
                + "gnusocial,!gnusocial,";
        assertEquals(result1, MyHtml.normalizeWordsForSearch(text2));
        assertEquals(result1.toLowerCase(), MyHtml.getContentToSearch(text2));
        assertEquals(result1.toLowerCase(), MyHtml.getContentToSearch(text1));

        final String text3 = "<p>Hello! Does anyone use?! <a href=\"https://mstdn.io/tags/andstatus\" rel=\"nofollow " +
                "noopener\" target=\"_blank\">#<span>Andstatus</span></a>? How do I get to recognize my instance?</p>";
        final String result2 = ",hello,does,anyone,use,andstatus,#andstatus,how,do,i,get,to,recognize,my,instance,";
        assertEquals(result2, MyHtml.getContentToSearch(text3));

        String text4 = "Uh someone on XYZ just said \"I found a Something in the XYZ fridge.\" @ABVinskeep we''re gonna need an investigation of this.";
        String result4 = ",uh,someone,on,xyz,just,said,i,found,a,something,in,the,xyz,fridge,abvinskeep,@abvinskeep,we,re,gonna,need,an,investigation,of,this,";
        assertEquals(result4, MyHtml.getContentToSearch(text4));
    }

    @Test
    public void testToPlainText() {
        String linebreaks = "This note\nhas \nnewline";
        String singleLine = "This note has newline";

        String text1 = "This note<br >has <br>newline ";
        assertEquals(linebreaks, MyHtml.toPlainText(text1));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text1));

        String text2 = "This note<br />has <p>newline</p>";
        assertEquals(linebreaks, MyHtml.toPlainText(text2));
        assertEquals(linebreaks, MyHtml.toPlainText(linebreaks));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text2));

        String text3 = "This <a href='#as'>note</a><br />has <br><br>newline";
        String doubleLinebreaks = "This note\nhas \n\nnewline";
        assertEquals(doubleLinebreaks, MyHtml.toPlainText(text3));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text3));

        String text3_2 = "This <a href='#as'>note</a><br />has <br><br><br>newline";
        assertEquals(doubleLinebreaks, MyHtml.toPlainText(text3_2));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text3_2));

        String text3_3 = "This note\nhas \n\n\nnewline";
        assertEquals(doubleLinebreaks, MyHtml.toPlainText(text3_3));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text3_3));

        String text4 = "<p>This <a href='#as'>note</a></p><br />has <p>newline</p>";
        String double2Linebreaks = "This note\n\nhas \nnewline";
        assertEquals(double2Linebreaks, MyHtml.toPlainText(text4));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text4));

        String text5 = "<p>This <a href='#as'>note</a></p>has <p>newline</p>";
        assertEquals(linebreaks, MyHtml.toPlainText(text5));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text5));

        String text6 = "<p>This <a href='#as'>note</a></p>   has <p>newline</p> ";
        assertEquals(linebreaks, MyHtml.toPlainText(text6));
        assertEquals(singleLine, MyHtml.toCompactPlainText(text6));

        assertEquals("I'm working", MyHtml.toPlainText("I&apos;m working"));
    }

    @Test
    public void testStoreLineBreaks() {
        String text1 = "Today's note<br >has <br>linebreaks ";
        String text2 = "Today's note\nhas \nlinebreaks ";
        String text3 = "Today's note<br >\nhas <br>\nlinebreaks ";

        final String exp1 = text1.trim();
        assertEquals(exp1, MyHtml.toContentStoredAsHtml(text1, TextMediaType.HTML, true));
        assertEquals(exp1, MyHtml.toContentStoredAsHtml(text1, TextMediaType.UNKNOWN, true));

        final String exp2 = "Today's note&lt;br &gt;has &lt;br&gt;linebreaks";
        assertEquals(exp2, MyHtml.toContentStoredAsHtml(text1, TextMediaType.PLAIN, true));
        assertEquals(exp2, MyHtml.toContentStoredAsHtml(text1, TextMediaType.PLAIN, false));

        final String exp3 = "Today's note<br />has <br />linebreaks";
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text1, TextMediaType.HTML, false));
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text1, TextMediaType.UNKNOWN, false));

        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text2, TextMediaType.UNKNOWN, true));
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text2, TextMediaType.PLAIN, true));
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text2, TextMediaType.PLAIN, false));
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text2, TextMediaType.HTML, false));
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text2, TextMediaType.UNKNOWN, false));

        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text3, TextMediaType.HTML, false));
        assertEquals(exp3, MyHtml.toContentStoredAsHtml(text3, TextMediaType.UNKNOWN, false));

        final String exp4 = text2.trim();
        assertEquals(exp4, MyHtml.toContentStoredAsHtml(text2, TextMediaType.HTML, true));

        final String exp5 = text3.trim();
        assertEquals(exp5, MyHtml.toContentStoredAsHtml(text3, TextMediaType.HTML, true));
        assertEquals(exp5, MyHtml.toContentStoredAsHtml(text3, TextMediaType.UNKNOWN, true));

        final String exp6 = "Today's note&lt;br &gt;<br />has &lt;br&gt;<br />linebreaks";
        assertEquals(exp6, MyHtml.toContentStoredAsHtml(text3, TextMediaType.PLAIN, true));
        assertEquals(exp6, MyHtml.toContentStoredAsHtml(text3, TextMediaType.PLAIN, false));

        String text4 = "Today's note\nhas \n\nlinebreaks ";
        final String exp7 = "Today's note<br />has <br /><br />linebreaks";
        assertEquals(exp7, MyHtml.toContentStoredAsHtml(text4, TextMediaType.PLAIN, true));
        assertEquals(exp7, MyHtml.toContentStoredAsHtml(text4, TextMediaType.PLAIN, false));

    }

    @Test
    public void teststripExcessiveLineBreaks() {
        final String twoLineBreaks = "one\n\ntwo";
        assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks(twoLineBreaks));
        assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks("one\n\n\ntwo"));
        assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks("one\n\n\n \ntwo"));
        final String oneLineBreak = "one\ntwo";
        assertEquals(oneLineBreak, MyHtml.stripExcessiveLineBreaks(oneLineBreak));
    }
}
