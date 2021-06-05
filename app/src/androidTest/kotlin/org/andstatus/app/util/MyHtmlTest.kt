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

import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.TextMediaType
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MyHtmlTest {

    @Before
    fun setUp() {
        TestSuite.initialize(this)
    }

    @Test
    fun testPrepareForView() {
        Assert.assertEquals(SAMPLE1_FOR_VIEW, MyHtml.prepareForView(SAMPLE1_HTML))
        Assert.assertEquals(SAMPLE2_FOR_VIEW, MyHtml.prepareForView(SAMPLE2_HTML1))
        Assert.assertEquals(SAMPLE2_FOR_VIEW, MyHtml.prepareForView(SAMPLE2_HTML2))
    }

    @Test
    fun testHasHtmlMarkup() {
        Assert.assertFalse(MyHtml.hasHtmlMarkup(SAMPLE1))
        Assert.assertTrue(MyHtml.hasHtmlMarkup(SAMPLE1_HTML))
    }

    @Test
    fun testBodyToSearch() {
        val text1 = """@somebody,  [This]  .       is'
 a	; "normalised {text}"@user@domain.com, #<a href="#some">AndStatus</a>. (!gnusocial)"""
        val text2 = """@somebody,  [This]  .       is'
 a	; "normalised {text}"@user@domain.com, #AndStatus. (!gnusocial)"""
        val result1 = (",somebody,@somebody,This,is,a,normalised,text,user,@user@domain.com,AndStatus,#AndStatus,"
                + "gnusocial,!gnusocial,")
        Assert.assertEquals(result1, MyHtml.normalizeWordsForSearch(text2))
        Assert.assertEquals(result1.toLowerCase(), MyHtml.getContentToSearch(text2))
        Assert.assertEquals(result1.toLowerCase(), MyHtml.getContentToSearch(text1))
        val text3 = "<p>Hello! Does anyone use?! <a href=\"https://mstdn.io/tags/andstatus\" rel=\"nofollow " +
                "noopener\" target=\"_blank\">#<span>Andstatus</span></a>? How do I get to recognize my instance?</p>"
        val result2 = ",hello,does,anyone,use,andstatus,#andstatus,how,do,i,get,to,recognize,my,instance,"
        Assert.assertEquals(result2, MyHtml.getContentToSearch(text3))
        val text4 = "Uh someone on XYZ just said \"I found a Something in the XYZ fridge.\" @ABVinskeep we''re gonna need an investigation of this."
        val result4 = ",uh,someone,on,xyz,just,said,i,found,a,something,in,the,xyz,fridge,abvinskeep,@abvinskeep,we,re,gonna,need,an,investigation,of,this,"
        Assert.assertEquals(result4, MyHtml.getContentToSearch(text4))
    }

    @Test
    fun testToPlainText() {
        val linebreaks = "This note\nhas \nnewline"
        val singleLine = "This note has newline"
        val text1 = "This note<br >has <br>newline "
        Assert.assertEquals(linebreaks, MyHtml.htmlToPlainText(text1))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text1))
        val text2 = "This note<br />has <p>newline</p>"
        Assert.assertEquals(linebreaks, MyHtml.htmlToPlainText(text2))
        Assert.assertEquals(linebreaks, MyHtml.htmlToPlainText(linebreaks))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text2))
        val text3 = "This <a href='#as'>note</a><br />has <br><br>newline"
        val doubleLinebreaks = "This note\nhas \n\nnewline"
        Assert.assertEquals(doubleLinebreaks, MyHtml.htmlToPlainText(text3))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text3))
        val text3_2 = "This <a href='#as'>note</a><br />has <br><br><br>newline"
        Assert.assertEquals(doubleLinebreaks, MyHtml.htmlToPlainText(text3_2))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text3_2))
        val text3_3 = "This note\nhas \n\n\nnewline"
        Assert.assertEquals(doubleLinebreaks, MyHtml.htmlToPlainText(text3_3))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text3_3))
        val text4 = "<p>This <a href='#as'>note</a></p><br />has <p>newline</p>"
        val double2Linebreaks = "This note\n\nhas \nnewline"
        Assert.assertEquals(double2Linebreaks, MyHtml.htmlToPlainText(text4))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text4))
        val text5 = "<p>This <a href='#as'>note</a></p>has <p>newline</p>"
        Assert.assertEquals(linebreaks, MyHtml.htmlToPlainText(text5))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text5))
        val text6 = "<p>This <a href='#as'>note</a></p>   has <p>newline</p> "
        Assert.assertEquals(linebreaks, MyHtml.htmlToPlainText(text6))
        Assert.assertEquals(singleLine, MyHtml.htmlToCompactPlainText(text6))
        Assert.assertEquals("I'm working", MyHtml.htmlToPlainText("I&apos;m working"))
    }

    @Test
    fun testSendingToTwitter() {
        Assert.assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodyTypedPlain, TextMediaType.PLAIN, true))
        Assert.assertEquals(twitterBodyTypedPlain, MyHtml.fromContentStored(twitterBodyHtml, TextMediaType.PLAIN))
        Assert.assertEquals(twitterBodyReceived, MyHtml.fromContentStored(twitterBodyHtml, TextMediaType.PLAIN_ESCAPED))
        Assert.assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodyReceived, TextMediaType.PLAIN_ESCAPED, true))
        Assert.assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodyReceived, TextMediaType.PLAIN_ESCAPED, false))
    }

    @Test
    fun testStoreLineBreaks() {
        val textBr = "Today's note<br >has <br>linebreaks "
        val textN = "Today's note\nhas \nlinebreaks "
        val textBrN = "Today's note<br >\nhas <br>\nlinebreaks "
        Assert.assertEquals(textBr, MyHtml.toContentStored(textBr, TextMediaType.HTML, true))
        Assert.assertEquals(textBr, MyHtml.toContentStored(textBr, TextMediaType.UNKNOWN, true))
        val expEscaped = "Today's note&lt;br &gt;has &lt;br&gt;linebreaks"
        Assert.assertEquals(expEscaped, MyHtml.toContentStored(textBr, TextMediaType.PLAIN, true))
        Assert.assertEquals(expEscaped, MyHtml.toContentStored(textBr, TextMediaType.PLAIN, false))
        val expBr = "Today's note<br />has <br />linebreaks"
        Assert.assertEquals(expBr, MyHtml.toContentStored(textBr, TextMediaType.HTML, false))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textBr, TextMediaType.UNKNOWN, false))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.UNKNOWN, true))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.UNKNOWN, false))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.PLAIN, true))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textN, TextMediaType.PLAIN, false))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textBrN, TextMediaType.HTML, false))
        Assert.assertEquals(expBr, MyHtml.toContentStored(textBrN, TextMediaType.UNKNOWN, false))
        val expNoBreaks = "Today's note has linebreaks"
        Assert.assertEquals(expNoBreaks, MyHtml.toContentStored(textN, TextMediaType.HTML, false))
        Assert.assertEquals(textN, MyHtml.toContentStored(textN, TextMediaType.HTML, true))
        Assert.assertEquals(textBrN, MyHtml.toContentStored(textBrN, TextMediaType.HTML, true))
        Assert.assertEquals(textBrN, MyHtml.toContentStored(textBrN, TextMediaType.UNKNOWN, true))
        val expOverEscapedBr = "Today's note&lt;br &gt;<br />has &lt;br&gt;<br />linebreaks"
        Assert.assertEquals(expOverEscapedBr, MyHtml.toContentStored(textBrN, TextMediaType.PLAIN, true))
        Assert.assertEquals(expOverEscapedBr, MyHtml.toContentStored(textBrN, TextMediaType.PLAIN, false))
        val textNN = "Today's note\nhas \n\nlinebreaks "
        val expBrBr = "Today's note<br />has <br /><br />linebreaks"
        Assert.assertEquals(expBrBr, MyHtml.toContentStored(textNN, TextMediaType.PLAIN, true))
        Assert.assertEquals(expBrBr, MyHtml.toContentStored(textNN, TextMediaType.PLAIN, false))
        Assert.assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodyReceived, TextMediaType.PLAIN_ESCAPED, true))
        Assert.assertEquals(twitterBodyHtml, MyHtml.toContentStored(twitterBodyReceived, TextMediaType.PLAIN_ESCAPED, false))
    }

    @Test
    fun testStripExcessiveLineBreaks() {
        val twoLineBreaks = "one\n\ntwo"
        Assert.assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks(twoLineBreaks))
        Assert.assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks("one\n\n\ntwo"))
        Assert.assertEquals(twoLineBreaks, MyHtml.stripExcessiveLineBreaks("one\n\n\n \ntwo"))
        val oneLineBreak = "one\ntwo"
        Assert.assertEquals(oneLineBreak, MyHtml.stripExcessiveLineBreaks(oneLineBreak))
    }

    @Test
    fun testToPlainEscaped() {
        assertToPlainEscaped(
                "Line one &lt; escaped &gt;\nLine two",
                "Line one &lt; escaped &gt;<br />Line two")
        val oneLineEscaped = "Less &lt; and escaped &amp;lt; greater &gt;"
        assertToPlainEscaped(oneLineEscaped, oneLineEscaped)
        assertToPlainEscaped(twitterBodyReceived, twitterBodyHtml)
    }

    private fun assertToPlainEscaped(expected: String?, textHtml: String?) {
        Assert.assertEquals("To plain escaped", expected,
                MyHtml.fromContentStored(textHtml, TextMediaType.PLAIN_ESCAPED))
    }

    companion object {
        private const val SAMPLE1: String = "This note\nhas newline"
        private const val SAMPLE1_FOR_VIEW: String = "This note<br>\nhas newline"
        private const val SAMPLE1_HTML: String = "<p dir=\"ltr\">$SAMPLE1_FOR_VIEW</p>\n"
        private const val SAMPLE2_FOR_VIEW: String = "@auser@example.com This is a link " +
                "<a href=\"https://example.com/page1.html#something\">https://example.com/page1.html#something</a><br>\n" +
                "The second line"
        private const val SAMPLE2_HTML1: String = "<p dir=\"ltr\">$SAMPLE2_FOR_VIEW</p>"
        private const val SAMPLE2_HTML2: String = SAMPLE2_HTML1 + "\n"

        /* Plain text to manually copy-paste to Note Editor:
Testing if and what is escaped in a Tweet:
1. "less-than" sign <  and escaped: &lt;
2. "greater-than" sign > and escaped: &gt;
3. Ampersand & and escaped: &amp;
4. Apostrophe '
5. br HTML tag: <br /> and without "/": <br> ?!
 */
        var twitterBodyTypedPlain: String? = """Testing if and what is escaped in a Tweet:
1. "less-than" sign <  and escaped: &lt;
2. "greater-than" sign > and escaped: &gt;
3. Ampersand & and escaped: &amp;
4. Apostrophe '
5. br HTML tag: <br /> and without "/": <br> ?!"""
        private val twitterBodyReceived: String = """Testing if and what is escaped in a Tweet:
1. "less-than" sign &lt;  and escaped: &amp;lt;
2. "greater-than" sign &gt; and escaped: &amp;gt;
3. Ampersand &amp; and escaped: &amp;amp;
4. Apostrophe '
5. br HTML tag: &lt;br /&gt; and without "/": &lt;br&gt; ?!"""
        var twitterBodyToPost: String? = twitterBodyTypedPlain
        var twitterBodyHtml: String? = "Testing if and what is escaped in a Tweet:<br />" +
                "1. \"less-than\" sign &lt;  and escaped: &amp;lt;<br />" +
                "2. \"greater-than\" sign &gt; and escaped: &amp;gt;<br />" +
                "3. Ampersand &amp; and escaped: &amp;amp;<br />" +
                "4. Apostrophe '<br />" +
                "5. br HTML tag: &lt;br /&gt; and without \"/\": &lt;br&gt; ?!"
    }
}
