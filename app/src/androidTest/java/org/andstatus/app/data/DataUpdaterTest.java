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
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.MsgTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Audience;
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

import java.util.List;
import java.util.Set;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataUpdaterTest {
    private MyContext myContext;
    private Context context;

    @Before
    public void setUp() throws Exception {
        TestSuite.initializeWithData(this);
        myContext = TestSuite.getMyContextForTest();
        context = myContext.context();
        demoData.checkDataPath();
    }

    @Test
    public void testFriends() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        DemoMessageInserter.deleteOldMessage(accountUser.originId, messageOid);

        CommandExecutionContext counters = new CommandExecutionContext(
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma));
        DataUpdater di = new DataUpdater(counters);
        String username = "somebody" + demoData.TESTRUN_UID + "@identi.ca";
        String userOid = "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(accountUser.originId, userOid);
        somebody.setUserName(username);
        somebody.followedByMe = TriState.FALSE;
        somebody.setProfileUrl("http://identi.ca/somebody");
        di.onActivity(somebody.update(accountUser, accountUser));

        somebody.userId = MyQuery.oidToId(OidEnum.USER_OID, accountUser.originId, userOid);
        assertTrue("User " + username + " added", somebody.userId != 0);
        DemoConversationInserter.assertIfUserIsMyFriend(somebody, false, ma);

        MbActivity activity = MbActivity.newPartialMessage(accountUser, messageOid, 13312696000L, DownloadStatus.LOADED);
        activity.setActor(somebody);
        MbMessage message = activity.getMessage();
        message.setBody("The test message by Somebody");
        message.via = "MyCoolClient";
        message.url = "http://identi.ca/somebody/comment/dasdjfdaskdjlkewjz1EhSrTRB";

        TestSuite.clearAssertionData();
        long messageId = di.onActivity(activity).getMessage().msgId;
        assertNotEquals("Message added", 0, messageId);
        assertNotEquals("Activity added", 0, activity.getId());
        AssertionData data = TestSuite.getMyContextForTest().takeDataByKey(DataUpdater.MSG_ASSERTION_KEY);
        assertFalse("Data put", data.isEmpty());
        assertEquals("Message Oid", messageOid, data.getValues()
                .getAsString(MsgTable.MSG_OID));
        assertEquals("Message is loaded", DownloadStatus.LOADED,
                DownloadStatus.load(data.getValues().getAsInteger(MsgTable.MSG_STATUS)));
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
        long authorId = MyQuery.msgIdToLongColumnValue(ActivityTable.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebody.userId, authorId);
        String url = MyQuery.msgIdToStringColumnValue(MsgTable.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyQuery.msgIdToLongColumnValue(ActivityTable.ACTOR_ID, messageId);
        assertEquals("Sender of the message", somebody.userId, senderId);
        url = MyQuery.userIdToStringColumnValue(UserTable.PROFILE_URL, senderId);
        assertEquals("Url of the author " + somebody.getUserName(), somebody.getProfileUrl(), url);

        Uri contentUri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.MY_FRIENDS, ma, 0, null));
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimeSortOrder(TimelineType.MY_FRIENDS, false);
        sa.addSelection("fUserId = ?", Long.toString(somebody.userId));
        String[] PROJECTION = new String[] {
                MsgTable._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Friends timeline", cursor != null && cursor.getCount() == 0);
        cursor.close();

        somebody.followedByMe = TriState.TRUE;
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
    public void testPrivateMessageToMyAccount() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        String messageOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ-" + demoData.TESTRUN_UID;

        String username = "t131t@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:"
                + username);
        author.setUserName(username);

        MbActivity activity = new DemoMessageInserter(accountUser).buildActivity(
                author,
                "Hello, this is a test Direct message by your namesake from http://pumpity.net",
                null, messageOid, DownloadStatus.LOADED);
        final MbMessage message = activity.getMessage();
        message.via = "AnyOtherClient";
        message.addRecipient(accountUser);
        message.setPrivate(TriState.TRUE);
        final long messageId = new DataUpdater(ma).onActivity(activity).getMessage().msgId;
        assertNotEquals("Message added", 0, messageId);
        assertNotEquals("Activity added", 0, activity.getId());

        assertEquals("Message should be private", TriState.TRUE,
                MyQuery.msgIdToTriState(MsgTable.PRIVATE, messageId));
        DemoMessageInserter.assertNotified(activity, TriState.TRUE);

        Audience audience = Audience.fromMsgId(accountUser.originId, messageId);
        assertNotEquals("No recipients for " + activity, 0, audience.getRecipients().size());
        assertEquals("Recipient " + ma.getAccountName() + "; " + audience.getRecipients(),
                ma.getUserId(), audience.getFirst().userId);
        assertEquals("Number of recipients for " + activity, 1, audience.getRecipients().size());
    }

    @Test
    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        String authorUserName = "anybody@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:"
                + authorUserName);
        author.setUserName(authorUserName);

        MbActivity activity = MbActivity.newPartialMessage(accountUser,
                "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED" +  demoData.TESTRUN_UID,
                13312697000L, DownloadStatus.LOADED);
        activity.setActor(author);
        MbMessage message = activity.getMessage();
        message.setBody("This test message will be favorited by First Reader from http://pumpity.net");
        message.via = "SomeOtherClient";

        String otherUserName = "firstreader@identi.ca";
        MbUser otherUser = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:" + otherUserName);
        otherUser.setUserName(otherUserName);
        MbActivity likeActivity = MbActivity.fromInner(otherUser, MbActivityType.LIKE, activity);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(likeActivity).getMessage().msgId;
        assertNotEquals("Message added", 0, messageId);
        assertNotEquals("First activity added", 0, activity.getId());
        assertNotEquals("LIKE activity added", 0, likeActivity.getId());

        List<MbUser> stargazers = MyQuery.getStargazers(myContext.getDatabase(), accountUser.originId, message.msgId);
        boolean favoritedByOtherUser = false;
        for (MbUser user : stargazers) {
            if (user.equals(accountUser)) {
                fail("Message is favorited by my account " + ma + " - " + message);
            } else if (user.equals(author)) {
                fail("Message is favorited by my author " + message);
            } if (user.equals(otherUser)) {
                favoritedByOtherUser = true;
            } else {
                fail("Message is favorited by unexpected user " + user + " - " + message);
            }
        }
        assertEquals("Message is not favorited by " + otherUser + ": " + stargazers,
                true, favoritedByOtherUser);
        assertNotEquals("Message is favorited (by some my account)", TriState.TRUE,
                MyQuery.msgIdToTriState(MsgTable.FAVORITED, messageId));
        assertEquals("Activity is subscribed " + likeActivity, TriState.UNKNOWN,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, likeActivity.getId()));
        DemoMessageInserter.assertNotified(likeActivity, TriState.UNKNOWN);
        assertEquals("Message is reblogged", TriState.UNKNOWN,
                MyQuery.msgIdToTriState(MsgTable.REBLOGGED, messageId));

        // TODO: Below is actually a timeline query test, so maybe expand / move...
        Uri contentUri = MatchedUri.getTimelineUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, null, 0, ma.getOrigin()));
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimeSortOrder(TimelineType.EVERYTHING, false);
        sa.addSelection(MsgTable.MSG_ID + " = ?", Long.toString(messageId));
        String[] PROJECTION = new String[] {
                ActivityTable.ACTIVITY_ID,
                ActivityTable.ACTOR_ID,
                ActivityTable.SUBSCRIBED,
                ActivityTable.INS_DATE,
                MsgTable.MSG_ID,
                MsgTable.FAVORITED,
                MsgTable.UPDATED_DATE,
                UserTable.LINKED_USER_ID,
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Cursor returned", cursor != null);
        boolean messageFound = false;
        while (cursor.moveToNext()) {
            assertEquals("Message with other id returned", messageId, DbUtils.getLong(cursor, MsgTable.MSG_ID));
            messageFound = true;
            assertNotEquals("Message is favorited", TriState.TRUE, DbUtils.getTriState(cursor, MsgTable.FAVORITED));
            assertNotEquals("Activity is subscribed", TriState.TRUE,
                    DbUtils.getTriState(cursor, ActivityTable.SUBSCRIBED));
        }
        cursor.close();
        assertTrue("Message is not in Everything timeline, msgId=" + messageId, messageFound);

    }

    @Test
    public void testReplyMessageFavoritedByMyActor() throws ConnectionException {
        oneReplyMessageFavoritedByMyActor("_it1", true);
        oneReplyMessageFavoritedByMyActor("_it2", false);
        oneReplyMessageFavoritedByMyActor("_it2", true);
    }

    private void oneReplyMessageFavoritedByMyActor(String iterationId, boolean favorited) {
        MyAccount ma = demoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        String authorUserName = "example@pumpity.net";
        MbUser author = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:" + authorUserName);
        author.setUserName(authorUserName);

        MbActivity activity = MbActivity.newPartialMessage(accountUser,
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123" + iterationId + demoData.TESTRUN_UID,
                13312795000L, DownloadStatus.LOADED);
        activity.setActor(author);
        MbMessage message = activity.getMessage();
        message.setBody("The test message by Example from the http://pumpity.net " +  iterationId);
        message.via = "UnknownClient";
        if (favorited) message.addFavoriteBy(accountUser, TriState.TRUE);

        String inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB" + iterationId  + demoData.TESTRUN_UID;
        MbActivity inReplyTo = MbActivity.newPartialMessage(accountUser, inReplyToOid,
                0, DownloadStatus.UNKNOWN);
        inReplyTo.setActor(MbUser.fromOriginAndUserOid(accountUser.originId,
                "irtUser" +  iterationId + demoData.TESTRUN_UID).setUserName("irt" + authorUserName +  iterationId));
        message.setInReplyTo(inReplyTo);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(activity).getMessage().msgId;
        assertNotEquals("Message added " + activity.getMessage(), 0, messageId);
        assertNotEquals("Activity added " + accountUser, 0, activity.getId());
        if (!favorited) {
            assertNotEquals("In reply to message added " + inReplyTo.getMessage(), 0, inReplyTo.getMessage().msgId);
            assertNotEquals("In reply to activity added " + inReplyTo, 0, inReplyTo.getId());
        }

        List<MbUser> stargazers = MyQuery.getStargazers(myContext.getDatabase(), accountUser.originId, message.msgId);
        boolean favoritedByMe = false;
        for (MbUser user : stargazers) {
            if (user.equals(accountUser)) {
                favoritedByMe = true;
            } else if (user.equals(author)) {
                fail("Message is favorited by my author " + message);
            } else {
                fail("Message is favorited by unexpected user " + user + " - " + message);
            }
        }
        if (favorited) {
            assertEquals("Message " + message.msgId + " is not favorited by " + accountUser + ": "
                            + stargazers + "\n" + activity,
                    true, favoritedByMe);
            assertEquals("Message should be favorited (by some my account) " + activity, TriState.TRUE,
                    MyQuery.msgIdToTriState(MsgTable.FAVORITED, messageId));
        }
        assertEquals("Activity is subscribed", TriState.UNKNOWN,
                MyQuery.activityIdToTriState(ActivityTable.SUBSCRIBED, activity.getId()));
        DemoMessageInserter.assertNotified(activity, TriState.UNKNOWN);
        assertEquals("Message is reblogged", TriState.UNKNOWN,
                MyQuery.msgIdToTriState(MsgTable.REBLOGGED, messageId));
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
        MbActivity activity = ConnectionGnuSocialTest.getMessageWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext());

        MyAccount ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOriginId(activity.getActor().originId);
        assertTrue("Account is valid " + ma, ma.isValid());
        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(activity).getMessage().msgId;
        assertNotEquals("Message added " + activity.getMessage(), 0, messageId);
        assertNotEquals("Activity added " + activity, 0, activity.getId());

        DownloadData dd = DownloadData.getSingleForMessage(messageId,
                activity.getMessage().attachments.get(0).contentType, null);
        assertEquals("Image URI stored", activity.getMessage().attachments.get(0).getUri(), dd.getUri());
    }

    @Test
    public void testUnsentMessageWithAttachment() throws Exception {
        final String method = "testUnsentMessageWithAttachment";
        MyAccount ma = MyContextHolder.get().persistentAccounts().getFirstSucceeded();
        MbUser accountUser = ma.toPartialUser();
        MbActivity activity = MbActivity.newPartialMessage(accountUser, "", System.currentTimeMillis(), DownloadStatus.SENDING);
        activity.setActor(accountUser);
        MbMessage message = activity.getMessage();
        message.setBody("Unsent message with an attachment " + demoData.TESTRUN_UID);
        message.attachments.add(MbAttachment.fromUriAndContentType(demoData.LOCAL_IMAGE_TEST_URI,
                MyContentType.IMAGE));
        new DataUpdater(ma).onActivity(activity);
        assertNotEquals("Message added " + activity, 0, message.msgId);
        assertNotEquals("Activity added " + activity, 0, activity.getId());
        assertEquals("Status of unsent message", DownloadStatus.SENDING, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message.msgId)));

        DownloadData dd = DownloadData.getSingleForMessage(message.msgId,
                message.attachments.get(0).contentType, null);
        assertEquals("Image URI stored", message.attachments.get(0).getUri(), dd.getUri());
        assertEquals("Local image immediately loaded " + dd, DownloadStatus.LOADED, dd.getStatus());

        DbUtils.waitMs(method, 1000);

        // Emulate receiving of message
        final String oid = "sentMsgOid" + demoData.TESTRUN_UID;
        MbActivity activity2 = MbActivity.newPartialMessage(accountUser, oid, System.currentTimeMillis(), DownloadStatus.LOADED);
        activity2.setActor(activity.getAuthor());
        MbMessage message2 = activity2.getMessage();
        message2.setBody("Just sent: " + message.getBody());
        message2.attachments.add(MbAttachment.fromUriAndContentType(demoData.IMAGE1_URL, MyContentType.IMAGE));
        message2.msgId = message.msgId;
        new DataUpdater(ma).onActivity(activity2);

        assertEquals("Row id didn't change", message.msgId, message2.msgId);
        assertEquals("Message body updated", message2.getBody(),
                MyQuery.msgIdToStringColumnValue(MsgTable.BODY, message.msgId));
        assertEquals("Status of loaded message", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, message.msgId)));

        DownloadData dd2 = DownloadData.getSingleForMessage(message2.msgId,
                message2.attachments.get(0).contentType, null);
        assertEquals("New image URI stored", message2.attachments.get(0).getUri(), dd2.getUri());

        assertEquals("Not loaded yet. " + dd2, DownloadStatus.ABSENT, dd2.getStatus());
        AttachmentDownloaderTest.loadAndAssertStatusForRow(dd2.getDownloadId(), DownloadStatus.LOADED, false);
    }

    @Test
    public void testUserNameChanged() {
        MyAccount ma = TestSuite.getMyContextForTest().persistentAccounts().fromAccountName(demoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser accountUser = ma.toPartialUser();
        String username = "peter" + demoData.TESTRUN_UID;
        MbUser user1 = new DemoMessageInserter(ma).buildUserFromOid("34804" + demoData.TESTRUN_UID);
        user1.setUserName(username);
        user1.setProfileUrl("https://" + demoData.GNUSOCIAL_TEST_ORIGIN_NAME + ".example.com/");
        
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
                + demoData.TESTRUN_UID);
        user2SameOldUserName.setUserName(username);
        long userId2 = di.onActivity(user2SameOldUserName.update(accountUser)).getUser().userId;
        assertTrue("Other user with the same user name as old name of user", userId1 != userId2);
        assertEquals("Username stored", user2SameOldUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId2));

        MbUser user3SameNewUserName = new DemoMessageInserter(ma).buildUserFromOid("34806"
                + demoData.TESTRUN_UID);
        user3SameNewUserName.setUserName(user1.getUserName());
        user3SameNewUserName.setProfileUrl("https://" + demoData.GNUSOCIAL_TEST_ORIGIN_NAME + ".other.example.com/");
        long userId3 = di.onActivity(user3SameNewUserName.update(accountUser)).getUser().userId;
        assertTrue("User added " + user3SameNewUserName, userId3 != 0);
        assertTrue("Other user with the same user name as the new name of user1, but different WebFingerId", userId1 != userId3);
        assertEquals("Username stored for userId=" + userId3, user3SameNewUserName.getUserName(),
                MyQuery.userIdToStringColumnValue(UserTable.USERNAME, userId3));
    }

    @Test
    public void testInsertUser() {
        MyAccount ma = demoData.getMyAccount(demoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser user = new DemoMessageInserter(ma).buildUserFromOid("34807" + demoData.TESTRUN_UID);
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
        MyAccount ma = demoData.getConversationMyAccount();
        String buddyUserName = "buddy" +  demoData.TESTRUN_UID + "@example.com";
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

        buddyUserName = demoData.CONVERSATION_AUTHOR_THIRD_USERNAME;
        body = "@" + buddyUserName + " I know you are already in our cache";
        addOneMessage4testReplyInBody(buddyUserName, body, true);
    }

    private void addOneMessage4testReplyInBody(String buddyUserName, String body, boolean isReply) {
        MyAccount ma = demoData.getConversationMyAccount();
        MbUser accountUser = ma.toPartialUser();

        DataUpdater di = new DataUpdater(ma);
        String username = "somebody" + demoData.TESTRUN_UID + "@somewhere.net";
        String userOid = "acct:" + username;
        MbUser somebody = MbUser.fromOriginAndUserOid(accountUser.originId, userOid);
        somebody.setUserName(username);
        somebody.setProfileUrl("https://somewhere.net/" + username);

        MbActivity activity = MbActivity.newPartialMessage(accountUser, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        activity.setActor(somebody);
        MbMessage message = activity.getMessage();
        message.setBody(body);
        message.via = "MyCoolClient";

        long messageId = di.onActivity(activity).getMessage().msgId;
        MbUser buddy = MbUser.EMPTY;
        for (MbUser user : activity.recipients().getRecipients()) {
            if (user.getUserName().equals(buddyUserName)) {
                buddy = user;
                break;
            }
        }
        assertTrue("Message added", messageId != 0);
        if (isReply) {
            assertTrue("'" + buddyUserName + "' should be a recipient " + activity.recipients().getRecipients(),
                    buddy.nonEmpty());
            assertNotEquals("'" + buddyUserName + "' is not added " + buddy, 0, buddy.userId);
        } else {
            assertTrue("Don't treat this message as a reply:'"
                    + message.getBody() + "'", buddy.isEmpty());
        }
    }

    @Test
    public void testMention() {
        MyAccount ma = demoData.getMyAccount(demoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        MbUser accountUser = ma.toPartialUser();
        MyAccount myMentionedAccount = demoData.getMyAccount(demoData.GNUSOCIAL_TEST_ACCOUNT2_NAME);
        MbUser myMentionedUser = myMentionedAccount.toPartialUser().setUserName(myMentionedAccount.getUsername());
        MbUser author1 = MbUser.fromOriginAndUserOid(accountUser.originId, "sam" + demoData.TESTRUN_UID);
        author1.setUserName("samBrook");

        MbActivity activity1 = MbActivity.newPartialMessage(accountUser, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        activity1.setActor(author1);
        MbMessage message = activity1.getMessage();
        message.setBody("@" + myMentionedUser.getUserName() + " I mention your another account");
        message.via = "AndStatus";

        MbActivity activity2 = MbActivity.from(accountUser, MbActivityType.UPDATE);
        activity2.setActor(author1);
        activity2.setActivity(activity1);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(activity2).getMessage().msgId;
        assertTrue("Message added", messageId != 0);
    }
}
