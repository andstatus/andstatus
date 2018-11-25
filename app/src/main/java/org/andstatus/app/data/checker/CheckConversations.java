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
import android.support.annotation.NonNull;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlIds;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class CheckConversations extends DataChecker {
    private Map<Long, NoteItem> items = new TreeMap<>();
    private Map<Long, List<NoteItem>> replies = new TreeMap<>();
    private Set<Long> noteIdsOfOneConversation = new HashSet<>();

    private class NoteItem {
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

    public CheckConversations setNoteIdsOfOneConversation(@NonNull Set<Long> ids) {
        noteIdsOfOneConversation.addAll(ids);
        return  this;
    }

    @Override
    long fixInternal(boolean countOnly) {
        loadNotes();
        if (noteIdsOfOneConversation.isEmpty()) {
            fixConversationsUsingReplies();
            fixConversationsUsingConversationOid();
        } else {
            fixOneConversation();
        }
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
        if (noteIdsOfOneConversation.size() > 0) {
            sql += " WHERE " + NoteTable.CONVERSATION_ID + " IN ("
                    + "SELECT DISTINCT " + NoteTable.CONVERSATION_ID
                    + " FROM " + NoteTable.TABLE_NAME + " WHERE "
                    + NoteTable._ID + SqlIds.fromIds(noteIdsOfOneConversation).getSql()
            + ")";
        }

        Cursor cursor = null;
        long rowsCount = 0;
        try {
            cursor = myContext.getDatabase().rawQuery(sql, null);
            while (cursor.moveToNext()) {
                rowsCount++;
                NoteItem item = new NoteItem();
                item.id = DbUtils.getLong(cursor, NoteTable._ID);
                item.originId = DbUtils.getLong(cursor, NoteTable.ORIGIN_ID);
                item.inReplyToId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
                item.inReplyToId_initial = item.inReplyToId;
                item.conversationId = DbUtils.getLong(cursor, NoteTable.CONVERSATION_ID);
                item.conversationId_initial = item.conversationId;
                item.conversationOid = DbUtils.getString(cursor, NoteTable.CONVERSATION_OID);
                items.put(item.id, item);
                if (item.inReplyToId != 0) {
                    replies.computeIfAbsent(item.inReplyToId, k -> new ArrayList<>()).add(item);
                }
            }
        } finally {
            DbUtils.closeSilently(cursor);
        }
        logger.logProgress(Long.toString(rowsCount) + " notes loaded");
    }

    private void fixConversationsUsingReplies() {
        int counter = 0;
        for (NoteItem item : items.values()) {
            if (item.inReplyToId != 0) {
                NoteItem parent = items.get(item.inReplyToId);
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
        Map<Long, Map<String, NoteItem>> origins = new ConcurrentHashMap<>();
        for (NoteItem item : items.values()) {
            if (!StringUtils.isEmpty(item.conversationOid)) {
                Map<String, NoteItem> firstConversationMembers = origins.get(item.originId);
                if (firstConversationMembers == null) {
                    firstConversationMembers = new ConcurrentHashMap<>();
                    origins.put(item.originId, firstConversationMembers);
                }
                NoteItem parent = firstConversationMembers.get(item.conversationOid);
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

    private void changeConversationOfReplies(NoteItem parent, int level) {
        List<NoteItem> replies1 = replies.get(parent.id);
        if (replies1 == null) {
            return;
        }
        for (NoteItem item : replies1) {
            if (item.fixConversationId(parent.conversationId)) {
                if (level > 0) {
                    changeConversationOfReplies(item, level - 1);
                } else {
                    logger.logProgress("Too long conversation, couldn't fix noteId=" + item.id);
                }
            }
        }
    }

    private void fixOneConversation() {
        long newConversationId = items.values().stream().map(noteItem -> noteItem.conversationId)
                .min(Long::compareTo).orElse(0L);
        if (newConversationId == 0) throw new IllegalStateException("Conversation ID=0, " + noteIdsOfOneConversation);
        for (NoteItem item : items.values()) {
            item.conversationId = newConversationId;
        }
    }

    private int saveChanges(boolean countOnly) {
        int changedCount = 0;
        for (NoteItem item : items.values()) {
            if (item.isChanged()) {
                String sql = "";
                try {
                    if (changedCount < 5 && MyLog.isVerboseEnabled()) {
                        MyLog.v(this, "noteId=" + item.id + "; "
                            + (item.isInReplyToIdChanged() ? "inReplyToId changed from "
                                + item.inReplyToId_initial + " to " + item.inReplyToId : "")
                            + (item.isInReplyToIdChanged() && item.isConversationIdChanged() ? " and " : "")
                            + (item.isConversationIdChanged() ? "conversationId changed from "
                                + item.conversationId_initial + " to " + item.conversationId : "")
                            + ", Content:'" + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, item.id) + "'");
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