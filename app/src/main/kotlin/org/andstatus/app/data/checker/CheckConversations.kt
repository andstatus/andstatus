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
package org.andstatus.app.data.checker

import android.database.Cursor
import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.data.MyQuery
import org.andstatus.app.data.SqlIds
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils.isRealOid
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author yvolk@yurivolkov.com
 */
class CheckConversations : DataChecker() {
    private val items: MutableMap<Long, NoteItem> = TreeMap()
    private val replies: MutableMap<Long, MutableList<NoteItem>> = TreeMap()
    private val noteIdsOfOneConversation: MutableSet<Long> = HashSet()

    private inner class NoteItem {
        var id: Long = 0
        var originId: Long = 0
        var inReplyToIdInitial: Long = 0
        var inReplyToId: Long = 0
        var conversationIdInitial: Long = 0
        var conversationId: Long = 0
        var conversationOid: String = ""
        fun fixConversationId(conversationId: Long): Boolean {
            val different = this.conversationId != conversationId
            if (different) {
                this.conversationId = conversationId
            }
            return different
        }

        fun fixInReplyToId(inReplyToId: Int): Boolean {
            val different = this.inReplyToId != inReplyToId.toLong()
            if (different) {
                this.inReplyToId = inReplyToId.toLong()
            }
            return different
        }

        fun isChanged(): Boolean {
            return isConversationIdChanged() || isInReplyToIdChanged()
        }

        fun isConversationIdChanged(): Boolean {
            return conversationId != conversationIdInitial
        }

        fun isInReplyToIdChanged(): Boolean {
            return inReplyToId != inReplyToIdInitial
        }

        override fun toString(): String {
            return "NoteItem{" +
                "id=" + id +
                ", originId=" + originId +
                if (inReplyToIdInitial == inReplyToId) {
                    ", inReplyToId=" + inReplyToId
                } else {
                    ", inReplyToId changed from " + inReplyToIdInitial +
                        " to " + inReplyToId
                } +
                if (conversationIdInitial == conversationId) {
                    ", conversationId=" + conversationId
                } else {
                    ", conversationId changed from " + conversationIdInitial +
                        " to " + conversationId
                } +
                ", conversationOid='" + conversationOid + '\'' +
                '}'
        }
    }

    fun setNoteIdsOfOneConversation(ids: MutableSet<Long>): CheckConversations {
        noteIdsOfOneConversation.addAll(ids)
        return this
    }

    override fun fixInternal(): Long {
        loadNotes()
        if (noteIdsOfOneConversation.isEmpty()) {
            fixConversationsUsingReplies()
            fixConversationsUsingConversationOid()
        } else {
            fixOneConversation()
        }
        return saveChanges(countOnly).toLong()
    }

