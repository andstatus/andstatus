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
import android.support.annotation.NonNull;
import android.text.Html;

import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;

/**
 * @author yvolk@yurivolkov.com
 */
public class NoteViewItem extends BaseNoteViewItem<NoteViewItem> {
    public final static NoteViewItem EMPTY = new NoteViewItem(true);

    protected NoteViewItem(boolean isEmpty) {
        super(isEmpty);
    }

    @Override
    @NonNull
    public NoteViewItem fromCursor(MyContext myContext, Cursor cursor) {
        return getNew().fromCursorRow(myContext, cursor);
    }

    @NonNull
    @Override
    public NoteViewItem getNew() {
        return new NoteViewItem(false);
    }

    public NoteViewItem fromCursorRow(MyContext myContext, Cursor cursor) {
        setMyContext(myContext);
        setNoteId(DbUtils.getLong(cursor, ActivityTable.NOTE_ID));
        setOrigin(myContext.origins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID)));
        setLinkedAccount(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID));

        setName(DbUtils.getString(cursor, NoteTable.NAME));
        setContent(DbUtils.getString(cursor, NoteTable.CONTENT));
        contentToSearch = DbUtils.getString(cursor, NoteTable.CONTENT_TO_SEARCH);
        inReplyToNoteId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
        inReplyToActor = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID));
        isPublic = DbUtils.getTriState(cursor, NoteTable.PUBLIC);
        audience = Audience.fromNoteId(getOrigin(), getNoteId(), isPublic);
        activityUpdatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        updatedDate = DbUtils.getLong(cursor, NoteTable.UPDATED_DATE);
        noteStatus = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS));
        author = ActorViewItem.fromActorId(getOrigin(), DbUtils.getLong(cursor, NoteTable.AUTHOR_ID));
        favorited = DbUtils.getTriState(cursor, NoteTable.FAVORITED) == TriState.TRUE;
        reblogged = DbUtils.getTriState(cursor, NoteTable.REBLOGGED) == TriState.TRUE;

        String via = DbUtils.getString(cursor, NoteTable.VIA);
        if (!StringUtils.isEmpty(via)) {
            noteSource = Html.fromHtml(via).toString().trim();
        }

        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            attachedImageFile = AttachedImageFile.fromCursor(cursor);
        }

        for (Actor actor : MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), getOrigin(), getNoteId())) {
            rebloggers.put(actor.actorId, actor.getWebFingerId());
        }
        return this;
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, I18n.trimTextAt(getContent().toString(), 40) + ","
                + getDetails(getMyContext().context()));
    }
}
