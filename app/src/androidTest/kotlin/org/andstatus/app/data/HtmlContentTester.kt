/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data

import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.DemoData
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyContextState
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Note
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.MyHtml
import org.junit.Assert

class HtmlContentTester {
    private val ma: MyAccount
    fun insertPumpIoHtmlContent() {
        val author = DemoNoteInserter(ma).buildActorFromOid("acct:html@example.com")
        Assert.assertEquals("Author1: $author", MyContextState.READY, author.origin.myContext.state)
        author.setAvatarUrl("http://png-5.findicons.com/files/icons/2198/dark_glass/128/html.png")
        val bodyString = ("<h4>This is a note with HTML content</h4>"
                + "<p>This is a second line, <b>Bold</b> formatting."
                + "<br /><i>This is italics</i>. <b>And this is bold</b> <u>The text is underlined</u>.</p>"
                + "<p>A separate paragraph.</p>")
        Assert.assertFalse("HTML removed", MyHtml.htmlToPlainText(bodyString).contains("<"))
        assertHtmlNote(author, bodyString, null)
        assertHtmlNote(author, HTML_BODY_IMG_STRING, DemoData.demoData.htmlNoteOid)
    }

    private fun assertHtmlNote(author: Actor, bodyString: String?, noteOid: String?) {
        if (author.origin.isHtmlContentAllowed()) {
            assertHtmlNoteContentAllowed(author, bodyString, noteOid, true)
        } else {
            assertHtmlNoteContentAllowed(author, "$bodyString no HTML",
                    if (noteOid.isNullOrEmpty()) null else "$noteOid-noHtml", false)
        }
    }

    private fun assertHtmlNoteContentAllowed(author: Actor,
                                             bodyString: String?, noteOid: String?, htmlContentAllowed: Boolean) {
        val mi = DemoNoteInserter(ma)
        Assert.assertEquals("Author: $author", MyContextState.READY, author.origin.myContext.state)
        val activity = mi.buildActivity(author, "", bodyString, null, noteOid,
                DownloadStatus.LOADED)
        mi.onActivity(activity)
        val noteStored: Note = Note.Companion.loadContentById( MyContextHolder.myContextHolder.getNow(), activity.getNote().noteId)
        Assert.assertTrue("Note was loaded " + activity.getNote(), noteStored.nonEmpty)
        if (htmlContentAllowed) {
            Assert.assertEquals("HTML preserved", bodyString, noteStored.content)
        } else {
            Assert.assertFalse("HTML should be removed: " + noteStored.content,
                    noteStored.content.replace(MyHtml.LINEBREAK_HTML.toRegex(), "\n").contains("<"))
        }
        Assert.assertEquals("Stored content " + activity.getNote(), activity.getNote().getContentToSearch(),
                noteStored.getContentToSearch())
        val storedContentToSearch = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT_TO_SEARCH, noteStored.noteId)
        Assert.assertEquals("Stored content to search", noteStored.getContentToSearch(), storedContentToSearch)
    }

    companion object {
        val HTML_BODY_IMG_STRING: String = ("A note with <b>HTML</b> <i>img</i> tag: "
                + "<img src='http://static.fsf.org/dbd/hollyweb.jpeg' alt='Stop DRM in HTML5' />"
                + ", <a href='http://www.fsf.org/'>the link in 'a' tag</a> <br/>"
                + "and a plain text link to the issue 60: https://github.com/andstatus/andstatus/issues/60")
    }

    init {
        val origin: Origin =  MyContextHolder.myContextHolder.getNow().origins().fromName(DemoData.demoData.conversationOriginName)
        Assert.assertTrue(DemoData.demoData.conversationOriginName + " exists",
                origin.originType !== OriginType.UNKNOWN)
        ma = DemoData.demoData.getMyAccount(DemoData.demoData.conversationAccountName)
        Assert.assertTrue(DemoData.demoData.conversationAccountName + " exists", ma.isValid)
    }
}
