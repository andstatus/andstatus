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
    public static final String TIMELINE_NAME = "timeline_name";
    public static final String TIMELINE_DESCRIPTION = "timeline_description";
    public static final String TIMELINE_TYPE = "timeline_type";
    public static final String ACCOUNT_ID = UserTable.ACCOUNT_ID;
    public static final String USER_ID = UserTable.USER_ID;
    public static final String ORIGIN_ID =  OriginTable.ORIGIN_ID;
    public static final String SEARCH_QUERY = "search_query";

    /** If the timeline is synced automatically */
    public static final String SYNCED = "synced";
    /** If the timeline should be shown in a Timeline selector */
    public static final String DISPLAY_IN_SELECTOR = "display_in_selector";
    /** Used for sorting timelines in a selector */
    public static final String SELECTOR_ORDER = "selector_order";

    /** When this timeline was last time successfully synced */
    public static final String SYNCED_DATE = "synced_date";
    /** When last sync error occurred */
    public static final String SYNC_FAILED_DATE = "sync_failed_date";
    /** Error message at {@link #SYNC_FAILED_DATE} */
    public static final String ERROR_MESSAGE = "error_message";

    /** Number of successful sync operations: "Synced {@link #SYNCED_TIMES_COUNT} times" */
    public static final String SYNCED_TIMES_COUNT = "synced_times_count";
    /** Number of failed sync operations */
    public static final String SYNC_FAILED_TIMES_COUNT = "sync_failed_times_count";
    public static final String NEW_ITEMS_COUNT = "new_items_count";
    public static final String COUNT_SINCE = "count_since";

    /** Accumulated numbers for statistics. They are reset by a user's request */
    public static final String SYNCED_TIMES_COUNT_TOTAL = "synced_times_count_total";
    public static final String SYNC_FAILED_TIMES_COUNT_TOTAL = "sync_failed_times_count_total";
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

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TimelineTable.TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + TimelineTable.TIMELINE_NAME + " TEXT,"
                + TimelineTable.TIMELINE_DESCRIPTION + " TEXT,"
                + TimelineTable.TIMELINE_TYPE + " STRING NOT NULL,"
                + TimelineTable.ACCOUNT_ID + " INTEGER,"
                + TimelineTable.USER_ID + " INTEGER,"
                + TimelineTable.ORIGIN_ID + " INTEGER,"
                + TimelineTable.SEARCH_QUERY + " TEXT,"

                + TimelineTable.SYNCED + " BOOLEAN DEFAULT 1 NOT NULL,"
                + TimelineTable.DISPLAY_IN_SELECTOR + " BOOLEAN DEFAULT 1 NOT NULL,"
                + TimelineTable.SELECTOR_ORDER + " INTEGER DEFAULT 1 NOT NULL,"

                + TimelineTable.SYNCED_DATE + " INTEGER,"
                + TimelineTable.SYNC_FAILED_DATE + " INTEGER,"
                + TimelineTable.ERROR_MESSAGE + " TEXT,"

                + TimelineTable.SYNCED_TIMES_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + TimelineTable.SYNC_FAILED_TIMES_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + TimelineTable.NEW_ITEMS_COUNT + " INTEGER DEFAULT 0 NOT NULL,"
                + TimelineTable.COUNT_SINCE + " INTEGER,"

                + TimelineTable.SYNCED_TIMES_COUNT_TOTAL + " INTEGER DEFAULT 0 NOT NULL,"
                + TimelineTable.SYNC_FAILED_TIMES_COUNT_TOTAL + " INTEGER DEFAULT 0 NOT NULL,"
                + TimelineTable.NEW_ITEMS_COUNT_TOTAL + " INTEGER DEFAULT 0 NOT NULL,"

                + TimelineTable.YOUNGEST_POSITION + " TEXT,"
                + TimelineTable.YOUNGEST_ITEM_DATE + " INTEGER,"
                + TimelineTable.YOUNGEST_SYNCED_DATE + " INTEGER,"

                + TimelineTable.OLDEST_POSITION + " TEXT,"
                + TimelineTable.OLDEST_ITEM_DATE + " INTEGER,"
                + TimelineTable.OLDEST_SYNCED_DATE + " INTEGER"
                + ")");
    }
}
