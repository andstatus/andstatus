/**
 * Copyright (C) 2013-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.Cursor;
import android.net.Uri;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.LoadableListActivity.SyncLoader;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.timeline.Timeline;
import org.andstatus.app.timeline.TimelineType;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ConversationLoader<T extends ConversationItem> implements SyncLoader {
    private static final int MAX_INDENT_LEVEL = 19;
    
    private final Context context;
    private final MyAccount ma;
    private final long selectedMessageId;
    private boolean mAllowLoadingFromInternet = false;
    private final ReplyLevelComparator<T> replyLevelComparator = new ReplyLevelComparator<>();
    private final TFactory<T> tFactory;

    final List<T> mMsgs = new ArrayList<>();
    LoadableListActivity.ProgressPublisher mProgress;

    public List<T> getList() {
        return mMsgs;
    }

    final List<Long> idsOfTheMessagesToFind = new ArrayList<>();

    public ConversationLoader(Class<T> tClass, Context context, MyAccount ma, long selectedMessageId) {
        tFactory = new TFactory<>(tClass);
        this.context = context;
        this.ma = ma;
        this.selectedMessageId = selectedMessageId;
    }
    
    @Override
    public void load(ProgressPublisher publisher) {
        mProgress = publisher;
        idsOfTheMessagesToFind.clear();
        mMsgs.clear();
        findPreviousMessagesRecursively(newOMsg(selectedMessageId, 0));
        Collections.sort(mMsgs, replyLevelComparator);
        enumerateMessages();
        if (SharedPreferencesUtil.getBoolean(
                MyPreferences.KEY_OLD_MESSAGES_FIRST_IN_CONVERSATION, false)) {
            reverseListOrder();
        }
        Collections.sort(mMsgs);
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
                if (oMsg.mInReplyToMsgId != 0) {
                    findPreviousMessagesRecursively(newOMsg(oMsg.mInReplyToMsgId,
                            oMsg.mReplyLevel - 1));
                }
            }
        } else if (mAllowLoadingFromInternet) {
            loadFromInternet(oMsg.getMsgId());
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

    public void findRepliesRecursively(T oMsg) {
        MyLog.v(this, "findReplies for id=" + oMsg.getMsgId());
        List<Long> replies = MyQuery.getReplyIds(oMsg.getMsgId());
        oMsg.mNReplies = replies.size();
        for (long replyId : replies) {
            T oMsgReply = newOMsg(replyId, oMsg.mReplyLevel + 1);
            findPreviousMessagesRecursively(oMsgReply);
        }
    }
    
    private T newOMsg(long msgId, int replyLevel) {
        T oMsg = tFactory.newT();
        oMsg.setMsgId(msgId);
        oMsg.mReplyLevel = replyLevel;
        return oMsg;
    }
    
    private void loadMessageFromDatabase(ConversationItem oMsg) {
        Uri uri = MatchedUri.getTimelineItemUri(
                Timeline.getTimeline(TimelineType.EVERYTHING, ma, 0, null), oMsg.getMsgId());
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, oMsg.getProjection(), null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                oMsg.load(cursor);
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
    }

    private boolean addMessageToList(T oMsg) {
        boolean added = false;
        if (mMsgs.contains(oMsg)) {
            MyLog.v(this, "Message id=" + oMsg.getMsgId() + " is in the list already");
        } else {
            mMsgs.add(oMsg);
            if (mProgress != null) {
                mProgress.publish(Integer.toString(mMsgs.size()));
            }
            added = true;
        }
        return added;
    }

    private void loadFromInternet(long msgId) {
        MyLog.v(this, "Message id=" + msgId + " will be loaded from the Internet");
        MyServiceManager.sendForegroundCommand(
                CommandData.newItemCommand(CommandEnum.GET_STATUS, ma, msgId));
    }

    private static class ReplyLevelComparator<T extends ConversationItem> implements Comparator<T>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int compare(T lhs, T rhs) {
            int compared = rhs.mReplyLevel - lhs.mReplyLevel;
            if (compared == 0) {
                if (lhs.createdDate == rhs.createdDate) {
                    if ( lhs.getMsgId() == rhs.getMsgId()) {
                        compared = 0;
                    } else {
                        compared = (rhs.getMsgId() - lhs.getMsgId() > 0 ? 1 : -1);
                    }
                } else {
                    compared = (rhs.createdDate - lhs.createdDate > 0 ? 1 : -1);
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
        for (ConversationItem oMsg : mMsgs) {
            oMsg.mListOrder = 0;
            oMsg.mHistoryOrder = 0;
        }
        OrderCounters order = new OrderCounters();
        for (int ind = mMsgs.size()-1; ind >= 0; ind--) {
            ConversationItem oMsg = mMsgs.get(ind);
            if (oMsg.mListOrder < 0 ) {
                continue;
            }
            enumerateBranch(oMsg, order, 0);
        }
    }

    private void enumerateBranch(ConversationItem oMsg, OrderCounters order, int indent) {
        if (!addMessageIdToFind(oMsg.getMsgId())) {
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
        for (int ind = mMsgs.size() - 1; ind >= 0; ind--) {
           ConversationItem reply = mMsgs.get(ind);
           if (reply.mInReplyToMsgId == oMsg.getMsgId()) {
               reply.mNParentReplies = oMsg.mNReplies;
               enumerateBranch(reply, order, indentNext);
           }
        }
    }

    private void reverseListOrder() {
        for (ConversationItem oMsg : mMsgs) {
            oMsg.mListOrder = mMsgs.size() - oMsg.mListOrder - 1; 
        }
    }

    public void allowLoadingFromInternet() {
        this.mAllowLoadingFromInternet = true;
    }

    @Override
    public int size() {
        return mMsgs.size();
    }

}
