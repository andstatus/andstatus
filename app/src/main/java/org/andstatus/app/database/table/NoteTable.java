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
import org.andstatus.app.util.TriState;

/**
 * Table for both public and private notes
 * i.e. for tweets, dents, notices
 * and also for "direct messages", "direct dents" etc.
 */
public final class NoteTable implements BaseColumns {
    public static final String TABLE_NAME = "note";

    private NoteTable() {
    }

    // Table columns are below:
    /*
     * {@link BaseColumns#_ID} is primary key in this database
     * No, we can not rename the {@link BaseColumns#_ID}, it should always be "_id".
     * See <a href="http://stackoverflow.com/questions/3359414/android-column-id-does-not-exist">Android column '_id' does not exist?</a>
     */

    /**
     * ID of the originating (source) system ("Social Network": twitter.com, identi.ca, ... ) where the row was created
     */
    public static final String ORIGIN_ID =  OriginTable.ORIGIN_ID;
    /**
     * ID in the originating system
     * The id is not unique for this table, because we have IDs from different systems in one column
     * and IDs from different systems may overlap.
     */
    public static final String NOTE_OID = "note_oid";
    /**
     * See {@link DownloadStatus}. Defaults to {@link DownloadStatus#UNKNOWN}
     */
    public static final String NOTE_STATUS = "note_status";
    /** Conversation ID, internal to AndStatus */
    public static final String CONVERSATION_ID = "conversation_id";
    /** ID of the conversation in the originating system (if the system supports this) */
    public static final String CONVERSATION_OID = "conversation_oid";
    /**
     * A link to the representation of the resource. Currently this is simply URL to the HTML
     * representation of the resource (its "permalink")
     */
    public static final String URL = "url";
    /** A simple, human-readable, plain-text name for the Note */
    public static final String NAME = "note_name";
    /** Content of the note */
    public static final String CONTENT = "content";
    /** Name and Content text, prepared for easy searching in a database */
    public static final String CONTENT_TO_SEARCH = "content_to_search";
    /**
     * String generally describing Client's software used to post this note
     * It's like "User Agent" string in the browsers?!: "via ..."
     * (This is "source" field in tweets)
     */
    public static final String VIA = "via";
    /**
     * If not null: Link to the #_ID in this table
     */
    public static final String IN_REPLY_TO_NOTE_ID = "in_reply_to_note_id";
    /**
     * Date and time when the note was created or updated in the originating system.
     * We store it as long milliseconds.
     */
    public static final String UPDATED_DATE = "note_updated_date";
    /** Date and time when the Note was first LOADED into this database
     * or if it was not LOADED yet, when the row was inserted into this database */
    public static final String INS_DATE = "note_ins_date";
    /** {@link TriState} true - The Note is definitely public (e.g. it has Public group as one of its audience members)
     * false - it is definitely private */
    public static final String PUBLIC = "public";
    /** Some of my accounts favorited this note */
    public static final String FAVORITED = "favorited";
    /** The Note is reblogged by some of my actors
     * In some sense REBLOGGED is like FAVORITED.
     * Main difference: visibility. REBLOGGED are shown for all followers in their Home timelines.
     */
    public static final String REBLOGGED = "reblogged";

    public static final String FAVORITE_COUNT = "favorite_count";
    public static final String REBLOG_COUNT = "reblog_count";
    public static final String REPLY_COUNT = "reply_count";  // To be calculated locally?!
    public static final String ATTACHMENTS_COUNT = "attachments_count";

    // Columns, which duplicate other existing info. Here to speed up data retrieval
    public static final String AUTHOR_ID = "note_author_id";
    /**
     * If not null: to which Sender this note is a reply = Actor._ID
     * This field is not necessary but speeds up IN_REPLY_TO_NAME calculation
     */
    public static final String IN_REPLY_TO_ACTOR_ID = "in_reply_to_actor_id";

    // Derived columns (they are not stored in this table but are result of joins and aliasing)

    /** Alias for the primary key */
    public static final String NOTE_ID =  "note_id";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ORIGIN_ID + " INTEGER NOT NULL,"
                + NOTE_OID + " TEXT NOT NULL,"
                + NOTE_STATUS + " INTEGER NOT NULL DEFAULT 0,"
                + CONVERSATION_ID + " INTEGER NOT NULL DEFAULT 0" + ","
                + CONVERSATION_OID + " TEXT,"
                + URL + " TEXT,"
                + NAME + " TEXT,"
                + CONTENT + " TEXT,"
                + CONTENT_TO_SEARCH + " TEXT,"
                + VIA + " TEXT,"
                + AUTHOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + IN_REPLY_TO_NOTE_ID + " INTEGER,"
                + IN_REPLY_TO_ACTOR_ID + " INTEGER,"
                + PUBLIC + " INTEGER NOT NULL DEFAULT 0,"
                + FAVORITED + " INTEGER NOT NULL DEFAULT 0,"
                + REBLOGGED + " INTEGER NOT NULL DEFAULT 0,"
                + FAVORITE_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + REBLOG_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + REPLY_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + ATTACHMENTS_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + INS_DATE + " INTEGER NOT NULL,"
                + UPDATED_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_note_origin ON " + TABLE_NAME + " ("
                + ORIGIN_ID + ", "
                + NOTE_OID
                + ")"
        );

        // Index not null rows only, see https://www.sqlite.org/partialindex.html
        DbUtils.execSQL(db, "CREATE INDEX idx_note_in_reply_to_note_id ON " + TABLE_NAME + " ("
                + IN_REPLY_TO_NOTE_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_note_conversation_id ON " + TABLE_NAME + " ("
                + CONVERSATION_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_conversation_oid ON " + TABLE_NAME + " ("
                + ORIGIN_ID + ", "
                + CONVERSATION_OID
                + ")"
        );

    }
}
