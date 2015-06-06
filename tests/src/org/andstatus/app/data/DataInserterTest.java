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
import android.database.Cursor;
import android.net.Uri;
import android.test.InstrumentationTestCase;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.ConnectionGnuSocialTest;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;

import java.util.Set;

public class DataInserterTest extends InstrumentationTestCase {
    private Context context;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        context = TestSuite.getMyContextForTest().context();
        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
    }

    public void testFollowingUser() throws ConnectionException {
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        MessageInserter.deleteOldMessage(TestSuite.getConversationOriginId(), messageOid);

        CommandExecutionContext counters = new CommandExecutionContext(CommandData.getEmpty(),
                TestSuite.getConversationMyAccount()).setTimelineType(TimelineType.HOME);
        DataInserter di = new DataInserter(counters);
        String username = "somebody@identi.ca";
        String userOid = "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(), userOid);
        somebody.setUserName(username);
        somebody.actor = TestSuite.getConversationMbUser();
        somebody.followedByActor = TriState.FALSE;
        somebody.setUrl("http://identi.ca/somebody");
        di.insertOrUpdateUser(somebody);

        long somebodyId = MyQuery.oidToId(OidEnum.USER_OID, TestSuite.getConversationOriginId(), userOid);
        assertTrue("User " + username + " added", somebodyId != 0);

        Set<Long> followedIds = MyQuery.getIdsOfUsersFollowedBy(TestSuite
                .getConversationMyAccount().getUserId());
        assertFalse("User " + username + " is not followed", followedIds.contains(somebodyId));

        MbMessage message = MbMessage.fromOriginAndOid(TestSuite.getConversationOriginId(), messageOid);
        message.setBody("The test message by Somebody");
        message.sentDate = 13312696000L;
        message.via = "MyCoolClient";
        message.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        message.sender = somebody;
        message.actor = TestSuite.getConversationMbUser();
        TestSuite.clearAssertionData();
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);
        AssersionData data = TestSuite.getMyContextForTest().takeDataByKey("insertOrUpdateMsg");
        assertFalse("Data put", data.isEmpty());
        assertEquals("Message Oid", messageOid, data.getValues()
                .getAsString(MyDatabase.Msg.MSG_OID));
        assertEquals("Message permalink before storage", message.url,
                data.getValues().getAsString(MyDatabase.Msg.URL));
        assertEquals(
                "Message permalink",
                message.url,
                MyContextHolder.get().persistentOrigins()
                        .fromId(TestSuite.getConversationOriginId())
                        .messagePermalink(messageId));

        long authorId = MyQuery.msgIdToLongColumnValue(Msg.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebodyId, authorId);
        String url = MyQuery.msgIdToStringColumnValue(Msg.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyQuery.msgIdToLongColumnValue(Msg.SENDER_ID, messageId);
        assertEquals("Sender of the message", somebodyId, senderId);
        url = MyQuery.userIdToStringColumnValue(User.URL, senderId);
        assertEquals("Url of the sender " + somebody.getUserName(), somebody.getUrl(), url);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.FOLLOWING_USER,
                false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.FollowingUser.FOLLOWING_USER_ID + " = ?",
                new String[] {
                    Long.toString(somebodyId)
                });
        String[] PROJECTION = new String[] {
                Msg._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Following timeline", cursor.getCount() == 0);
        cursor.close();

        somebody.followedByActor = TriState.TRUE;
        di.insertOrUpdateUser(somebody);

        followedIds = MyQuery.getIdsOfUsersFollowedBy(TestSuite.getConversationMyAccount()
                .getUserId());
        assertTrue("User " + username + ", id=" + somebodyId + " is followed",
                followedIds.contains(somebodyId));

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Message by user=" + somebodyId + " is in the Following timeline",
                cursor.getCount() > 0);
        cursor.close();
    }

    public void testDirectMessageToMyAccount() throws ConnectionException {
        String messageOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ";
        MessageInserter.deleteOldMessage(TestSuite.getConversationOriginId(), messageOid);

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(), "acct:"
                + username);
        author.setUserName(username);
        author.actor = TestSuite.getConversationMbUser();

        MbMessage message = new MessageInserter(TestSuite.getConversationMyAccount()).buildMessage(
                author,
                "Hello, this is a test Direct message by your namesake from http://pumpity.net",
                null, messageOid);
        message.sentDate = 13312699000L;
        message.via = "AnyOtherClient";
        message.recipient = TestSuite.getConversationMbUser();
        long messageId = new MessageInserter(TestSuite.getConversationMyAccount())
                .addMessage(message);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.HOME, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[] {
                    Long.toString(messageId)
                });
        String[] PROJECTION = new String[] {
                Msg.RECIPIENT_ID,
                MyDatabase.User.LINKED_USER_ID,
                MsgOfUser.DIRECTED
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertEquals("Recipient " + TestSuite.getConversationMyAccount().getAccountName() + "; Id",
                TestSuite.getConversationMyAccount().getUserId(), cursor.getLong(0));
        assertEquals("Message is directed to AccountUser", TestSuite.getConversationMyAccount()
                .getUserId(), cursor.getLong(1));
        assertTrue("Message " + messageId + " is direct", cursor.getInt(2) == 1);
        cursor.close();
    }

    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        String username = "anybody@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(), "acct:"
                + username);
        author.setUserName(username);
        author.actor = TestSuite.getConversationMbUser();

        username = "firstreader@identi.ca";
        MbUser firstReader = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(),
                "acct:" + username);
        firstReader.setUserName(username);
        firstReader.actor = TestSuite.getConversationMbUser();

        MbMessage message = MbMessage.fromOriginAndOid(TestSuite.getConversationOriginId(),
                "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED");
        message.setBody("The test message by Anybody from http://pumpity.net");
        message.sentDate = 13312697000L;
        message.via = "SomeOtherClient";
        message.sender = author;
        message.actor = firstReader;
        message.favoritedByActor = TriState.TRUE;

        DataInserter di = new DataInserter(TestSuite.getConversationMyAccount());
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.HOME, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[] {
                    Long.toString(messageId)
                });
        String[] PROJECTION = new String[] {
                MsgOfUser.FAVORITED,
                MyDatabase.User.LINKED_USER_ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertEquals("MsgOfUser was not created, msgId=" + messageId, 0, cursor.getCount());
        cursor.close();
    }

    public void testMessageFavoritedByAccountUser() throws ConnectionException {
        String username = "example@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(), "acct:"
                + username);
        author.setUserName(username);
        author.actor = TestSuite.getConversationMbUser();

        MbMessage message = MbMessage.fromOriginAndOid(TestSuite.getConversationOriginId(),
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123");
        message.setBody("The test message by Example from the http://pumpity.net");
        message.sentDate = 13312795000L;
        message.via = "UnknownClient";
        message.sender = author;
        message.actor = TestSuite.getConversationMbUser();
        message.favoritedByActor = TriState.TRUE;

        DataInserter di = new DataInserter(TestSuite.getConversationMyAccount());
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.HOME, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[] {
                    Long.toString(messageId)
                });
        String[] PROJECTION = new String[] {
                MsgOfUser.FAVORITED,
                MyDatabase.User.LINKED_USER_ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId + ", count=" + cursor.getCount(), cursor.getCount() == 1);
        cursor.moveToFirst();
        assertTrue("Message favorited", cursor.getInt(0) == 1);
        assertTrue("Message not favorited by AccountUser", cursor.getLong(1) == TestSuite
                .getConversationMyAccount().getUserId());
        cursor.close();
    }

    public void testMessageWithAttachment() throws Exception {
        MbMessage message = ConnectionGnuSocialTest.getMessageWithAttachment(this
                .getInstrumentation().getContext());

        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .findFirstSucceededMyAccountByOriginId(message.originId);
        DataInserter di = new DataInserter(ma);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);

        DownloadData dd = DownloadData.newForMessage(messageId,
                message.attachments.get(0).contentType, null);
        assertEquals("Image URL stored", message.attachments.get(0).getUrl(), dd.getUrl());
    }

    public void testUserNameChanged() {
        MyAccount ma = TestSuite.getMyContextForTest().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME); 
        String username = "peter" + TestSuite.TESTRUN_UID;
        MbUser user1 = new MessageInserter(ma).buildUserFromOid("34804" + TestSuite.TESTRUN_UID);
        user1.setUserName(username);
        
        DataInserter di = new DataInserter(ma);
        long userId1 = di.insertOrUpdateUser(user1);
        assertTrue("User added", userId1 != 0);
        assertEquals("Username stored", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId1));

        MbUser user1partial = MbUser.fromOriginAndUserOid(user1.originId, user1.oid);
        long userId1partial = di.insertOrUpdateUser(user1partial);
        assertEquals("Same user", userId1, userId1partial);
        assertEquals("Username didn't change", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId1));

        MbUser user1Renamed = user1;
        user1Renamed.setUserName(user1Renamed.getUserName() + "renamed");
        long userId1Renamed = di.insertOrUpdateUser(user1Renamed);
        assertEquals("Same user renamed", userId1, userId1Renamed);
        assertEquals("Same user renamed", user1Renamed.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId1));

        MbUser user2SameOldUserName = new MessageInserter(ma).buildUserFromOid("34805"
                + TestSuite.TESTRUN_UID);
        user2SameOldUserName.setUserName(username);
        long userId2 = di.insertOrUpdateUser(user2SameOldUserName);
        assertTrue("Other user with the same user name as old name of user", userId1 != userId2);
        assertEquals("Username stored", user2SameOldUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId2));

        MbUser user3SameNewUserName = new MessageInserter(ma).buildUserFromOid("34806"
                + TestSuite.TESTRUN_UID);
        user3SameNewUserName.setUserName(user1Renamed.getUserName());
        long userId3 = di.insertOrUpdateUser(user3SameNewUserName);
        assertTrue("User added " + user3SameNewUserName, userId3 != 0);
        assertTrue("Other user with the same user name as old name of user", userId1 != userId3);
        assertEquals("Username stored for userId=" + userId3, user3SameNewUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId3));
    }
}
