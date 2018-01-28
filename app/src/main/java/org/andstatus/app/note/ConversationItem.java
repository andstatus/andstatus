/**
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

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.util.I18n;

public abstract class ConversationItem<T extends ConversationItem<T>> extends BaseNoteViewItem<T> implements Comparable<ConversationItem> {
    ConversationItem inReplyToViewItem = null;

    boolean reversedListOrder = false;
    /** Numeration starts from 0 **/
    int mListOrder = 0;
    /**
     * This order is reverse to the {@link #mListOrder}. 
     * First note in the conversation has order == 1.
     * The number is visible to a User.
     */
    int historyOrder = 0;
    int mNReplies = 0;
    int mNParentReplies = 0;
    int indentLevel = 0;
    int replyLevel = 0;

    protected ConversationItem(boolean isEmpty) {
        super(isEmpty);
    }

    public void setReversedListOrder(boolean reversedListOrder) {
        this.reversedListOrder = reversedListOrder;
    }

    /**
     * The newest replies are first, "branches" look up
     */
    @Override
    public int compareTo(ConversationItem another) {
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
        if (o == null || !(o instanceof ConversationItem)) {
            return false;
        }
        final ConversationItem other = (ConversationItem) o;
        return getNoteId() == other.getNoteId();
    }

    @Override
    public final int hashCode() {
        return Long.valueOf(getNoteId()).hashCode();
    }

    abstract String[] getProjection();
    
    void load(Cursor cursor) {
        inReplyToNoteId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
        updatedDate = DbUtils.getLong(cursor, NoteTable.UPDATED_DATE);
    }

    @Override
    public StringBuilder getDetails(Context context) {
        final StringBuilder builder = super.getDetails(context);
        if (inReplyToViewItem != null) {
            I18n.appendWithSpace(builder, "(" + inReplyToViewItem.historyOrder
                    + ")");
        }
        return builder;
    }
}
