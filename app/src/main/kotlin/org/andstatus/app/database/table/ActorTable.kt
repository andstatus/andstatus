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
import org.andstatus.app.account.MyAccount
import org.andstatus.app.data.DbUtils

/**
 * Actors table (they are both senders AND recipients in the [NoteTable] table)
 * Some of these Users are Accounts (connected to accounts in AndStatus),
 * see [MyAccount.getActorId]
 */
object ActorTable : BaseColumns {
    val TABLE_NAME: String = "actor"

    // Table columns
    /* {@link BaseColumns#_ID} is primary key in this database  */
    val USER_ID: String = UserTable.USER_ID

    /**
     * ID of the originating (source) system (twitter.com, identi.ca, ... ) where the row was created
     */
    val ORIGIN_ID: String = OriginTable.ORIGIN_ID

    /**
     * ID in the originating system
     * The id is not unique for this table, because we have IDs from different systems in one column.
     */
    val ACTOR_OID: String = "actor_oid"

    /** [org.andstatus.app.actor.GroupType]  */
    val GROUP_TYPE: String = "group_type"

    /** [org.andstatus.app.actor.GroupType.hasParentActor]
     * denotes the Actor, whose the Group is  */
    val PARENT_ACTOR_ID: String = "parent_actor_id"

    /** This is called "screen_name" in Twitter API, "login" or "username" in others, "preferredUsername" in ActivityPub  */
    val USERNAME: String = "username"

    /** It looks like an email address with your nickname then "@" then your server  */
    val WEBFINGER_ID: String = "webfinger_id"

    /** This is called "name" in Twitter API and in ActivityPub  */
    val REAL_NAME: String = "real_name"

    /** Actor's description / "About myself" "bio" "summary"  */
    val SUMMARY: String = "actor_description" // TODO: Rename

    /** Location string  */
    val LOCATION: String = "location"

    /** URL of Actor's Profile web page  */
    val PROFILE_PAGE: String = "profile_url" // TODO: Rename

    /** URL of Actor's Home web page  */
    val HOMEPAGE: String = "homepage"

    /** The latest url of the avatar  */
    val AVATAR_URL: String = "avatar_url"
    val NOTES_COUNT: String = "notes_count"
    val FAVORITES_COUNT: String = "favorited_count"
    val FOLLOWING_COUNT: String = "following_count"
    val FOLLOWERS_COUNT: String = "followers_count"

    /**
     * Date and time when the row was created in the originating system.
     * We store it as long returned by [Connection.dateFromJson].
     * NULL means the row was not retrieved from the Internet yet
     * (And maybe there is no such an Actor in the originating system...)
     */
    val CREATED_DATE: String = "actor_created_date"
    val UPDATED_DATE: String = "actor_updated_date"

    /** Date and time the row was inserted into this database  */
    val INS_DATE: String = "actor_ins_date"

    /**
     * Id of the latest activity where this actor was an Actor or an Author
     */
    val ACTOR_ACTIVITY_ID: String = "actor_activity_id"

    /**
     * Date of the latest activity of this Actor (were he was an Actor)
     */
    val ACTOR_ACTIVITY_DATE: String = "actor_activity_date"
    /*
     * Derived columns (they are not stored in this table but are result of joins)
     */
    /** Alias for the primary key  */
    val ACTOR_ID: String = "actor_id"

    /** Alias for the primary key used for accounts  */
    val ACCOUNT_ID: String = "account_id"

    /**
     * Derived from [ActivityTable.ACTOR_ID]
     * Whether this (and other similar...) is [.USERNAME] or [.REAL_NAME], depends on settings
     *
     * Derived from [ActivityTable.ACTOR_ID]  */
    val ACTIVITY_ACTOR_NAME: String = "activity_actor_name"

    /** Derived from [NoteTable.AUTHOR_ID]  */
    val AUTHOR_NAME: String = "author_name"
    val DEFAULT_SORT_ORDER: String = USERNAME + " ASC"
    fun create(db: SQLiteDatabase) {
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
                + ")")
        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_actor_origin ON " + TABLE_NAME + " ("
                + ORIGIN_ID + ", "
                + ACTOR_OID
                + ")")
        DbUtils.execSQL(db, "CREATE INDEX idx_actor_user ON " + TABLE_NAME + " ("
                + USER_ID
                + ")")
        DbUtils.execSQL(db, "CREATE INDEX idx_actor_webfinger ON " + TABLE_NAME + " ("
                + WEBFINGER_ID
                + ")")
    }
}
