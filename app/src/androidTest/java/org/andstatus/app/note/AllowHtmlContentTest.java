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

package org.andstatus.app.note;

import android.content.Intent;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DataUpdater;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.HtmlContentTester;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.ConnectionMock;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RawResourceUtils;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AllowHtmlContentTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void testAllowHtlContent() throws Exception {
        boolean isAllowedInPumpIoStored = demoData.getPumpioConversationOrigin().isHtmlContentAllowed();
        boolean isAllowedInGnuSocialStored = demoData.getGnuSocialOrigin().isHtmlContentAllowed();
        new HtmlContentTester().insertPumpIoHtmlContent();
        oneGnuSocialTest(isAllowedInGnuSocialStored);

        setHtmlContentAllowed(!isAllowedInPumpIoStored, !isAllowedInGnuSocialStored);
        new HtmlContentTester().insertPumpIoHtmlContent();
        oneGnuSocialTest(!isAllowedInGnuSocialStored);

        // Return setting back
        setHtmlContentAllowed(isAllowedInPumpIoStored, isAllowedInGnuSocialStored);

        testShareHtml();
    }

    private void testShareHtml() {
        Origin origin = demoData.getPumpioConversationOrigin();

        assertNotNull(demoData.conversationOriginName + " exists", origin);
        long noteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(), demoData.htmlNoteOid);
        Note note = Note.loadContentById(MyContextHolder.get(), noteId);
        assertTrue("origin=" + origin.getId() + "; oid=" + demoData.htmlNoteOid, noteId != 0);
        NoteShare noteShare = new NoteShare(origin, noteId, NoteDownloads.fromNoteId(MyContextHolder.get(), noteId));
        Intent intent = noteShare.intentToViewAndShare(true);
        assertTrue(intent.hasExtra(Intent.EXTRA_TEXT));
        assertTrue(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_TEXT).contains(
                        MyHtml.htmlToPlainText(HtmlContentTester.HTML_BODY_IMG_STRING)));
        if (origin.isHtmlContentAllowed()) {
            assertTrue(note.getContent(), intent.hasExtra(Intent.EXTRA_HTML_TEXT));
            assertThat(note.getContent(),
                    intent.getStringExtra(Intent.EXTRA_HTML_TEXT),
                    containsString(HtmlContentTester.HTML_BODY_IMG_STRING));
        }
    }

    @Test
    public void testSharePlainText() {
        String body = "Posting as a plain Text " + demoData.testRunUid;
        final MyAccount myAccount = demoData.getMyAccount(demoData.twitterTestAccountName);
        AActivity activity = DemoNoteInserter.addNoteForAccount(myAccount, body,
                demoData.plainTextNoteOid, DownloadStatus.LOADED);
        NoteShare noteShare = new NoteShare(myAccount.getOrigin(), activity.getNote().noteId,
                NoteDownloads.fromNoteId(MyContextHolder.get(), activity.getNote().noteId));
        Intent intent = noteShare.intentToViewAndShare(true);
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_TEXT));
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_TEXT).contains(body));
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_HTML_TEXT));
        demoData.assertConversations();
    }

    private void setHtmlContentAllowed(boolean allowedInPumpIo, boolean allowedInGnuSocial) {
        new Origin.Builder(demoData.getPumpioConversationOrigin()).setHtmlContentAllowed(allowedInPumpIo).save();
        new Origin.Builder(demoData.getGnuSocialOrigin()).setHtmlContentAllowed(allowedInGnuSocial).save();

        TestSuite.forget();
        TestSuite.initialize(this);

        assertEquals("is HTML content allowed in PumpIo", allowedInPumpIo,
                demoData.getPumpioConversationOrigin().isHtmlContentAllowed());
    }

    private void oneGnuSocialTest(boolean isHtmlAllowed) throws Exception {
        assertEquals("is HTML content allowed in GnuSocial", isHtmlAllowed,
                demoData.getGnuSocialOrigin().isHtmlContentAllowed());

        ConnectionMock mock = ConnectionMock.newFor(demoData.gnusocialTestAccountName);
        mock.addResponse(org.andstatus.app.tests.R.raw.gnusocial_note_with_html);
        final String noteOid = "4453144";
        AActivity activity = mock.connection.getNote(noteOid);

        assertEquals("Received a note " + activity, AObjectType.NOTE, activity.getObjectType());
        assertEquals("Should be UPDATE " + activity, ActivityType.UPDATE,  activity.type);
        assertEquals("Note Oid", noteOid, activity.getNote().oid);
        assertEquals("Actor Username", "vaeringjar", activity.getActor().getUsername());
        assertEquals("Author should be Actor", activity.getActor(), activity.getAuthor());
        assertTrue("inReplyTo should not be empty " + activity , activity.getNote().getInReplyTo().nonEmpty());

        JSONObject jso = new JSONObject(RawResourceUtils.getString(org.andstatus.app.tests.R.raw.gnusocial_note_with_html));
        String expectedContent = jso.getString( isHtmlAllowed ? "statusnet_html" : "text");
        final String actualContent =  isHtmlAllowed
                ? activity.getNote().getContent()
                : MyHtml.htmlToPlainText(activity.getNote().getContent());
        assertEquals(isHtmlAllowed ? "HTML content allowed" : "No HTML content", expectedContent, actualContent);

        activity.getNote().setUpdatedDate(MyLog.uniqueCurrentTimeMS());
        activity.setUpdatedNow(0);

        MyAccount ma = demoData.getGnuSocialAccount();
        CommandExecutionContext executionContext = new CommandExecutionContext(
                MyContextHolder.get(), CommandData.newItemCommand(CommandEnum.GET_NOTE, ma, 123));
        new DataUpdater(executionContext).onActivity(activity);

    }

}
