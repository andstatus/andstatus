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
package org.andstatus.app.note;

import android.database.Cursor;
import androidx.annotation.NonNull;
import android.text.Html;

import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

/**
 * @author yvolk@yurivolkov.com
 */
public class NoteViewItem extends BaseNoteViewItem<NoteViewItem> {
    public final static NoteViewItem EMPTY = new NoteViewItem(true, DATETIME_MILLIS_NEVER);

    protected NoteViewItem(boolean isEmpty, long updatedDate) {
        super(isEmpty, updatedDate);
    }

    @Override
    @NonNull
    public NoteViewItem fromCursor(MyContext myContext, Cursor cursor) {
        return new NoteViewItem(myContext, cursor);
    }

    private NoteViewItem (MyContext myContext, Cursor cursor) {
        super(myContext, cursor);
        setLinkedAccount(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID));
        contentToSearch = DbUtils.getString(cursor, NoteTable.CONTENT_TO_SEARCH);
        insertedDate = DbUtils.getLong(cursor, ActivityTable.INS_DATE);
        activityUpdatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        author = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.AUTHOR_ID));
        setOtherViewProperties(cursor);
    }

    @Override
    public String toString() {
        return MyStringBuilder.formatKeyValue(this, I18n.trimTextAt(getContent().toString(), 40) + ","
                + getDetails(getMyContext().context(), false));
    }
}
