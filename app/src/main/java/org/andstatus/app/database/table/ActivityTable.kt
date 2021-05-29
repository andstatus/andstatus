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
package org.andstatus.app.database.table

import android.database.sqlite.SQLiteDatabase
import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.social.AActivity
import org.andstatus.app.net.social.ActivityType
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.timeline.meta.TimelineType

/** The table holds [AActivity]  */
object ActivityTable : BaseColumns {
    val TABLE_NAME: String = "activity"
    val ORIGIN_ID: String = "activity_" + OriginTable.ORIGIN_ID

    /** id in a Social Network [OriginTable]  */
    val ACTIVITY_OID: String = "activity_oid"
    val ACCOUNT_ID: String = "account_id"

    /** ID of [ActivityType]  */
    val ACTIVITY_TYPE: String = "activity_type"
    val ACTOR_ID: String = "activity_" + ActorTable.ACTOR_ID

    /** Note as Object  */
    val NOTE_ID: String = "activity_" + NoteTable.NOTE_ID

    /** Actor as Object  */
    val OBJ_ACTOR_ID: String = "obj_" + ActorTable.ACTOR_ID

    /** Inner Activity as Object  */
    val OBJ_ACTIVITY_ID: String = "obj_activity_id"

    /** [.ACCOUNT_ID] is subscribed to this action or was a "Secondary target audience"  */
    val SUBSCRIBED: String = "subscribed"

    /** [.NOTIFIED_ACTOR_ID] is interacted  */
    val INTERACTED: String = "interacted"
    val INTERACTION_EVENT: String = "interaction_event"

    /** [.NOTIFIED_ACTOR_ID] should be notified of this action  */
    val NOTIFIED: String = "notified"
    val NOTIFIED_ACTOR_ID: String = "notified_actor_id"

    /** [NotificationEventType], it is not 0 if the notification is active  */
    val NEW_NOTIFICATION_EVENT: String = "new_notification_event"
    val UPDATED_DATE: String = "activity_updated_date"

    /** Date and time when this Activity was first loaded into this database
     * or if it was not loaded yet, when the row was inserted into this database  */
    val INS_DATE: String = "activity_ins_date"

    // Aliases
    val ACTIVITY_ID: String = "activity_id"
    val AUTHOR_ID: String = "author_id"
    val LAST_UPDATE_ID: String = "last_update_id"
    fun getTimelineSortOrder(timelineType: TimelineType, ascending: Boolean): String {
        return getTimeSortField(timelineType) + if (ascending) " ASC" else " DESC"
    }

    fun getTimeSortField(timelineType: TimelineType): String {
        return if (timelineType == TimelineType.UNREAD_NOTIFICATIONS) INS_DATE else UPDATED_DATE
    }

    fun create(db: SQLiteDatabase) {
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
                + ")")
        DbUtils.execSQL(db, "CREATE UNIQUE INDEX idx_activity_origin ON " + TABLE_NAME + " ("
                + ORIGIN_ID + ", "
                + ACTIVITY_OID
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_message ON " + TABLE_NAME + " ("
                + NOTE_ID
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_obj_actor ON " + TABLE_NAME + " ("
                + OBJ_ACTOR_ID
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_activity ON " + TABLE_NAME + " ("
                + OBJ_ACTIVITY_ID
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_timeline ON " + TABLE_NAME + " ("
                + UPDATED_DATE
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_actor_timeline ON " + TABLE_NAME + " ("
                + ACTOR_ID + ", "
                + UPDATED_DATE
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_subscribed_timeline ON " + TABLE_NAME + " ("
                + SUBSCRIBED + ", "
                + UPDATED_DATE
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_notified_timeline ON " + TABLE_NAME + " ("
                + NOTIFIED + ", "
                + UPDATED_DATE
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_notified_actor ON " + TABLE_NAME + " ("
                + NOTIFIED + ", "
                + NOTIFIED_ACTOR_ID
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_new_notification ON " + TABLE_NAME + " ("
                + NEW_NOTIFICATION_EVENT
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_interacted_timeline ON " + TABLE_NAME + " ("
                + INTERACTED + ", "
                + UPDATED_DATE
                + ")"
        )
        DbUtils.execSQL(db, "CREATE INDEX idx_activity_interacted_actor ON " + TABLE_NAME + " ("
                + INTERACTED + ", "
                + NOTIFIED_ACTOR_ID
                + ")"
        )
    }
}
