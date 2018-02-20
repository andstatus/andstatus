/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.net.Uri;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
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
 * Helper class to find out a relation of a Note to MyAccount
 * @author yvolk@yurivolkov.com
 */
public class NoteForAccount {
    public static final NoteForAccount EMPTY = new NoteForAccount(Origin.EMPTY, 0, 0, MyAccount.EMPTY);
    @NonNull
    public final Origin origin;
    private final long activityId;
    public final long noteId;
    public DownloadStatus status = DownloadStatus.UNKNOWN;
    private String body = "";
    public long authorId = 0;
    public String authorName = "";
    public long actorId = 0;
    public String actorName = "";
    private boolean isAuthorMySucceededMyAccount = false;
    TriState isPrivate = TriState.UNKNOWN;
    public String imageFilename = null;
    @NonNull
    private final MyAccount myAccount;
    private final long accountActorId;
    public boolean isSubscribed = false;
    public boolean isAuthor = false;
    public boolean isActor = false;
    private boolean isRecipient = false;
    public boolean favorited = false;
    public boolean reblogged = false;
    public boolean actorFollowed = false;
    public boolean authorFollowed = false;

    public NoteForAccount(@NonNull Origin origin, long activityId, long noteId, MyAccount myAccount) {
        this.origin = origin;
        this.activityId = activityId;
        this.noteId = noteId;
        this.myAccount = calculateMyAccount(origin, myAccount);
        this.accountActorId = this.myAccount.getActorId();
        if (this.myAccount.isValid()) {
            getData();
        }
    }

    @NonNull
    private MyAccount calculateMyAccount(Origin origin, MyAccount ma) {
        if (ma == null || !origin.isValid() || !ma.getOrigin().equals(origin) || !ma.isValid()) {
            return MyAccount.EMPTY;
        }
        return ma;
    }

    private void getData() {
        final String method = "getData";
        String sql = "SELECT " + NoteTable.NOTE_STATUS + ", "
                + NoteTable.BODY + ", "
                + NoteTable.AUTHOR_ID + ","
                + NoteTable.PRIVATE
                + " FROM " + NoteTable.TABLE_NAME
                + " WHERE " + NoteTable._ID + "=" + noteId;
        SQLiteDatabase db = MyContextHolder.get().getDatabase();
        if (db == null) {
            MyLog.v(this, method + "; Database is null");
            return;
        }
        try (Cursor cursor = db.rawQuery(sql, null)) {
            if (cursor.moveToNext()) {
                status = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS));
                body = DbUtils.getString(cursor, NoteTable.BODY);
                authorId = DbUtils.getLong(cursor, NoteTable.AUTHOR_ID);
                authorName = MyQuery.actorIdToName(db, authorId, MyPreferences.getActorInTimeline());
                isAuthor = (accountActorId == authorId);
                isAuthorMySucceededMyAccount = isAuthor && myAccount.isValidAndSucceeded();
                isPrivate = DbUtils.getTriState(cursor, NoteTable.PRIVATE);
            }
        } catch (Exception e) {
            MyLog.i(this, method + "; SQL:'" + sql + "'", e);
        }
        Audience recipients = Audience.fromNoteId(origin, noteId);
        isRecipient = recipients.contains(accountActorId);
        DownloadData downloadData = DownloadData.getSingleForNote(noteId, MyContentType.IMAGE, Uri.EMPTY);
        imageFilename = downloadData.getStatus() == DownloadStatus.LOADED ? downloadData.getFilename() : "";
        ActorToNote actorToNote = MyQuery.favoritedAndReblogged(db, noteId, accountActorId);
        favorited = actorToNote.favorited;
        reblogged = actorToNote.reblogged;
        isSubscribed = actorToNote.subscribed;
        authorFollowed = MyQuery.isFollowing(accountActorId, authorId);
        if (activityId == 0) {
            actorId = authorId;
        } else {
            actorId = MyQuery.activityIdToLongColumnValue(ActivityTable.ACTOR_ID, activityId);
        }
        actorName = MyQuery.actorIdToName(db, actorId, MyPreferences.getActorInTimeline());
        isActor = actorId == accountActorId;
        actorFollowed = !isActor && (actorId == authorId ? authorFollowed : MyQuery.isFollowing(accountActorId, actorId));
    }

    @NonNull
    public MyAccount getMyAccount() {
        return myAccount;
    }
    
    public boolean isPrivate() {
        return isPrivate.equals(TriState.TRUE);
    }

    public boolean isTiedToThisAccount() {
        return isRecipient || favorited || reblogged || isAuthor
                || actorFollowed || authorFollowed;
    }

    public boolean hasPrivateAccess() {
        return isRecipient || isAuthor;
    }

    public boolean isLoaded() {
        return status == DownloadStatus.LOADED;
    }

    public boolean isAuthorSucceededMyAccount() {
        return isAuthorMySucceededMyAccount;
    }

    public String getBodyTrimmed() {
        return I18n.trimTextAt(MyHtml.fromHtml(body), 80).toString();
    }
}
