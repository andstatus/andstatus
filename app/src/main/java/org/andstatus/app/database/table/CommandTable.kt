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
 * Command queues
 * @author yvolk@yurivolkov.com
 */
object CommandTable : BaseColumns {
    val TABLE_NAME: String? = "command"
    val QUEUE_TYPE: String? = "queue_type"
    val COMMAND_CODE: String? = "command_code"
    val CREATED_DATE: String? = "command_created_date"
    val IN_FOREGROUND: String? = "in_foreground"
    val MANUALLY_LAUNCHED: String? = "manually_launched"
    val DESCRIPTION: String? = "command_description"

    /** Timeline attributes
     * Timeline here may have ID=0 for non-persistent timelines  */
    val TIMELINE_ID: String? = TimelineTable.TIMELINE_ID
    val TIMELINE_TYPE: String? = TimelineTable.TIMELINE_TYPE
    val ACCOUNT_ID: String? = ActorTable.ACCOUNT_ID
    val ACTOR_ID: String? = TimelineTable.ACTOR_ID

    /** This is used e.g. when a [.ACTOR_ID] is not known  */
    val USERNAME: String? = ActorTable.USERNAME
    val ORIGIN_ID: String? = TimelineTable.ORIGIN_ID
    val SEARCH_QUERY: String? = TimelineTable.SEARCH_QUERY

    /** This is MessageId mostly, but not only...  */
    val ITEM_ID: String? = "item_id"

    // Command execution result is below
    val LAST_EXECUTED_DATE: String? = "last_executed_date"
    val EXECUTION_COUNT: String? = "execution_count"
    val RETRIES_LEFT: String? = "retries_left"
    val NUM_AUTH_EXCEPTIONS: String? = "num_auth_exceptions"
    val NUM_IO_EXCEPTIONS: String? = "num_io_exceptions"
    val NUM_PARSE_EXCEPTIONS: String? = "num_parse_exceptions"
    val ERROR_MESSAGE: String? = "error_message"
    val DOWNLOADED_COUNT: String? = "downloaded_count"
    val PROGRESS_TEXT: String? = "progress_text"
    fun create(db: SQLiteDatabase?) {
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
                + ")")
    }
}