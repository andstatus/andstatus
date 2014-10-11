/**
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

package org.andstatus.app;

import android.content.Intent;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.HtmlContentInserter;
import org.andstatus.app.data.MessageInserter;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyHtml;

public class MessageShareTest extends InstrumentationTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
    }

    public void testShareHtml() throws Exception {
        new HtmlContentInserter().insertHtml();
        
        Origin origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.CONVERSATION_ORIGIN_NAME);
        assertTrue(TestSuite.CONVERSATION_ORIGIN_NAME + " exists", origin != null);
        long msgId = MyProvider.oidToId(OidEnum.MSG_OID, origin.getId(), TestSuite.HTML_MESSAGE_OID);
        assertTrue("origin=" + origin.getId() + "; oid=" + TestSuite.HTML_MESSAGE_OID, msgId != 0);
        MessageShare messageShare = new MessageShare(MyContextHolder.get().context(), msgId);
        Intent intent = messageShare.intentForShare();
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
    
    public void testSharePlainText() {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.TWITTER_TEST_ACCOUNT_NAME); 
        assertTrue(TestSuite.TWITTER_TEST_ACCOUNT_NAME + " exists", ma != null);
        String body = "Posting as a plain Text";
        MessageInserter mi = new MessageInserter(ma);
        long msgId = mi.addMessage(mi.buildMessage(mi.buildUser(), body, null, TestSuite.PLAIN_TEXT_MESSAGE_OID));
        MessageShare messageShare = new MessageShare(MyContextHolder.get().context(), msgId);
        Intent intent = messageShare.intentForShare();
        assertTrue(intent.getExtras().containsKey(Intent.EXTRA_TEXT));
        assertTrue(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_TEXT).contains(body));
        assertFalse(intent.getExtras().containsKey(Intent.EXTRA_HTML_TEXT));
    }
}
