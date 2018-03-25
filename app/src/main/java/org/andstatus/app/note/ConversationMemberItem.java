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

import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.util.MyHtml;

import java.util.ArrayList;
import java.util.List;

public class ConversationMemberItem extends ConversationItem<ConversationMemberItem> {
    public final static ConversationMemberItem EMPTY = new ConversationMemberItem(true);

    protected ConversationMemberItem(boolean isEmpty) {
        super(isEmpty);
    }

    @NonNull
    @Override
    public ConversationMemberItem getNew() {
        return new ConversationMemberItem(false);
    }

    @Override
    String[] getProjection() {
        List<String> columnNames = new ArrayList<>();
        columnNames.add(ActivityTable.NOTE_ID);
        columnNames.add(NoteTable.UPDATED_DATE);
        columnNames.add(NoteTable.IN_REPLY_TO_NOTE_ID);
        columnNames.add(NoteTable.AUTHOR_ID);
        columnNames.add(NoteTable.NAME);
        columnNames.add(NoteTable.CONTENT);
        return columnNames.toArray(new String[]{});
    }

    @Override
    void load(Cursor cursor) {
        super.load(cursor);
        author = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.AUTHOR_ID));
        setName(MyHtml.prepareForView(DbUtils.getString(cursor, NoteTable.NAME)));
        setContent(MyHtml.prepareForView(DbUtils.getString(cursor, NoteTable.CONTENT)));
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
