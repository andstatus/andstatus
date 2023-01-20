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

/**
 * Definition and configuration of timelines
 * @author yvolk@yurivolkov.com
 */
object TimelineTable : BaseColumns {
    val TABLE_NAME: String = "timeline"

    /** Alias for #_ID  */
    val TIMELINE_ID: String = "timeline_id"
    val TIMELINE_TYPE: String = "timeline_type"
    val ACTOR_ID: String = ActorTable.ACTOR_ID
    val ACTOR_IN_TIMELINE: String = "actor_in_timeline"  // TODO: Delete it as unused
    val ORIGIN_ID: String = OriginTable.ORIGIN_ID
    val SEARCH_QUERY: String = "search_query"

    /** If the timeline is synced automatically  */
    val IS_SYNCED_AUTOMATICALLY: String = "is_synced_automatically"

    /** If the timeline should be shown in a Timeline selector  */
    val DISPLAYED_IN_SELECTOR: String = "displayed_in_selector"

    /** Used for sorting timelines in a selector  */
    val SELECTOR_ORDER: String = "selector_order"

    /** When this timeline was last time successfully synced  */
    val SYNC_SUCCEEDED_DATE: String = "sync_succeeded_date"

    /** When last sync error occurred  */
    val SYNC_FAILED_DATE: String = "sync_failed_date"

    /** Error message at [.SYNC_FAILED_DATE]  */
    val ERROR_MESSAGE: String = "error_message"

    /** Number of successful sync operations: "Synced [.SYNCED_TIMES_COUNT] times"  */
    val SYNCED_TIMES_COUNT: String = "synced_times_count"

    /** Number of failed sync operations  */
    val SYNC_FAILED_TIMES_COUNT: String = "sync_failed_times_count"
    val DOWNLOADED_ITEMS_COUNT: String = "downloaded_items_count"
    val NEW_ITEMS_COUNT: String = "new_items_count"
    val COUNT_SINCE: String = "count_since"

    /** Accumulated numbers for statistics. They are reset by a user's request  */
    val SYNCED_TIMES_COUNT_TOTAL: String = "synced_times_count_total"
    val SYNC_FAILED_TIMES_COUNT_TOTAL: String = "sync_failed_times_count_total"
    val DOWNLOADED_ITEMS_COUNT_TOTAL: String = "downloaded_items_count_total"
    val NEW_ITEMS_COUNT_TOTAL: String = "new_items_count_total"

    /** Timeline position of the youngest ever downloaded message  */
    val YOUNGEST_POSITION: String = "youngest_position"

    /** Date of the item corresponding to the [.YOUNGEST_POSITION]  */
    val YOUNGEST_ITEM_DATE: String = "youngest_item_date"

    /** Last date when youngest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update  */
    val YOUNGEST_SYNCED_DATE: String = "youngest_synced_date"

    /** Timeline position of the oldest ever downloaded message  */
    val OLDEST_POSITION: String = "oldest_position"

    /** Date of the item corresponding to the [.OLDEST_POSITION]  */
    val OLDEST_ITEM_DATE: String = "oldest_item_date"

    /** Last date when oldest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update  */
    val OLDEST_SYNCED_DATE: String = "oldest_synced_date"

    /** Position of the timeline, which a User viewed   */
    val VISIBLE_ITEM_ID: String = "visible_item_id"
    val VISIBLE_Y: String = "visible_y"
    val VISIBLE_OLDEST_DATE: String = "visible_oldest_date"
    val LAST_CHANGED_DATE: String = "last_changed_date"

    fun create(db: SQLiteDatabase) {
        DbUtils.execSQL(
            db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TIMELINE_TYPE + " TEXT NOT NULL,"
                + ACTOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + ACTOR_IN_TIMELINE + " TEXT,"
                + ORIGIN_ID + " INTEGER NOT NULL DEFAULT 0,"
                + SEARCH_QUERY + " TEXT,"
                + IS_SYNCED_AUTOMATICALLY + " BOOLEAN NOT NULL DEFAULT 0,"
                + DISPLAYED_IN_SELECTOR + " INTEGER NOT NULL DEFAULT 0,"
                + SELECTOR_ORDER + " INTEGER NOT NULL DEFAULT 0,"
                + SYNC_SUCCEEDED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + SYNC_FAILED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + ERROR_MESSAGE + " TEXT,"
                + SYNCED_TIMES_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + SYNC_FAILED_TIMES_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + DOWNLOADED_ITEMS_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + NEW_ITEMS_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + COUNT_SINCE + " INTEGER NOT NULL DEFAULT 0,"
                + SYNCED_TIMES_COUNT_TOTAL + " INTEGER NOT NULL DEFAULT 0,"
                + SYNC_FAILED_TIMES_COUNT_TOTAL + " INTEGER NOT NULL DEFAULT 0,"
                + DOWNLOADED_ITEMS_COUNT_TOTAL + " INTEGER NOT NULL DEFAULT 0,"
                + NEW_ITEMS_COUNT_TOTAL + " INTEGER NOT NULL DEFAULT 0,"
                + YOUNGEST_POSITION + " TEXT,"
                + YOUNGEST_ITEM_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + YOUNGEST_SYNCED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + OLDEST_POSITION + " TEXT,"
                + OLDEST_ITEM_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + OLDEST_SYNCED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + VISIBLE_ITEM_ID + " INTEGER NOT NULL DEFAULT 0,"
                + VISIBLE_Y + " INTEGER NOT NULL DEFAULT 0,"
                + VISIBLE_OLDEST_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + LAST_CHANGED_DATE + " INTEGER NOT NULL DEFAULT 0"
                + ")"
        )
    }
}
