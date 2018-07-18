/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.graphics.CachedImage;
import org.andstatus.app.graphics.ImageCaches;
import org.andstatus.app.graphics.MediaMetadata;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class AvatarFile extends ImageFile {
    public static final AvatarFile EMPTY = new AvatarFile(Actor.EMPTY, "", MediaMetadata.EMPTY,
            DownloadStatus.ABSENT, DATETIME_MILLIS_NEVER);
    private final Actor actor;
    public static final int AVATAR_SIZE_DIP = 48;
    
    @NonNull
    public static AvatarFile fromCursor(Actor actor, Cursor cursor) {
        final String filename = DbUtils.getString(cursor, DownloadTable.AVATAR_FILE_NAME);
        return actor.isEmpty()
                ? AvatarFile.EMPTY
                : new AvatarFile(actor, filename, MediaMetadata.EMPTY,
                    DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS)),
                    DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE));
    }

    public static AvatarFile fromActorOnly(Actor actor) {
        return actor.isEmpty()
                ? AvatarFile.EMPTY
                : new AvatarFile(actor, "", MediaMetadata.EMPTY, DownloadStatus.UNKNOWN, DATETIME_MILLIS_NEVER);
    }

    private AvatarFile(Actor actor, String filename, MediaMetadata mediaMetadata, DownloadStatus downloadStatus,
                       long downloadedDate) {
        super(filename, mediaMetadata, 0, downloadStatus, downloadedDate);
        this.actor = actor;
    }

    @Override
    public CacheName getCacheName() {
        return CacheName.AVATAR;
    }

    @Override
    public long getId() {
        return getActor().actorId;
    }

    @NonNull
    public Actor getActor() {
        return actor == null ? Actor.EMPTY : actor;
    }

    @Override
    public CachedImage getDefaultImage() {
        return ImageCaches.getStyledImage(R.drawable.ic_person_black_36dp, R.drawable.ic_person_white_36dp);
    }

    @Override
    protected void requestDownload() {
        if (getActor().actorId == 0) return;

        MyServiceManager.sendCommand(
                CommandData.newActorCommand(CommandEnum.GET_AVATAR, getActor().actorId, getActor().getUsername()));
    }

    @Override
    protected boolean isDefaultImageRequired() {
        return true;
    }

    public void resetAvatarErrors(MyContext myContext) {
        SQLiteDatabase db = myContext.getDatabase();
        if (getActor().actorId == 0 || db == null) return;

        db.execSQL("UPDATE " + DownloadTable.TABLE_NAME +
                " SET " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.ABSENT.save() +
                " WHERE " + DownloadTable.ACTOR_ID + "=" + getActor().actorId +
                " AND " + DownloadTable.DOWNLOAD_STATUS + "<>" + DownloadStatus.LOADED.save()
        );
    }
}