    private fun loadNotes() {
        items.clear()
        replies.clear()
        var sql = ("SELECT " + BaseColumns._ID
            + ", " + NoteTable.ORIGIN_ID
            + ", " + NoteTable.IN_REPLY_TO_NOTE_ID
            + ", " + NoteTable.CONVERSATION_ID
            + ", " + NoteTable.CONVERSATION_OID
            + " FROM " + NoteTable.TABLE_NAME)
        if (noteIdsOfOneConversation.size > 0) {
            sql += (" WHERE " + NoteTable.CONVERSATION_ID + " IN ("
                + "SELECT DISTINCT " + NoteTable.CONVERSATION_ID
                + " FROM " + NoteTable.TABLE_NAME + " WHERE "
                + BaseColumns._ID + SqlIds.fromIds(noteIdsOfOneConversation).getSql()
                + ")")
        }
        var cursor: Cursor? = null
        var rowsCount: Long = 0
        try {
            cursor = myContext.database?.rawQuery(sql, null)
            while (cursor?.moveToNext() == true) {
                rowsCount++
                val item = NoteItem()
                item.id = DbUtils.getLong(cursor, BaseColumns._ID)
                item.originId = DbUtils.getLong(cursor, NoteTable.ORIGIN_ID)
                item.inReplyToId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID)
                item.inReplyToIdInitial = item.inReplyToId
                item.conversationId = DbUtils.getLong(cursor, NoteTable.CONVERSATION_ID)
                item.conversationIdInitial = item.conversationId
                item.conversationOid = DbUtils.getString(cursor, NoteTable.CONVERSATION_OID)
                items[item.id] = item
                if (item.inReplyToId != 0L) {
                    replies.computeIfAbsent(item.inReplyToId) { ArrayList() }.add(item)
                }
            }
        } finally {
            closeSilently(cursor)
        }
        logger.logProgress(rowsCount.toString() + " notes loaded")
    }

    private fun fixConversationsUsingReplies() {
        val counter = AtomicInteger()
        for (item in items.values) {
            if (item.inReplyToId != 0L) {
                val parent = items[item.inReplyToId]
                if (parent == null || parent.originId != item.originId) {
                    item.fixInReplyToId(0)
                } else {
                    if (parent.conversationId == 0L) {
                        parent.fixConversationId(if (item.conversationId == 0L) parent.id else item.conversationId)
                    }
                    if (item.fixConversationId(parent.conversationId)) {
                        changeConversationOfReplies(item, 200)
                    }
                }
            }
            counter.incrementAndGet()
            logger.logProgressIfLongProcess { "Checked replies for " + counter.get() + " notes of " + items.size }
        }
    }

    private fun fixConversationsUsingConversationOid() {
        val counter = AtomicInteger()
        val originToConversations: MutableMap<Long, MutableMap<String, NoteItem>> = ConcurrentHashMap()
        for (item in items.values) {
            if (item.conversationOid.isRealOid) {
                val firstConversationMembers: MutableMap<String, NoteItem> = originToConversations
                    .computeIfAbsent(item.originId) { ConcurrentHashMap() }
                val parent = firstConversationMembers[item.conversationOid]
                if (parent == null) {
                    item.fixConversationId(if (item.conversationId == 0L) item.id else item.conversationId)
                    firstConversationMembers[item.conversationOid] = item
                } else {
                    if (item.fixConversationId(parent.conversationId)) {
                        changeConversationOfReplies(item, 200)
                    }
                }
            }
            counter.incrementAndGet()
            logger.logProgressIfLongProcess { "Checked conversations for " + counter + " notes of " + items.size }
        }
    }

    private fun changeConversationOfReplies(parent: NoteItem, level: Int) {
        val replies1 = replies[parent.id] ?: return
        for (item in replies1) {
            if (item.originId != parent.originId) {
                item.fixInReplyToId(0)
            } else if (item.fixConversationId(parent.conversationId)) {
                if (level > 0) {
                    changeConversationOfReplies(item, level - 1)
                } else {
                    logger.logProgress("Too long conversation, couldn't fix noteId=" + item.id)
                }
            }
        }
    }

    private fun fixOneConversation() {
        val newConversationId = items.values.stream().map { noteItem: NoteItem -> noteItem.conversationId }
            .min { obj: Long, anotherLong: Long -> obj.compareTo(anotherLong) }.orElse(0L)
        check(newConversationId != 0L) { "Conversation ID=0, $noteIdsOfOneConversation" }
        for (item in items.values) {
            item.conversationId = newConversationId
        }
    }

    private fun saveChanges(countOnly: Boolean): Int {
        val counter = AtomicInteger()
        for (item in items.values) {
            if (item.isChanged()) {
                var sql = ""
                try {
//                    if (counter.get() < 5 && MyLog.isVerboseEnabled()) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(this, "$item" +
                                ", content:'" + MyQuery.noteIdToStringColumnValue(NoteTable.CONTENT, item.id) + "'"
                        )
                    }
                    if (!countOnly) {
                        sql = ("UPDATE " + NoteTable.TABLE_NAME
                            + " SET "
                            + (if (item.isInReplyToIdChanged()) NoteTable.IN_REPLY_TO_NOTE_ID + "=" + DbUtils.sqlZeroToNull(
                            item.inReplyToId
                        ) else "")
                            + (if (item.isInReplyToIdChanged() && item.isConversationIdChanged()) ", " else "")
                            + (if (item.isConversationIdChanged()) NoteTable.CONVERSATION_ID + "=" + DbUtils.sqlZeroToNull(
                            item.conversationId
                        ) else "")
                            + " WHERE " + BaseColumns._ID + "=" + item.id)
                        myContext.database?.execSQL(sql)
                    }
                    counter.incrementAndGet()
                    logger.logProgressIfLongProcess { "Saved changes for $counter notes" }
                } catch (e: Exception) {
                    val logMsg = "Error: " + e.message + ", SQL:" + sql
                    logger.logProgress(logMsg)
                    MyLog.e(this, logMsg, e)
                }
            }
        }
        return counter.get()
    }
}
