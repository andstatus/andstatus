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

import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.MyDatabase.OidEnum;
import org.andstatus.app.net.social.ConnectionPumpio;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.SelectionAndArgs;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;

import java.net.URL;

public class MessageInserter extends InstrumentationTestCase {
    private MyAccount ma;
    private Origin origin;
    private MbUser accountMbUser;

    public MessageInserter(MyAccount maIn) {
        ma = maIn;
        assertTrue(ma != null);
        origin = MyContextHolder.get().persistentOrigins().fromId(ma.getOriginId());
        assertTrue("Origin for " + ma.getAccountName() + " exists", origin != null);
        accountMbUser = buildUserFromOid(ma.getUserOid());
    }

    public MbUser getAccountMbUser() {
        return accountMbUser;
    }
    
    public MbUser buildUser() {
        if (origin.getOriginType() == OriginType.PUMPIO) {
            return buildUserFromOid("acct:userOf" + origin.getName() + TestSuite.TESTRUN_UID);
        }
        return buildUserFromOid(TestSuite.TESTRUN_UID);
    }

    public MbUser buildUserFromOidAndAvatar(String userOid, String avatarUrlString) {
        MbUser mbUser = buildUserFromOid(userOid);
        mbUser.avatarUrl = avatarUrlString;
        return mbUser;
    }
    
    public final MbUser buildUserFromOid(String userOid) {
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), userOid);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            ConnectionPumpio connection = new ConnectionPumpio();
            mbUser.setUserName(connection.userOidToUsername(userOid));
            mbUser.setUrl("http://" + connection.usernameToHost(mbUser.getUserName()) + "/"
                    + connection.usernameToNickname(mbUser.getUserName()));
        } else {
            mbUser.setUserName("userOf" + origin.getName() + userOid);
        }
        if (accountMbUser != null) {
            mbUser.actor = accountMbUser;
        }
        return mbUser;
    }
    
    public MbMessage buildMessage(MbUser author, String body, MbMessage inReplyToMessage, String messageOidIn) {
        String messageOid = messageOidIn;
        if (TextUtils.isEmpty(messageOid)) {
            if (origin.getOriginType() == OriginType.PUMPIO) {
                messageOid = author.getUrl()  + "/" + (inReplyToMessage == null ? "note" : "comment") + "/thisisfakeuri" + System.nanoTime();
            } else {
                messageOid = String.valueOf(System.nanoTime());
            }
        }
        MbMessage message = MbMessage.fromOriginAndOid(origin.getId(), messageOid);
        message.setBody(body);
        message.sentDate = System.currentTimeMillis();
        message.via = "AndStatus";
        message.sender = author;
        message.actor = accountMbUser;
        message.inReplyToMessage = inReplyToMessage;
        if (origin.getOriginType() == OriginType.PUMPIO) {
            message.url = message.oid;
        }        
        try {
            Thread.sleep(2);
        } catch (InterruptedException ignored) {
        }
        return message;
    }
    
    public long addMessage(MbMessage message) {
        TimelineType tt = TimelineType.HOME;
        if (message.isPublic() ) {
            tt = TimelineType.PUBLIC;
        }
        DataInserter di = new DataInserter(new CommandExecutionContext(CommandData.getEmpty(), ma).setTimelineType(tt));
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added " + message.oid, messageId != 0);

        String permalink = origin.messagePermalink(messageId);
        URL urlPermalink = UrlUtils.fromString(permalink); 
        assertTrue("Message permalink is a valid URL '" + permalink + "', " + message.toString(),  urlPermalink != null);
        if (origin.getUrl() != null && origin.getOriginType() != OriginType.TWITTER) {
            assertEquals("Message permalink has the same host as origin, " + message.toString(), origin.getUrl().getHost(), urlPermalink.getHost());
        }
        if (!TextUtils.isEmpty(message.url)) {
            assertEquals("Message permalink", message.url, origin.messagePermalink(messageId));
        }
        
        if (message.favoritedByActor == TriState.TRUE) {
            long msgIdFromMsgOfUser = MyQuery.conditionToLongColumnValue(MyDatabase.MsgOfUser.TABLE_NAME, MyDatabase.MsgOfUser.MSG_ID, 
                    "t." + MyDatabase.MsgOfUser.MSG_ID + "=" + messageId);
            assertEquals("msgofuser found for msgId=" + messageId, messageId, msgIdFromMsgOfUser);
            
            long userIdFromMsgOfUser = MyQuery.conditionToLongColumnValue(MyDatabase.MsgOfUser.TABLE_NAME, MyDatabase.MsgOfUser.MSG_ID, 
                    "t." + MyDatabase.MsgOfUser.USER_ID + "=" + ma.getUserId());
            assertEquals("userId found for msgId=" + messageId, ma.getUserId(), userIdFromMsgOfUser);
        }
        
        return messageId;
    }

    public static void deleteOldMessage(long originId, String messageOid) {
        long messageIdOld = MyQuery.oidToId(OidEnum.MSG_OID, originId, messageOid);
        if (messageIdOld != 0) {
            int deleted = TestSuite.getMyContextForTest().context().getContentResolver().delete(MatchedUri.getMsgUri(0, messageIdOld),  null, null);
            assertEquals( "Old message id=" + messageIdOld + " deleted", 1, deleted);
        }
    }
    
    public static long addMessageForAccount(String accountName, String body, String messageOid) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromAccountName(accountName);
        assertTrue(accountName + " exists", ma.isValid());
        MessageInserter mi = new MessageInserter(ma);
        return mi.addMessage(mi.buildMessage(mi.buildUser(), body, null, messageOid));
    }
}
