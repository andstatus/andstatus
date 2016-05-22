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

import android.provider.BaseColumns;

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
    public static final String ACCOUNT_ID = UserTable.USER_ID;
    public static final String TIMELINE_TYPE = TimelineTable.TIMELINE_TYPE;
    /** Timeline here may have type but not ID for not persistent timelines */
    public static final String TIMELINE_ID = TimelineTable.TIMELINE_ID;
    public static final String IN_FOREGROUND = "in_foreground";
    public static final String MANUALLY_LAUNCHED = "manually_launched";
    public static final String ITEM_ID = "item_id";
    public static final String BODY = MsgTable.BODY;
    public static final String SEARCH_QUERY = "search_query";
    public static final String USERNAME = UserTable.USERNAME;

    // Result is below
    public static final String LAST_EXECUTED_DATE = "last_executed_date";
    public static final String EXECUTION_COUNT = "execution_count";
    public static final String RETRIES_LEFT = "retries_left";
    public static final String NUM_AUTH_EXCEPTIONS = "num_auth_exceptions";
    public static final String NUM_IO_EXCEPTIONS = "num_io_exceptions";
    public static final String NUM_PARSE_EXCEPTIONS = "num_parse_exceptions";
    public static final String ERROR_MESSAGE = "error_message";
    public static final String DOWNLOADED_COUNT = "downloaded_count";
    public static final String PROGRESS_TEXT = "progress_text";
}
