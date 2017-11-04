/*
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.database.converter;

import org.andstatus.app.data.DbUtils;

class Convert24 extends ConvertOneStep {
    @Override
    protected void execute2() {
        versionTo = 25;

        sql = "CREATE TABLE timeline (_id INTEGER PRIMARY KEY AUTOINCREMENT,timeline_type STRING NOT NULL,account_id INTEGER,user_id INTEGER,user_in_timeline TEXT,origin_id INTEGER,search_query TEXT,is_synced_automatically BOOLEAN DEFAULT 0 NOT NULL,displayed_in_selector INTEGER DEFAULT 0 NOT NULL,selector_order INTEGER DEFAULT 0 NOT NULL,sync_succeeded_date INTEGER,sync_failed_date INTEGER,error_message TEXT,synced_times_count INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count INTEGER DEFAULT 0 NOT NULL,downloaded_items_count INTEGER DEFAULT 0 NOT NULL,new_items_count INTEGER DEFAULT 0 NOT NULL,count_since INTEGER,synced_times_count_total INTEGER DEFAULT 0 NOT NULL,sync_failed_times_count_total INTEGER DEFAULT 0 NOT NULL,downloaded_items_count_total INTEGER DEFAULT 0 NOT NULL,new_items_count_total INTEGER DEFAULT 0 NOT NULL,youngest_position TEXT,youngest_item_date INTEGER,youngest_synced_date INTEGER,oldest_position TEXT,oldest_item_date INTEGER,oldest_synced_date INTEGER,visible_item_id INTEGER,visible_y INTEGER,visible_oldest_date INTEGER)";
        DbUtils.execSQL(db, sql);

        sql = "CREATE TABLE command (_id INTEGER PRIMARY KEY NOT NULL,queue_type TEXT NOT NULL,command_code TEXT NOT NULL,command_created_date INTEGER NOT NULL,command_description TEXT,in_foreground BOOLEAN DEFAULT 0 NOT NULL,manually_launched BOOLEAN DEFAULT 0 NOT NULL,timeline_id INTEGER,timeline_type STRING,account_id INTEGER,user_id INTEGER,origin_id INTEGER,search_query TEXT,item_id INTEGER,username TEXT,last_executed_date INTEGER,execution_count INTEGER DEFAULT 0 NOT NULL,retries_left INTEGER DEFAULT 0 NOT NULL,num_auth_exceptions INTEGER DEFAULT 0 NOT NULL,num_io_exceptions INTEGER DEFAULT 0 NOT NULL,num_parse_exceptions INTEGER DEFAULT 0 NOT NULL,error_message TEXT,downloaded_count INTEGER DEFAULT 0 NOT NULL,progress_text TEXT)";
        DbUtils.execSQL(db, sql);
    }
}
