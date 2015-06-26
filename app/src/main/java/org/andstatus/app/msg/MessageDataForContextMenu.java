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

package org.andstatus.app.msg;

import android.content.Context;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MessageForAccount;
import org.andstatus.app.data.TimelineType;

/**
 * Helper class for the message context menu creation 
 * @author yvolk@yurivolkov.com
 */
class MessageDataForContextMenu {
    private MessageForAccount msg;
    
    public MessageDataForContextMenu(Context context, long firstUserId, 
            long preferredUserId, TimelineType timelineType, long msgId, boolean forceFirstUser) {
        MyAccount ma = MyContextHolder.get().persistentAccounts()
                .getAccountForThisMessage(msgId, firstUserId,
                        preferredUserId,
                        false);
        msg = new MessageForAccount(msgId, ma);
        if (!ma.isValid()) {
            return;
        }
        if ( !forceFirstUser 
                && !msg.isTiedToThisAccount()
                && ma.getUserId() != preferredUserId
                && timelineType != TimelineType.FOLLOWING_USER) {
            MyAccount ma2 = MyContextHolder.get().persistentAccounts().fromUserId(preferredUserId);
            if (ma2.isValid() && ma.getOriginId() == ma2.getOriginId()) {
                msg = new MessageForAccount(msgId, ma2);
            }
        }
    }

    public MessageForAccount getMsg() {
        return msg;
    }
}
