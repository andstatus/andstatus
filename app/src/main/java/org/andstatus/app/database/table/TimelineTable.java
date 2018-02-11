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

/**
 * Definition and configuration of timelines
 * @author yvolk@yurivolkov.com
 */
public class TimelineTable implements BaseColumns {
    public static final String TABLE_NAME = "timeline";

    private TimelineTable() {
        // Empty
    }

    /** Alias for #_ID */
    public static final String TIMELINE_ID = "timeline_id";
    public static final String TIMELINE_TYPE = "timeline_type";
    public static final String ACCOUNT_ID = ActorTable.ACCOUNT_ID;
    public static final String ACTOR_ID = ActorTable.ACTOR_ID;
    public static final String ACTOR_IN_TIMELINE = "actor_in_timeline";
    public static final String ORIGIN_ID =  OriginTable.ORIGIN_ID;
    public static final String SEARCH_QUERY = "search_query";

    /** If the timeline is synced automatically */
    public static final String IS_SYNCED_AUTOMATICALLY = "is_synced_automatically";
    /** If the timeline should be shown in a Timeline selector */
    public static final String DISPLAYED_IN_SELECTOR = "displayed_in_selector";
    /** Used for sorting timelines in a selector */
    public static final String SELECTOR_ORDER = "selector_order";

    /** When this timeline was last time successfully synced */
    public static final String SYNC_SUCCEEDED_DATE = "sync_succeeded_date";
    /** When last sync error occurred */
    public static final String SYNC_FAILED_DATE = "sync_failed_date";
    /** Error message at {@link #SYNC_FAILED_DATE} */
    public static final String ERROR_MESSAGE = "error_message";

    /** Number of successful sync operations: "Synced {@link #SYNCED_TIMES_COUNT} times" */
    public static final String SYNCED_TIMES_COUNT = "synced_times_count";
    /** Number of failed sync operations */
    public static final String SYNC_FAILED_TIMES_COUNT = "sync_failed_times_count";
    public static final String DOWNLOADED_ITEMS_COUNT = "downloaded_items_count";
    public static final String NEW_ITEMS_COUNT = "new_items_count";
    public static final String COUNT_SINCE = "count_since";

    /** Accumulated numbers for statistics. They are reset by a user's request */
    public static final String SYNCED_TIMES_COUNT_TOTAL = "synced_times_count_total";
    public static final String SYNC_FAILED_TIMES_COUNT_TOTAL = "sync_failed_times_count_total";
    public static final String DOWNLOADED_ITEMS_COUNT_TOTAL = "downloaded_items_count_total";
    public static final String NEW_ITEMS_COUNT_TOTAL = "new_items_count_total";

    /** Timeline position of the youngest ever downloaded message */
    public static final String YOUNGEST_POSITION = "youngest_position";
    /** Date of the item corresponding to the {@link #YOUNGEST_POSITION} */
    public static final String YOUNGEST_ITEM_DATE = "youngest_item_date";
    /** Last date when youngest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update */
    public static final String YOUNGEST_SYNCED_DATE = "youngest_synced_date";

    /** Timeline position of the oldest ever downloaded message */
    public static final String OLDEST_POSITION = "oldest_position";
    /** Date of the item corresponding to the {@link #OLDEST_POSITION} */
    public static final String OLDEST_ITEM_DATE = "oldest_item_date";
    /** Last date when oldest items of this timeline were successfully synced
     * (even if there were no new item at that time).
     * It may be used to calculate when it will be time for the next automatic update */
    public static final String OLDEST_SYNCED_DATE = "oldest_synced_date";

    /** Position of the timeline, which a User viewed  */
    public static final String VISIBLE_ITEM_ID = "visible_item_id";
    public static final String VISIBLE_Y = "visible_y";
    public static final String VISIBLE_OLDEST_DATE = "visible_oldest_date";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TIMELINE_TYPE + " TEXT NOT NULL,"
                + ACCOUNT_ID + " INTEGER NOT NULL DEFAULT 0,"
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
                + VISIBLE_OLDEST_DATE + " INTEGER NOT NULL DEFAULT 0"

                + ")");
    }
}
