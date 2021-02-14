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

package org.andstatus.app.data;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextState;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.StringUtil;

import static org.andstatus.app.context.DemoData.demoData;
import static org.andstatus.app.context.MyContextHolder.myContextHolder;
import static org.andstatus.app.util.MyHtml.LINEBREAK_HTML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HtmlContentTester {
    private final MyAccount ma;
    public static final String HTML_BODY_IMG_STRING = "A note with <b>HTML</b> <i>img</i> tag: "
            + "<img src='http://static.fsf.org/dbd/hollyweb.jpeg' alt='Stop DRM in HTML5' />"
            + ", <a href='http://www.fsf.org/'>the link in 'a' tag</a> <br/>" 
            + "and a plain text link to the issue 60: https://github.com/andstatus/andstatus/issues/60";

    public HtmlContentTester() {
        Origin origin = myContextHolder.getNow().origins().fromName(demoData.conversationOriginName);
        assertTrue(demoData.conversationOriginName + " exists",
                origin.getOriginType() != OriginType.UNKNOWN);
        ma = demoData.getMyAccount(demoData.conversationAccountName);
        assertTrue(demoData.conversationAccountName + " exists", ma.isValid());
    }

    public void insertPumpIoHtmlContent() {
        Actor author = new DemoNoteInserter(ma).buildActorFromOid("acct:html@example.com");
        assertEquals("Author1: " + author, MyContextState.READY, author.origin.myContext.state());
        author.setAvatarUrl("http://png-5.findicons.com/files/icons/2198/dark_glass/128/html.png");

        String bodyString = "<h4>This is a note with HTML content</h4>"
                + "<p>This is a second line, <b>Bold</b> formatting." 
                + "<br /><i>This is italics</i>. <b>And this is bold</b> <u>The text is underlined</u>.</p>"
                + "<p>A separate paragraph.</p>";
        assertFalse("HTML removed", MyHtml.htmlToPlainText(bodyString).contains("<"));
        assertHtmlNote(author, bodyString, null);

        assertHtmlNote(author, HTML_BODY_IMG_STRING, demoData.htmlNoteOid);
    }

    private void assertHtmlNote(Actor author, String bodyString, String noteOid) {
        if (author.origin.isHtmlContentAllowed()) {
            assertHtmlNoteContentAllowed(author, bodyString, noteOid, true);
        } else {
            assertHtmlNoteContentAllowed(author, bodyString + " no HTML",
                    StringUtil.isEmpty(noteOid) ? null : noteOid + "-noHtml", false);
        }
    }

	private void assertHtmlNoteContentAllowed(Actor author,
                                              String bodyString, String noteOid, boolean htmlContentAllowed) {
        DemoNoteInserter mi = new DemoNoteInserter(ma);
        assertEquals("Author: " + author, MyContextState.READY, author.origin.myContext.state());
        final AActivity activity = mi.buildActivity(author, "", bodyString, null, noteOid,
                DownloadStatus.LOADED);
        mi.onActivity(activity);

        Note noteStored = Note.loadContentById(myContextHolder.getNow(), activity.getNote().noteId);
        assertTrue("Note was loaded " + activity.getNote(), noteStored.nonEmpty());
        if (htmlContentAllowed) {
            assertEquals("HTML preserved", bodyString, noteStored.getContent());
        } else {
            assertFalse("HTML should be removed: " + noteStored.getContent(),
                    noteStored.getContent().replaceAll(LINEBREAK_HTML, "\n").contains("<"));
        }
        assertEquals("Stored content " + activity.getNote(), activity.getNote().getContentToSearch(),
                noteStored.getContentToSearch());
        String storedContentToSearch = MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT_TO_SEARCH, noteStored.noteId);
        assertEquals("Stored content to search", noteStored.getContentToSearch(), storedContentToSearch);
	}
}
