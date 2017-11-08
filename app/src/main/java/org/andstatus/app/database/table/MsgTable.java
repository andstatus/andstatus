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

/**
 * Table for both public and direct messages
 * i.e. for tweets, dents, notices
 * and also for "direct messages", "direct dents" etc.
 */
public final class MsgTable implements BaseColumns {
    public static final String TABLE_NAME = "msg";

    private MsgTable() {
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
    public static final String MSG_OID = "msg_oid";
    /**
     * See {@link DownloadStatus}. Defaults to {@link DownloadStatus#UNKNOWN}
     */
    public static final String MSG_STATUS = "msg_status";
    /** Conversation ID, internal to AndStatus */
    public static final String CONVERSATION_ID = "conversation_id";
    /** ID of the conversation in the originating system (if the system supports this) */
    public static final String CONVERSATION_OID = "conversation_oid";
    /**
     * A link to the representation of the resource. Currently this is simply URL to the HTML
     * representation of the resource (its "permalink")
     */
    public static final String URL = "url";
    /**
     * Text of the message ("TEXT" may be reserved word so it was renamed here)
     */
    public static final String BODY = "body";
    /**
     * Body text, prepared for easy searching in a database
     */
    public static final String BODY_TO_SEARCH = "body_to_search";
    /**
     * String generally describing Client's software used to post this message
     * It's like "User Agent" string in the browsers?!: "via ..."
     * (This is "source" field in tweets)
     */
    public static final String VIA = "via";
    /**
     * If not null: Link to the Msg._ID in this table
     */
    public static final String IN_REPLY_TO_MSG_ID = "in_reply_to_msg_id";
    /**
     * Date and time when the message was created or updated in the originating system.
     * We store it as long milliseconds.
     */
    public static final String UPDATED_DATE = "msg_updated_date";
    /** Date and time the row was inserted into this database */
    public static final String INS_DATE = "msg_ins_date";
    /** The Msg is definitely private (e.g. "Direct message")
     *  Absence of this flag means that we don't know for sure, if the message is private */
    public static final String PRIVATE = "private";
    /** Some of my accounts favorited this message */
    public static final String FAVORITED = "favorited";
    /** The Msg is reblogged by some of my accounts
     * In some sense REBLOGGED is like FAVORITED.
     * Main difference: visibility. REBLOGGED are shown for all followers in their Home timelines.
     */
    public static final String REBLOGGED = "reblogged";
    /** Some of my accounts is mentioned in this message, is a recipient (in TO or CC)
     *     or this is a reply to my account */
    public static final String MENTIONED = "mentioned";

    public static final String FAVORITE_COUNT = "favorite_count";
    public static final String REBLOG_COUNT = "reblog_count";
    public static final String REPLY_COUNT = "reply_count";  // To be calculated locally?!

    // Columns, which duplicate other existing info. Here to speed up data retrieval
    public static final String AUTHOR_ID = "msg_author_id";
    /**
     * If not null: to which Sender this message is a reply = User._ID
     * This field is not necessary but speeds up IN_REPLY_TO_NAME calculation
     */
    public static final String IN_REPLY_TO_USER_ID = "in_reply_to_user_id";

    // Derived columns (they are not stored in this table but are result of joins and aliasing)

    /** Alias for the primary key */
    public static final String MSG_ID =  "msg_id";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ORIGIN_ID + " INTEGER NOT NULL,"
                + MSG_OID + " TEXT NOT NULL,"
                + MSG_STATUS + " INTEGER NOT NULL DEFAULT 0,"
                + CONVERSATION_ID + " INTEGER NOT NULL DEFAULT 0" + ","
                + CONVERSATION_OID + " TEXT,"
                + URL + " TEXT,"
                + BODY + " TEXT,"
                + BODY_TO_SEARCH + " TEXT,"
                + VIA + " TEXT,"
                + AUTHOR_ID + " INTEGER NOT NULL,"
                + IN_REPLY_TO_MSG_ID + " INTEGER,"
                + IN_REPLY_TO_USER_ID + " INTEGER,"
                + PRIVATE + " INTEGER NOT NULL DEFAULT 0,"
                + FAVORITED + " INTEGER NOT NULL DEFAULT 0,"
                + REBLOGGED + " INTEGER NOT NULL DEFAULT 0,"
                + MENTIONED + " INTEGER NOT NULL DEFAULT 0,"
                + FAVORITE_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + REBLOG_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + REPLY_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + INS_DATE + " INTEGER NOT NULL,"
                + UPDATED_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_msg_origin ON " + TABLE_NAME + " ("
                + ORIGIN_ID + ", "
                + MSG_OID
                + ")"
        );

        // Index not null rows only, see https://www.sqlite.org/partialindex.html
        DbUtils.execSQL(db, "CREATE INDEX idx_msg_in_reply_to_msg_id ON " + TABLE_NAME + " ("
                + IN_REPLY_TO_MSG_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_msg_conversation_id ON " + TABLE_NAME + " ("
                + CONVERSATION_ID
                + ")"
        );

    }
}
