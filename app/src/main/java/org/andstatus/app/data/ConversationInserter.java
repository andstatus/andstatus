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
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.util.GregorianCalendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConversationInserter {
    private static AtomicInteger iterationCounter = new AtomicInteger(0);
    private static final Map<String, MbUser> users = new ConcurrentHashMap<>();

    private int iteration = 0;
    private MyAccount ma;
    private String bodySuffix = "";

    public static Map<String, MbUser> getUsers() {
        return users;
    }

    public void insertConversation(String bodySuffixIn) {
        if (TextUtils.isEmpty(bodySuffixIn)) {
            bodySuffix = "";
        } else {
            bodySuffix = " " + bodySuffixIn;
        }
        iteration = iterationCounter.incrementAndGet();
        ma = MyContextHolder.get().persistentAccounts().fromAccountName(DemoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(DemoData.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        insertAndTestConversation();
    }

    private void insertAndTestConversation() {
        assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, DemoData.CONVERSATION_ORIGIN_TYPE  );

        MbUser myAuthor = buildUserFromOid(ma.getUserOid());

        MbUser author2 = buildUserFromOid("acct:second@identi.ca");
        author2.avatarUrl = "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png";

        MbUser author3 = buildUserFromOid(DemoData.CONVERSATION_MEMBER_USER_OID);
        author3.setRealName("John Smith");
        author3.setHomepage("http://johnsmith.com/welcome");
        author3.setCreatedDate(new GregorianCalendar(2011,5,12).getTimeInMillis());
        author3.setDescription("I am an ordinary guy, interested in computer science");
        author3.avatarUrl = "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif";
        users.put(author3.oid, author3);

        MbUser author4 = buildUserFromOid("acct:fourthWithoutAvatar@pump.example.com");
        
        MbMessage minus1 = buildMessage(author2, "Older one message", null, null);
        MbMessage selected = buildMessage(getAuthor1(), "Selected message", minus1,
                iteration == 1 ? DemoData.CONVERSATION_ENTRY_MESSAGE_OID : null);
        MbMessage reply1 = buildMessage(author3, "Reply 1 to selected", selected, null);
        reply1.getAuthor().followedByActor = TriState.TRUE;

        MbMessage reply1Copy = MbMessage.fromOriginAndOid(reply1.originId, reply1.myUserOid, reply1.oid,
                DownloadStatus.UNKNOWN);
        MbMessage reply12 = buildMessage(author2, "Reply 12 to 1 in Replies", reply1Copy, null);
        reply1.replies.add(reply12);

        MbMessage reply2 = buildMessage(author2, "Reply 2 to selected is public", selected, null);
        addPublicMessage(reply2, true);
        MbMessage reply3 = buildMessage(getAuthor1(), "Reply 3 to selected by the same author", selected, null);
        reply3.attachments
        .add(MbAttachment
                .fromUrlAndContentType(UrlUtils.fromString(
                        "http://www.publicdomainpictures.net/pictures/100000/nahled/broadcasting-tower-14081029181fC.jpg"),
                        MyContentType.IMAGE));
        addMessage(selected);
        addMessage(reply3);
        addMessage(reply1);
        addMessage(reply2);
        MbMessage reply4 = buildMessage(author4, "Reply 4 to Reply 1 other author", reply1, null);
        addMessage(reply4);
        addPublicMessage(reply4, false);

        final String BODY_OF_MENTIONS_MESSAGE = "@fourthWithoutAvatar@pump.example.com Reply 5 to Reply 4\n"
                + "@" + DemoData.CONVERSATION_MEMBER_USERNAME
                + " @unknownUser@example.com";
        MbMessage reply5 = buildMessage(author2, BODY_OF_MENTIONS_MESSAGE, reply4,
                iteration == 1 ? DemoData.CONVERSATION_MENTIONS_MESSAGE_OID : null);
        addMessage(reply5);

        MbUser reblogger1 = buildUserFromOid("acct:reblogger@identi.ca");
        reblogger1.avatarUrl = "http://www.avatarsdb.com/avatars/cow_face.jpg";
        MbMessage reblog1 = buildMessage(reblogger1, BODY_OF_MENTIONS_MESSAGE, null, null);
        addMessage(MbMessage.makeReblog(reblog1, reply5));

        addMessage(buildMessage(author3, "Reply 6 to Reply 4 - the second", reply4, null).
                setFavoritedByMe(TriState.TRUE));

        MbMessage reply7 = buildMessage(getAuthor1(), "Reply 7 to Reply 2 is about " 
        + DemoData.PUBLIC_MESSAGE_TEXT + " and something else", reply2, null);
        addPublicMessage(reply7, true);
        
        MbMessage reply8 = buildMessage(author4, "<b>Reply 8</b> to Reply 7", reply7, null);

        MbMessage reblog2 = buildMessage(myAuthor, reply8.getBody(), null, null);
        addMessage(MbMessage.makeReblog(reblog2, reply8));

        MbMessage reply9 = buildMessage(author2, "Reply 9 to Reply 7", reply7, null);
        reply9.attachments
                .add(MbAttachment
                        .fromUrlAndContentType( UrlUtils.fromString(
                                "http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg"),
                                MyContentType.IMAGE));
        addMessage(reply9);
        addMessage(buildMessage(author4, "A duplicate of " + reply9.getBody(), null, null));

        // Message downloaded by another account
        MyAccount acc2 = MyContextHolder.get().persistentAccounts().fromAccountName(DemoData.CONVERSATION_ACCOUNT2_NAME);
        MbUser actorOld = author3.actor;
        author3.actor = users.get(DemoData.CONVERSATION_ACCOUNT2_USER_OID);
        author3.followedByActor = TriState.TRUE;
        MbMessage reply10 = buildMessage(acc2, author3, "Reply 10 to Reply 8", reply8, null);
        assertEquals("Another account as a message actor", reply10.myUserOid, DemoData.CONVERSATION_ACCOUNT2_USER_OID);
        assertEquals("Another account as a author actor", reply10.getAuthor().actor.oid, DemoData.CONVERSATION_ACCOUNT2_USER_OID);
        MessageInserter.addMessage(acc2, reply10);
        author3.followedByActor = TriState.UNKNOWN;
        author3.actor = actorOld;

        MbMessage reply11 = buildMessage(author2, "Reply 11 to Reply 7, " + DemoData.GLOBAL_PUBLIC_MESSAGE_TEXT + " text", reply7, null);
        addPublicMessage(reply11, true);

        MbMessage reply13 = buildMessage(myAuthor, "My reply to Reply 2", reply2, null);
        MbMessage reply14 = buildMessage(author3, "Reply to my message 13", reply13, null);
        addMessage(reply14);
    }

    private MbUser getAuthor1() {
        MbUser author1 = buildUserFromOid(DemoData.CONVERSATION_ENTRY_USER_OID);
        author1.avatarUrl = "https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png";
        return author1;
    }
    
    private void addPublicMessage(MbMessage message, boolean isPublic) {
        message.setPublic(isPublic);
        long id = addMessage(message);
        long storedPublic = MyQuery.msgIdToLongColumnValue(MsgTable.PUBLIC, id);
        assertTrue("Message is " + (isPublic ? "public" : "private") + ": " + message.getBody(),
                (isPublic == (storedPublic != 0)));
    }

    private MbUser buildUserFromOid(String userOid) {
        return new MessageInserter(ma).buildUserFromOid(userOid);
    }
    
    private MbMessage buildMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        return buildMessage(ma, author, body, inReplyToMessage, messageOidIn);
    }

    private MbMessage buildMessage(MyAccount ma, MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        return new MessageInserter(ma).buildMessage(author, body
                        + (inReplyToMessage != null ? " it" + iteration : "") + bodySuffix,
                inReplyToMessage, messageOidIn, DownloadStatus.LOADED);
    }

    private long addMessage(MbMessage message) {
        return MessageInserter.addMessage(ma, message);
    }
}
