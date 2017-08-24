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

package org.andstatus.app.database;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.social.Connection;

/**
 * Users table (they are both senders AND recipients in the {@link MsgTable} table)
 * Some of these Users are Accounts (connected to accounts in AndStatus),
 * see {@link MyAccount#getUserId()}
 */
public final class UserTable implements BaseColumns {
    public static final String TABLE_NAME = "user";

    private UserTable() {
    }

    // Table columns
    /* {@link BaseColumns#_ID} is primary key in this database  */

    /**
     * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
     */
    public static final String ORIGIN_ID =  OriginTable.ORIGIN_ID;
    /**
     * ID in the originating system
     * The id is not unique for this table, because we have IDs from different systems in one column.
     */
    public static final String USER_OID = "user_oid";
    /** This is called "screen_name" in Twitter API */
    public static final String USERNAME = "username";
    /** It looks like an email address with your nickname then "@" then your server */
    public static final String WEBFINGER_ID = "webfinger_id";
    /** This is called "name" in Twitter API */
    public static final String REAL_NAME = "real_name";
    /** User's description / "About myself" */
    public static final String DESCRIPTION = "user_description";
    /** Location string */
    public static final String LOCATION = "location";
    /**
     * User's profile URL
     * A link to the representation of the resource. Currently this is simply URL to the HTML
     * representation of the resource (its "permalink")
     */
    public static final String PROFILE_URL = "profile_url";
    /** URL of User's web home page */
    public static final String HOMEPAGE = "homepage";
    /** The latest url of the avatar */
    public static final String AVATAR_URL = "avatar_url";
    public static final String BANNER_URL = "banner_url";

    public static final String MSG_COUNT = "msg_count";
    public static final String FAVORITES_COUNT = "favorited_count";
    public static final String FOLLOWING_COUNT = "following_count";
    public static final String FOLLOWERS_COUNT = "followers_count";

    /**
     * Date and time when the row was created in the originating system.
     * We store it as long returned by {@link Connection#dateFromJson}.
     * NULL means the row was not retrieved from the Internet yet
     * (And maybe there is no such User in the originating system...)
     */
    public static final String CREATED_DATE = "user_created_date";
    public static final String UPDATED_DATE = "user_updated_date";
    /** Date and time the row was inserted into this database */
    public static final String INS_DATE = "user_ins_date";

    /**
     * Id of the latest message where this User was a Sender or an Author
     */
    public static final String USER_ACTIVITY_ID = "user_msg_id";  // TODO: Rename as constant
    /**
     * Date of the latest message where this User was a Sender or an Author
     */
    public static final String USER_ACTIVITY_DATE = "user_msg_date";   // TODO: Rename as constant

    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key */
    public static final String USER_ID = "user_id";
    /** Alias for the primary key used for accounts */
    public static final String ACCOUNT_ID = "account_id";
    /** Alias used in a timeline to distinguish messages for different users */
    public static final String LINKED_USER_ID = "linked_user_id";
    /**
     * Derived from {@link ActivityTable#ACTOR_ID}
     * TODO: Whether this (and other similar...) is {@link #USERNAME} or {@link #REAL_NAME}, depends on settings
     */
    public static final String SENDER_NAME = "sender_name";  // TODO: Rename to "actor_name"
    /** Derived from {@link MsgTable#AUTHOR_ID} */
    public static final String AUTHOR_NAME = "author_name";
    /** Derived from {@link ActivityTable#ACTOR_ID} */
    public static final String ACTOR_NAME = "actor_name";
    /** Derived from {@link MsgTable#IN_REPLY_TO_USER_ID} */
    public static final String IN_REPLY_TO_NAME = "in_reply_to_name";
    /** Derived from {@link MsgTable#RECIPIENT_ID} */
    public static final String RECIPIENT_NAME = "recipient_name";

    public static final String DEFAULT_SORT_ORDER = USERNAME + " ASC";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + UserTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + UserTable.ORIGIN_ID + " INTEGER NOT NULL,"
                + UserTable.USER_OID + " TEXT,"
                + UserTable.USERNAME + " TEXT NOT NULL,"
                + UserTable.WEBFINGER_ID + " TEXT NOT NULL,"
                + UserTable.REAL_NAME + " TEXT,"
                + UserTable.DESCRIPTION + " TEXT,"
                + UserTable.LOCATION + " TEXT,"
                + UserTable.PROFILE_URL + " TEXT,"
                + UserTable.HOMEPAGE + " TEXT,"
                + UserTable.AVATAR_URL + " TEXT,"
                + UserTable.BANNER_URL + " TEXT,"
                + UserTable.MSG_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FAVORITES_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FOLLOWING_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.FOLLOWERS_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.CREATED_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.UPDATED_DATE + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.INS_DATE + " INTEGER NOT NULL,"
                + UserTable.USER_ACTIVITY_ID + " INTEGER DEFAULT 0 NOT NULL,"
                + UserTable.USER_ACTIVITY_DATE + " INTEGER DEFAULT 0 NOT NULL"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_user_origin ON " + UserTable.TABLE_NAME + " ("
                + UserTable.ORIGIN_ID + ", "
                + UserTable.USER_OID
                + ")");
    }
}
