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

    private static final String SAMPLE2_FOR_VIEW = "@auser@example.com This is a link " +
            "<a href=\"https://example.com/page1.html#something\">https://example.com/page1.html#something</a><br>\n" +
            "The second line";
    private static final String SAMPLE2_HTML1 = "<p dir=\"ltr\">" + SAMPLE2_FOR_VIEW + "</p>";
    private static final String SAMPLE2_HTML2 = SAMPLE2_HTML1 + "\n";

    private static String twitterBodySent =
            "Testing if and what is escaped in a Tweet:\n" +
            "1. \"less-than\" sign &lt;  and escaped: &amp;lt;\n" +
            "2. \"greater-than\" sign &gt; and escaped: &amp;gt;\n" +
            "3. Ampersand &amp; and escaped: &amp;amp;\n" +
            "4. Apostrophe '\n" +
            "5. br HTML tag: &lt;br /&gt; and without \"/\": &lt;br&gt; ?!";
     public static String twitterBodyToPost =
            "Testing if and what is escaped in a Tweet:\n" +
            "1. \"less-than\" sign &lt;  and escaped: &amp;lt;\n" +
            "2. \"greater-than\" sign &gt; and escaped: &amp;gt;\n" +
            "3. Ampersand &amp; and escaped: &amp;amp;\n" +
            "4. Apostrophe '\n" +
            "5. br HTML tag: &lt;br /&gt; and without \"/\": &lt;br&gt; ?!";
    public static String twitterBodyHtml =
            "Testing if and what is escaped in a Tweet:<br />" +
            "1. \"less-than\" sign &lt;  and escaped: &amp;lt;<br />" +
            "2. \"greater-than\" sign &gt; and escaped: &amp;gt;<br />" +
            "3. Ampersand &amp; and escaped: &amp;amp;<br />" +
            "4. Apostrophe '<br />" +
            "5. br HTML tag: &lt;br /&gt; and without \"/\": &lt;br&gt; ?!";


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
        assertEquals(linebreaks, MyHtml.htmlToPlainText(text1));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text1));

        String text2 = "This note<br />has <p>newline</p>";
        assertEquals(linebreaks, MyHtml.htmlToPlainText(text2));
        assertEquals(linebreaks, MyHtml.htmlToPlainText(linebreaks));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text2));

        String text3 = "This <a href='#as'>note</a><br />has <br><br>newline";
        String doubleLinebreaks = "This note\nhas \n\nnewline";
        assertEquals(doubleLinebreaks, MyHtml.htmlToPlainText(text3));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text3));

        String text3_2 = "This <a href='#as'>note</a><br />has <br><br><br>newline";
        assertEquals(doubleLinebreaks, MyHtml.htmlToPlainText(text3_2));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text3_2));

        String text3_3 = "This note\nhas \n\n\nnewline";
        assertEquals(doubleLinebreaks, MyHtml.htmlToPlainText(text3_3));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text3_3));

        String text4 = "<p>This <a href='#as'>note</a></p><br />has <p>newline</p>";
        String double2Linebreaks = "This note\n\nhas \nnewline";
        assertEquals(double2Linebreaks, MyHtml.htmlToPlainText(text4));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text4));

        String text5 = "<p>This <a href='#as'>note</a></p>has <p>newline</p>";
        assertEquals(linebreaks, MyHtml.htmlToPlainText(text5));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text5));

        String text6 = "<p>This <a href='#as'>note</a></p>   has <p>newline</p> ";
        assertEquals(linebreaks, MyHtml.htmlToPlainText(text6));
        assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text6));

        assertEquals("I'm working", MyHtml.htmlToPlainText("I&apos;m working"));
    }

    @Test
    public void testStoreLineBreaks() {
        String textBr =  "Today's note<br >has <br>linebreaks ";
        String textN =   "Today's note\nhas \nlinebreaks ";
        String textBrN = "Today's note<br >\nhas <br>\nlinebreaks ";

        assertEquals(textBr, MyHtml.toContentStored(textBr, TextMediaType.HTML, true));
        assertEquals(textBr, MyHtml.toContentStored(textBr, TextMediaType.UNKNOWN, true));

        final String expEscaped = "Today's note&lt;br &gt;has &lt;br&gt;linebreaks";
        assertEquals(expEscaped, MyHtml.toContentStored(textBr, TextMediaType.PLAIN, true));
        assertEquals(expEscaped, MyHtml.toContentStored(textBr, TextMediaType.PLAIN, false));

        final String expBr = "Today's note<br />has <br />linebreaks";
        assertEquals(expBr, MyHtml.toContentStored(textBr, TextMediaType.HTML, false));
        assertEquals(expBr, MyHtml.toContentStored(textBr, TextMediaType.UNKNOWN, false));

        assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.UNKNOWN, true));
        assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.UNKNOWN, false));
        assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.PLAIN, true));
        assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.PLAIN, false));

        assertEquals(expBr, MyHtml.toContentStored(textBrN, TextMediaType.HTML, false));
        assertEquals(expBr, MyHtml.toContentStored(textBrN, TextMediaType.UNKNOWN, false));

        String expNoBreaks = "Today's note has linebreaks";
        assertEquals(expNoBreaks, MyHtml.toContentStored(textN, TextMediaType.HTML, false));

        assertEquals(textN, MyHtml.toContentStored(textN, TextMediaType.HTML, true));

        assertEquals(textBrN, MyHtml.toContentStored(textBrN, TextMediaType.HTML, true));
        assertEquals(textBrN, MyHtml.toContentStored(textBrN, TextMediaType.UNKNOWN, true));

        final String expOverEscapedBr = "Today's note&lt;br &gt;<br />has &lt;br&gt;<br />linebreaks";
        assertEquals(expOverEscapedBr, MyHtml.toContentStored(textBrN, TextMediaType.PLAIN, true));
        assertEquals(expOverEscapedBr, MyHtml.toContentStored(textBrN, TextMediaType.PLAIN, false));

        String textNN = "Today's note\nhas \n\nlinebreaks ";
        final String expBrBr = "Today's note<br />has <br /><br />linebreaks";
        assertEquals(expBrBr, MyHtml.toContentStored(textNN, TextMediaType.PLAIN, true));
        assertEquals(expBrBr, MyHtml.toContentStored(textNN, TextMediaType.PLAIN, false));

        assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodySent, TextMediaType.PLAIN_ESCAPED, true));
        assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodySent, TextMediaType.PLAIN_ESCAPED, false));
    }

    @Test
    public void testStripExcessiveLineBreaks() {
        final String twoLineBreaks = "one\n\ntwo";
        assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks(twoLineBreaks));
        assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks("one\n\n\ntwo"));
        assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks("one\n\n\n \ntwo"));
        final String oneLineBreak = "one\ntwo";
        assertEquals(oneLineBreak, MyHtml.stripExcessiveLineBreaks(oneLineBreak));
    }

    @Test
    public void testToPlainEscaped() {

        assertToPlainEscaped(
                "Line one &lt; escaped &gt;\nLine two",
                "Line one &lt; escaped &gt;<br />Line two");

        final String oneLineEscaped = "Less &lt; and escaped &amp;lt; greater &gt;";
        assertToPlainEscaped(oneLineEscaped, oneLineEscaped);

        assertToPlainEscaped(twitterBodyToPost, twitterBodyHtml);

    }

    private void assertToPlainEscaped(String expected, String textHtml) {
        assertEquals("To plain escaped", expected,
                MyHtml.fromContentStored(textHtml, TextMediaType.PLAIN_ESCAPED));
    }
}
