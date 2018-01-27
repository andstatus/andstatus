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
import android.text.TextUtils;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AttachedImageFile;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.TimelineSql;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.database.table.ActorTable;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
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
    public NoteViewItem fromCursor(Cursor cursor) {
        return getNew().fromCursorRow(getMyContext(), cursor);
    }

    @NonNull
    @Override
    public NoteViewItem getNew() {
        return new NoteViewItem(false);
    }

    public NoteViewItem fromCursorRow(MyContext myContext, Cursor cursor) {
        long startTime = System.currentTimeMillis();
        setMyContext(myContext);
        setMsgId(DbUtils.getLong(cursor, ActivityTable.MSG_ID));
        setOrigin(myContext.persistentOrigins().fromId(DbUtils.getLong(cursor, ActivityTable.ORIGIN_ID)));
        setLinkedAccount(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID));

        authorName = TimelineSql.userColumnIndexToNameAtTimeline(cursor,
                cursor.getColumnIndex(ActorTable.AUTHOR_NAME), MyPreferences.getShowOrigin());
        setBody(MyHtml.prepareForView(DbUtils.getString(cursor, NoteTable.BODY)));
        inReplyToMsgId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_NOTE_ID);
        inReplyToUserId = DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID);
        inReplyToName = DbUtils.getString(cursor, ActorTable.IN_REPLY_TO_NAME);
        recipientName = DbUtils.getString(cursor, ActorTable.RECIPIENT_NAME);
        activityUpdatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
        updatedDate = DbUtils.getLong(cursor, NoteTable.UPDATED_DATE);
        msgStatus = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS));

        authorId = DbUtils.getLong(cursor, NoteTable.AUTHOR_ID);

        favorited = DbUtils.getTriState(cursor, NoteTable.FAVORITED) == TriState.TRUE;
        reblogged = DbUtils.getTriState(cursor, NoteTable.REBLOGGED) == TriState.TRUE;

        String via = DbUtils.getString(cursor, NoteTable.VIA);
        if (!TextUtils.isEmpty(via)) {
            messageSource = Html.fromHtml(via).toString().trim();
        }

        avatarFile = AvatarFile.fromCursor(authorId, cursor, DownloadTable.AVATAR_FILE_NAME);
        if (MyPreferences.getDownloadAndDisplayAttachedImages()) {
            attachedImageFile = new AttachedImageFile(
                    DbUtils.getLong(cursor, DownloadTable.IMAGE_ID),
                    DbUtils.getString(cursor, DownloadTable.IMAGE_FILE_NAME));
        }

        long beforeRebloggers = System.currentTimeMillis();
        for (Actor actor : MyQuery.getRebloggers(MyContextHolder.get().getDatabase(), getOrigin(), getMsgId())) {
            rebloggers.put(actor.actorId, actor.getWebFingerId());
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, ": " + (System.currentTimeMillis() - startTime) + "ms, "
                    + rebloggers.size() + " rebloggers: " + (System.currentTimeMillis() - beforeRebloggers) + "ms");
        }
        return this;
    }

    @Override
    public String toString() {
        return MyLog.formatKeyValue(this, I18n.trimTextAt(MyHtml.fromHtml(getBody()), 40) + ","
                + getDetails(getMyContext().context()));
    }
}
