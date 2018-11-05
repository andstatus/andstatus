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
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DemoNoteInserter;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.HtmlContentInserter;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyHtml;
import org.junit.Before;
import org.junit.Test;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoteShareTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithAccounts(this);
    }

    @Test
    public void testShareHtml() {
        boolean isHtmlContentAllowedStored = demoData.getConversationOrigin().isHtmlContentAllowed();
        new HtmlContentInserter().insertHtml();
        setHtmlContentAllowed(!isHtmlContentAllowedStored);
        new HtmlContentInserter().insertHtml();
        setHtmlContentAllowed(isHtmlContentAllowedStored);

        Origin origin = demoData.getConversationOrigin();

        assertTrue(demoData.conversationOriginName + " exists", origin != null);
        long noteId = MyQuery.oidToId(OidEnum.NOTE_OID, origin.getId(), demoData.htmlNoteOid);
        assertTrue("origin=" + origin.getId() + "; oid=" + demoData.htmlNoteOid, noteId != 0);
        NoteShare noteShare = new NoteShare(origin, noteId, null);
        Intent intent = noteShare.intentToViewAndShare(true);
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_TEXT));
        assertTrue(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_TEXT).contains(
                        MyHtml.fromHtml(HtmlContentInserter.HTML_BODY_IMG_STRING)));
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_HTML_TEXT));
        assertTrue(
                intent.getStringExtra(Intent.EXTRA_HTML_TEXT),
                intent.getStringExtra(Intent.EXTRA_HTML_TEXT).contains(
                        HtmlContentInserter.HTML_BODY_IMG_STRING));
    }

    @Test
    public void testSharePlainText() {
        String body = "Posting as a plain Text " + demoData.testRunUid;
        final MyAccount myAccount = demoData.getMyAccount(demoData.twitterTestAccountName);
        AActivity activity = DemoNoteInserter.addNoteForAccount(myAccount, body,
                demoData.plainTextNoteOid, DownloadStatus.LOADED);
        NoteShare noteShare = new NoteShare(myAccount.getOrigin(), activity.getNote().noteId, null);
        Intent intent = noteShare.intentToViewAndShare(true);
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_TEXT));
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT), intent.getStringExtra(Intent.EXTRA_TEXT).contains(body));
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_HTML_TEXT));
        demoData.assertConversations();
    }

    private void setHtmlContentAllowed(boolean allowed) {
        new Origin.Builder(demoData.getConversationOrigin()).setHtmlContentAllowed(allowed).save();
        TestSuite.forget();
        TestSuite.initialize(this);
        assertEquals(allowed, demoData.getConversationOrigin().isHtmlContentAllowed());
    }

}
