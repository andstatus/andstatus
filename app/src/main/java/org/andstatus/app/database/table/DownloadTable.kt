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
package org.andstatus.app.database.table

import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.DownloadType
import org.andstatus.app.data.MyContentType

/** Avatar, Note's attachment...  */
object DownloadTable : BaseColumns {
    val TABLE_NAME: String = "download"

    /** See [DownloadType]  */
    val DOWNLOAD_TYPE: String = "download_type"

    /** Index (e.g. of an attachment for a particular note) Starting from 0  */
    val DOWNLOAD_NUMBER: String = "download_number"

    /** Avatar is connected to exactly one actor  */
    val ACTOR_ID: String = ActorTable.ACTOR_ID

    /** Attachment is connected to a note  */
    val NOTE_ID: String = NoteTable.NOTE_ID

    /** See [MyContentType]  */
    val CONTENT_TYPE: String = "content_type"
    val MEDIA_TYPE: String = "media_type"
    val URL: String = "url"
    val PREVIEW_OF_DOWNLOAD_ID: String = "preview_of_download_id"

    /**
     * See [DownloadStatus]. Defaults to [DownloadStatus.UNKNOWN]
     */
    val DOWNLOAD_STATUS: String = "download_status"
    val WIDTH: String = "width"
    val HEIGHT: String = "height"
    val DURATION: String = "duration"
    val FILE_NAME: String = "file_name"
    val FILE_SIZE: String = "file_size"
    val DOWNLOADED_DATE: String = "downloaded_date"
    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key  */
    val IMAGE_ID: String = "image_id"
    val AVATAR_FILE_NAME: String = "avatar_file_name"

    /** Alias helping to show first attached image  */
    val IMAGE_FILE_NAME: String = "image_file_name"
    val IMAGE_URI: String = "image_uri"
    fun create(db: SQLiteDatabase) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + DOWNLOAD_TYPE + " INTEGER NOT NULL,"
                + DOWNLOAD_NUMBER + " INTEGER NOT NULL DEFAULT 0,"
                + ACTOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + NOTE_ID + " INTEGER NOT NULL DEFAULT 0,"
                + CONTENT_TYPE + " INTEGER NOT NULL DEFAULT 0,"
                + MEDIA_TYPE + " TEXT NOT NULL,"
                + URL + " TEXT NOT NULL,"
                + PREVIEW_OF_DOWNLOAD_ID + " INTEGER,"
                + DOWNLOAD_STATUS + " INTEGER NOT NULL DEFAULT 0,"
                + WIDTH + " INTEGER NOT NULL DEFAULT 0,"
                + HEIGHT + " INTEGER NOT NULL DEFAULT 0,"
                + DURATION + " INTEGER NOT NULL DEFAULT 0,"
                + FILE_NAME + " TEXT,"
                + FILE_SIZE + " INTEGER NOT NULL DEFAULT 0,"
                + DOWNLOADED_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")")
        DbUtils.execSQL(db, "CREATE INDEX idx_download_actor ON " + TABLE_NAME + " ("
                + ACTOR_ID + ", "
                + DOWNLOAD_STATUS
                + ")")
        DbUtils.execSQL(db, "CREATE INDEX idx_download_note ON " + TABLE_NAME + " ("
                + NOTE_ID + ", "
                + DOWNLOAD_NUMBER
                + ")")
        DbUtils.execSQL(db, "CREATE INDEX idx_download_downloaded_date ON " + TABLE_NAME + " ("
                + DOWNLOADED_DATE
                + ")")
    }
}