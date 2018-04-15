/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.database.table.NoteTable;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

/**
 * Helper class to find out a relation of a Note with {@link #noteId} to MyAccount-s
 * @author yvolk@yurivolkov.com
 */
public class NoteForAnyAccount {
    public static final NoteForAnyAccount EMPTY = new NoteForAnyAccount(MyContext.EMPTY, 0, 0);
    public final MyContext myContext;
    @NonNull
    public final Origin origin;
    private final long activityId;
    public final long noteId;
    public DownloadStatus status = DownloadStatus.UNKNOWN;
    private String content = "";
    public long authorId = 0;
    public String authorName = "";
    public long actorId = 0;
    public String actorName = "";
    TriState isPublic = TriState.UNKNOWN;
    public String imageFilename = null;
    Audience recipients;

    public NoteForAnyAccount(MyContext myContext, long activityId, long noteId) {
        this.myContext = myContext;
        this.origin = myContext.origins().fromId(MyQuery.noteIdToOriginId(noteId));
        this.activityId = activityId;
        this.noteId = noteId;
        if (noteId != 0 && this.origin.isValid()) {
            getData();
        }
    }

    private void getData() {
        final String method = "getData";
        String sql = "SELECT " + NoteTable.NOTE_STATUS + ", "
                + NoteTable.CONTENT + ", "
                + NoteTable.AUTHOR_ID + ","
                + NoteTable.PUBLIC
                + " FROM " + NoteTable.TABLE_NAME
                + " WHERE " + NoteTable._ID + "=" + noteId;
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.v(this, method + "; Database is null");
            return;
        }
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToNext()) {
                status = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS));
                content = DbUtils.getString(cursor, NoteTable.CONTENT);
                authorId = DbUtils.getLong(cursor, NoteTable.AUTHOR_ID);
                authorName = MyQuery.actorIdToName(myContext, authorId, MyPreferences.getActorInTimeline());
                isPublic = DbUtils.getTriState(cursor, NoteTable.PUBLIC);
            }
        } catch (Exception e) {
            MyLog.i(this, method + "; SQL:'" + sql + "'", e);
        }
        recipients = Audience.fromNoteId(origin, noteId);
        DownloadData downloadData = DownloadData.getSingleAttachment(noteId);
        imageFilename = downloadData.getStatus() == DownloadStatus.LOADED ? downloadData.getFilename() : "";
        if (activityId == 0) {
            actorId = authorId;
        } else {
            actorId = MyQuery.activityIdToLongColumnValue(ActivityTable.ACTOR_ID, activityId);
        }
        actorName = MyQuery.actorIdToName(myContext, actorId, MyPreferences.getActorInTimeline());
    }

    public TriState getPublic() {
        return isPublic;
    }

    public boolean isLoaded() {
        return status == DownloadStatus.LOADED;
    }

    public String getBodyTrimmed() {
        return I18n.trimTextAt(MyHtml.fromHtml(content), 80).toString();
    }
}
