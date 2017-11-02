/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.support.annotation.NonNull;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.timeline.meta.TimelineType;

/** The table holds {@link org.andstatus.app.net.social.MbActivity} */
public final class ActivityTable implements BaseColumns {
    public static final String TABLE_NAME = "activity";

    public static final String ORIGIN_ID =  "activity_" + OriginTable.ORIGIN_ID;
    /** id in a Social Network {@link OriginTable} */
    public static final String ACTIVITY_OID = "activity_oid";
    public static final String ACCOUNT_ID = "account_id";
    /** ID of {@link org.andstatus.app.net.social.MbActivityType} */
    public static final String ACTIVITY_TYPE = "activity_type";
    public static final String ACTOR_ID = "actor_id";
    /** Message as Object */
    public static final String MSG_ID = "activity_" + MsgTable.MSG_ID;
    /** Us as Object */
    public static final String USER_ID = "activity_" + UserTable.USER_ID;
    /** Inner Activity as Object */
    public static final String OBJ_ACTIVITY_ID = "obj_activity_id";

    /** {@link #ACCOUNT_ID} is subscribed to this action or was a "Secondary target audience" */
    public static final String SUBSCRIBED = "subscribed";
    /** {@link #ACCOUNT_ID} should be notified of this action */
    public static final String NOTIFIED = "notified";

    public static final String UPDATED_DATE = "activity_updated_date";
    /**
     * Date and time the row was inserted into this database
     */
    public static final String INS_DATE = "activity_ins_date";

    // Aliases
    public static final String ACTIVITY_ID = "activity_id";
    public static final String AUTHOR_ID = "author_id";
    public static final String LAST_UPDATE_ID = "last_update_id";

    public static String getTimeSortOrder(TimelineType timelineType, boolean ascending) {
        return getTimeSortField(timelineType) + (ascending ? " ASC" : " DESC");
    }

    public static String getTimeSortField(@NonNull TimelineType timelineType) {
        return timelineType.showsActivities() ? UPDATED_DATE : MsgTable.UPDATED_DATE;
    }

    private ActivityTable() {
        // Empty
    }

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + ORIGIN_ID + " INTEGER NOT NULL,"
                + ACTIVITY_OID + " TEXT NOT NULL,"
                + ACCOUNT_ID + " INTEGER NOT NULL,"
                + ACTIVITY_TYPE + " INTEGER NOT NULL,"
                + ACTOR_ID + " INTEGER,"
                + MSG_ID + " INTEGER,"
                + USER_ID + " INTEGER,"
                + OBJ_ACTIVITY_ID + " INTEGER,"
                + SUBSCRIBED + " INTEGER DEFAULT 0 NOT NULL,"
                + NOTIFIED + " INTEGER DEFAULT 0 NOT NULL,"
                + UPDATED_DATE + " INTEGER,"
                + INS_DATE + " INTEGER NOT NULL"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_activity_origin ON " + TABLE_NAME + " ("
                    + ORIGIN_ID + ", "
                    + ACTIVITY_OID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_message ON " + TABLE_NAME + " ("
                    + MSG_ID
                + ")"
                + " WHERE " + MSG_ID + " IS NOT NULL"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_user ON " + TABLE_NAME + " ("
                + USER_ID
                + ")"
                + " WHERE " + USER_ID + " IS NOT NULL"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_activity ON " + TABLE_NAME + " ("
                + OBJ_ACTIVITY_ID
                + ")"
                + " WHERE " + OBJ_ACTIVITY_ID + " IS NOT NULL"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_ins_date ON " + TABLE_NAME + " ("
                + INS_DATE
                + ")"
        );
    }
}
