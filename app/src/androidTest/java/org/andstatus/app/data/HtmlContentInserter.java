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

import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyHtml;
import org.junit.Before;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HtmlContentInserter {
    private MyAccount ma;
    private Origin origin;
    public static final String HTML_BODY_IMG_STRING = "A message with <b>HTML</b> <i>img</i> tag: " 
            + "<img src='http://static.fsf.org/dbd/hollyweb.jpeg' alt='Stop DRM in HTML5' />"
            + ", <a href='http://www.fsf.org/'>the link in 'a' tag</a> <br/>" 
            + "and a plain text link to the issue 60: https://github.com/andstatus/andstatus/issues/60";

    private void mySetup() {
        TestSuite.initializeWithData(this);
        origin = MyContextHolder.get().persistentOrigins().fromName(demoData.CONVERSATION_ORIGIN_NAME);
        assertTrue(demoData.CONVERSATION_ORIGIN_NAME + " exists", origin.getOriginType() != OriginType.UNKNOWN);
        ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(demoData.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
    }
    
    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        mySetup();
    }

    private Actor buildUserFromOid(String userOid) {
        return new DemoMessageInserter(ma).buildActorFromOid(userOid);
    }
    
    public void insertHtml() {
        mySetup();
        testHtmlContent();
    }

    private void testHtmlContent() {
        boolean isHtmlContentAllowedStored = origin.isHtmlContentAllowed(); 
        Actor author1 = buildUserFromOid("acct:html@example.com");
        author1.avatarUrl = "http://png-5.findicons.com/files/icons/2198/dark_glass/128/html.png";

        String bodyString = "<h4>This is a message with HTML content</h4>" 
                + "<p>This is a second line, <b>Bold</b> formatting." 
                + "<br /><i>This is italics</i>. <b>And this is bold</b> <u>The text is underlined</u>.</p>"
                + "<p>A separate paragraph.</p>";
        assertFalse("HTML removed", MyHtml.fromHtml(bodyString).contains("<"));
        assertHtmlMessage(author1, bodyString, null);

        assertHtmlMessage(author1, HTML_BODY_IMG_STRING, demoData.HTML_MESSAGE_OID);
        
        setHtmlContentAllowed(isHtmlContentAllowedStored);
    }

    private void setHtmlContentAllowed(boolean allowed) {
        new Origin.Builder(origin).setHtmlContentAllowed(allowed).save();
        TestSuite.forget();
        TestSuite.initialize(this);
        mySetup();
        assertEquals(allowed, origin.isHtmlContentAllowed());
    }
    
    private void assertHtmlMessage(Actor author, String bodyString, String messageOid) {
        assertHtmlMessageContentAllowed(author, bodyString, messageOid, true);
        assertHtmlMessageContentAllowed(author, bodyString + " no HTML", 
        		TextUtils.isEmpty(messageOid) ? null : messageOid + "-noHtml", false);
    }

	private DemoMessageInserter assertHtmlMessageContentAllowed(Actor author,
                                                                String bodyString, String messageOid, boolean htmlContentAllowed) {
		setHtmlContentAllowed(htmlContentAllowed);
        DemoMessageInserter mi = new DemoMessageInserter(ma);
        final AActivity activity = mi.buildActivity(author, bodyString, null, messageOid, DownloadStatus.LOADED);
        mi.onActivity(activity);
        long msgId1 = activity.getMessage().msgId;
        String body = MyQuery.msgIdToStringColumnValue(MsgTable.BODY, msgId1);
        if (htmlContentAllowed) {
            assertEquals("HTML preserved", bodyString, body);
        } else {
            assertEquals("HTML removed", MyHtml.fromHtml(bodyString), body);
        }
        String bodyToSearch = MyQuery.msgIdToStringColumnValue(MsgTable.BODY_TO_SEARCH, msgId1);
        assertEquals("Body to search", MyHtml.getBodyToSearch(body), bodyToSearch);
		return mi;
	}
}
