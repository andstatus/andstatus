/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.note

import android.content.Context
import android.database.Cursor
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.TimelineSql
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime

class ConversationViewItem : BaseNoteViewItem<ConversationViewItem>, Comparable<ConversationViewItem> {
    var inReplyToViewItem: ConversationViewItem? = null
    var activityType: ActivityType? = ActivityType.EMPTY
    var conversationId: Long = 0
    var reversedListOrder = false

    /** Numeration starts from 0  */
    var mListOrder = 0

    /**
     * This order is reverse to the [.mListOrder].
     * First note in the conversation has order == 1.
     * The number is visible to a User.
     */
    var historyOrder = 0
    var nReplies = 0
    var nParentReplies = 0
    var indentLevel = 0
    var replyLevel = 0

    protected constructor(isEmpty: Boolean, updatedDate: Long) : super(isEmpty, updatedDate) {}

    internal constructor(myContext: MyContext, cursor: Cursor) : super(myContext, cursor) {
        conversationId = DbUtils.getLong(cursor, NoteTable.CONVERSATION_ID)
        author = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.AUTHOR_ID))
        inReplyToNoteId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID)
        activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE))
        setOtherViewProperties(cursor)
    }

    fun newNonLoaded(myContext: MyContext, id: Long): ConversationViewItem {
        val item = ConversationViewItem(false, RelativeTime.DATETIME_MILLIS_NEVER)
        item.myContext = myContext
        item.setNoteId(id)
        return item
    }

    /**
     * The newest replies are first, "branches" look up
     */
    override fun compareTo(other: ConversationViewItem): Int {
        var compared = mListOrder - other.mListOrder
        if (compared == 0) {
            compared = if (updatedDate == other.updatedDate) {
                if (getNoteId() == other.getNoteId()) {
                    0
                } else {
                    if (other.getNoteId() - getNoteId() > 0) 1 else -1
                }
            } else {
                if (other.updatedDate - updatedDate > 0) 1 else -1
            }
        }
        if (reversedListOrder) compared = 0 - compared
        return compared
    }

    fun isLoaded(): Boolean {
        return updatedDate > 0
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ConversationViewItem) {
            return false
        }
        return getNoteId() == other.getNoteId()
    }

    override fun hashCode(): Int {
        return getNoteId().hashCode()
    }

    fun getProjection(): MutableSet<String> {
        return TimelineSql.getConversationProjection()
    }

    override fun getDetails(context: Context, showReceivedTime: Boolean): MyStringBuilder {
        val builder = super.getDetails(context, showReceivedTime)
        inReplyToViewItem?.let {
            builder.withSpace("(" + it.historyOrder + ")")
        }
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            builder.withSpace("(i$indentLevel,r$replyLevel)")
        }
        return builder
    }

    override fun fromCursor(myContext: MyContext, cursor: Cursor): ConversationViewItem {
        return ConversationViewItem(myContext, cursor)
    }

    fun isActorAConversationParticipant(): Boolean {
        return when (activityType) {
            ActivityType.CREATE, ActivityType.UPDATE -> true
            else -> false
        }
    }

    companion object {
        val EMPTY: ConversationViewItem = ConversationViewItem(true, RelativeTime.DATETIME_MILLIS_NEVER)
    }
}
