/**
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ConversationViewLoader {
    private static final int MAX_INDENT_LEVEL = 19;
    
    private Context context;
    private MyAccount ma;
    private long selectedMessageId;
    private ReplyLevelComparator replyLevelComparator = new ReplyLevelComparator();
    
    List<ConversationOneMessage> oMsgs = new ArrayList<ConversationOneMessage>();

    public List<ConversationOneMessage> getMsgs() {
        return oMsgs;
    }

    List<Long> idsOfTheMessagesToFind = new ArrayList<Long>();

    public ConversationViewLoader(Context context, MyAccount ma, long selectedMessageId) {
        this.context = context;
        this.ma = ma;
        this.selectedMessageId = selectedMessageId;
    }
    
    public void load() {
        idsOfTheMessagesToFind.clear();
        oMsgs.clear();
        findPreviousMessagesRecursively(new ConversationOneMessage(selectedMessageId, 0));
        Collections.sort(oMsgs, replyLevelComparator);
        enumerateMessages();
        if (MyPreferences.getBoolean(
                MyPreferences.KEY_OLD_MESSAGES_FIRST_IN_CONVERSATION, false)) {
            reverseListOrder();
        }
        Collections.sort(oMsgs);
    }

    private void findPreviousMessagesRecursively(ConversationOneMessage oMsg) {
        if (!addMessageIdToFind(oMsg.mMsgId)) {
            return;
        }
        findRepliesRecursively(oMsg);
        MyLog.v(this, "findPreviousMessages id=" + oMsg.mMsgId);
        loadMessageFromDatabase(oMsg);
        if (oMsg.isLoaded()) {
            if (addMessageToList(oMsg)) {
                if (oMsg.mInReplyToMsgId != 0) {
                    findPreviousMessagesRecursively(new ConversationOneMessage(oMsg.mInReplyToMsgId,
                            oMsg.mReplyLevel - 1));
                } else {
                    checkInReplyToNameOf(oMsg);                    
                }
            }
        } else {
            retrieveFromInternet(oMsg.mMsgId);
        }
    }
    
    /** Returns true if message was added 
     *          false in a case the message existed already 
     * */
    private boolean addMessageIdToFind(long msgId) {
        if (msgId == 0) {
            return false;
        } else if (idsOfTheMessagesToFind.contains(msgId)) {
            MyLog.v(this, "findMessages cycled on the id=" + msgId);
            return false;
        }
        idsOfTheMessagesToFind.add(msgId);
        return true;
    }

    public void findRepliesRecursively(ConversationOneMessage oMsg) {
        MyLog.v(this, "findReplies for id=" + oMsg.mMsgId);
        List<Long> replies = MyProvider.getReplyIds(oMsg.mMsgId);
        oMsg.mNReplies = replies.size();
        for (long replyId : replies) {
            ConversationOneMessage oMsgReply = new ConversationOneMessage(replyId, oMsg.mReplyLevel + 1);
            findPreviousMessagesRecursively(oMsgReply);
        }
    }
    
    private void loadMessageFromDatabase(ConversationOneMessage oMsg) {
        Uri uri = MyProvider.getTimelineMsgUri(ma.getUserId(), TimelineTypeEnum.EVERYTHING, true, oMsg.mMsgId);
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, TimelineSql.getConversationProjection(), null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                oMsg.load(cursor);
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    private boolean addMessageToList(ConversationOneMessage oMsg) {
        boolean added = false;
        if (oMsgs.contains(oMsg)) {
            MyLog.v(this, "Message id=" + oMsg.mMsgId + " is in the list already");
        } else {
            oMsgs.add(oMsg);
            added = true;
        }
        return added;
    }
    
    private void checkInReplyToNameOf(ConversationOneMessage oMsg) {
        if (!SharedPreferencesUtil.isEmpty(oMsg.mInReplyToName)) {
            MyLog.v(this, "Message id=" + oMsg.mMsgId + " has reply to name ("
                    + oMsg.mInReplyToName
                    + ") but no reply to message id");
            // Don't try to retrieve this message again. 
            // It looks like such messages really exist.
            ConversationOneMessage oMsg2 = new ConversationOneMessage(0, oMsg.mReplyLevel-1);
            // This allows to place the message on the timeline correctly
            oMsg2.mCreatedDate = oMsg.mCreatedDate - 60000;
            oMsg2.mAuthor = oMsg.mInReplyToName;
            oMsg2.mBody = "("
                    + context.getText(R.string.id_of_this_message_was_not_specified)
                    + ")";
            addMessageToList(oMsg2);
        }
    }

    private void retrieveFromInternet(long msgId) {
        MyLog.v(this, "Message id=" + msgId + " should be retrieved from the Internet");
        MyServiceManager.sendForegroundCommand(new CommandData(CommandEnum.GET_STATUS, ma
                .getAccountName(), msgId));
    }

    private static class ReplyLevelComparator implements Comparator<ConversationOneMessage>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(ConversationOneMessage lhs, ConversationOneMessage rhs) {
            int compared = rhs.mReplyLevel - lhs.mReplyLevel;
            if (compared == 0) {
                if (lhs.mCreatedDate == rhs.mCreatedDate) {
                    if ( lhs.mMsgId == rhs.mMsgId) {
                        compared = 0;
                    } else {
                        compared = (rhs.mMsgId - lhs.mMsgId > 0 ? 1 : -1);
                    }
                } else {
                    compared = (rhs.mCreatedDate - lhs.mCreatedDate > 0 ? 1 : -1);
                }
            }
            return compared;
        }
    }

    private static class OrderCounters {
        int list = -1;
        int history = 1;
    }
    
    private void enumerateMessages() {
        idsOfTheMessagesToFind.clear();
        for (ConversationOneMessage oMsg : oMsgs) {
            oMsg.mListOrder = 0;
            oMsg.mHistoryOrder = 0;
        }
        OrderCounters order = new OrderCounters();
        for (int ind = oMsgs.size()-1; ind >= 0; ind--) {
            ConversationOneMessage oMsg = oMsgs.get(ind);
            if (oMsg.mListOrder < 0 ) {
                continue;
            }
            enumerateBranch(oMsg, order, 0);
        }
    }

    private void enumerateBranch(ConversationOneMessage oMsg, OrderCounters order, int indent) {
        if (!addMessageIdToFind(oMsg.mMsgId)) {
            return;
        }
        int indentNext = indent;
        oMsg.mHistoryOrder = order.history++;
        oMsg.mListOrder = order.list--;
        oMsg.mIndentLevel = indent;
        if ((oMsg.mNReplies > 1 || oMsg.mNParentReplies > 1)
                && indentNext < MAX_INDENT_LEVEL) {
            indentNext++;
        }
        for (int ind = oMsgs.size() - 1; ind >= 0; ind--) {
           ConversationOneMessage reply = oMsgs.get(ind);
           if (reply.mInReplyToMsgId == oMsg.mMsgId) {
               reply.mNParentReplies = oMsg.mNReplies;
               enumerateBranch(reply, order, indentNext);
           }
        }
    }

    private void reverseListOrder() {
        for (ConversationOneMessage oMsg : oMsgs) {
            oMsg.mListOrder = oMsgs.size() - oMsg.mListOrder - 1; 
        }
    }
    
}
