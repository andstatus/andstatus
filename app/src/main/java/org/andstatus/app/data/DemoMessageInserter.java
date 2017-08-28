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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.ActivityTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DemoMessageInserter {
    public final MbUser accountUser;
    private final Origin origin;

    public DemoMessageInserter(MyAccount ma) {
        this(ma.toPartialUser());
    }

    public DemoMessageInserter(MbUser accountUser) {
        this.accountUser = accountUser;
        assertTrue(accountUser != null);
        origin = MyContextHolder.get().persistentOrigins().fromId(accountUser.originId);
        assertTrue("Origin exists for " + accountUser, origin.isValid());
    }

    public MbUser buildUser() {
        if (origin.getOriginType() == OriginType.PUMPIO) {
            return buildUserFromOid("acct:userOf" + origin.getName() + DemoData.TESTRUN_UID);
        }
        return buildUserFromOid(DemoData.TESTRUN_UID);
    }

    public MbUser buildUserFromOidAndAvatar(String userOid, String avatarUrlString) {
        MbUser mbUser = buildUserFromOid(userOid);
        mbUser.avatarUrl = avatarUrlString;
        return mbUser;
    }
    
    final MbUser buildUserFromOid(String userOid) {
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), userOid);
        String username;
        String profileUrl;
        if (origin.getOriginType() == OriginType.PUMPIO) {
            ConnectionPumpio connection = new ConnectionPumpio();
            username = connection.userOidToUsername(userOid);
            profileUrl = "http://" + connection.usernameToHost(username) + "/"
                    + connection.usernameToNickname(username);
        } else {
            username = "userOf" + origin.getName() + userOid;
            profileUrl = "https://" + DemoData.GNUSOCIAL_TEST_ORIGIN_NAME
                    + ".example.com/profiles/" + username;
        }
        mbUser.setUserName(username);
        mbUser.setProfileUrl(profileUrl);
        mbUser.setRealName("Real " + username);
        mbUser.setDescription("This is about " + username);
        mbUser.setHomepage("https://example.com/home/" + username + "/start/");
        mbUser.location = "Faraway place #" + DemoData.TESTRUN_UID;
        mbUser.avatarUrl = mbUser.getHomepage() + "avatar.jpg";
        mbUser.bannerUrl = mbUser.getHomepage() + "banner.png";
        long rand = InstanceId.next();
        mbUser.msgCount = rand * 2 + 3;
        mbUser.favoritesCount = rand + 11;
        mbUser.followingCount = rand + 17;
        mbUser.followersCount = rand;
        return mbUser;
    }

    public MbActivity buildActivity(MbUser author, String body, MbActivity inReplyToActivity, String messageOidIn,
                                    DownloadStatus messageStatus) {
        final String method = "buildMessage";
        String messageOid = messageOidIn;
        if (TextUtils.isEmpty(messageOid) && messageStatus != DownloadStatus.SENDING) {
            if (origin.getOriginType() == OriginType.PUMPIO) {
                messageOid =  (author.isPartiallyDefined() ? "http://pumpiotest" + origin.getId()
                        + ".example.com/user/" + author.oid : author.getProfileUrl())
                        + "/" + (inReplyToActivity == null ? "note" : "comment")
                        + "/thisisfakeuri" + System.nanoTime();
            } else {
                messageOid = MyLog.uniqueDateTimeFormatted();
            }
        }
        MbActivity activity = MbActivity.from(accountUser, MbActivityType.CREATE);
        activity.setTimelinePosition(messageOid + "-" + activity.type.name().toLowerCase());
        activity.setActor(author);
        activity.setUpdatedDate(System.currentTimeMillis());
        MbMessage message = MbMessage.fromOriginAndOid(origin.getId(), messageOid, messageStatus);
        activity.setMessage(message);
        message.setUpdatedDate(activity.getUpdatedDate());
        message.setBody(body);
        message.via = "AndStatus";
        message.setInReplyTo(inReplyToActivity);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            message.url = message.oid;
        }
        DbUtils.waitMs(method, 10);
        return activity;
    }

    static void onActivityS(MbActivity activity) {
        new DemoMessageInserter(activity.accountUser).onActivity(activity);
    }

    public void onActivity(final MbActivity activity) {
        MbMessage message = activity.getMessage();
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(accountUser.userId);
        assertTrue("Persistent account exists for " + accountUser, ma.isValid());
        DataUpdater di = new DataUpdater(new CommandExecutionContext(
                        CommandData.newTimelineCommand(CommandEnum.EMPTY, ma,
                                message.isPrivate() ? TimelineType.DIRECT : TimelineType.HOME)));
        long messageId = di.onActivity(activity).getMessage().msgId;
        assertNotEquals( "Message was not added: " + message, 0, messageId);
        assertNotEquals( "Activity was not added: " + activity, 0, activity.getId());

        String permalink = origin.messagePermalink(messageId);
        URL urlPermalink = UrlUtils.fromString(permalink); 
        assertNotNull("Message permalink is a valid URL '" + permalink + "',\n" + message.toString()
                + "\n origin: " + origin
                + "\n author: " + activity.getAuthor().toString(), urlPermalink);
        if (origin.getUrl() != null && origin.getOriginType() != OriginType.TWITTER) {
            assertEquals("Message permalink has the same host as origin, " + message.toString(),
                    origin.getUrl().getHost(), urlPermalink.getHost());
        }
        if (!TextUtils.isEmpty(message.url)) {
            assertEquals("Message permalink", message.url, origin.messagePermalink(messageId));
        }

        MbUser author = activity.getAuthor();
        assertNotEquals( "Author id for " + author + " not set in message " + message + " in activity " + activity, 0,
                MyQuery.msgIdToUserId(MsgTable.AUTHOR_ID, message.msgId));

        if (message.getFavoritedByMe() == TriState.TRUE) {
            long favoritedUser = MyQuery.conditionToLongColumnValue(ActivityTable.TABLE_NAME,
                    ActivityTable.ACTOR_ID, "t." + ActivityTable.ACTIVITY_TYPE
                            + "=" + MbActivityType.LIKE.id +" AND t." + ActivityTable.MSG_ID + "=" + messageId);
            assertEquals("User, who favorited " + message, accountUser.userId, favoritedUser);
        }

        if (message.isReblogged()) {
            long rebloggerId = MyQuery.conditionToLongColumnValue(ActivityTable.TABLE_NAME,
                    ActivityTable.ACTOR_ID, "t." + ActivityTable.ACTIVITY_TYPE
                            + "=" + MbActivityType.ANNOUNCE.id +" AND t." + ActivityTable.MSG_ID + "=" + messageId);
            assertTrue("Reblogger found for " + message, rebloggerId != 0);
        }

        if (!message.replies.isEmpty()) {
            for (MbMessage reply : message.replies) {
                long inReplyToMsgId = MyQuery.conditionToLongColumnValue(MsgTable.TABLE_NAME,
                        MsgTable.IN_REPLY_TO_MSG_ID,
                        "t." + MsgTable.MSG_OID + "='" + reply.oid + "'" );
                assertEquals("Inserting reply:<" + reply.getBody() + ">", messageId, inReplyToMsgId);
            }
        }
    }

    static void deleteOldMessage(long originId, String messageOid) {
        long messageIdOld = MyQuery.oidToId(OidEnum.MSG_OID, originId, messageOid);
        if (messageIdOld != 0) {
            int deleted = MyContextHolder.get().context().getContentResolver().delete(
                    MatchedUri.getMsgUri(0, messageIdOld),  null, null);
            assertEquals( "Old message id=" + messageIdOld + " deleted", 1, deleted);
        }
    }
    
    public static MbActivity addMessageForAccount(MyAccount ma, String body, String messageOid, DownloadStatus messageStatus) {
        assertTrue("Is not valid: " + ma, ma.isValid());
        MbUser accountUser = ma.toPartialUser();
        DemoMessageInserter mi = new DemoMessageInserter(accountUser);
        MbActivity activity = mi.buildActivity(accountUser, body, null, messageOid, messageStatus);
        mi.onActivity(activity);
        return activity;
    }
}
