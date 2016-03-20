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
import org.andstatus.app.data.MyDatabase.Msg;
import org.andstatus.app.data.MyDatabase.MsgOfUser;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.ConnectionGnuSocialTest;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.service.AttachmentDownloaderTest;
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

    public void testFriends() throws ConnectionException {
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
        somebody.setProfileUrl("http://identi.ca/somebody");
        di.insertOrUpdateUser(somebody);

        long somebodyId = MyQuery.oidToId(OidEnum.USER_OID, TestSuite.getConversationOriginId(),
                userOid);
        assertTrue("User " + username + " added", somebodyId != 0);

        Set<Long> followedIds = MyQuery.getFriendsIds(TestSuite
                .getConversationMyAccount().getUserId());
        assertFalse("User " + username + " is not followed", followedIds.contains(somebodyId));

        MbMessage message = MbMessage.fromOriginAndOid(TestSuite.getConversationOriginId(),
                messageOid, DownloadStatus.LOADED);
        message.setBody("The test message by Somebody");
        message.sentDate = 13312696000L;
        message.via = "MyCoolClient";
        message.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        message.sender = somebody;
        message.actor = TestSuite.getConversationMbUser();

        TestSuite.clearAssertionData();
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);
        AssertionData data = TestSuite.getMyContextForTest().takeDataByKey(DataInserter.MSG_ASSERTION_KEY);
        assertFalse("Data put", data.isEmpty());
        assertEquals("Message Oid", messageOid, data.getValues()
                .getAsString(MyDatabase.Msg.MSG_OID));
        assertEquals("Message is loaded", DownloadStatus.LOADED, DownloadStatus.load(data.getValues().getAsInteger(Msg.MSG_STATUS)));
        assertEquals("Message permalink before storage", message.url,
                data.getValues().getAsString(MyDatabase.Msg.URL));
        assertEquals(
                "Message permalink",
                message.url,
                MyContextHolder.get().persistentOrigins()
                        .fromId(TestSuite.getConversationOriginId())
                        .messagePermalink(messageId));

        assertEquals("Message stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(Msg.MSG_STATUS, messageId)));
        long authorId = MyQuery.msgIdToLongColumnValue(Msg.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebodyId, authorId);
        String url = MyQuery.msgIdToStringColumnValue(Msg.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyQuery.msgIdToLongColumnValue(Msg.SENDER_ID, messageId);
        assertEquals("Sender of the message", somebodyId, senderId);
        url = MyQuery.userIdToStringColumnValue(User.PROFILE_URL, senderId);
        assertEquals("Url of the sender " + somebody.getUserName(), somebody.getProfileUrl(), url);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.FRIENDS,
                false, 0);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DESC_SORT_ORDER;
        sa.addSelection("fUserId = ?",
                new String[] {
                    Long.toString(somebodyId)
                });
        String[] PROJECTION = new String[] {
                Msg._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Friends timeline", cursor.getCount() == 0);
        cursor.close();

        somebody.followedByActor = TriState.TRUE;
        di.insertOrUpdateUser(somebody);

        followedIds = MyQuery.getFriendsIds(TestSuite.getConversationMyAccount()
                .getUserId());
        assertTrue("User " + username + ", id=" + somebodyId + " is followed",
                followedIds.contains(somebodyId));

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Message by user=" + somebodyId + " is in the Friends timeline",
                cursor.getCount() > 0);
        cursor.close();

        MyContextHolder.get().persistentAccounts().initialize();
        for (long id : followedIds) {
            assertTrue("isFriend: " + id, MyContextHolder.get().persistentAccounts().isMeOrMyFriend(id));
        }
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
                null, messageOid, DownloadStatus.LOADED);
        message.sentDate = 13312699000L;
        message.via = "AnyOtherClient";
        message.recipient = TestSuite.getConversationMbUser();
        long messageId = new MessageInserter(TestSuite.getConversationMyAccount())
                .addMessage(message);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.HOME, false, 0);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DESC_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[]{
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
                "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED", DownloadStatus.LOADED);
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
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.HOME, false, 0);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DESC_SORT_ORDER;
        sa.addSelection(MyDatabase.Msg.MSG_ID + " = ?",
                new String[]{
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
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123", DownloadStatus.LOADED);
        message.setBody("The test message by Example from the http://pumpity.net");
        message.sentDate = 13312795000L;
        message.via = "UnknownClient";
        message.sender = author;
        message.actor = TestSuite.getConversationMbUser();
        message.favoritedByActor = TriState.TRUE;

        String inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB";
        MbMessage inReplyTo = MbMessage.fromOriginAndOid(TestSuite.getConversationOriginId(),
                inReplyToOid, DownloadStatus.UNKNOWN);
        inReplyTo.sender = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(),
                "irtUser" + TestSuite.TESTRUN_UID).setUserName("irt" + username);
        inReplyTo.actor = message.actor;
        message.inReplyToMessage = inReplyTo;

        DataInserter di = new DataInserter(TestSuite.getConversationMyAccount());
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);

        Uri contentUri = MatchedUri.getTimelineUri(
                TestSuite.getConversationMyAccount().getUserId(), TimelineType.HOME, false, 0);
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MyDatabase.Msg.DESC_SORT_ORDER;
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

        assertEquals("Message stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(Msg.MSG_STATUS, messageId)));
        long inReplyToId = MyQuery.oidToId(OidEnum.MSG_OID, TestSuite.getConversationOriginId(),
                inReplyToOid);
        assertTrue("In reply to message added", inReplyToId != 0);
        assertEquals("Message reply status is unknown", DownloadStatus.UNKNOWN, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(Msg.MSG_STATUS, inReplyToId)));
    }

    public void testMessageWithAttachment() throws Exception {
        MbMessage message = ConnectionGnuSocialTest.getMessageWithAttachment(this
                .getInstrumentation().getContext());

        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .findFirstSucceededMyAccountByOriginId(message.originId);
        DataInserter di = new DataInserter(ma);
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);

        DownloadData dd = DownloadData.getSingleForMessage(messageId,
                message.attachments.get(0).contentType, null);
        assertEquals("Image URI stored", message.attachments.get(0).getUri(), dd.getUri());
    }

    public void testUnsentMessageWithAttachment() throws Exception {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .findFirstSucceededMyAccountByOriginId(0);
        MbMessage message = MbMessage.fromOriginAndOid(ma.getOriginId(), "",
                DownloadStatus.SENDING);
        message.actor = MbUser.fromOriginAndUserOid(ma.getOriginId(), ma.getUserOid());
        message.sender = message.actor;
        message.sentDate = System.currentTimeMillis();
        final String body = "Unsent message with an attachment " + TestSuite.TESTRUN_UID;
        message.setBody(body);
        message.attachments.add(MbAttachment.fromUriAndContentType(TestSuite.LOCAL_IMAGE_TEST_URI,
                MyContentType.IMAGE));
        DataInserter di = new DataInserter(ma);
        message.msgId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", message.msgId != 0);
        assertEquals("Status of unsent message", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.MSG_STATUS, message.msgId)));

        DownloadData dd = DownloadData.getSingleForMessage(message.msgId,
                message.attachments.get(0).contentType, null);
        assertEquals("Image URI stored", message.attachments.get(0).getUri(), dd.getUri());
        assertEquals("Local image immediately loaded " + dd, DownloadStatus.LOADED, dd.getStatus());

        Thread.sleep(1000);

        final String oid = "unsentMsgOid" + TestSuite.TESTRUN_UID;
        MbMessage message2 = MbMessage.fromOriginAndOid(ma.getOriginId(), oid,
                DownloadStatus.LOADED);
        message2.actor = message.actor;
        message2.sender = message2.actor;
        message2.sentDate = System.currentTimeMillis();
        final String body2 = "Unsent <b>message</b> with an attachment loaded " + TestSuite.TESTRUN_UID;
        message2.setBody(body2);
        message2.attachments.add(MbAttachment.fromUriAndContentType(TestSuite.IMAGE1_URL,
                MyContentType.IMAGE));
        message2.msgId = message.msgId;

        long rowId2 = di.insertOrUpdateMsg(message2);
        assertEquals("Row id didn't change", message.msgId, message2.msgId);
        assertEquals("Message updated", message.msgId, rowId2);
        assertEquals("Status of loaded message", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.MSG_STATUS, message2.msgId)));

        DownloadData dd2 = DownloadData.getSingleForMessage(message2.msgId,
                message2.attachments.get(0).contentType, null);
        assertEquals("New image URI stored", message2.attachments.get(0).getUri(), dd2.getUri());

        assertEquals("Not loaded yet. " + dd2, DownloadStatus.ABSENT, dd2.getStatus());
        AttachmentDownloaderTest.loadAndAssertStatusForRow(dd2.getDownloadId(), DownloadStatus.LOADED, false);
    }

    public void testUserNameChanged() {
        MyAccount ma = TestSuite.getMyContextForTest().persistentAccounts().fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME); 
        String username = "peter" + TestSuite.TESTRUN_UID;
        MbUser user1 = new MessageInserter(ma).buildUserFromOid("34804" + TestSuite.TESTRUN_UID);
        user1.setUserName(username);
        user1.setProfileUrl("https://" + TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME + ".example.com/");
        
        DataInserter di = new DataInserter(ma);
        long userId1 = di.insertOrUpdateUser(user1);
        assertTrue("User added", userId1 != 0);
        assertEquals("Username stored", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId1));

        MbUser user1partial = MbUser.fromOriginAndUserOid(user1.originId, user1.oid);
        assertTrue("Partially defined", user1partial.isPartiallyDefined());
        long userId1partial = di.insertOrUpdateUser(user1partial);
        assertEquals("Same user", userId1, userId1partial);
        assertEquals("Partially defined user shouldn't change Username", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId1));
        assertEquals("Partially defined user shouldn't change WebfingerId", user1.getWebFingerId(),
                MyQuery.userIdToStringColumnValue(User.WEBFINGER_ID, userId1));
        assertEquals("Partially defined user shouldn't change Real name", user1.getRealName(),
                MyQuery.userIdToStringColumnValue(User.REAL_NAME, userId1));

        user1.setUserName(user1.getUserName() + "renamed");
        long userId1Renamed = di.insertOrUpdateUser(user1);
        assertEquals("Same user renamed", userId1, userId1Renamed);
        assertEquals("Same user renamed", user1.getUserName(),
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
        user3SameNewUserName.setUserName(user1.getUserName());
        user3SameNewUserName.setProfileUrl("https://" + TestSuite.GNUSOCIAL_TEST_ORIGIN_NAME + ".other.example.com/");
        long userId3 = di.insertOrUpdateUser(user3SameNewUserName);
        assertTrue("User added " + user3SameNewUserName, userId3 != 0);
        assertTrue("Other user with the same user name as the new name of user1, but different WebFingerId", userId1 != userId3);
        assertEquals("Username stored for userId=" + userId3, user3SameNewUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, userId3));
    }

    public void testInsertUser() {
        MyAccount ma = TestSuite.getMyContextForTest().persistentAccounts()
                .fromAccountName(TestSuite.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser user = new MessageInserter(ma).buildUserFromOid("34807" + TestSuite.TESTRUN_UID);

        DataInserter di = new DataInserter(ma);
        long id = di.insertOrUpdateUser(user);
        assertTrue("User added", id != 0);
        assertEquals("Username", user.getUserName(),
                MyQuery.userIdToStringColumnValue(MyDatabase.User.USERNAME, id));
        assertEquals("oid", user.oid,
                MyQuery.userIdToStringColumnValue(User.USER_OID, id));
        assertEquals("Display name", user.getRealName(),
                MyQuery.userIdToStringColumnValue(User.REAL_NAME, id));
        assertEquals("Location", user.location,
                MyQuery.userIdToStringColumnValue(User.LOCATION, id));
        assertEquals("profile image URL", user.avatarUrl,
                MyQuery.userIdToStringColumnValue(User.AVATAR_URL, id));
        assertEquals("profile URL", user.getProfileUrl(),
                MyQuery.userIdToStringColumnValue(User.PROFILE_URL, id));
        assertEquals("Banner URL", user.bannerUrl,
                MyQuery.userIdToStringColumnValue(User.BANNER_URL, id));
        assertEquals("Homepage", user.getHomepage(),
                MyQuery.userIdToStringColumnValue(User.HOMEPAGE, id));
        assertEquals("WebFinger ID", user.getWebFingerId(),
                MyQuery.userIdToStringColumnValue(User.WEBFINGER_ID, id));
        assertEquals("Description", user.getDescription(),
                MyQuery.userIdToStringColumnValue(User.DESCRIPTION, id));
        assertEquals("Messages count", user.msgCount,
                MyQuery.userIdToLongColumnValue(User.MSG_COUNT, id));
        assertEquals("Favorites count", user.favoritesCount,
                MyQuery.userIdToLongColumnValue(User.FAVORITES_COUNT, id));
        assertEquals("Following (friends) count", user.followingCount,
                MyQuery.userIdToLongColumnValue(User.FOLLOWING_COUNT, id));
        assertEquals("Followers count", user.followersCount,
                MyQuery.userIdToLongColumnValue(User.FOLLOWERS_COUNT, id));
        assertEquals("Created at", user.getCreatedDate(),
                MyQuery.userIdToLongColumnValue(User.CREATED_DATE, id));
        assertEquals("Created at", user.getUpdatedDate(),
                MyQuery.userIdToLongColumnValue(User.UPDATED_DATE, id));
    }

    public void testReplyInBody() {
        String buddyUserName = "buddy" +  TestSuite.TESTRUN_UID + "@example.com";
        String body = "@" + buddyUserName + " I'm replying to you in a message body."
                + " Hope you will see this as a real reply!";
        addOneMessage4testReplyInBody(buddyUserName, body, true);

        addOneMessage4testReplyInBody(buddyUserName, "Oh, " + body, false);

        long userId1 = MyQuery.webFingerIdToId(TestSuite.getConversationMyAccount().getOriginId(), buddyUserName);
        assertEquals("User has temp Oid", MbUser.getTempOid(buddyUserName, ""), MyQuery.idToOid(OidEnum.USER_OID, userId1, 0));

        String realBuddyOid = "acc:" + buddyUserName;
        MbUser user = MbUser.fromOriginAndUserOid(TestSuite.getConversationMyAccount().getOriginId(), realBuddyOid);
        user.setUserName(buddyUserName);
        DataInserter di = new DataInserter(TestSuite.getConversationMyAccount());
        long userId2 = di.insertOrUpdateUser(user);
        assertEquals(userId1, userId2);
        assertEquals("TempOid replaced with real", realBuddyOid, MyQuery.idToOid(OidEnum.USER_OID, userId1, 0));

        body = "<a href=\"http://example.com/a\">@" + buddyUserName + "</a>, this is an HTML <i>formatted</i> message";
        addOneMessage4testReplyInBody(buddyUserName, body, true);

        buddyUserName = TestSuite.CONVERSATION_MEMBER_USERNAME;
        body = "@" + buddyUserName + " I know you are already in our cache";
        addOneMessage4testReplyInBody(buddyUserName, body, true);
    }

    protected void addOneMessage4testReplyInBody(String buddyUserName, String body, boolean isReply) {
        DataInserter di = new DataInserter(TestSuite.getConversationMyAccount());
        String username = "somebody" + TestSuite.TESTRUN_UID + "@somewhere.net";
        String userOid = "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(TestSuite.getConversationOriginId(), userOid);
        somebody.setUserName(username);
        somebody.actor = TestSuite.getConversationMbUser();
        somebody.setProfileUrl("https://somewhere.net/" + username);

        MbMessage message = MbMessage.fromOriginAndOid(TestSuite.getConversationOriginId(),
                String.valueOf(System.nanoTime()), DownloadStatus.LOADED);
        message.setBody(body);
        message.sentDate = System.currentTimeMillis();
        message.via = "MyCoolClient";
        message.sender = somebody;
        message.actor = TestSuite.getConversationMbUser();

        long messageId = di.insertOrUpdateMsg(message);
        assertTrue("Message added", messageId != 0);
        long buddyId = MyQuery.msgIdToLongColumnValue(Msg.IN_REPLY_TO_USER_ID, messageId);
        if (isReply) {
            assertTrue("@username at the beginning of a message body is treated as a reply body:'"
                    + message.getBody() + "'", buddyId != 0);
            assertEquals(buddyUserName, MyQuery.userIdToStringColumnValue(User.USERNAME, buddyId));
        } else {
            assertTrue("Don't treat this message as a reply:'"
                    + message.getBody() + "'", buddyId == 0);
        }
    }
}
