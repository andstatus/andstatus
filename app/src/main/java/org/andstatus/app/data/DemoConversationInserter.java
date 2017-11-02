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
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.andstatus.app.context.DemoData.demoData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DemoConversationInserter {
    private static AtomicInteger iterationCounter = new AtomicInteger(0);
    private static final Map<String, MbUser> users = new ConcurrentHashMap<>();

    private int iteration = 0;
    private MyAccount ma;
    private MbUser accountUser = MbUser.EMPTY;
    private String bodySuffix = "";

    public static void onNewDemoData() {
        iterationCounter.set(0);
        users.clear();
    }

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
        ma = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(demoData.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        accountUser = ma.toPartialUser();
        insertAndTestConversation();
    }

    private void insertAndTestConversation() {
        assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, demoData.CONVERSATION_ORIGIN_TYPE  );

        MbUser author2 = buildUserFromOid(demoData.CONVERSATION_AUTHOR_SECOND_USER_OID);
        author2.avatarUrl = "http://png.findicons.com/files/icons/1780/black_and_orange/300/android_orange.png";

        MbUser author3 = buildUserFromOid(demoData.CONVERSATION_AUTHOR_THIRD_USER_OID);
        author3.setRealName("John Smith");
        author3.setUserName(demoData.CONVERSATION_AUTHOR_THIRD_USERNAME);
        author3.setHomepage("http://johnsmith.com/welcome");
        author3.setCreatedDate(new GregorianCalendar(2011,5,12).getTimeInMillis());
        author3.setDescription("I am an ordinary guy, interested in computer science");
        author3.avatarUrl = "http://www.large-icons.com/stock-icons/free-large-android/48x48/happy-robot.gif";
        users.put(author3.oid, author3);

        MbUser author4 = buildUserFromOid("acct:fourthWithoutAvatar@pump.example.com");
        
        MbActivity minus1 = buildActivity(author2, "Older one message", null, null);
        MbActivity selected = buildActivity(getAuthor1(), "Selected message from Home timeline", minus1,
                iteration == 1 ? demoData.CONVERSATION_ENTRY_MESSAGE_OID : null);
        selected.setSubscribedByMe(TriState.TRUE);
        MbActivity reply1 = buildActivity(author3, "Reply 1 to selected", selected, null);
        author3.followedByMe = TriState.TRUE;

        MbActivity reply1Copy = buildActivity(accountUser,
                MbUser.fromOriginAndUserOid(reply1.accountUser.originId, reply1.getAuthor().oid),
                "", MbActivity.EMPTY,
                reply1.getMessage().oid, DownloadStatus.UNKNOWN);
        MbActivity reply12 = buildActivity(author2, "Reply 12 to 1 in Replies", reply1Copy, null);
        reply1.getMessage().replies.add(reply12);

        MbActivity reply2 = buildActivity(author2, "Reply 2 to selected is private", selected, null);
        addPrivateMessage(reply2, TriState.TRUE);
        if (iteration == 1) {
            assertEquals("Should be subscribed " + selected, TriState.TRUE,
                    TriState.fromId(MyQuery.activityIdToLongColumnValue(ActivityTable.SUBSCRIBED, selected.getId())));
        }

        MbActivity reply3 = buildActivity(getAuthor1(), "Reply 3 to selected by the same author", selected, null);
        reply3.getMessage().attachments.add(MbAttachment
            .fromUrlAndContentType(UrlUtils.fromString(
                    "http://www.publicdomainpictures.net/pictures/100000/nahled/broadcasting-tower-14081029181fC.jpg"),
                    MyContentType.IMAGE));
        addActivity(reply3);
        addActivity(reply1);
        addActivity(reply2);
        MbActivity reply4 = buildActivity(author4, "Reply 4 to Reply 1 other author", reply1, null);
        addActivity(reply4);

        DemoMessageInserter.increaseUpdateDate(reply4);
        addPrivateMessage(reply4, TriState.FALSE);

        DemoConversationInserter.assertIfUserIsMyFriend(author3, true, ma);

        final String BODY_OF_MENTIONS_MESSAGE = "@fourthWithoutAvatar@pump.example.com Reply 5 to Reply 4\n"
                + "@" + author3.getUserName()
                + " @unknownUser@example.com";
        MbActivity reply5 = buildActivity(author2, BODY_OF_MENTIONS_MESSAGE, reply4,
                iteration == 1 ? demoData.CONVERSATION_MENTIONS_MESSAGE_OID : null);
        addActivity(reply5);

        MbUser reblogger1 = buildUserFromOid("acct:reblogger@" + demoData.PUMPIO_MAIN_HOST);
        reblogger1.avatarUrl = "http://www.avatarsdb.com/avatars/cow_face.jpg";
        MbActivity reblogOf5 = buildActivity(reblogger1, MbActivityType.ANNOUNCE);
        reblogOf5.setMessage(reply5.getMessage().shallowCopy());
        reblogOf5.setSubscribedByMe(TriState.TRUE);
        addActivity(reblogOf5);

        final MbActivity reply6 = buildActivity(author3, "Reply 6 to Reply 4 - the second", reply4, null);
        reply6.getMessage().addFavoriteBy(accountUser, TriState.TRUE);
        addActivity(reply6);

        MbActivity likeOf6 = buildActivity(author2, MbActivityType.LIKE);
        likeOf6.setMessage(reply6.getMessage().shallowCopy());
        addActivity(likeOf6);

        MbActivity reply7 = buildActivity(getAuthor1(), "Reply 7 to Reply 2 is about "
        + demoData.PUBLIC_MESSAGE_TEXT + " and something else", reply2, null);
        addPrivateMessage(reply7, TriState.FALSE);
        
        MbActivity reply8 = buildActivity(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        MbActivity reblogOfNewActivity8 = buildActivity(author3, MbActivityType.ANNOUNCE);
        reblogOfNewActivity8.setActivity(reply8);
        addActivity(reblogOfNewActivity8);

        MbActivity reply9 = buildActivity(author2, "Reply 9 to Reply 7", reply7, null);
        reply9.setSubscribedByMe(TriState.TRUE);
        reply9.getMessage().attachments
                .add(MbAttachment
                        .fromUrlAndContentType( UrlUtils.fromString(
                                "http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg"),
                                MyContentType.IMAGE));
        addActivity(reply9);
        final MbActivity duplicateOfReply9 = buildActivity(author4, "A duplicate of " + reply9.getMessage().getBody(),
                null, null);
        duplicateOfReply9.setSubscribedByMe(TriState.TRUE);
        addActivity(duplicateOfReply9);

        MbActivity myLikeOf9 =  MbActivity.from(accountUser, MbActivityType.LIKE) ;
        myLikeOf9.setActor(accountUser);
        myLikeOf9.setMessage(reply9.getMessage().shallowCopy());
        addActivity(myLikeOf9);

        // Message downloaded by another account
        final MyAccount ma2 = demoData.getMyAccount(demoData.CONVERSATION_ACCOUNT2_NAME);
        MbUser accountUser2 = ma2.toPartialUser();
        author3.followedByMe = TriState.TRUE;
        MbActivity reply10 = buildActivity(accountUser2, author3, "Reply 10 to Reply 8", reply8, null, DownloadStatus.LOADED);
        assertEquals("The third is a message Author", author3,  reply10.getAuthor());
        addActivity(reply10);
        author3.followedByMe = TriState.UNKNOWN;

        DemoConversationInserter.assertIfUserIsMyFriend(author3, true, ma2);

        MbActivity reply11 = buildActivity(author2, "Reply 11 to Reply 7, " + demoData.GLOBAL_PUBLIC_MESSAGE_TEXT
                + " text", reply7, null);
        addPrivateMessage(reply11, TriState.FALSE);

        MbActivity reply13 = buildActivity(accountUser, "My reply to Reply 2", reply2, null);
        MbActivity reply14 = buildActivity(author3, "Reply to my message 13", reply13, null);
        addActivity(reply14);

        MbActivity reblogOf14 = buildActivity(author2, MbActivityType.ANNOUNCE);
        reblogOf14.setMessage(reply14.getMessage().shallowCopy());
        addActivity(reblogOf14);

        MbActivity mentionOfAuthor3 = buildActivity(reblogger1, "@" + author3.getUserName() + " mention in reply to 4",
                reply4, iteration == 1 ? demoData.CONVERSATION_MENTION_OF_AUTHOR3_OID : null);
        addActivity(mentionOfAuthor3);

        MbActivity followOf3 = buildActivity(author2, MbActivityType.FOLLOW);
        followOf3.setUser(author3);
        addActivity(followOf3);

        MbActivity notLoaded1 = MbActivity.newPartialMessage(accountUser, MyLog.uniqueDateTimeFormatted());
        MbUser notLoadedUser = MbUser.fromOriginAndUserOid(accountUser.originId, "acct:notloaded@someother.host"
        + demoData.TEST_ORIGIN_PARENT_HOST);
        notLoaded1.setActor(notLoadedUser);
        MbActivity reply15 = buildActivity(author4, "Reply to not loaded 1", notLoaded1, null);
        addActivity(reply15);
    }

    private MbUser getAuthor1() {
        MbUser author1 = buildUserFromOid(demoData.CONVERSATION_ENTRY_AUTHOR_OID);
        author1.avatarUrl = "https://raw.github.com/andstatus/andstatus/master/app/src/main/res/drawable/splash_logo.png";
        return author1;
    }
    
    private void addPrivateMessage(MbActivity activity, TriState isPrivate) {
        activity.getMessage().setPrivate(isPrivate);
        addActivity(activity);
        TriState storedPrivate = TriState.fromId(
                MyQuery.msgIdToLongColumnValue(MsgTable.PRIVATE, activity.getMessage().msgId));
        assertEquals("Message is " + (isPrivate.equals(TriState.TRUE) ? "private" :
                        isPrivate.equals(TriState.FALSE) ? "non private" : "") + ": " + activity.getMessage().getBody(),
                isPrivate, storedPrivate);
    }

    private MbUser buildUserFromOid(String userOid) {
        return new DemoMessageInserter(accountUser).buildUserFromOid(userOid);
    }

    private MbActivity buildActivity(MbUser actor, MbActivityType type) {
        return new DemoMessageInserter(accountUser).buildActivity(actor, type, "");
    }

    private MbActivity buildActivity(MbUser author, String body, MbActivity inReplyTo, String messageOidIn) {
        return buildActivity(accountUser, author, body, inReplyTo, messageOidIn, DownloadStatus.LOADED);
    }

    private MbActivity buildActivity(MbUser accountUser, MbUser author, String body, MbActivity inReplyTo,
                                     String messageOidIn, DownloadStatus status) {
        return new DemoMessageInserter(accountUser).buildActivity(author, body
                        + (inReplyTo != null ? " it" + iteration : "") + bodySuffix,
                inReplyTo, messageOidIn, status);
    }

    private void addActivity(MbActivity activity) {
        DemoMessageInserter.onActivityS(activity);
    }

    static void assertIfUserIsMyFriend(MbUser user, boolean isFriendOf, MyAccount ma) {
        Set<Long> friendsIds = MyQuery.getFriendsIds(ma.getUserId());
        assertEquals("User " + user + " is a friend of " + ma, isFriendOf, friendsIds.contains(user.userId));
    }
}
