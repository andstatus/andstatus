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

package org.andstatus.app.data;

import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.DatabaseHolder.Msg;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyHtml;

public class HtmlContentInserter extends InstrumentationTestCase {
    private InstrumentationTestCase testCase;
    private MyAccount ma;
    private Origin origin;
    public static final String HTML_BODY_IMG_STRING = "A message with <b>HTML</b> <i>img</i> tag: " 
            + "<img src='http://static.fsf.org/dbd/hollyweb.jpeg' alt='Stop DRM in HTML5' />"
            + ", <a href='http://www.fsf.org/'>the link in 'a' tag</a> <br/>" 
            + "and a plain text link to the issue 60: https://github.com/andstatus/andstatus/issues/60";
    
    public HtmlContentInserter(InstrumentationTestCase parent) {
        testCase = parent;
    }
    
    private void mySetup() throws Exception {
        origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.CONVERSATION_ORIGIN_NAME);
        assertTrue(TestSuite.CONVERSATION_ORIGIN_NAME + " exists", origin.getOriginType() != OriginType.UNKNOWN);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME); 
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        mySetup();
    }

    private MbUser buildUserFromOid(String userOid) {
        return new MessageInserter(ma).buildUserFromOid(userOid);
    }
    
    public void insertHtml() throws Exception {
        mySetup();
        testHtmlContent();
    }
    
    private void testHtmlContent() throws Exception {
        boolean isHtmlContentAllowedStored = origin.isHtmlContentAllowed(); 
        MbUser author1 = buildUserFromOid("acct:html@example.com");
        author1.avatarUrl = "http://png-5.findicons.com/files/icons/2198/dark_glass/128/html.png";

        String bodyString = "<h4>This is a message with HTML content</h4>" 
                + "<p>This is a second line, <b>Bold</b> formatting." 
                + "<br /><i>This is italics</i>. <b>And this is bold</b> <u>The text is underlined</u>.</p>"
                + "<p>A separate paragraph.</p>";
        assertFalse("HTML removed", MyHtml.fromHtml(bodyString).contains("<"));
        assertHtmlMessage(author1, bodyString, null);

        assertHtmlMessage(author1, HTML_BODY_IMG_STRING, TestSuite.HTML_MESSAGE_OID);
        
        setHtmlContentAllowed(isHtmlContentAllowedStored);
    }

    private void setHtmlContentAllowed(boolean allowed) throws Exception {
        new Origin.Builder(origin).setHtmlContentAllowed(allowed).save();
        TestSuite.forget();
        TestSuite.initialize(testCase);
        mySetup();
        assertEquals(allowed, origin.isHtmlContentAllowed());
    }
    
    private void assertHtmlMessage(MbUser author, String bodyString, String messageOid) throws Exception {
        assertHtmlMessageContentAllowed(author, bodyString, messageOid, true);
        assertHtmlMessageContentAllowed(author, bodyString + " no HTML", 
        		TextUtils.isEmpty(messageOid) ? null : messageOid + "-noHtml", false);
    }

	private MessageInserter assertHtmlMessageContentAllowed(MbUser author,
			String bodyString, String messageOid, boolean htmlContentAllowed) throws Exception {
		setHtmlContentAllowed(htmlContentAllowed);
        MessageInserter mi = new MessageInserter(ma);
        long msgId1 = mi.addMessage(mi.buildMessage(author, bodyString, null, messageOid, DownloadStatus.LOADED));
        String body1 = MyQuery.msgIdToStringColumnValue(Msg.BODY, msgId1);
        if (htmlContentAllowed) {
            assertEquals("HTML preserved", bodyString, body1);
        } else {
            assertEquals("HTML removed", MyHtml.fromHtml(bodyString), body1);
        }
		return mi;
	}
}
