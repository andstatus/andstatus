/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.util.MyLog;

import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class RecursiveConversationLoader<T extends ConversationItem> extends ConversationLoader<T> {
    public RecursiveConversationLoader(Class<T> tClass, MyContext myContext, MyAccount ma,
                                       long selectedMessageId) {
        super(tClass, myContext, ma, selectedMessageId);
    }

    @Override
    protected void load2(T oMsg) {
        findPreviousMessagesRecursively(oMsg);
    }

    private void findPreviousMessagesRecursively(T oMsg) {
        if (!addMessageIdToFind(oMsg.getMsgId())) {
            return;
        }
        findRepliesRecursively(oMsg);
        MyLog.v(this, "findPreviousMessages id=" + oMsg.getMsgId());
        loadMessageFromDatabase(oMsg);
        if (oMsg.isLoaded()) {
            if (addMessageToList(oMsg)) {
                if (oMsg.inReplyToMsgId != 0) {
                    findPreviousMessagesRecursively(newOMsg(oMsg.inReplyToMsgId,
                            oMsg.replyLevel - 1));
                }
            }
        } else if (mAllowLoadingFromInternet) {
            loadFromInternet(oMsg.getMsgId());
        }
    }

    public void findRepliesRecursively(T oMsg) {
        MyLog.v(this, "findReplies for id=" + oMsg.getMsgId());
        List<Long> replies = MyQuery.getReplyIds(oMsg.getMsgId());
        oMsg.mNReplies = replies.size();
        for (long replyId : replies) {
            T oMsgReply = newOMsg(replyId, oMsg.replyLevel + 1);
            findPreviousMessagesRecursively(oMsgReply);
        }
    }
}
