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

package org.andstatus.app.msg;

import android.content.Intent;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DemoMessageInserter;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageShareTest {

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
    }

    @Test
    public void testShareHtml() throws Exception {
        new HtmlContentInserter().insertHtml();
        
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(demoData.CONVERSATION_ORIGIN_NAME);
        assertTrue(demoData.CONVERSATION_ORIGIN_NAME + " exists", origin != null);
        long msgId = MyQuery.oidToId(OidEnum.MSG_OID, origin.getId(), demoData.HTML_MESSAGE_OID);
        assertTrue("origin=" + origin.getId() + "; oid=" + demoData.HTML_MESSAGE_OID, msgId != 0);
        MessageShare messageShare = new MessageShare(origin, msgId, null);
        Intent intent = messageShare.intentToViewAndShare(true);
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
        String body = "Posting as a plain Text " + demoData.TESTRUN_UID;
        final MyAccount myAccount = demoData.getMyAccount(demoData.TWITTER_TEST_ACCOUNT_NAME);
        AActivity activity = DemoMessageInserter.addMessageForAccount(myAccount, body,
                demoData.PLAIN_TEXT_MESSAGE_OID, DownloadStatus.LOADED);
        MessageShare messageShare = new MessageShare(myAccount.getOrigin(), activity.getMessage().msgId, null);
        Intent intent = messageShare.intentToViewAndShare(true);
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_TEXT));
        assertTrue(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_TEXT).contains(body));
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_HTML_TEXT));
        demoData.assertConversations();
    }
}
