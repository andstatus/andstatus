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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.social.Connection;

/**
 * Actors table (they are both senders AND recipients in the {@link NoteTable} table)
 * Some of these Users are Accounts (connected to accounts in AndStatus),
 * see {@link MyAccount#getActorId()}
 */
public final class ActorTable implements BaseColumns {
    public static final String TABLE_NAME = "actor";

    private ActorTable() {
    }

    // Table columns
    /* {@link BaseColumns#_ID} is primary key in this database  */

    public static final String USER_ID = UserTable.USER_ID;

    /**
     * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
     */
    public static final String ORIGIN_ID =  OriginTable.ORIGIN_ID;
    /**
     * ID in the originating system
     * The id is not unique for this table, because we have IDs from different systems in one column.
     */
    public static final String ACTOR_OID = "actor_oid";

    /** {@link org.andstatus.app.actor.GroupType} */
    public static final String GROUP_TYPE = "group_type";
    /** {@link org.andstatus.app.actor.GroupType#parentActorRequired}
     * denotes the Actor, whose the Group is */
    public static final String PARENT_ACTOR_ID = "parent_actor_id";

    /** This is called "screen_name" in Twitter API, "login" or "username" in others, "preferredUsername" in ActivityPub */
    public static final String USERNAME = "username";
    /** It looks like an email address with your nickname then "@" then your server */
    public static final String WEBFINGER_ID = "webfinger_id";
    /** This is called "name" in Twitter API and in ActivityPub */
    public static final String REAL_NAME = "real_name";
    /** Actor's description / "About myself" "bio" "summary" */
    public static final String SUMMARY = "actor_description";  // TODO: Rename
    /** Location string */
    public static final String LOCATION = "location";
    /** URL of Actor's Profile web page */
    public static final String PROFILE_PAGE = "profile_url";  // TODO: Rename
    /** URL of Actor's Home web page */
    public static final String HOMEPAGE = "homepage";
    /** The latest url of the avatar */
    public static final String AVATAR_URL = "avatar_url";

    public static final String NOTES_COUNT = "notes_count";
    public static final String FAVORITES_COUNT = "favorited_count";
    public static final String FOLLOWING_COUNT = "following_count";
    public static final String FOLLOWERS_COUNT = "followers_count";

    /**
     * Date and time when the row was created in the originating system.
     * We store it as long returned by {@link Connection#dateFromJson}.
     * NULL means the row was not retrieved from the Internet yet
     * (And maybe there is no such an Actor in the originating system...)
     */
    public static final String CREATED_DATE = "actor_created_date";
    public static final String UPDATED_DATE = "actor_updated_date";
    /** Date and time the row was inserted into this database */
    public static final String INS_DATE = "actor_ins_date";

    /**
     * Id of the latest activity where this actor was an Actor or an Author
     */
    public static final String ACTOR_ACTIVITY_ID = "actor_activity_id";
    /**
     * Date of the latest activity of this Actor (were he was an Actor)
     */
    public static final String ACTOR_ACTIVITY_DATE = "actor_activity_date";

    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key */
    public static final String ACTOR_ID = "actor_id";
    /** Alias for the primary key used for accounts */
    public static final String ACCOUNT_ID = "account_id";
    /**
     * Derived from {@link ActivityTable#ACTOR_ID}
     * Whether this (and other similar...) is {@link #USERNAME} or {@link #REAL_NAME}, depends on settings
     *
     * Derived from {@link ActivityTable#ACTOR_ID} */
    public static final String ACTIVITY_ACTOR_NAME = "activity_actor_name";
    /** Derived from {@link NoteTable#AUTHOR_ID} */
    public static final String AUTHOR_NAME = "author_name";

    public static final String DEFAULT_SORT_ORDER = USERNAME + " ASC";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + USER_ID + " INTEGER NOT NULL,"
                + ORIGIN_ID + " INTEGER NOT NULL,"
                + ACTOR_OID + " TEXT NOT NULL,"
                + GROUP_TYPE + " INTEGER NOT NULL DEFAULT 0,"
                + PARENT_ACTOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + USERNAME + " TEXT NOT NULL,"
                + WEBFINGER_ID + " TEXT NOT NULL,"
                + REAL_NAME + " TEXT,"
                + SUMMARY + " TEXT,"
                + LOCATION + " TEXT,"
                + PROFILE_PAGE + " TEXT,"
                + HOMEPAGE + " TEXT,"
                + AVATAR_URL + " TEXT,"
                + NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + FAVORITES_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + FOLLOWING_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + FOLLOWERS_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + CREATED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + UPDATED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + INS_DATE + " INTEGER NOT NULL,"
                + ACTOR_ACTIVITY_ID + " INTEGER NOT NULL DEFAULT 0,"
                + ACTOR_ACTIVITY_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_actor_origin ON " + TABLE_NAME + " ("
                + ORIGIN_ID + ", "
                + ACTOR_OID
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_actor_user ON " + TABLE_NAME + " ("
                + USER_ID
                + ")");

        DbUtils.execSQL(db, "CREATE INDEX idx_actor_webfinger ON " + TABLE_NAME + " ("
                + WEBFINGER_ID
                + ")");
    }
}
