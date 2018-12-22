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

import android.database.Cursor;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.ActivityType;

import java.util.HashSet;
import java.util.Set;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class ConversationMemberItem extends ConversationItem<ConversationMemberItem> {
    public final static ConversationMemberItem EMPTY = new ConversationMemberItem(true, DATETIME_MILLIS_NEVER);

    ActivityType activityType = ActivityType.EMPTY;


    protected ConversationMemberItem(boolean isEmpty, long updatedDate) {
        super(isEmpty, updatedDate);
    }

    public ConversationMemberItem(MyContext myContext, Cursor cursor) {
        super(myContext, cursor);
        activityType = ActivityType.fromId(DbUtils.getLong(cursor, ActivityTable.ACTIVITY_TYPE));
    }

    @Override
    protected ConversationMemberItem newNonLoaded(MyContext myContext, long id) {
        ConversationMemberItem item = new ConversationMemberItem(false, DATETIME_MILLIS_NEVER);
        item.setMyContext(myContext);
        item.setNoteId(id);
        return item;
    }

    @Override
    Set<String> getProjection() {
        Set<String> columnNames = new HashSet<>();
        columnNames.add(ActivityTable.NOTE_ID);
        columnNames.add(ActivityTable.ACTIVITY_TYPE);
        columnNames.add(NoteTable.CONVERSATION_ID);
        columnNames.add(NoteTable.UPDATED_DATE);
        columnNames.add(NoteTable.IN_REPLY_TO_NOTE_ID);
        columnNames.add(ActivityTable.ORIGIN_ID);
        columnNames.add(NoteTable.AUTHOR_ID);
        return columnNames;
    }

    @NonNull
    @Override
    public ConversationMemberItem fromCursor(MyContext myContext, Cursor cursor) {
        return new ConversationMemberItem(myContext, cursor);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ConversationMemberItem [author=");
        builder.append(author);
        builder.append(", ind=");
        builder.append(mListOrder);
        builder.append("]");
        return builder.toString();
    }
}
