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
import org.andstatus.app.net.ConnectionPumpio;
import org.andstatus.app.net.MbMessage;
import org.andstatus.app.net.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandExecutionContext;
import org.andstatus.app.util.TriState;

public class MessageInserter extends InstrumentationTestCase {
    private MyAccount ma;
    private Origin origin;
    private MbUser accountMbUser;
    
    public MessageInserter(MyAccount maIn) {
        ma = maIn;
        assertTrue(ma != null);
        origin = MyContextHolder.get().persistentOrigins().fromId(ma.getOriginId());
        assertTrue("Origin for " + ma.getAccountName() + " exists", origin != null);
    }
    
    public MbUser buildUser() {
        if (origin.getOriginType() == OriginType.PUMPIO) {
            return buildUserFromOid("acct:user" + String.valueOf(System.nanoTime()));
        }
        return buildUserFromOid(String.valueOf(System.nanoTime()));
    }
    
    public MbUser buildUserFromOid(String userOid) {
        MbUser mbUser = MbUser.fromOriginAndUserOid(origin.getId(), userOid);
        if (origin.getOriginType() == OriginType.PUMPIO) {
            ConnectionPumpio connection = new ConnectionPumpio();
            mbUser.userName = connection.userOidToUsername(userOid);
            mbUser.url = "http://" + connection.usernameToHost(mbUser.userName) + "/"
                    + connection.usernameToNickname(mbUser.userName);
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
                messageOid = author.url  + "/" + (inReplyToMessage == null ? "note" : "comment") + "/thisisfakeuri" + System.nanoTime();
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
        try {
            Thread.sleep(2);
        } catch (InterruptedException ignored) {
        }
        return message;
    }
    
    public long addMessage(MbMessage message) {
        TimelineTypeEnum tt = TimelineTypeEnum.HOME;
        if (message.isPublic() ) {
            tt = TimelineTypeEnum.PUBLIC;
        }
        DataInserter di = new DataInserter(new CommandExecutionContext(CommandData.getEmpty(), ma).setTimelineType(tt));
        long messageId = di.insertOrUpdateMsg(message);
        assertTrue( "Message added " + message.oid, messageId != 0);
        
        
        if (message.isPublic() ) {
            long msgIdFromMsgOfUser = MyProvider.conditionToLongColumnValue(MyDatabase.MsgOfUser.TABLE_NAME, MyDatabase.MsgOfUser.MSG_ID, 
                    "t." + MyDatabase.MsgOfUser.MSG_ID + "=" + messageId);
            assertEquals("msgofuser not found for msgId=" + messageId, 0, msgIdFromMsgOfUser);
        } else {
            if (message.favoritedByActor == TriState.TRUE) {
                long msgIdFromMsgOfUser = MyProvider.conditionToLongColumnValue(MyDatabase.MsgOfUser.TABLE_NAME, MyDatabase.MsgOfUser.MSG_ID, 
                        "t." + MyDatabase.MsgOfUser.MSG_ID + "=" + messageId);
                assertEquals("msgofuser found for msgId=" + messageId, messageId, msgIdFromMsgOfUser);
                
                long userIdFromMsgOfUser = MyProvider.conditionToLongColumnValue(MyDatabase.MsgOfUser.TABLE_NAME, MyDatabase.MsgOfUser.MSG_ID, 
                        "t." + MyDatabase.MsgOfUser.USER_ID + "=" + ma.getUserId());
                assertEquals("userId found for msgId=" + messageId, ma.getUserId(), userIdFromMsgOfUser);
            }
        }
        
        return messageId;
    }
    
}
