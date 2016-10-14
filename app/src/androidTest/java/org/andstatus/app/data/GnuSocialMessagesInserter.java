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

import android.support.annotation.Nullable;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;

import java.util.concurrent.atomic.AtomicInteger;

public class GnuSocialMessagesInserter extends InstrumentationTestCase {
    private static AtomicInteger iterationCounter = new AtomicInteger(0);
    private int iteration = 0;

    private MbUser accountMbUser;
    private MyAccount ma;
    private Origin origin;

    public void insertData() {
        mySetup();
        addConversation();
    }
    
    private void mySetup() {
        iteration = iterationCounter.incrementAndGet();
        origin = MyContextHolder.get().persistentOrigins().fromName(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME);
        assertTrue(TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME + " exists", origin != null);
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME); 
        assertTrue(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME + " exists", ma.isValid());
        accountMbUser = userFromOidAndAvatar(TestSuite.GNUSOCIAL_TEST_ACCOUNT_USER_OID,
                TestSuite.GNUSOCIAL_TEST_ACCOUNT_AVATAR_URL);
    }
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        mySetup();
    }
    
    public void addConversation() {
        MbUser author1 = userFromOidAndAvatar("1",
                "https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png");
        MbUser author2 = userFromOidAndAvatar("2",
                "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png");
        MbUser author3 = userFromOidAndAvatar("3",
                "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif");
        MbUser author4 = userFromOidAndAvatar("4", "");

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
        long storedPublic = MyQuery.msgIdToLongColumnValue(MsgTable.PUBLIC, id);
        assertTrue("Message is " + (isPublic ? "public" : "private" )+ ": " + message.getBody(), (isPublic == ( storedPublic != 0)));
    }

    private MbUser userFromOidAndAvatar(String userOid,@Nullable String avatarUrl) {
        String userName = "user" + userOid;
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), userOid);
        mbUser.setUserName(userName);
        if (avatarUrl != null) {
            mbUser.avatarUrl = avatarUrl;
        }
        mbUser.setProfileUrl(origin.getUrl());
        if (accountMbUser != null) {
            mbUser.actor = accountMbUser;
        }
        return mbUser;
    }
    
    private MbMessage buildMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        return new MessageInserter(ma).buildMessage(author, body
                        + (inReplyToMessage != null ? " it" + iteration : ""),
                inReplyToMessage, messageOidIn, DownloadStatus.LOADED);
    }
    
    private long addMessage(MbMessage message) {
        DataInserter di = new DataInserter(new CommandExecutionContext(
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma)));
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added " + message.oid, messageId != 0);
        return messageId;
    }
}
