/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.database.table;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.DownloadType;
import org.andstatus.app.data.MyContentType;

/** Avatar, Note's attachment... */
public final class DownloadTable implements BaseColumns {
    public static final String TABLE_NAME = "download";
    private DownloadTable() {
    }
    /** See {@link DownloadType} */
    public static final String DOWNLOAD_TYPE = "download_type";
    /** Index (e.g. of an attachment for a particular note) Starting from 0 */
    public static final String DOWNLOAD_NUMBER = "download_number";
    /** Avatar is connected to exactly one actor */
    public static final String ACTOR_ID = ActorTable.ACTOR_ID;
    /** Attachment is connected to a note */
    public static final String NOTE_ID =  NoteTable.NOTE_ID;
    /** See {@link MyContentType} */
    public static final String CONTENT_TYPE = "content_type";
    public static final String MEDIA_TYPE = "media_type";
    public static final String URI = "url";  // TODO: Rename to "uri"
    /**
     * See {@link DownloadStatus}. Defaults to {@link DownloadStatus#UNKNOWN}
     */
    public static final String DOWNLOAD_STATUS = "download_status";
    public static final String WIDTH = "width";
    public static final String HEIGHT = "height";
    public static final String DURATION = "duration";
    public static final String FILE_NAME = "file_name";
    public static final String FILE_SIZE = "file_size";
    public static final String DOWNLOADED_DATE = "downloaded_date";

    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key */
    public static final String IMAGE_ID = "image_id";

    public static final String AVATAR_FILE_NAME = "avatar_file_name";
    /** Alias helping to show first attached image */
    public static final String IMAGE_FILE_NAME = "image_file_name";
    public static final String IMAGE_URL = "image_url";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DOWNLOAD_TYPE + " INTEGER NOT NULL,"
                + DOWNLOAD_NUMBER + " INTEGER NOT NULL DEFAULT 0,"
                + ACTOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + NOTE_ID + " INTEGER NOT NULL DEFAULT 0,"
                + CONTENT_TYPE + " INTEGER NOT NULL DEFAULT 0,"
                + MEDIA_TYPE + " TEXT NOT NULL,"
                + URI + " TEXT NOT NULL,"
                + DOWNLOAD_STATUS + " INTEGER NOT NULL DEFAULT 0,"
                + WIDTH + " INTEGER NOT NULL DEFAULT 0,"
                + HEIGHT + " INTEGER NOT NULL DEFAULT 0,"
                + DURATION + " INTEGER NOT NULL DEFAULT 0,"
                + FILE_NAME + " TEXT,"
                + FILE_SIZE + " INTEGER NOT NULL DEFAULT 0,"
                + DOWNLOADED_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_download_actor ON " + TABLE_NAME + " ("
                + ACTOR_ID + ", "
                + DOWNLOAD_STATUS
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_download_note ON " + TABLE_NAME + " ("
                + NOTE_ID + ", "
                + DOWNLOAD_NUMBER
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_download_downloaded_date ON " + TABLE_NAME + " ("
                + DOWNLOADED_DATE
                + ")");
    }
}
