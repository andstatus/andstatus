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
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbAttachment;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;

import java.net.MalformedURLException;
import java.net.URL;

public class ConversationInserter extends InstrumentationTestCase {
    private static volatile int iteration = 0;

    private MbUser accountMbUser;
    private MyAccount ma;
    private Origin origin;
    private String bodySuffix = "";

    public void insertConversation(String bodySuffixIn) throws Exception {
        if (TextUtils.isEmpty(bodySuffixIn)) {
            bodySuffix = "";
        } else {
            bodySuffix = " " + bodySuffixIn;
        }
        mySetup();
        insertAndTestConversation();
    }

    public void insertMessage(String body) throws Exception {
        mySetup();
        MbMessage message = buildMessage(getAuthor1(), body, null, null);
        addMessage(message);
    }
    
    private void mySetup() throws Exception {
        iteration++;
        origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.CONVERSATION_ORIGIN_NAME);
        assertTrue(TestSuite.CONVERSATION_ORIGIN_NAME + " exists", origin != null);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME); 
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", ma != null);
        accountMbUser = buildUserFromOid(TestSuite.CONVERSATION_ACCOUNT_USER_OID);
        accountMbUser.avatarUrl = TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL;
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        mySetup();
    }
    
    private void insertAndTestConversation() throws ConnectionException, MalformedURLException {
        assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, TestSuite.CONVERSATION_ORIGIN_TYPE  );
        
        MbUser author2 = buildUserFromOid("acct:second@identi.ca");
        author2.avatarUrl = "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png";
        MbUser author3 = buildUserFromOid("acct:third@pump.example.com");
        author3.avatarUrl = "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif";
        MbUser author4 = buildUserFromOid("acct:fourthWithoutAvatar@pump.example.com");
        
        MbMessage minus1 = buildMessage(author2, "Older one message", null, null);
        MbMessage selected = buildMessage(getAuthor1(), "Selected message", minus1, TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        MbMessage reply1 = buildMessage(author3, "Reply 1 to selected", selected, null);
        MbMessage reply2 = buildMessage(author2, "Reply 2 to selected is public", selected, null);
        addPublicMessage(reply2, true);
        MbMessage reply3 = buildMessage(getAuthor1(), "Reply 3 to selected by the same author", selected, null);
        reply3.attachments
        .add(MbAttachment
                .fromUrlAndContentType(
                        new URL(
                                "http://www.publicdomainpictures.net/pictures/100000/nahled/broadcasting-tower-14081029181fC.jpg"),
                        MyContentType.IMAGE));
        addMessage(selected);
        addMessage(reply3);
        addMessage(reply1);
        addMessage(reply2);
        MbMessage reply4 = buildMessage(author4, "Reply 4 to Reply 1 other author", reply1, null);
        addMessage(reply4);
        addPublicMessage(reply4, false);
        addMessage(buildMessage(author2, "Reply 5 to Reply 4", reply4, null));
        addMessage(buildMessage(author3, "Reply 6 to Reply 4 - the second", reply4, null));

        MbMessage reply7 = buildMessage(getAuthor1(), "Reply 7 to Reply 2 is about " 
        + TestSuite.PUBLIC_MESSAGE_TEXT + " and something else", reply2, null);
        addPublicMessage(reply7, true);
        
        MbMessage reply8 = buildMessage(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        
        MbMessage reply9 = buildMessage(author2, "Reply 9 to Reply 7", reply7, null);
        reply9.attachments
                .add(MbAttachment
                        .fromUrlAndContentType(
                                new URL(
                                        "http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg"),
                                MyContentType.IMAGE));
        addMessage(reply9);
        
        MbMessage reply10 = buildMessage(author3, "Reply 10 to Reply 8", reply8, null);
        addMessage(reply10);
        MbMessage reply11 = buildMessage(author2, "Reply 11 to Reply 7, " + TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT + " text", reply7, null);
        addPublicMessage(reply11, true);
    }

    private MbUser getAuthor1() {
        MbUser author1 = buildUserFromOid("acct:first@example.net");
        author1.avatarUrl = "https://raw.github.com/andstatus/andstatus/master/res/drawable/splash_logo.png";
        return author1;
    }
    
    private void addPublicMessage(MbMessage message, boolean isPublic) {
        message.setPublic(isPublic);
        long id = addMessage(message);
        long storedPublic = MyProvider.msgIdToLongColumnValue(Msg.PUBLIC, id);
        assertTrue("Message is " + (isPublic ? "public" : "private") + ": " + message.getBody(),
                (isPublic == (storedPublic != 0)));
    }

    private MbUser buildUserFromOid(String userOid) {
        return new MessageInserter(ma).buildUserFromOid(userOid);
    }
    
    private MbMessage buildMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        return new MessageInserter(ma).buildMessage(author, body
                + (inReplyToMessage != null ? " it" + iteration : "") + bodySuffix,
                inReplyToMessage, messageOidIn);
    }
    
    private long addMessage(MbMessage message) {
        return new MessageInserter(ma).addMessage(message);
    }
}
