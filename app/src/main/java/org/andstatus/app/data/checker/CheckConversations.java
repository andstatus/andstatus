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

package org.andstatus.app.data.checker;

import android.database.Cursor;
import android.text.TextUtils;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class CheckConversations extends DataChecker {
    private Map<Long, MsgItem> items = new TreeMap<>();
    private Map<Long, List<MsgItem>> replies = new TreeMap<>();

    private class MsgItem {
        long id = 0;
        long originId = 0;
        long inReplyToId_initial = 0;
        long inReplyToId = 0;
        long conversationId_initial = 0;
        long conversationId = 0;
        String conversationOid = "";

        boolean fixConversationId(long conversationId) {
            final boolean different = this.conversationId != conversationId;
            if (different) {
                this.conversationId = conversationId;
            }
            return different;
        }

        boolean fixInReplyToId(int inReplyToId) {
            final boolean different = this.inReplyToId != inReplyToId;
            if (different) {
                this.inReplyToId = inReplyToId;
            }
            return different;
        }

        public boolean isChanged() {
            return isConversationIdChanged() || isInReplyToIdChanged();
        }

        boolean isConversationIdChanged() {
            return conversationId != conversationId_initial;
        }

        boolean isInReplyToIdChanged() {
            return inReplyToId != inReplyToId_initial;
        }

        @Override
        public String toString() {
            return "MsgItem{" +
                    "id=" + id +
                    ", originId=" + originId +
                    ", inReplyToId_initial=" + inReplyToId_initial +
                    ", inReplyToId=" + inReplyToId +
                    ", conversationId_initial=" + conversationId_initial +
                    ", conversationId=" + conversationId +
                    ", conversationOid='" + conversationOid + '\'' +
                    '}';
        }
    }

    @Override
    long fixInternal(boolean countOnly) {
        loadNotes();
        fixConversationsUsingReplies();
        fixConversationsUsingConversationOid();
        return saveChanges(countOnly);
    }

    private void loadNotes() {
        items.clear();
        replies.clear();
        String sql = "SELECT " + NoteTable._ID
                + ", " + NoteTable.ORIGIN_ID
                + ", " + NoteTable.IN_REPLY_TO_NOTE_ID
                + ", " + NoteTable.CONVERSATION_ID
                + ", " + NoteTable.CONVERSATION_OID
                + " FROM " + NoteTable.TABLE_NAME
                ;
        Cursor c = null;
        long rowsCount = 0;
        try {
            c = myContext.getDatabase().rawQuery(sql, null);
            while (c.moveToNext()) {
                rowsCount++;
                MsgItem item = new MsgItem();
                item.id = c.getLong(0);
                item.originId = c.getLong(1);
                item.inReplyToId = c.getLong(2);
                item.inReplyToId_initial = item.inReplyToId;
                item.conversationId = c.getLong(3);
                item.conversationId_initial = item.conversationId;
                item.conversationOid = c.getString(4);
                items.put(item.id, item);
                if (item.inReplyToId != 0) {
                    List<MsgItem> replies1 = replies.get(item.inReplyToId);
                    if (replies1 == null) {
                        replies1 = new ArrayList<>();
                        replies.put(item.inReplyToId, replies1);
                    }
                    replies1.add(item);
                }
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        logger.logProgress(Long.toString(rowsCount) + " notes loaded");
    }

    private void fixConversationsUsingReplies() {
        int counter = 0;
        for (MsgItem item : items.values()) {
            if (item.inReplyToId != 0) {
                MsgItem parent = items.get(item.inReplyToId);
                if (parent == null) {
                    item.fixInReplyToId(0);
                } else {
                    if (parent.conversationId == 0) {
                        parent.fixConversationId(item.conversationId == 0 ? parent.id : item.conversationId);
                    }
                    if (item.fixConversationId(parent.conversationId)) {
                        changeConversationOfReplies(item, 200);
                    }
                }
            }
            counter++;
            if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                logger.logProgress("Checked replies for " + counter + " notes of " + items.size());
            }
        }
    }

    private void fixConversationsUsingConversationOid() {
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
                        changeConversationOfReplies(item, 200);
                    }
                }
            }
            counter++;
            if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                logger.logProgress("Checked conversations for " + counter + " notes of " + items.size());
            }
        }
    }

    private void changeConversationOfReplies(MsgItem parent, int level) {
        List<MsgItem> replies1 = replies.get(parent.id);
        if (replies1 == null) {
            return;
        }
        for (MsgItem item : replies1) {
            if (item.fixConversationId(parent.conversationId)) {
                if (level > 0) {
                    changeConversationOfReplies(item, level - 1);
                } else {
                    logger.logProgress("Too long conversation, couldn't fix msgId=" + item.id);
                }
            }
        }
    }

    private int saveChanges(boolean countOnly) {
        int changedCount = 0;
        for (MsgItem item : items.values()) {
            if (item.isChanged()) {
                String sql = "";
                try {
                    if (changedCount < 5 && MyLog.isVerboseEnabled()) {
                        MyLog.v(this, "msgId=" + item.id + "; "
                            + (item.isInReplyToIdChanged() ? "inReplyToId changed from "
                                + item.inReplyToId_initial + " to " + item.inReplyToId : "")
                            + (item.isInReplyToIdChanged() && item.isConversationIdChanged() ? " and " : "")
                            + (item.isConversationIdChanged() ? "conversationId changed from "
                                + item.conversationId_initial + " to " + item.conversationId : "")
                            + ", Body:'" + MyQuery.msgIdToStringColumnValue(NoteTable.BODY, item.id) + "'");
                    }
                    if (!countOnly) {
                        sql = "UPDATE " + NoteTable.TABLE_NAME
                                + " SET "
                                + (item.isInReplyToIdChanged() ?
                                    NoteTable.IN_REPLY_TO_NOTE_ID + "=" + DbUtils.sqlZeroToNull(item.inReplyToId) : "")
                                + (item.isInReplyToIdChanged() && item.isConversationIdChanged() ? ", " : "")
                                + (item.isConversationIdChanged() ?
                                    NoteTable.CONVERSATION_ID + "=" + DbUtils.sqlZeroToNull(item.conversationId) : "")
                                + " WHERE " + NoteTable._ID + "=" + item.id;
                        myContext.getDatabase().execSQL(sql);
                    }
                    changedCount++;
                    if (logger.loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS)) {
                        logger.logProgress("Saved changes for " + changedCount + " notes");
                        MyServiceManager.setServiceUnavailable();
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