/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.util.RelativeTime

/**
 * @author yvolk@yurivolkov.com
 */
class NoteViewItem : BaseNoteViewItem<NoteViewItem?> {
    constructor(isEmpty: Boolean, updatedDate: Long) : super(isEmpty, updatedDate) {}

    override fun fromCursor(myContext: MyContext?, cursor: Cursor?): NoteViewItem {
        return NoteViewItem(myContext, cursor)
    }

    private constructor(myContext: MyContext?, cursor: Cursor?) : super(myContext, cursor) {
        setLinkedAccount(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID))
        contentToSearch = DbUtils.getString(cursor, NoteTable.CONTENT_TO_SEARCH)
        insertedDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE)
        activityUpdatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE)
        author = ActorViewItem.Companion.fromActorId(origin, DbUtils.getLong(cursor, NoteTable.AUTHOR_ID))
        setOtherViewProperties(cursor)
    }

    companion object {
        val EMPTY: NoteViewItem? = NoteViewItem(true, RelativeTime.DATETIME_MILLIS_NEVER)
    }
}