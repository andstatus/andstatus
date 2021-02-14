/*
 * Copyright (c) 2017-2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.notification.NotificationEventType;
import org.andstatus.app.timeline.meta.TimelineType;

/** The table holds {@link AActivity} */
public final class ActivityTable implements BaseColumns {
    public static final String TABLE_NAME = "activity";

    public static final String ORIGIN_ID =  "activity_" + OriginTable.ORIGIN_ID;
    /** id in a Social Network {@link OriginTable} */
    public static final String ACTIVITY_OID = "activity_oid";
    public static final String ACCOUNT_ID = "account_id";
    /** ID of {@link ActivityType} */
    public static final String ACTIVITY_TYPE = "activity_type";
    public static final String ACTOR_ID = "activity_" + ActorTable.ACTOR_ID;
    /** Note as Object */
    public static final String NOTE_ID = "activity_" + NoteTable.NOTE_ID;
    /** Actor as Object */
    public static final String OBJ_ACTOR_ID = "obj_" + ActorTable.ACTOR_ID;
    /** Inner Activity as Object */
    public static final String OBJ_ACTIVITY_ID = "obj_activity_id";

    /** {@link #ACCOUNT_ID} is subscribed to this action or was a "Secondary target audience" */
    public static final String SUBSCRIBED = "subscribed";
    /** {@link #NOTIFIED_ACTOR_ID} is interacted **/
    public static final String INTERACTED = "interacted";
    public static final String INTERACTION_EVENT = "interaction_event";
    /** {@link #NOTIFIED_ACTOR_ID} should be notified of this action */
    public static final String NOTIFIED = "notified";
    public static final String NOTIFIED_ACTOR_ID = "notified_actor_id";
    /** {@link NotificationEventType}, it is not 0 if the notification is active */
    public static final String NEW_NOTIFICATION_EVENT = "new_notification_event";

    public static final String UPDATED_DATE = "activity_updated_date";
    /** Date and time when this Activity was first loaded into this database
     * or if it was not loaded yet, when the row was inserted into this database */
    public static final String INS_DATE = "activity_ins_date";

    // Aliases
    public static final String ACTIVITY_ID = "activity_id";
    public static final String AUTHOR_ID = "author_id";
    public static final String LAST_UPDATE_ID = "last_update_id";

    public static String getTimelineSortOrder(TimelineType timelineType, boolean ascending) {
        return getTimeSortField(timelineType) + (ascending ? " ASC" : " DESC");
    }

    public static String getTimeSortField(@NonNull TimelineType timelineType) {
        return timelineType == TimelineType.UNREAD_NOTIFICATIONS
                ? INS_DATE
                : UPDATED_DATE;
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
                + ACTOR_ID + " INTEGER NOT NULL,"
                + NOTE_ID + " INTEGER NOT NULL,"
                + OBJ_ACTOR_ID + " INTEGER NOT NULL,"
                + OBJ_ACTIVITY_ID + " INTEGER NOT NULL,"
                + SUBSCRIBED + " INTEGER NOT NULL DEFAULT 0,"
                + INTERACTED + " INTEGER NOT NULL DEFAULT 0,"
                + INTERACTION_EVENT + " INTEGER NOT NULL DEFAULT 0,"
                + NOTIFIED + " INTEGER NOT NULL DEFAULT 0,"
                + NOTIFIED_ACTOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + NEW_NOTIFICATION_EVENT + " INTEGER NOT NULL DEFAULT 0,"
                + INS_DATE + " INTEGER NOT NULL,"
                + UPDATED_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")");

        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_activity_origin ON " + TABLE_NAME + " ("
                    + ORIGIN_ID + ", "
                    + ACTIVITY_OID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_message ON " + TABLE_NAME + " ("
                + NOTE_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_obj_actor ON " + TABLE_NAME + " ("
                + OBJ_ACTOR_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_activity ON " + TABLE_NAME + " ("
                + OBJ_ACTIVITY_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_timeline ON " + TABLE_NAME + " ("
                + UPDATED_DATE
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_actor_timeline ON " + TABLE_NAME + " ("
                + ACTOR_ID + ", "
                + UPDATED_DATE
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_subscribed_timeline ON " + TABLE_NAME + " ("
                + SUBSCRIBED + ", "
                + UPDATED_DATE
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_notified_timeline ON " + TABLE_NAME + " ("
                + NOTIFIED + ", "
                + UPDATED_DATE
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_notified_actor ON " + TABLE_NAME + " ("
                + NOTIFIED + ", "
                + NOTIFIED_ACTOR_ID
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_new_notification ON " + TABLE_NAME + " ("
                + NEW_NOTIFICATION_EVENT
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_interacted_timeline ON " + TABLE_NAME + " ("
                + INTERACTED + ", "
                + UPDATED_DATE
                + ")"
        );

        DbUtils.execSQL(db, "CREATE INDEX idx_activity_interacted_actor ON " + TABLE_NAME + " ("
                + INTERACTED + ", "
                + NOTIFIED_ACTOR_ID
                + ")"
        );

    }
}
