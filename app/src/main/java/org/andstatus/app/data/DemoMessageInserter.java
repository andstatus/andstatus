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
import org.andstatus.app.database.MsgOfUserTable;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.pumpio.ConnectionPumpio;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DemoMessageInserter {
    private MyAccount ma;
    private Origin origin;
    private MbUser accountMbUser;

    public DemoMessageInserter(MyAccount maIn) {
        ma = maIn;
        assertTrue(ma != null);
        origin = MyContextHolder.get().persistentOrigins().fromId(ma.getOriginId());
        assertTrue("Origin for " + ma.getAccountName() + " exists", origin != null);
        accountMbUser = buildUserFromOid(ma.getUserOid(), true);
    }

    MbUser getAccountMbUser() {
        return accountMbUser;
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
        return  buildUserFromOid(userOid, false);
    }

    private MbUser buildUserFromOid(String userOid, boolean partial) {
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
        if (!partial) {
            mbUser.setUserName(username);
        }
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

        if (accountMbUser != null) {
            mbUser.actor = accountMbUser;
        }
        return mbUser;
    }

    public MbMessage buildMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn, DownloadStatus messageStatus) {
        final String method = "buildMessage";
        String messageOid = messageOidIn;
        if (TextUtils.isEmpty(messageOid) && messageStatus != DownloadStatus.SENDING) {
            if (origin.getOriginType() == OriginType.PUMPIO) {
                messageOid = author.getProfileUrl()  + "/" + (inReplyToMessage == null ? "note" : "comment") + "/thisisfakeuri" + System.nanoTime();
            } else {
                messageOid = String.valueOf(System.nanoTime());
            }
        }
        MbMessage message = MbMessage.fromOriginAndOid(origin.getId(), accountMbUser.oid, messageOid, messageStatus);
        message.setBody(body);
        message.setUpdatedDate(System.currentTimeMillis());
        message.via = "AndStatus";
        message.setAuthor(author);
        message.setInReplyTo(inReplyToMessage);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            message.url = message.oid;
        }
        DbUtils.waitMs(method, 10);
        return message;
    }

    static long addMessage(MyAccount ma, MbMessage message) {
        return new DemoMessageInserter(ma).addMessage(message);
    }

    public long addMessage(final MbMessage message) {
        DataUpdater di = new DataUpdater(new CommandExecutionContext(
                        CommandData.newTimelineCommand(CommandEnum.EMPTY, ma,
                                message.isPublic() ? TimelineType.PUBLIC : TimelineType.HOME)));
        long messageId = di.onActivity(message.update(message.getActor()));
        assertTrue( "Message added " + message.oid, messageId != 0);

        String permalink = origin.messagePermalink(messageId);
        URL urlPermalink = UrlUtils.fromString(permalink); 
        assertTrue("Message permalink is a valid URL '" + permalink + "',\n" + message.toString()
                + "\n author: " + message.getAuthor().toString(), urlPermalink != null);
        if (origin.getUrl() != null && origin.getOriginType() != OriginType.TWITTER) {
            assertEquals("Message permalink has the same host as origin, " + message.toString(),
                    origin.getUrl().getHost(), urlPermalink.getHost());
        }
        if (!TextUtils.isEmpty(message.url)) {
            assertEquals("Message permalink", message.url, origin.messagePermalink(messageId));
        }
        
        if (message.getFavoritedByMe() == TriState.TRUE) {
            long msgIdFromMsgOfUser = MyQuery.conditionToLongColumnValue(MsgOfUserTable.TABLE_NAME,
                    MsgOfUserTable.MSG_ID, "t." + MsgOfUserTable.MSG_ID + "=" + messageId);
            assertEquals("msgOfUser found for " + message, messageId, msgIdFromMsgOfUser);
            
            long userIdFromMsgOfUser = MyQuery.conditionToLongColumnValue(MsgOfUserTable.TABLE_NAME, MsgOfUserTable
                            .USER_ID, "t." + MsgOfUserTable.USER_ID + "=" + ma.getUserId());
            assertEquals("userId found for " + message, ma.getUserId(), userIdFromMsgOfUser);
        }

        if (message.isReblogged()) {
            long rebloggerId = MyQuery.conditionToLongColumnValue(MsgOfUserTable.TABLE_NAME,
                    MsgOfUserTable.USER_ID,
                    "t." + MsgOfUserTable.MSG_ID + "=" + messageId
            + " AND t." + MsgOfUserTable.REBLOGGED + "=1" );
            assertTrue("Reblogger found for msgId=" + messageId, rebloggerId != 0);
        }

        if (!message.replies.isEmpty()) {
            for (MbMessage reply : message.replies) {
                long inReplyToMsgId = MyQuery.conditionToLongColumnValue(MsgTable.TABLE_NAME,
                        MsgTable.IN_REPLY_TO_MSG_ID,
                        "t." + MsgTable.MSG_OID + "='" + reply.oid + "'" );
                assertEquals("Inserting reply:<" + reply.getBody() + ">", messageId, inReplyToMsgId);
            }
        }

        return messageId;
    }

    static void deleteOldMessage(long originId, String messageOid) {
        long messageIdOld = MyQuery.oidToId(OidEnum.MSG_OID, originId, messageOid);
        if (messageIdOld != 0) {
            int deleted = MyContextHolder.get().context().getContentResolver().delete(MatchedUri.getMsgUri(0, messageIdOld),  null, null);
            assertEquals( "Old message id=" + messageIdOld + " deleted", 1, deleted);
        }
    }
    
    public static long addMessageForAccount(MyAccount ma, String body, String messageOid, DownloadStatus messageStatus) {
        assertTrue("Is not valid: " + ma, ma.isValid());
        DemoMessageInserter mi = new DemoMessageInserter(ma);
        return mi.addMessage(mi.buildMessage(mi.buildUser(), body, null, messageOid, messageStatus));
    }
}
