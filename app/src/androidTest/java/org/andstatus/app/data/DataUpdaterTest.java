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

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.DemoData;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.ConnectionGnuSocialTest;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.service.AttachmentDownloaderTest;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataUpdaterTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        context = TestSuite.getMyContextForTest().context();
        DemoData.checkDataPath();
    }

    @Test
    public void testFriends() throws ConnectionException {
        MyAccount ma = DemoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        DemoMessageInserter.deleteOldMessage(accountUser.originId, messageOid);

        CommandExecutionContext counters = new CommandExecutionContext(
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma));
        DataUpdater di = new DataUpdater(counters);
        String username = "somebody" + DemoData.TESTRUN_UID + "@identi.ca";
        String userOid = "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(accountUser.originId, userOid);
        somebody.setUserName(username);
        somebody.followedByActor = TriState.FALSE;
        somebody.setProfileUrl("http://identi.ca/somebody");
        di.onActivity(somebody.update(accountUser, accountUser));

        somebody.userId = MyQuery.oidToId(OidEnum.USER_OID, accountUser.originId, userOid);
        assertTrue("User " + username + " added", somebody.userId != 0);
        DemoConversationInserter.assertIfUserIsMyFriend(somebody, false, ma);

        MbMessage message = MbMessage.fromOriginAndOid(accountUser.originId, messageOid, DownloadStatus.LOADED);
        message.setBody("The test message by Somebody");
        message.setUpdatedDate(13312696000L);
        message.via = "MyCoolClient";
        message.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        message.setAuthor(somebody);

        TestSuite.clearAssertionData();
        long messageId = di.onActivity(message.update(accountUser)).getMessage().msgId;
        assertTrue("Message added", messageId != 0);
        AssertionData data = TestSuite.getMyContextForTest().takeDataByKey(DataUpdater.MSG_ASSERTION_KEY);
        assertFalse("Data put", data.isEmpty());
        assertEquals("Message Oid", messageOid, data.getValues()
                .getAsString(MsgTable.MSG_OID));
        assertEquals("Message is loaded", DownloadStatus.LOADED, DownloadStatus.load(data.getValues().getAsInteger(MsgTable.MSG_STATUS)));
        assertEquals("Message permalink before storage", message.url,
                data.getValues().getAsString(MsgTable.URL));
        assertEquals(
                "Message permalink",
                message.url,
                MyContextHolder.get().persistentOrigins()
                        .fromId(accountUser.originId)
                        .messagePermalink(messageId));

        assertEquals("Message stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, messageId)));
        long authorId = MyQuery.msgIdToLongColumnValue(MsgTable.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebody.userId, authorId);
        String url = MyQuery.msgIdToStringColumnValue(MsgTable.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyQuery.msgIdToLongColumnValue(MsgTable.ACTOR_ID, messageId);
        assertEquals("Sender of the message", somebody.userId, senderId);
        url = MyQuery.userIdToStringColumnValue(UserTable.PROFILE_URL, senderId);
        assertEquals("Url of the author " + somebody.getUserName(), somebody.getProfileUrl(), url);

        Uri contentUri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.MY_FRIENDS, ma, 0, null));
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MsgTable.DESC_SORT_ORDER;
        sa.addSelection("fUserId = ?", Long.toString(somebody.userId));
        String[] PROJECTION = new String[] {
                MsgTable._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Friends timeline", cursor != null && cursor.getCount() == 0);
        cursor.close();

        somebody.followedByActor = TriState.TRUE;
        di.onActivity(somebody.update(accountUser, accountUser));
        DemoConversationInserter.assertIfUserIsMyFriend(somebody, true, ma);

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Message by user=" + somebody.userId + " is not in the Friends timeline",
                cursor != null && cursor.getCount() > 0);
        cursor.close();

        Set<Long> friendsIds = MyQuery.getFriendsIds(ma.getUserId());
        MyContextHolder.get().persistentAccounts().initialize();
        for (long id : friendsIds) {
            assertTrue("isFriend: " + id, MyContextHolder.get().persistentAccounts().isMeOrMyFriend(id));
        }
    }

    @Test
    public void testDirectMessageToMyAccount() throws ConnectionException {
        MyAccount ma = DemoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        String messageOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ";
        DemoMessageInserter.deleteOldMessage(accountUser.originId, messageOid);

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:"
                + username);
        author.setUserName(username);

        MbMessage message = new DemoMessageInserter(accountUser).buildMessage(
                author,
                "Hello, this is a test Direct message by your namesake from http://pumpity.net",
                null, messageOid, DownloadStatus.LOADED);
        message.setUpdatedDate(13312699000L);
        message.via = "AnyOtherClient";
        message.setRecipient(accountUser);
        long messageId = DemoMessageInserter.onActivityS(message.update(accountUser));

        Uri contentUri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.HOME, ma, 0, null));
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MsgTable.DESC_SORT_ORDER;
        sa.addSelection(MsgTable.MSG_ID + " = ?", Long.toString(messageId));
        String[] PROJECTION = new String[] {
                MsgTable.RECIPIENT_ID,
                UserTable.LINKED_USER_ID,
                MsgOfUserTable.DIRECTED
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId, cursor.getCount() == 1);
        cursor.moveToFirst();
        assertEquals("Recipient " + ma.getAccountName() + "; Id",
                ma.getUserId(), cursor.getLong(0));
        assertEquals("Message is directed to AccountUser", ma
                .getUserId(), cursor.getLong(1));
        assertTrue("Message " + messageId + " is direct", cursor.getInt(2) == 1);
        cursor.close();
    }

    @Test
    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        MyAccount ma = DemoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        String authorUserName = "anybody@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:"
                + authorUserName);
        author.setUserName(authorUserName);

        String actorUserName = "firstreader@identi.ca";
        MbUser otherUser = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:" + actorUserName);
        otherUser.setUserName(actorUserName);

        MbMessage message = MbMessage.fromOriginAndOid(accountUser.originId,
                "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED" +  DemoData.TESTRUN_UID,
                DownloadStatus.LOADED);
        message.setBody("The test message by Anybody from http://pumpity.net");
        message.setUpdatedDate(13312697000L);
        message.via = "SomeOtherClient";
        message.setAuthor(author);

        MbActivity activity = MbActivity.from(accountUser, MbActivityType.LIKE);
        activity.setActor(otherUser);
        activity.setMessage(message);
        message.setFavorited(TriState.TRUE);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(activity).getMessage().msgId;
        assertTrue("Message added", messageId != 0);

        Uri contentUri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, null, 0, ma.getOrigin()));
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MsgTable.DESC_SORT_ORDER;
        sa.addSelection(MsgTable._ID + " = ?", Long.toString(messageId));
        String[] PROJECTION = new String[] {
                MsgOfUserTable.FAVORITED,
                UserTable.LINKED_USER_ID,
                MsgOfUserTable.SUBSCRIBED,
                MsgTable._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        boolean messageFound = false;
        MyAccount meLinked = MyAccount.EMPTY;
        boolean linkedToActorFound = false;
        while (cursor.moveToNext()) {
            assertEquals("Message with other id returned", messageId, cursor.getLong(3));
            messageFound = true;
            MyAccount me = MyContextHolder.get().persistentAccounts().fromUserId(cursor.getLong(1));
            if (me.isValid()) {
                meLinked = me;
                assertEquals("Message is favorited by me: " + me, 0, cursor.getLong(0));
                assertEquals("Message is subscribed by me: " + me, 0, cursor.getLong(2));
            } else if (cursor.getLong(1) == otherUser.userId) {
                linkedToActorFound = true;
                assertEquals("Message is not favorited by: " + otherUser, 1, cursor.getLong(0));
            }
        }
        assertTrue("Message is not in Everything timeline, msgId=" + messageId, messageFound);
        // TODO: Remember that other User favorited something, so the below will be assertTrue
        assertFalse("Linked to " + otherUser + " (this is not expected yet)", linkedToActorFound);
        assertFalse("Linked to my account " + meLinked + " (this is not expected yet)", meLinked.isValid());
        cursor.close();
    }

    @Test
    public void testMessageFavoritedByAccountUser() throws ConnectionException {
        MyAccount ma = DemoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        String authorUserName = "example@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:"
                + authorUserName);
        author.setUserName(authorUserName);

        MbMessage message = MbMessage.fromOriginAndOid(accountUser.originId,
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123", DownloadStatus.LOADED);
        message.setBody("The test message by Example from the http://pumpity.net");
        message.setUpdatedDate(13312795000L);
        message.via = "UnknownClient";
        message.setAuthor(author);
        message.setFavoritedByMe(TriState.TRUE);

        String inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB";
        MbMessage inReplyTo = MbMessage.fromOriginAndOid(accountUser.originId,
                inReplyToOid, DownloadStatus.UNKNOWN);
        inReplyTo.setAuthor(MbUser.fromOriginAndUserOid(accountUser.originId,
                "irtUser" + DemoData.TESTRUN_UID).setUserName("irt" + authorUserName));
        message.setInReplyTo(inReplyTo);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(message.update(accountUser)).getMessage().msgId;
        assertTrue("Message added", messageId != 0);

        Uri contentUri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.HOME, ma, 0, null));
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = MsgTable.DESC_SORT_ORDER;
        sa.addSelection(MsgTable.MSG_ID + " = ?", Long.toString(messageId));
        String[] PROJECTION = new String[] {
                MsgOfUserTable.FAVORITED,
                UserTable.LINKED_USER_ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        assertTrue("Message found, id=" + messageId + ", count=" + cursor.getCount(), cursor.getCount() == 1);
        cursor.moveToFirst();
        assertTrue("Message favorited", cursor.getInt(0) == 1);
        assertTrue("Message not favorited by AccountUser", cursor.getLong(1) == ma.getUserId());
        cursor.close();

        assertEquals("Message stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, messageId)));
        long inReplyToId = MyQuery.oidToId(OidEnum.MSG_OID, accountUser.originId,
                inReplyToOid);
        assertTrue("In reply to message added", inReplyToId != 0);
        assertEquals("Message reply status is unknown", DownloadStatus.UNKNOWN, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, inReplyToId)));
    }

    @Test
    public void testMessageWithAttachment() throws Exception {
        MbMessage message = ConnectionGnuSocialTest.getMessageWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext());

        MyAccount ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(message.originId);
        assertTrue("Account is valid " + ma, ma.isValid());
        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(message.update(ma.toPartialUser())).getMessage().msgId;
        assertTrue("Message added", messageId != 0);

        DownloadData dd = DownloadData.getSingleForMessage(messageId,
                message.attachments.get(0).contentType, null);
        assertEquals("Image URI stored", message.attachments.get(0).getUri(), dd.getUri());
    }

    @Test
    public void testUnsentMessageWithAttachment() throws Exception {
        final String method = "testUnsentMessageWithAttachment";
        MyAccount ma = MyContextHolder.get().persistentAccounts().getFirstSucceeded();
        MbUser accountUser = ma.toPartialUser();
        MbMessage message = MbMessage.fromOriginAndOid(ma.getOriginId(), "", DownloadStatus.SENDING);
        message.setAuthor(MbUser.fromOriginAndUserOid(ma.getOriginId(), ma.getUserOid()));
        message.setUpdatedDate(System.currentTimeMillis());
        final String body = "Unsent message with an attachment " + DemoData.TESTRUN_UID;
        message.setBody(body);
        message.attachments.add(MbAttachment.fromUriAndContentType(DemoData.LOCAL_IMAGE_TEST_URI,
                MyContentType.IMAGE));
        DataUpdater di = new DataUpdater(ma);
        message.msgId = di.onActivity(message.update(accountUser)).getMessage().msgId;
        assertTrue("Message added", message.msgId != 0);
        assertEquals("Status of unsent message", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message.msgId)));

        DownloadData dd = DownloadData.getSingleForMessage(message.msgId,
                message.attachments.get(0).contentType, null);
        assertEquals("Image URI stored", message.attachments.get(0).getUri(), dd.getUri());
        assertEquals("Local image immediately loaded " + dd, DownloadStatus.LOADED, dd.getStatus());

        DbUtils.waitMs(method, 1000);

        final String oid = "unsentMsgOid" + DemoData.TESTRUN_UID;
        MbMessage message2 = MbMessage.fromOriginAndOid(accountUser.originId, oid, DownloadStatus.LOADED);
        message2.setAuthor(message.getAuthor());
        message2.setUpdatedDate(System.currentTimeMillis());
        final String body2 = "Unsent <b>message</b> with an attachment loaded " + DemoData.TESTRUN_UID;
        message2.setBody(body2);
        message2.attachments.add(MbAttachment.fromUriAndContentType(DemoData.IMAGE1_URL,
                MyContentType.IMAGE));
        message2.msgId = message.msgId;

        long rowId2 = di.onActivity(message2.update(accountUser)).getMessage().msgId;
        assertEquals("Row id didn't change", message.msgId, message2.msgId);
        assertEquals("Message updated", message.msgId, rowId2);
        assertEquals("Status of loaded message", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message2.msgId)));

        DownloadData dd2 = DownloadData.getSingleForMessage(message2.msgId,
                message2.attachments.get(0).contentType, null);
        assertEquals("New image URI stored", message2.attachments.get(0).getUri(), dd2.getUri());

        assertEquals("Not loaded yet. " + dd2, DownloadStatus.ABSENT, dd2.getStatus());
        AttachmentDownloaderTest.loadAndAssertStatusForRow(dd2.getDownloadId(), DownloadStatus.LOADED, false);
    }

    @Test
    public void testUserNameChanged() {
        MyAccount ma = TestSuite.getMyContextForTest().persistentAccounts().fromAccountName(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser accountUser = ma.toPartialUser();
        String username = "peter" + DemoData.TESTRUN_UID;
        MbUser user1 = new DemoMessageInserter(ma).buildUserFromOid("34804" + DemoData.TESTRUN_UID);
        user1.setUserName(username);
        user1.setProfileUrl("https://" + DemoData.GNUSOCIAL_TEST_ORIGIN_NAME + ".example.com/");
        
        DataUpdater di = new DataUpdater(ma);
        long userId1 = di.onActivity(user1.update(accountUser)).getUser().userId;
        assertTrue("User added", userId1 != 0);
        assertEquals("Username stored", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId1));

        MbUser user1partial = MbUser.fromOriginAndUserOid(user1.originId, user1.oid);
        assertTrue("Partially defined", user1partial.isPartiallyDefined());
        long userId1partial = di.onActivity(user1partial.update(accountUser)).getUser().userId;
        assertEquals("Same user", userId1, userId1partial);
        assertEquals("Partially defined user shouldn't change Username", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId1));
        assertEquals("Partially defined user shouldn't change WebfingerId", user1.getWebFingerId(),
                MyQuery.userIdToStringColumnValue(UserTable.WEBFINGER_ID, userId1));
        assertEquals("Partially defined user shouldn't change Real name", user1.getRealName(),
                MyQuery.userIdToStringColumnValue(UserTable.REAL_NAME, userId1));

        user1.setUserName(user1.getUserName() + "renamed");
        long userId1Renamed = di.onActivity(user1.update(accountUser)).getUser().userId;
        assertEquals("Same user renamed", userId1, userId1Renamed);
        assertEquals("Same user renamed", user1.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId1));

        MbUser user2SameOldUserName = new DemoMessageInserter(ma).buildUserFromOid("34805"
                + DemoData.TESTRUN_UID);
        user2SameOldUserName.setUserName(username);
        long userId2 = di.onActivity(user2SameOldUserName.update(accountUser)).getUser().userId;
        assertTrue("Other user with the same user name as old name of user", userId1 != userId2);
        assertEquals("Username stored", user2SameOldUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId2));

        MbUser user3SameNewUserName = new DemoMessageInserter(ma).buildUserFromOid("34806"
                + DemoData.TESTRUN_UID);
        user3SameNewUserName.setUserName(user1.getUserName());
        user3SameNewUserName.setProfileUrl("https://" + DemoData.GNUSOCIAL_TEST_ORIGIN_NAME + ".other.example.com/");
        long userId3 = di.onActivity(user3SameNewUserName.update(accountUser)).getUser().userId;
        assertTrue("User added " + user3SameNewUserName, userId3 != 0);
        assertTrue("Other user with the same user name as the new name of user1, but different WebFingerId", userId1 != userId3);
        assertEquals("Username stored for userId=" + userId3, user3SameNewUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId3));
    }

    @Test
    public void testInsertUser() {
        MyAccount ma = DemoData.getMyAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser user = new DemoMessageInserter(ma).buildUserFromOid("34807" + DemoData.TESTRUN_UID);
        MbUser accountUser = ma.toPartialUser();

        DataUpdater di = new DataUpdater(ma);
        long id = di.onActivity(user.update(accountUser)).getUser().userId;
        assertTrue("User added", id != 0);
        assertEquals("Username", user.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, id));
        assertEquals("oid", user.oid,
                MyQuery.userIdToStringColumnValue(UserTable.USER_OID, id));
        assertEquals("Display name", user.getRealName(),
                MyQuery.userIdToStringColumnValue(UserTable.REAL_NAME, id));
        assertEquals("Location", user.location,
                MyQuery.userIdToStringColumnValue(UserTable.LOCATION, id));
        assertEquals("profile image URL", user.avatarUrl,
                MyQuery.userIdToStringColumnValue(UserTable.AVATAR_URL, id));
        assertEquals("profile URL", user.getProfileUrl(),
                MyQuery.userIdToStringColumnValue(UserTable.PROFILE_URL, id));
        assertEquals("Banner URL", user.bannerUrl,
                MyQuery.userIdToStringColumnValue(UserTable.BANNER_URL, id));
        assertEquals("Homepage", user.getHomepage(),
                MyQuery.userIdToStringColumnValue(UserTable.HOMEPAGE, id));
        assertEquals("WebFinger ID", user.getWebFingerId(),
                MyQuery.userIdToStringColumnValue(UserTable.WEBFINGER_ID, id));
        assertEquals("Description", user.getDescription(),
                MyQuery.userIdToStringColumnValue(UserTable.DESCRIPTION, id));
        assertEquals("Messages count", user.msgCount,
                MyQuery.userIdToLongColumnValue(UserTable.MSG_COUNT, id));
        assertEquals("Favorites count", user.favoritesCount,
                MyQuery.userIdToLongColumnValue(UserTable.FAVORITES_COUNT, id));
        assertEquals("Following (friends) count", user.followingCount,
                MyQuery.userIdToLongColumnValue(UserTable.FOLLOWING_COUNT, id));
        assertEquals("Followers count", user.followersCount,
                MyQuery.userIdToLongColumnValue(UserTable.FOLLOWERS_COUNT, id));
        assertEquals("Created at", user.getCreatedDate(),
                MyQuery.userIdToLongColumnValue(UserTable.CREATED_DATE, id));
        assertEquals("Created at", user.getUpdatedDate(),
                MyQuery.userIdToLongColumnValue(UserTable.UPDATED_DATE, id));
    }

    @Test
    public void testReplyInBody() {
        MyAccount ma = DemoData.getConversationMyAccount();
        String buddyUserName = "buddy" +  DemoData.TESTRUN_UID + "@example.com";
        String body = "@" + buddyUserName + " I'm replying to you in a message body."
                + " Hope you will see this as a real reply!";
        addOneMessage4testReplyInBody(buddyUserName, body, true);

        addOneMessage4testReplyInBody(buddyUserName, "Oh, " + body, false);

        long userId1 = MyQuery.webFingerIdToId(ma.getOriginId(), buddyUserName);
        assertEquals("User has temp Oid", MbUser.getTempOid(buddyUserName, ""), MyQuery.idToOid(OidEnum.USER_OID, userId1, 0));

        String realBuddyOid = "acc:" + buddyUserName;
        MbUser user = MbUser.fromOriginAndUserOid(ma.getOriginId(), realBuddyOid);
        user.setUserName(buddyUserName);
        DataUpdater di = new DataUpdater(ma);
        long userId2 = di.onActivity(user.update(ma.toPartialUser())).getUser().userId;
        assertEquals(userId1, userId2);
        assertEquals("TempOid replaced with real", realBuddyOid, MyQuery.idToOid(OidEnum.USER_OID, userId1, 0));

        body = "<a href=\"http://example.com/a\">@" + buddyUserName + "</a>, this is an HTML <i>formatted</i> message";
        addOneMessage4testReplyInBody(buddyUserName, body, true);

        buddyUserName = DemoData.CONVERSATION_MEMBER_USERNAME;
        body = "@" + buddyUserName + " I know you are already in our cache";
        addOneMessage4testReplyInBody(buddyUserName, body, true);
    }

    private void addOneMessage4testReplyInBody(String buddyUserName, String body, boolean isReply) {
        MyAccount ma = DemoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        DataUpdater di = new DataUpdater(ma);
        String username = "somebody" + DemoData.TESTRUN_UID + "@somewhere.net";
        String userOid = "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(accountUser.originId, userOid);
        somebody.setUserName(username);
        somebody.setProfileUrl("https://somewhere.net/" + username);

        MbMessage message = MbMessage.fromOriginAndOid(accountUser.originId,
                String.valueOf(System.nanoTime()), DownloadStatus.LOADED);
        message.setBody(body);
        message.setUpdatedDate(System.currentTimeMillis());
        message.via = "MyCoolClient";
        message.setAuthor(somebody);

        long messageId = di.onActivity(message.update(accountUser)).getMessage().msgId;
        assertTrue("Message added", messageId != 0);
        long buddyId = MyQuery.msgIdToLongColumnValue(MsgTable.IN_REPLY_TO_USER_ID, messageId);
        if (isReply) {
            assertTrue("@username at the beginning of a message body is treated as a reply body:'"
                    + message.getBody() + "'", buddyId != 0);
            assertEquals(buddyUserName, MyQuery.userIdToStringColumnValue(UserTable.USERNAME, buddyId));
        } else {
            assertTrue("Don't treat this message as a reply:'"
                    + message.getBody() + "'", buddyId == 0);
        }
    }

    @Test
    public void testMention() {
        MyAccount ma = DemoData.getMyAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser accountUser = ma.toPartialUser();
        MyAccount myMentionedAccount = DemoData.getMyAccount(DemoData.GNUSOCIAL_TEST_ACCOUNT2_NAME);
        MbUser myMentionedUser = myMentionedAccount.toPartialUser().setUserName(myMentionedAccount.getUsername());
        MbUser author1 = MbUser.fromOriginAndUserOid(accountUser.originId, "sam" + DemoData.TESTRUN_UID);
        author1.setUserName("samBrook");

        MbMessage message = MbMessage.fromOriginAndOid(accountUser.originId,
                String.valueOf(System.nanoTime()), DownloadStatus.LOADED);
        message.setBody("@" + myMentionedUser.getUserName() + " I mention your another account");
        message.setUpdatedDate(System.currentTimeMillis());
        message.via = "AndStatus";
        message.setAuthor(author1);

        MbActivity activity = MbActivity.from(accountUser, MbActivityType.UPDATE);
        activity.setActor(author1);
        activity.setMessage(message);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(message.update(accountUser)).getMessage().msgId;
        assertTrue("Message added", messageId != 0);
    }
}
