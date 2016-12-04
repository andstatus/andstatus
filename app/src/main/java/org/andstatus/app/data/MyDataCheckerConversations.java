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

package org.andstatus.app.data;

import android.database.Cursor;
import android.text.TextUtils;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.MsgTable;
import org.andstatus.app.util.MyLog;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyDataCheckerConversations {
    private static final int PROGRESS_REPORT_PERIOD_SECONDS = 20;
    private final MyContext myContext;
    private final ProgressLogger logger;

    private class MsgItem {
        long id = 0;
        long originId = 0;
        long inReplyToId = 0;
        long conversationId = 0;
        String conversationOid = "";

        public boolean conversationIdChanged = false;
        public boolean inReplyToIdChanged = false;

        public boolean fixConversationId(long conversationId) {
            final boolean different = this.conversationId != conversationId;
            if (different) {
                this.conversationId = conversationId;
                conversationIdChanged = true;
            }
            return different;
        }

        public boolean setInReplyToId(int inReplyToId) {
            final boolean different = this.inReplyToId != inReplyToId;
            if (different) {
                this.inReplyToId = inReplyToId;
                inReplyToIdChanged = true;
            }
            return different;
        }

        public boolean isChanged() {
            return conversationIdChanged || inReplyToIdChanged;
        }
    }

    public MyDataCheckerConversations(MyContext myContext, ProgressLogger logger) {
        this.myContext = myContext;
        this.logger = logger;
    }

    public void fixData() {
        fixInternal(false);
    }

    public int countChanges() {
        return fixInternal(true);
    }

    public int fixInternal(boolean countOnly) {
        final String method = "checkConversations";
        logger.logProgress(method + " started");
        Map<Long, MsgItem> items = loadMessages();
        fixConversationsUsingReplies(items);
        fixConversationsUsingConversationOid(items);
        int changedCount = saveChanges(items, countOnly);
        logger.logProgress(method + " ended, " + (changedCount > 0 ?  "changed " + changedCount + " messages" : " no changes were required"));
        return changedCount;
    }

    private Map<Long, MsgItem> loadMessages() {
        Map<Long, MsgItem> messages = new TreeMap<>();
        String sql = "SELECT " + MsgTable._ID
                + ", " + MsgTable.ORIGIN_ID
                + ", " + MsgTable.IN_REPLY_TO_MSG_ID
                + ", " + MsgTable.CONVERSATION_ID
                + ", " + MsgTable.CONVERSATION_OID
                + " FROM " + MsgTable.TABLE_NAME
                ;
        Cursor c = null;
        long rowsCount = 0;
        try {
            c = myContext.getDatabase().rawQuery(sql, null);
            while (c.moveToNext()) {
                rowsCount++;
                MsgItem message = new MsgItem();
                message.id = c.getLong(0);
                message.originId = c.getLong(1);
                message.inReplyToId = c.getLong(2);
                message.conversationId = c.getLong(3);
                message.conversationOid = c.getString(4);
                messages.put(message.id, message);
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        logger.logProgress(Long.toString(rowsCount) + " messages loaded");
        return messages;
    }

    private void fixConversationsUsingReplies(Map<Long, MsgItem> items) {
        int counter = 0;
        for (MsgItem item : items.values()) {
            if (item.inReplyToId != 0) {
                MsgItem parent = items.get(item.inReplyToId);
                if (parent == null) {
                    item.setInReplyToId(0);
                } else {
                    if (parent.conversationId == 0) {
                        parent.fixConversationId(item.conversationId == 0 ? parent.id : item.conversationId);
                    }
                    if (item.fixConversationId(parent.conversationId)) {
                        changeConversationOfReplies(items, item, 1000);
                    }
                }
            }
            counter++;
            if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                logger.logProgress("Checked replies for " + counter + " messages of " + items.size());
            }
        }
    }

    private void fixConversationsUsingConversationOid(Map<Long, MsgItem> items) {
        int counter = 0;
        Map<Long, Map<String, MsgItem>> origins = new ConcurrentHashMap<>();
        for (MsgItem item : items.values()) {
            if (!TextUtils.isEmpty(item.conversationOid)) {
                Map<String, MsgItem> firstConversationMembers = origins.get(item.originId);
                if (firstConversationMembers == null) {
                    firstConversationMembers = new ConcurrentHashMap<>();
                    origins.put(item.originId, firstConversationMembers);
                }
                MsgItem parent = firstConversationMembers.get(item.conversationOid);
                if (parent == null) {
                    item.fixConversationId(item.conversationId == 0 ? item.id : item.conversationId);
                    firstConversationMembers.put(item.conversationOid, item);
                } else {
                    if (item.fixConversationId(parent.conversationId)) {
                        changeConversationOfReplies(items, item, 200);
                    }
                }
            }
            counter++;
            if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                logger.logProgress("Checked conversations for " + counter + " messages of " + items.size());
            }
        }
    }

    private void changeConversationOfReplies(Map<Long, MsgItem> items, MsgItem parent, int level) {
        for (MsgItem item : items.values()) {
            if (item.inReplyToId == parent.id) {
                if (item.fixConversationId(parent.conversationId)) {
                    if (level > 0) {
                        changeConversationOfReplies(items, item, level - 1);
                    } else {
                        logger.logProgress("Too long conversation, couldn't fix msgId=" + item.id);
                    }
                }
            }
        }
    }

    private int saveChanges(Map<Long, MsgItem> items, boolean countOnly) {
        int changedCount = 0;
        for (MsgItem item : items.values()) {
            if (item.isChanged()) {
                String sql = "";
                try {
                    if (!countOnly) {
                        sql = "UPDATE " + MsgTable.TABLE_NAME
                                + " SET "
                                + (item.inReplyToIdChanged ?
                                    MsgTable.IN_REPLY_TO_MSG_ID + "=" + DbUtils.sqlZeroToNull(item.inReplyToId) : "")
                                + (item.inReplyToIdChanged && item.conversationIdChanged ? ", " : "")
                                + (item.conversationIdChanged ?
                                    MsgTable.CONVERSATION_ID + "=" + DbUtils.sqlZeroToNull(item.conversationId) : "")
                                + " WHERE " + MsgTable._ID + "=" + item.id;
                        myContext.getDatabase().execSQL(sql);
                    }
                    changedCount++;
                    if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                        logger.logProgress("Saved changes for " + changedCount + " messages");
                    }
                } catch (Exception e) {
                    String logMsg = "Error: " + e.getMessage() + ", SQL:" + sql;
                    logger.logProgress(logMsg);
                    MyLog.e(this, logMsg, e);
                }
            }
        }
        return changedCount;
    }

}
