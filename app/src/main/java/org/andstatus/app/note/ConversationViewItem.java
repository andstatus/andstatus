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

package org.andstatus.app.note;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;

import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.util.MyStringBuilder;

import java.util.Set;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class ConversationViewItem extends BaseNoteViewItem<ConversationViewItem> implements Comparable<ConversationViewItem> {
    public static final ConversationViewItem EMPTY = new ConversationViewItem(true, DATETIME_MILLIS_NEVER);

    ConversationViewItem inReplyToViewItem = null;

    ActivityType activityType = ActivityType.EMPTY;
    long conversationId = 0;
    boolean reversedListOrder = false;
    /** Numeration starts from 0 **/
    int mListOrder = 0;
    /**
     * This order is reverse to the {@link #mListOrder}. 
     * First note in the conversation has order == 1.
     * The number is visible to a User.
     */
    int historyOrder = 0;
    int nReplies = 0;
    int nParentReplies = 0;
    int indentLevel = 0;
    int replyLevel = 0;

    protected ConversationViewItem(boolean isEmpty, long updatedDate) {
        super(isEmpty, updatedDate);
    }

    ConversationViewItem(MyContext myContext, Cursor cursor) {
        super(myContext, cursor);
        conversationId = DbUtils.getLong(cursor, NoteTable.CONVERSATION_ID);
        author = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.AUTHOR_ID));
        inReplyToNoteId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
        activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
        setOtherViewProperties(cursor);
    }

    protected ConversationViewItem newNonLoaded(MyContext myContext, long id) {
        ConversationViewItem item = new ConversationViewItem(false, DATETIME_MILLIS_NEVER);
        item.setMyContext(myContext);
        item.setNoteId(id);
        return item;
    }

    public void setReversedListOrder(boolean reversedListOrder) {
        this.reversedListOrder = reversedListOrder;
    }

    /**
     * The newest replies are first, "branches" look up
     */
    @Override
    public int compareTo(ConversationViewItem another) {
        int compared = mListOrder - another.mListOrder;
        if (compared == 0) {
            if (updatedDate == another.updatedDate) {
                if ( getNoteId() == another.getNoteId()) {
                    compared = 0;
                } else {
                    compared = (another.getNoteId() - getNoteId() > 0 ? 1 : -1);
                }
            } else {
                compared = (another.updatedDate - updatedDate > 0 ? 1 : -1);
            }
        }
        if (reversedListOrder) compared = 0 - compared;
        return compared;
    }

    public boolean isLoaded() {
        return updatedDate > 0;
    }

    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConversationViewItem)) {
            return false;
        }
        final ConversationViewItem other = (ConversationViewItem) o;
        return getNoteId() == other.getNoteId();
    }

    @Override
    public final int hashCode() {
        return Long.valueOf(getNoteId()).hashCode();
    }

    Set<String> getProjection() {
        return TimelineSql.getConversationProjection();
    }

    @Override
    public MyStringBuilder getDetails(Context context, boolean showReceivedTime) {
        final MyStringBuilder builder = super.getDetails(context, showReceivedTime);
        if (inReplyToViewItem != null) {
            builder.withSpace("(" + inReplyToViewItem.historyOrder + ")");
        }
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            builder.withSpace("(i" + indentLevel + ",r" + replyLevel + ")");
        }
        return builder;
    }

    @NonNull
    @Override
    public ConversationViewItem fromCursor(MyContext myContext, Cursor cursor) {
        return new ConversationViewItem(myContext, cursor);
    }

    boolean isActorAConversationParticipant() {
        switch (activityType) {
            case CREATE:
            case UPDATE:
                return true;
            default:
                return false;
        }
    }
}
