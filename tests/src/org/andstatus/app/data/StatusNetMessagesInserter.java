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

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.TestSuite;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;

public class StatusNetMessagesInserter extends InstrumentationTestCase {
    private static volatile int iteration = 0;
    private Context context;

    private MbUser accountMbUser;
    private MyAccount ma;
    private Origin origin;

    public void insertData() throws Exception {
        mySetup();
        addConversation();
    }
    
    private void mySetup() throws Exception {
        iteration++;
        context = TestSuite.getMyContextForTest().context();
        origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.STATUSNET_TEST_ORIGIN_NAME);
        assertTrue(TestSuite.STATUSNET_TEST_ORIGIN_NAME + " exists", origin != null);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.STATUSNET_TEST_ACCOUNT_NAME); 
        assertTrue(TestSuite.STATUSNET_TEST_ACCOUNT_NAME + " exists", ma != null);
        accountMbUser = userFromOid(TestSuite.STATUSNET_TEST_ACCOUNT_USER_OID);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        mySetup();
    }
    
    public void addConversation() throws ConnectionException {
        MbUser author1 = userFromOid("1");
        author1.avatarUrl = "https://raw.github.com/andstatus/andstatus/master/res/drawable/splash_logo.png";
        MbUser author2 = userFromOid("2");
        author2.avatarUrl = "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png";
        MbUser author3 = userFromOid("3");
        author3.avatarUrl = "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif";
        MbUser author4 = userFromOid("4");
        
        MbMessage minus1 = buildMessage(author2, "Older one message", null, null);
        MbMessage selected = buildMessage(author1, "Selected message", minus1, TestSuite.CONVERSATION_ENTRY_MESSAGE_OID);
        MbMessage reply1 = buildMessage(author3, "Reply 1 to selected", selected, null);
        MbMessage reply2 = buildMessage(author2, "Reply 2 to selected is public", selected, null);
        addPublicMessage(reply2, true);
        MbMessage reply3 = buildMessage(author1, "Reply 3 to selected by the same author", selected, null);
        addMessage(selected);
        addMessage(reply3);
        addMessage(reply1);
        addMessage(reply2);
        MbMessage reply4 = buildMessage(author4, "Reply 4 to Reply 1, " + TestSuite.PUBLIC_MESSAGE_TEXT + " other author", reply1, null);
        addMessage(reply4);
        addPublicMessage(reply4, false);
        addMessage(buildMessage(author2, "Reply 5 to Reply 4", reply4, null));
        addMessage(buildMessage(author3, "Reply 6 to Reply 4 - the second", reply4, null));

        MbMessage reply7 = buildMessage(author1, "Reply 7 to Reply 2 is about " 
        + TestSuite.PUBLIC_MESSAGE_TEXT + " and something else", reply2, null);
        addPublicMessage(reply7, true);
        
        MbMessage reply8 = buildMessage(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        MbMessage reply9 = buildMessage(author2, "Reply 9 to Reply 7", reply7, null);
        addMessage(reply9);
        MbMessage reply10 = buildMessage(author3, "Reply 10 to Reply 8", reply8, null);
        addMessage(reply10);
        MbMessage reply11 = buildMessage(author2, "Reply 11 to Reply 7 with " + TestSuite.GLOBAL_PUBLIC_MESSAGE_TEXT + " text", reply7, null);
        addPublicMessage(reply11, true);
    }
    
    private void addPublicMessage(MbMessage message, boolean isPublic) {
        message.setPublic(isPublic);
        long id = addMessage(message);
        long storedPublic = MyProvider.msgIdToLongColumnValue(Msg.PUBLIC, id);
        assertTrue("Message is " + (isPublic ? "public" : "private" )+ ": " + message.getBody(), (isPublic == ( storedPublic != 0)));
    }

    private MbUser userFromOid(String userOid) {
        String userName = "user" + userOid;
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), userOid);
        mbUser.userName = userName;
        if (accountMbUser != null) {
            mbUser.actor = accountMbUser;
        }
        return mbUser;
    }
    
    private MbMessage buildMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        String messageOid = messageOidIn;
        if (TextUtils.isEmpty(messageOid)) {
            messageOid = author.url  + "/" + (inReplyToMessage == null ? "note" : "comment") + "thisisfakeuri" + System.nanoTime();
        }
        MbMessage message = MbMessage.fromOriginAndOid(origin.getId(), messageOid);
        message.setBody(body + (inReplyToMessage != null ? " it" + iteration : "" ));
        message.sentDate = System.currentTimeMillis();
        message.via = "AndStatus";
        message.sender = author;
        message.actor = accountMbUser;
        message.inReplyToMessage = inReplyToMessage;
        try {
            Thread.sleep(2);
        } catch (InterruptedException ignored) {
        }
        return message;
    }
    
    private long addMessage(MbMessage message) {
        DataInserter di = new DataInserter(ma, context, TimelineTypeEnum.HOME);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added " + message.oid, messageId != 0);
        return messageId;
    }
}
