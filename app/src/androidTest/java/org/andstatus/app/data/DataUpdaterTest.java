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
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.ConnectionGnuSocialTest;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.Actor;
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
        Actor accountActor = ma.getActor();
        String messageOid = "https://identi.ca/api/comment/dasdjfdaskdjlkewjz1EhSrTRB";
        DemoMessageInserter.deleteOldMessage(accountActor.origin, messageOid);

        CommandExecutionContext executionContext = new CommandExecutionContext(
                CommandData.newAccountCommand(CommandEnum.EMPTY, ma));
        DataUpdater di = new DataUpdater(executionContext);
        String username = "somebody" + demoData.TESTRUN_UID + "@identi.ca";
        String userOid = "acct:" + username;
        Actor somebody = Actor.fromOriginAndActorOid(accountActor.origin, userOid);
        somebody.setActorName(username);
        somebody.followedByMe = TriState.FALSE;
        somebody.setProfileUrl("http://identi.ca/somebody");
        di.onActivity(somebody.update(accountActor, accountActor));

        somebody.userId = MyQuery.oidToId(OidEnum.USER_OID, accountActor.origin.getId(), userOid);
        assertTrue("User " + username + " added", somebody.userId != 0);
        DemoConversationInserter.assertIfUserIsMyFriend(somebody, false, ma);

        AActivity activity = AActivity.newPartialMessage(accountActor, messageOid, System.currentTimeMillis() , DownloadStatus.LOADED);
        activity.setActor(somebody);
        Note message = activity.getMessage();
        message.setBody("The test message by Somebody at run " + demoData.TESTRUN_UID);
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
        assertEquals("Message permalink", message.url, accountActor.origin.messagePermalink(messageId));

        assertEquals("Message stored as loaded", DownloadStatus.LOADED, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, messageId)));
        long authorId = MyQuery.msgIdToLongColumnValue(ActivityTable.AUTHOR_ID, messageId);
        assertEquals("Author of the message", somebody.userId, authorId);
        String url = MyQuery.msgIdToStringColumnValue(MsgTable.URL, messageId);
        assertEquals("Url of the message", message.url, url);
        long senderId = MyQuery.msgIdToLongColumnValue(ActivityTable.ACTOR_ID, messageId);
        assertEquals("Sender of the message", somebody.userId, senderId);
        url = MyQuery.userIdToStringColumnValue(ActorTable.PROFILE_URL, senderId);
        assertEquals("Url of the author " + somebody.getActorName(), somebody.getProfileUrl(), url);
        assertEquals("Latest activity of " + somebody, activity.getId(),
                MyQuery.userIdToLongColumnValue(ActorTable.ACTOR_ACTIVITY_ID, somebody.userId));

        Uri contentUri = Timeline.getTimeline(TimelineType.MY_FRIENDS, ma, 0, null).getUri();
        SelectionAndArgs sa = new SelectionAndArgs();
        String sortOrder = ActivityTable.getTimeSortOrder(TimelineType.MY_FRIENDS, false);
        sa.addSelection(ActivityTable.ACTOR_ID + "=?", Long.toString(somebody.userId));
        String[] PROJECTION = new String[] {
                MsgTable._ID
        };
        Cursor cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("No messages of this user in the Friends timeline", cursor != null && cursor.getCount() == 0);
        cursor.close();

        somebody.followedByMe = TriState.TRUE;
        di.onActivity(somebody.update(accountActor, accountActor));
        DemoConversationInserter.assertIfUserIsMyFriend(somebody, true, ma);

        Set<Long> friendsIds = MyQuery.getFriendsIds(ma.getUserId());
        MyContextHolder.get().persistentAccounts().initialize();
        for (long id : friendsIds) {
            assertTrue("isFriend: " + id, MyContextHolder.get().persistentAccounts().isMeOrMyFriend(id));
        }

        cursor = context.getContentResolver().query(contentUri, PROJECTION, sa.selection,
                sa.selectionArgs, sortOrder);
        assertTrue("Message by user=" + somebody + " is not in the Friends timeline of " + ma,
                cursor != null && cursor.getCount() > 0);
        cursor.close();
    }

    @Test
    public void testPrivateMessageToMyAccount() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();

        String messageOid = "https://pumpity.net/api/comment/sa23wdi78dhgjerdfddajDSQ-" + demoData.TESTRUN_UID;

        String username = "t131t@pumpity.net";
        Actor author = Actor.fromOriginAndActorOid(accountActor.origin, "acct:"
                + username);
        author.setActorName(username);

        AActivity activity = new DemoMessageInserter(accountActor).buildActivity(
                author,
                "Hello, this is a test Direct message by your namesake from http://pumpity.net",
                null, messageOid, DownloadStatus.LOADED);
        final Note message = activity.getMessage();
        message.via = "AnyOtherClient";
        message.addRecipient(accountActor);
        message.setPrivate(TriState.TRUE);
        final long messageId = new DataUpdater(ma).onActivity(activity).getMessage().msgId;
        assertNotEquals("Message added", 0, messageId);
        assertNotEquals("Activity added", 0, activity.getId());

        assertEquals("Message should be private", TriState.TRUE,
                MyQuery.msgIdToTriState(MsgTable.PRIVATE, messageId));
        DemoMessageInserter.assertNotified(activity, TriState.TRUE);

        Audience audience = Audience.fromMsgId(accountActor.origin, messageId);
        assertNotEquals("No recipients for " + activity, 0, audience.getRecipients().size());
        assertEquals("Recipient " + ma.getAccountName() + "; " + audience.getRecipients(),
                ma.getUserId(), audience.getFirst().userId);
        assertEquals("Number of recipients for " + activity, 1, audience.getRecipients().size());
    }

    @Test
    public void testMessageFavoritedByOtherUser() throws ConnectionException {
        MyAccount ma = demoData.getConversationMyAccount();
        Actor accountActor = ma.getActor();

        String authorUserName = "anybody@pumpity.net";
        Actor author = Actor.fromOriginAndActorOid(accountActor.origin, "acct:"
                + authorUserName);
        author.setActorName(authorUserName);

        AActivity activity = AActivity.newPartialMessage(accountActor,
                "https://pumpity.net/api/comment/sdajklsdkiewwpdsldkfsdasdjWED" +  demoData.TESTRUN_UID,
                13312697000L, DownloadStatus.LOADED);
        activity.setActor(author);
        Note message = activity.getMessage();
        message.setBody("This test message will be favorited by First Reader from http://pumpity.net");
        message.via = "SomeOtherClient";

        String otherUserName = "firstreader@identi.ca";
        Actor otherUser = Actor.fromOriginAndActorOid(accountActor.origin, "acct:" + otherUserName);
        otherUser.setActorName(otherUserName);
        AActivity likeActivity = AActivity.fromInner(otherUser, ActivityType.LIKE, activity);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(likeActivity).getMessage().msgId;
        assertNotEquals("Message added", 0, messageId);
        assertNotEquals("First activity added", 0, activity.getId());
        assertNotEquals("LIKE activity added", 0, likeActivity.getId());

        List<Actor> stargazers = MyQuery.getStargazers(myContext.getDatabase(), accountActor.origin, message.msgId);
        boolean favoritedByOtherUser = false;
        for (Actor user : stargazers) {
            if (user.equals(accountActor)) {
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
        Uri contentUri = Timeline.getTimeline(TimelineType.EVERYTHING, null, 0, ma.getOrigin()).getUri();
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
                ActivityTable.ACCOUNT_ID,
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
        Actor accountActor = ma.getActor();

        String authorUserName = "example@pumpity.net";
        Actor author = Actor.fromOriginAndActorOid(accountActor.origin, "acct:" + authorUserName);
        author.setActorName(authorUserName);

        AActivity activity = AActivity.newPartialMessage(accountActor,
                "https://pumpity.net/api/comment/jhlkjh3sdffpmnhfd123" + iterationId + demoData.TESTRUN_UID,
                13312795000L, DownloadStatus.LOADED);
        activity.setActor(author);
        Note message = activity.getMessage();
        message.setBody("The test message by Example from the http://pumpity.net " +  iterationId);
        message.via = "UnknownClient";
        if (favorited) message.addFavoriteBy(accountActor, TriState.TRUE);

        String inReplyToOid = "https://identi.ca/api/comment/dfjklzdfSf28skdkfgloxWB" + iterationId  + demoData.TESTRUN_UID;
        AActivity inReplyTo = AActivity.newPartialMessage(accountActor, inReplyToOid,
                0, DownloadStatus.UNKNOWN);
        inReplyTo.setActor(Actor.fromOriginAndActorOid(accountActor.origin,
                "irtUser" +  iterationId + demoData.TESTRUN_UID).setActorName("irt" + authorUserName +  iterationId));
        message.setInReplyTo(inReplyTo);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(activity).getMessage().msgId;
        assertNotEquals("Message added " + activity.getMessage(), 0, messageId);
        assertNotEquals("Activity added " + accountActor, 0, activity.getId());
        if (!favorited) {
            assertNotEquals("In reply to message added " + inReplyTo.getMessage(), 0, inReplyTo.getMessage().msgId);
            assertNotEquals("In reply to activity added " + inReplyTo, 0, inReplyTo.getId());
        }

        List<Actor> stargazers = MyQuery.getStargazers(myContext.getDatabase(), accountActor.origin, message.msgId);
        boolean favoritedByMe = false;
        for (Actor user : stargazers) {
            if (user.equals(accountActor)) {
                favoritedByMe = true;
            } else if (user.equals(author)) {
                fail("Message is favorited by my author " + message);
            } else {
                fail("Message is favorited by unexpected user " + user + " - " + message);
            }
        }
        if (favorited) {
            assertEquals("Message " + message.msgId + " is not favorited by " + accountActor + ": "
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

        long inReplyToId = MyQuery.oidToId(OidEnum.MSG_OID, accountActor.origin.getId(),
                inReplyToOid);
        assertTrue("In reply to message added", inReplyToId != 0);
        assertEquals("Message reply status is unknown", DownloadStatus.UNKNOWN, DownloadStatus.load(
                MyQuery.msgIdToLongColumnValue(MsgTable.MSG_STATUS, inReplyToId)));
    }

    @Test
    public void testMessageWithAttachment() throws Exception {
        AActivity activity = ConnectionGnuSocialTest.getMessageWithAttachment(
                InstrumentationRegistry.getInstrumentation().getContext());

        MyAccount ma = MyContextHolder.get().persistentAccounts().getFirstSucceededForOrigin(activity.getActor().origin);
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
        Actor accountActor = ma.getActor();
        AActivity activity = AActivity.newPartialMessage(accountActor, "", System.currentTimeMillis(), DownloadStatus.SENDING);
        activity.setActor(accountActor);
        Note message = activity.getMessage();
        message.setBody("Unsent message with an attachment " + demoData.TESTRUN_UID);
        message.attachments.add(Attachment.fromUriAndContentType(demoData.LOCAL_IMAGE_TEST_URI,
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
        AActivity activity2 = AActivity.newPartialMessage(accountActor, oid, System.currentTimeMillis(), DownloadStatus.LOADED);
        activity2.setActor(activity.getAuthor());
        Note message2 = activity2.getMessage();
        message2.setBody("Just sent: " + message.getBody());
        message2.attachments.add(Attachment.fromUriAndContentType(demoData.IMAGE1_URL, MyContentType.IMAGE));
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
        Actor accountActor = ma.getActor();
        String username = "peter" + demoData.TESTRUN_UID;
        Actor user1 = new DemoMessageInserter(ma).buildActorFromOid("34804" + demoData.TESTRUN_UID);
        user1.setActorName(username);
        user1.setProfileUrl("https://" + demoData.GNUSOCIAL_TEST_ORIGIN_NAME + ".example.com/");
        
        DataUpdater di = new DataUpdater(ma);
        long userId1 = di.onActivity(user1.update(accountActor)).getObjActor().userId;
        assertTrue("User added", userId1 != 0);
        assertEquals("Username stored", user1.getActorName(),
                MyQuery.userIdToStringColumnValue(ActorTable.ACTORNAME, userId1));

        Actor user1partial = Actor.fromOriginAndActorOid(user1.origin, user1.oid);
        assertTrue("Partially defined", user1partial.isPartiallyDefined());
        long userId1partial = di.onActivity(user1partial.update(accountActor)).getObjActor().userId;
        assertEquals("Same user", userId1, userId1partial);
        assertEquals("Partially defined user shouldn't change Username", user1.getActorName(),
                MyQuery.userIdToStringColumnValue(ActorTable.ACTORNAME, userId1));
        assertEquals("Partially defined user shouldn't change WebfingerId", user1.getWebFingerId(),
                MyQuery.userIdToStringColumnValue(ActorTable.WEBFINGER_ID, userId1));
        assertEquals("Partially defined user shouldn't change Real name", user1.getRealName(),
                MyQuery.userIdToStringColumnValue(ActorTable.REAL_NAME, userId1));

        user1.setActorName(user1.getActorName() + "renamed");
        long userId1Renamed = di.onActivity(user1.update(accountActor)).getObjActor().userId;
        assertEquals("Same user renamed", userId1, userId1Renamed);
        assertEquals("Same user renamed", user1.getActorName(),
                MyQuery.userIdToStringColumnValue(ActorTable.ACTORNAME, userId1));

        Actor user2SameOldUserName = new DemoMessageInserter(ma).buildActorFromOid("34805"
                + demoData.TESTRUN_UID);
        user2SameOldUserName.setActorName(username);
        long userId2 = di.onActivity(user2SameOldUserName.update(accountActor)).getObjActor().userId;
        assertTrue("Other user with the same user name as old name of user", userId1 != userId2);
        assertEquals("Username stored", user2SameOldUserName.getActorName(),
                MyQuery.userIdToStringColumnValue(ActorTable.ACTORNAME, userId2));

        Actor user3SameNewUserName = new DemoMessageInserter(ma).buildActorFromOid("34806"
                + demoData.TESTRUN_UID);
        user3SameNewUserName.setActorName(user1.getActorName());
        user3SameNewUserName.setProfileUrl("https://" + demoData.GNUSOCIAL_TEST_ORIGIN_NAME + ".other.example.com/");
        long userId3 = di.onActivity(user3SameNewUserName.update(accountActor)).getObjActor().userId;
        assertTrue("User added " + user3SameNewUserName, userId3 != 0);
        assertTrue("Other user with the same user name as the new name of user1, but different WebFingerId", userId1 != userId3);
        assertEquals("Username stored for userId=" + userId3, user3SameNewUserName.getActorName(),
                MyQuery.userIdToStringColumnValue(ActorTable.ACTORNAME, userId3));
    }

    @Test
    public void testInsertUser() {
        MyAccount ma = demoData.getMyAccount(demoData.GNUSOCIAL_TEST_ACCOUNT_NAME);
        Actor user = new DemoMessageInserter(ma).buildActorFromOid("34807" + demoData.TESTRUN_UID);
        Actor accountActor = ma.getActor();

        DataUpdater di = new DataUpdater(ma);
        long id = di.onActivity(user.update(accountActor)).getObjActor().userId;
        assertTrue("User added", id != 0);
        assertEquals("Username", user.getActorName(),
                MyQuery.userIdToStringColumnValue(ActorTable.ACTORNAME, id));
        assertEquals("oid", user.oid,
                MyQuery.userIdToStringColumnValue(ActorTable.ACTOR_OID, id));
        assertEquals("Display name", user.getRealName(),
                MyQuery.userIdToStringColumnValue(ActorTable.REAL_NAME, id));
        assertEquals("Location", user.location,
                MyQuery.userIdToStringColumnValue(ActorTable.LOCATION, id));
        assertEquals("profile image URL", user.avatarUrl,
                MyQuery.userIdToStringColumnValue(ActorTable.AVATAR_URL, id));
        assertEquals("profile URL", user.getProfileUrl(),
                MyQuery.userIdToStringColumnValue(ActorTable.PROFILE_URL, id));
        assertEquals("Banner URL", user.bannerUrl,
                MyQuery.userIdToStringColumnValue(ActorTable.BANNER_URL, id));
        assertEquals("Homepage", user.getHomepage(),
                MyQuery.userIdToStringColumnValue(ActorTable.HOMEPAGE, id));
        assertEquals("WebFinger ID", user.getWebFingerId(),
                MyQuery.userIdToStringColumnValue(ActorTable.WEBFINGER_ID, id));
        assertEquals("Description", user.getDescription(),
                MyQuery.userIdToStringColumnValue(ActorTable.DESCRIPTION, id));
        assertEquals("Messages count", user.msgCount,
                MyQuery.userIdToLongColumnValue(ActorTable.MSG_COUNT, id));
        assertEquals("Favorites count", user.favoritesCount,
                MyQuery.userIdToLongColumnValue(ActorTable.FAVORITES_COUNT, id));
        assertEquals("Following (friends) count", user.followingCount,
                MyQuery.userIdToLongColumnValue(ActorTable.FOLLOWING_COUNT, id));
        assertEquals("Followers count", user.followersCount,
                MyQuery.userIdToLongColumnValue(ActorTable.FOLLOWERS_COUNT, id));
        assertEquals("Created at", user.getCreatedDate(),
                MyQuery.userIdToLongColumnValue(ActorTable.CREATED_DATE, id));
        assertEquals("Created at", user.getUpdatedDate(),
                MyQuery.userIdToLongColumnValue(ActorTable.UPDATED_DATE, id));
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
        assertEquals("User has temp Oid", Actor.getTempOid(buddyUserName, ""), MyQuery.idToOid(OidEnum.USER_OID, userId1, 0));

        String realBuddyOid = "acc:" + buddyUserName;
        Actor user = Actor.fromOriginAndActorOid(ma.getOrigin(), realBuddyOid);
        user.setActorName(buddyUserName);
        DataUpdater di = new DataUpdater(ma);
        long userId2 = di.onActivity(user.update(ma.getActor())).getObjActor().userId;
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
        Actor accountActor = ma.getActor();

        DataUpdater di = new DataUpdater(ma);
        String username = "somebody" + demoData.TESTRUN_UID + "@somewhere.net";
        String userOid = "acct:" + username;
        Actor somebody = Actor.fromOriginAndActorOid(accountActor.origin, userOid);
        somebody.setActorName(username);
        somebody.setProfileUrl("https://somewhere.net/" + username);

        AActivity activity = AActivity.newPartialMessage(accountActor, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        activity.setActor(somebody);
        Note message = activity.getMessage();
        message.setBody(body);
        message.via = "MyCoolClient";

        long messageId = di.onActivity(activity).getMessage().msgId;
        Actor buddy = Actor.EMPTY;
        for (Actor user : activity.recipients().getRecipients()) {
            if (user.getActorName().equals(buddyUserName)) {
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
        Actor accountActor = ma.getActor();
        MyAccount myMentionedAccount = demoData.getMyAccount(demoData.GNUSOCIAL_TEST_ACCOUNT2_NAME);
        Actor myMentionedUser = myMentionedAccount.getActor().setActorName(myMentionedAccount.getUsername());
        Actor author1 = Actor.fromOriginAndActorOid(accountActor.origin, "sam" + demoData.TESTRUN_UID);
        author1.setActorName("samBrook");

        AActivity activity1 = AActivity.newPartialMessage(accountActor, String.valueOf(System.nanoTime()),
                System.currentTimeMillis(), DownloadStatus.LOADED);
        activity1.setActor(author1);
        Note message = activity1.getMessage();
        message.setBody("@" + myMentionedUser.getActorName() + " I mention your another account");
        message.via = "AndStatus";

        AActivity activity2 = AActivity.from(accountActor, ActivityType.UPDATE);
        activity2.setActor(author1);
        activity2.setActivity(activity1);

        DataUpdater di = new DataUpdater(ma);
        long messageId = di.onActivity(activity2).getMessage().msgId;
        assertTrue("Message added", messageId != 0);
    }
}
