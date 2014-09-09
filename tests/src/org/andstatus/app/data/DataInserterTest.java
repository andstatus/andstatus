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
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.net.ConnectionStatusNetTest;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;

import java.util.Set;

public class DataInserterTest extends InstrumentationTestCase {
    private Context context;
    private MyAccount mMyAccount;
    private MbUser mAccountMbUser;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        TestSuite.initializeWithData(this);
        context = TestSuite.getMyContextForTest().context();
        mMyAccount = MyContextHolder.get().persistentAccounts().fromAccountName(TestSuite.CONVERSATION_ACCOUNT_NAME); 
        assertTrue(TestSuite.CONVERSATION_ACCOUNT_NAME + " exists", mMyAccount != null);
        Origin origin = MyContextHolder.get().persistentOrigins().fromId(mMyAccount.getOriginId());
        assertTrue("Origin for " + mMyAccount.getAccountName() + " exists", origin != null);

        mAccountMbUser = new MessageInserter(mMyAccount).buildUserFromOid(TestSuite.CONVERSATION_ACCOUNT_USER_OID);
        mAccountMbUser.avatarUrl = TestSuite.CONVERSATION_ACCOUNT_AVATAR_URL;

        assertEquals("Data path", "ok", TestSuite.checkDataPath(this));
    }
    
    public void testFollowingUser() throws ConnectionException {
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        deleteOldMessage(mMyAccount.getOriginId(), messageOid);
        
        CommandExecutionContext counters = new CommandExecutionContext(CommandData.getEmpty(), mMyAccount).setTimelineType(TimelineTypeEnum.HOME);
        DataInserter di = new DataInserter(counters);
        String username = "somebody@identi.ca";
        String userOid =  "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(mMyAccount.getOriginId(), userOid);
        somebody.userName = username;
        somebody.actor = mAccountMbUser;
        somebody.followedByActor = TriState.FALSE;
        somebody.url = "http://identi.ca/somebody";
        di.insertOrUpdateUser(somebody);

        long somebodyId = MyProvider.oidToId(OidEnum.USER_OID, mMyAccount.getOriginId(), userOid);
        assertTrue( "User " + username + " added", somebodyId != 0);
        
        Set<Long> followedIds = MyProvider.getIdsOfUsersFollowedBy(mMyAccount.getUserId());
        assertFalse( "User " + username + " is not followed", followedIds.contains(somebodyId));

        MbMessage message = MbMessage.fromOriginAndOid(mMyAccount.getOriginId(), messageOid);
        message.setBody("The test message by Somebody");
        message.sentDate = 13312696000L;
        message.via = "MyCoolClient";
        message.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        message.sender = somebody;
        message.actor = mAccountMbUser;
        TestSuite.clearAssertionData();
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        AssersionData data = TestSuite.getMyContextForTest().takeDataByKey("insertOrUpdateMsg");
        assertFalse( "Data put", data.isEmpty());
        assertEquals("Message Oid", messageOid, data.getValues().getAsString(MyDatabase.Msg.MSG_OID));
        assertEquals("Message permalink before storage", message.url, data.getValues().getAsString(MyDatabase.Msg.URL));
        assertEquals("Message permalink", message.url, MyContextHolder.get().persistentOrigins().fromId(mMyAccount.getOriginId()).messagePermalink(messageId));

        long authorId = MyProvider.msgIdToLongColumnValue(Msg.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebodyId, authorId);
        String url = MyProvider.msgIdToStringColumnValue(Msg.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyProvider.msgIdToLongColumnValue(Msg.SENDER_ID, messageId);
        assertEquals("Sender of the message", somebodyId, senderId);
        url = MyProvider.userIdToStringColumnValue(User.URL, senderId);
        assertEquals("Url of the sender " + somebody.userName , somebody.url, url);
        
        Uri contentUri = MyProvider.getTimelineUri(mMyAccount.getUserId(), TimelineTypeEnum.FOLLOWING_USER, false);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DEFAULT_SORT_ORDER;
        sa.addSelection(MyDatabase.FollowingUser.FOLLOWING_USER_ID + " = ?",
                new String[] {
                        Long.toString(somebodyId)
                });
        String[] PROJECTION = new String[] {
            Msg._ID
            };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Following timeline", cursor.getCount() == 0);
        cursor.close();
        
        somebody.followedByActor = TriState.TRUE;
        di.insertOrUpdateUser(somebody);

        followedIds = MyProvider.getIdsOfUsersFollowedBy(mMyAccount.getUserId());
        assertTrue( "User " + username + ", id=" + somebodyId + " is followed", followedIds.contains(somebodyId));

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Message by user=" + somebodyId + " is in the Following timeline", cursor.getCount() > 0);
        cursor.close();
    }
    
    public void testDirectMessageToMyAccount() throws ConnectionException {
        String messageOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ";
        deleteOldMessage(mMyAccount.getOriginId(), messageOid);

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(mMyAccount.getOriginId(), "acct:" + username);
        author.userName = username;
        author.actor = mAccountMbUser;
        
        MbMessage message = new MessageInserter(mMyAccount).buildMessage(author, 
                "Hello, this is a test Direct message by your namesake from http://pumpity.net", 
                null, messageOid);
        message.sentDate = 13312699000L;
        message.via = "AnyOtherClient";
        message.recipient = mAccountMbUser;
        long messageId = addMessage(message);
        
        Uri contentUri = MyProvider.getTimelineUri(mMyAccount.getUserId(), TimelineTypeEnum.HOME, false);
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
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertEquals("Recipient " + mMyAccount.getAccountName() + "; Id", mMyAccount.getUserId(), cursor.getLong(0));
        assertEquals("Message is directed to AccountUser", mMyAccount.getUserId(), cursor.getLong(1));
        assertTrue("Message " + messageId + " is direct", cursor.getInt(2) == 1);
        cursor.close();
    }

    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        String username = "anybody@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(mMyAccount.getOriginId(), "acct:" + username);
        author.userName = username;
        author.actor = mAccountMbUser;

        username = "firstreader@identi.ca";
        MbUser firstReader = MbUser.fromOriginAndUserOid(mMyAccount.getOriginId(), "acct:" + username);
        firstReader.userName = username;
        firstReader.actor = mAccountMbUser;
        
        MbMessage message = MbMessage.fromOriginAndOid(mMyAccount.getOriginId(), "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED");
        message.setBody("The test message by Anybody from http://pumpity.net");
        message.sentDate = 13312697000L;
        message.via = "SomeOtherClient";
        message.sender = author;
        message.actor = firstReader;
        message.favoritedByActor = TriState.TRUE;

        DataInserter di = new DataInserter(mMyAccount);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        
        Uri contentUri = MyProvider.getTimelineUri(mMyAccount.getUserId(), TimelineTypeEnum.HOME, false);
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
        MbUser author = MbUser.fromOriginAndUserOid(mMyAccount.getOriginId(), "acct:" + username);
        author.userName = username;
        author.actor = mAccountMbUser;

        MbMessage message = MbMessage.fromOriginAndOid(mMyAccount.getOriginId(), "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123");
        message.setBody("The test message by Example from the http://pumpity.net");
        message.sentDate = 13312795000L;
        message.via = "UnknownClient";
        message.sender = author;
        message.actor = mAccountMbUser;
        message.favoritedByActor = TriState.TRUE;

        DataInserter di = new DataInserter(mMyAccount);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        
        Uri contentUri = MyProvider.getTimelineUri(mMyAccount.getUserId(), TimelineTypeEnum.HOME, false);
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
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection, sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertTrue("Message favorited", cursor.getInt(0) == 1);
        assertTrue("Message not favorited by AccountUser", cursor.getLong(1) == mMyAccount.getUserId());
        cursor.close();
    }

    public void testMessageWithAttachment() throws Exception {
        MbMessage message = ConnectionStatusNetTest.getMessageWithAttachment(this.getInstrumentation().getContext());

        MyAccount myAccount = MyContextHolder.get().persistentAccounts().findFirstMyAccountByOriginId(message.originId);
        DataInserter di = new DataInserter(myAccount);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added", messageId != 0);
        
        DownloadData dd = DownloadData.newForMessage(messageId, message.attachments.get(0).contentType, null);
        assertEquals("Image URL stored", message.attachments.get(0).url , dd.getUrl());
    }
    
    private long addMessage(MbMessage message) {
        return new MessageInserter(mMyAccount).addMessage(message);
    }
    
    private void deleteOldMessage(long originId, String messageOid) {
        long messageIdOld = MyProvider.oidToId(OidEnum.MSG_OID, originId, messageOid);
        if (messageIdOld != 0) {
            SelectionAndArgs sa = new SelectionAndArgs();
            sa.addSelection(MyDatabase.Msg._ID + " = ?", new String[] {
                String.valueOf(messageIdOld)
            });
            int deleted = context.getContentResolver().delete(MyProvider.MSG_CONTENT_URI, sa.selection,
                    sa.selectionArgs);
            assertEquals( "Old message id=" + messageIdOld + " deleted", 1, deleted);
        }
    }

}
