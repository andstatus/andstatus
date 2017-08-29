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
import org.andstatus.app.context.DemoData;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.util.GregorianCalendar;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DemoConversationInserter {
    private static AtomicInteger iterationCounter = new AtomicInteger(0);
    private static final Map<String, MbUser> users = new ConcurrentHashMap<>();

    private int iteration = 0;
    private MyAccount ma;
    private MbUser accountUser = MbUser.EMPTY;
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
        ma = DemoData.getMyAccount(DemoData.CONVERSATION_ACCOUNT_NAME);
        assertTrue(DemoData.CONVERSATION_ACCOUNT_NAME + " exists", ma.isValid());
        accountUser = ma.toPartialUser();
        insertAndTestConversation();
    }

    private void insertAndTestConversation() {
        assertEquals("Only PumpIo supported in this test", OriginType.PUMPIO, DemoData.CONVERSATION_ORIGIN_TYPE  );

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
        
        MbActivity minus1 = buildActivity(author2, "Older one message", null, null);
        MbActivity selected = buildActivity(getAuthor1(), "Selected message from Home timeline", minus1,
                iteration == 1 ? DemoData.CONVERSATION_ENTRY_MESSAGE_OID : null);
        selected.getMessage().setSubscribedByMe(TriState.TRUE);
        MbActivity reply1 = buildActivity(author3, "Reply 1 to selected", selected, null);
        author3.followedByMe = TriState.TRUE;

        MbActivity reply1Copy = buildActivity(accountUser, MbUser.EMPTY, "", MbActivity.EMPTY,
                reply1.getMessage().oid, DownloadStatus.UNKNOWN);
        MbActivity reply12 = buildActivity(author2, "Reply 12 to 1 in Replies", reply1Copy, null);
        reply1.getMessage().replies.add(reply12.getMessage());

        MbActivity reply2 = buildActivity(author2, "Reply 2 to selected is private", selected, null);
        addPrivateMessage(reply2, TriState.TRUE);
        MbActivity reply3 = buildActivity(getAuthor1(), "Reply 3 to selected by the same author", selected, null);
        reply3.getMessage().attachments.add(MbAttachment
            .fromUrlAndContentType(UrlUtils.fromString(
                    "http://www.publicdomainpictures.net/pictures/100000/nahled/broadcasting-tower-14081029181fC.jpg"),
                    MyContentType.IMAGE));
        addActivity(selected);
        addActivity(reply3);
        addActivity(reply1);
        addActivity(reply2);
        MbActivity reply4 = buildActivity(author4, "Reply 4 to Reply 1 other author", reply1, null);
        addActivity(reply4);
        addPrivateMessage(reply4, TriState.FALSE);

        DemoConversationInserter.assertIfUserIsMyFriend(author3, true, ma);

        final String BODY_OF_MENTIONS_MESSAGE = "@fourthWithoutAvatar@pump.example.com Reply 5 to Reply 4\n"
                + "@" + DemoData.CONVERSATION_MEMBER_USERNAME
                + " @unknownUser@example.com";
        MbActivity reply5 = buildActivity(author2, BODY_OF_MENTIONS_MESSAGE, reply4,
                iteration == 1 ? DemoData.CONVERSATION_MENTIONS_MESSAGE_OID : null);
        addActivity(reply5);

        MbUser reblogger1 = buildUserFromOid("acct:reblogger@identi.ca");
        reblogger1.avatarUrl = "http://www.avatarsdb.com/avatars/cow_face.jpg";
        MbActivity reblog1 =  MbActivity.from(accountUser, MbActivityType.ANNOUNCE) ;
        reblog1.setActor(reblogger1);
        reblog1.setMessage(reply5.getMessage());
        DemoMessageInserter.onActivityS(reblog1);

        final MbActivity reply6 = buildActivity(author3, "Reply 6 to Reply 4 - the second", reply4, null);
        reply6.getMessage().setFavoritedByMe(TriState.TRUE);
        addActivity(reply6);

        MbActivity reply7 = buildActivity(getAuthor1(), "Reply 7 to Reply 2 is about "
        + DemoData.PUBLIC_MESSAGE_TEXT + " and something else", reply2, null);
        addPrivateMessage(reply7, TriState.FALSE);
        
        MbActivity reply8 = buildActivity(author4, "<b>Reply 8</b> to Reply 7", reply7, null);
        MbActivity reblogOfNewActivity =  MbActivity.from(accountUser, MbActivityType.ANNOUNCE) ;
        reblogOfNewActivity.setActor(author3);
        reblogOfNewActivity.setActivity(reply8);
        DemoMessageInserter.onActivityS(reblogOfNewActivity);

        MbActivity reply9 = buildActivity(author2, "Reply 9 to Reply 7", reply7, null);
        reply9.getMessage().attachments
                .add(MbAttachment
                        .fromUrlAndContentType( UrlUtils.fromString(
                                "http://www.publicdomainpictures.net/pictures/100000/nahled/autumn-tree-in-a-park.jpg"),
                                MyContentType.IMAGE));
        addActivity(reply9);
        addActivity(buildActivity(author4, "A duplicate of " + reply9.getMessage().getBody(),
                null, null));

        // Message downloaded by another account
        MbUser acc2 = DemoData.getMyAccount(DemoData.CONVERSATION_ACCOUNT2_NAME).toPartialUser();
        author3.followedByMe = TriState.TRUE;
        MbActivity reply10 = buildActivity(acc2, author3, "Reply 10 to Reply 8", reply8, null, DownloadStatus.LOADED);
        MbActivity activity10 = reply10.getMessage().act(acc2, acc2, MbActivityType.UPDATE);
        assertEquals("Another account as a message actor", activity10.getActor().oid, DemoData.CONVERSATION_ACCOUNT2_USER_OID);
        DemoMessageInserter.onActivityS(activity10);
        author3.followedByMe = TriState.UNKNOWN;

        MbActivity reply11 = buildActivity(author2, "Reply 11 to Reply 7, " + DemoData.GLOBAL_PUBLIC_MESSAGE_TEXT
                + " text", reply7, null);
        addPrivateMessage(reply11, TriState.FALSE);

        MbActivity reply13 = buildActivity(accountUser, "My reply to Reply 2", reply2, null);
        MbActivity reply14 = buildActivity(author3, "Reply to my message 13", reply13, null);
        addActivity(reply14);
    }

    private MbUser getAuthor1() {
        MbUser author1 = buildUserFromOid(DemoData.CONVERSATION_ENTRY_USER_OID);
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

    public static void assertIfUserIsMyFriend(MbUser user, boolean isFriendOf, MyAccount ma) {
        Set<Long> friendsIds = MyQuery.getFriendsIds(ma.getUserId());
        assertEquals("User " + user + " is a friend of " + ma, isFriendOf, friendsIds.contains(user.userId));
    }
}
