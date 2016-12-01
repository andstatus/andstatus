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
import org.andstatus.app.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyDataCheckerConversations {
    private final MyContext myContext;
    private final ProgressLogger logger;

    private class MsgItem {
        long id = 0;
        long inReplyToId = 0;
        long conversationId = 0;
        String conversationOid = "";

        public boolean changed = false;

        public boolean setConversationId(long conversationId) {
            final boolean different = this.conversationId != conversationId;
            if (different) {
                this.conversationId = conversationId;
                changed = true;
            }
            return different;
        }

        public boolean setConversationOid(String conversationOid) {
            boolean different = !StringUtils.equalsNotEmpty(this.conversationOid, conversationOid);
            if (different) {
                this.conversationOid = conversationOid;
                changed = true;
            }
            return  different;
        }

        public boolean setInReplyToId(int inReplyToId) {
            final boolean different = this.inReplyToId != inReplyToId;
            if (different) {
                this.inReplyToId = inReplyToId;
                changed = true;
            }
            return different;
        }
    }

    public MyDataCheckerConversations(MyContext myContext, ProgressLogger logger) {
        this.myContext = myContext;
        this.logger = logger;
    }

    public void fixData() {
        final String method = "checkConversations";
        logger.logProgress(method + " started");

        Map<Long, MsgItem> items = loadMessages();
        fixConversations(items);
        int changedCount = saveChanges(items);
        logger.logProgress(method + " ended, " + (changedCount > 0 ?  "changed " + changedCount + " messages" : " no changes were required"));
    }

    private Map<Long, MsgItem> loadMessages() {
        Map<Long, MsgItem> messages = new ConcurrentHashMap<>();
        String sql = "SELECT " + MsgTable._ID
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
                message.inReplyToId = c.getLong(1);
                message.conversationId = c.getLong(2);
                message.conversationOid = c.getString(3);
                messages.put(message.id, message);
            }
        } finally {
            DbUtils.closeSilently(c);
        }
        logger.logProgress(Long.toString(rowsCount) + " messages loaded");
        return messages;
    }

    public void fixConversations(Map<Long, MsgItem> items) {
        for (MsgItem item : items.values()) {
            if (item.inReplyToId != 0) {
                MsgItem parent = items.get(item.inReplyToId);
                if (parent == null) {
                    item.setInReplyToId(0);
                } else {
                    if (parent.conversationId == 0) {
                        parent.setConversationId(item.conversationId == 0 ? parent.id : item.conversationId);
                    }
                    if (TextUtils.isEmpty(parent.conversationOid)) {
                        parent.setConversationOid(item.conversationOid);
                    }
                    if (item.setConversationId(parent.conversationId) | item.setConversationOid(parent.conversationOid)) {
                        changeConversationOfChildren(items, item, 1000);
                    }
                }
            }
        }
    }

    private void changeConversationOfChildren(Map<Long, MsgItem> items, MsgItem parent, int level) {
        for (MsgItem item : items.values()) {
            if (item.inReplyToId == parent.id) {
                if (item.setConversationId(parent.conversationId) | item.setConversationOid(parent.conversationOid)) {
                    if (level > 0) {
                        changeConversationOfChildren(items, item, level - 1);
                    } else {
                        logger.logProgress("Too long conversation, couldn't fix msgId=" + item.id);
                    }
                }
            }
        }
    }

    private int saveChanges(Map<Long, MsgItem> items) {
        int changedCount = 0;
        for (MsgItem item : items.values()) {
            if (item.changed) {
                String sql = "";
                try {
                    sql = "UPDATE " + MsgTable.TABLE_NAME
                            + " SET "
                            + MsgTable.IN_REPLY_TO_MSG_ID + "=" + DbUtils.sqlZeroToNull(item.inReplyToId)
                            + ", " + MsgTable.CONVERSATION_ID + "=" + DbUtils.sqlZeroToNull(item.conversationId)
                            + ", " + MsgTable.CONVERSATION_OID + "=" + DbUtils.sqlEmptyToNull(item.conversationOid)
                            + " WHERE "
                            + MsgTable._ID + "=" + item.id;
                    myContext.getDatabase().execSQL(sql);
                    changedCount++;
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
