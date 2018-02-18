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
 * Command queues
 * @author yvolk@yurivolkov.com
 */
public final class CommandTable implements BaseColumns {
    public static final String TABLE_NAME = "command";

    private CommandTable() {
        // Empty
    }

    public static final String QUEUE_TYPE = "queue_type";

    public static final String COMMAND_CODE = "command_code";
    public static final String CREATED_DATE = "command_created_date";
    public static final String IN_FOREGROUND = "in_foreground";
    public static final String MANUALLY_LAUNCHED = "manually_launched";
    public static final String DESCRIPTION = "command_description";

    /** Timeline attributes
     * Timeline here may have ID=0 for non-persistent timelines */
    public static final String TIMELINE_ID = TimelineTable.TIMELINE_ID;
    public static final String TIMELINE_TYPE = TimelineTable.TIMELINE_TYPE;
    public static final String ACCOUNT_ID = ActorTable.ACCOUNT_ID;
    public static final String ACTOR_ID = TimelineTable.ACTOR_ID;
    /** This is used e.g. when a {@link #ACTOR_ID} is not known */
    public static final String USERNAME = ActorTable.USERNAME;
    public static final String ORIGIN_ID = TimelineTable.ORIGIN_ID;
    public static final String SEARCH_QUERY = TimelineTable.SEARCH_QUERY;

    /** This is MessageId mostly, but not only... */
    public static final String ITEM_ID = "item_id";

    // Command execution result is below
    public static final String LAST_EXECUTED_DATE = "last_executed_date";
    public static final String EXECUTION_COUNT = "execution_count";
    public static final String RETRIES_LEFT = "retries_left";
    public static final String NUM_AUTH_EXCEPTIONS = "num_auth_exceptions";
    public static final String NUM_IO_EXCEPTIONS = "num_io_exceptions";
    public static final String NUM_PARSE_EXCEPTIONS = "num_parse_exceptions";
    public static final String ERROR_MESSAGE = "error_message";
    public static final String DOWNLOADED_COUNT = "downloaded_count";
    public static final String PROGRESS_TEXT = "progress_text";

    public static void create(SQLiteDatabase db) {
        DbUtils.execSQL(db, "CREATE TABLE " + TABLE_NAME + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY NOT NULL,"
                + QUEUE_TYPE + " TEXT NOT NULL,"
                + COMMAND_CODE + " TEXT NOT NULL,"
                + CREATED_DATE + " INTEGER NOT NULL,"
                + DESCRIPTION + " TEXT,"
                + IN_FOREGROUND + " BOOLEAN NOT NULL DEFAULT 0,"
                + MANUALLY_LAUNCHED + " BOOLEAN NOT NULL DEFAULT 0,"

                + TIMELINE_ID + " INTEGER NOT NULL DEFAULT 0,"
                + TIMELINE_TYPE + " TEXT NOT NULL,"
                + ACCOUNT_ID + " INTEGER NOT NULL DEFAULT 0,"
                + ACTOR_ID + " INTEGER NOT NULL DEFAULT 0,"
                + ORIGIN_ID + " INTEGER NOT NULL DEFAULT 0,"
                + SEARCH_QUERY + " TEXT,"

                + ITEM_ID + " INTEGER NOT NULL DEFAULT 0,"
                + USERNAME + " TEXT,"

                + LAST_EXECUTED_DATE + " INTEGER NOT NULL DEFAULT 0,"
                + EXECUTION_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + RETRIES_LEFT + " INTEGER NOT NULL DEFAULT 0,"
                + NUM_AUTH_EXCEPTIONS + " INTEGER NOT NULL DEFAULT 0,"
                + NUM_IO_EXCEPTIONS + " INTEGER NOT NULL DEFAULT 0,"
                + NUM_PARSE_EXCEPTIONS + " INTEGER NOT NULL DEFAULT 0,"
                + ERROR_MESSAGE + " TEXT,"
                + DOWNLOADED_COUNT + " INTEGER NOT NULL DEFAULT 0,"
                + PROGRESS_TEXT + " TEXT"
                + ")");
    }
}
