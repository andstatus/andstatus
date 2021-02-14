/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data.converter;

import org.andstatus.app.data.DbUtils;

class Convert48 extends ConvertOneStep {
    Convert48() {
        versionTo = 50;
    }

    @Override
    protected void execute2() {
        progressLogger.logProgress(stepTitle + ": Adding summary and sensitive to Notes");
        sql = "ALTER TABLE note ADD COLUMN summary TEXT";
        DbUtils.execSQL(db, sql);
        sql = "ALTER TABLE note ADD COLUMN sensitive INTEGER DEFAULT 0";
        DbUtils.execSQL(db, sql);

        sql = "UPDATE note SET summary=note_name WHERE origin_id IN" +
                " (SELECT _id FROM origin WHERE origin_type_id=4)";  // Mastodon
        DbUtils.execSQL(db, sql);
        sql = "UPDATE note SET note_name=NULL" +
                " WHERE (length(note_name) > 0) AND" +
                " origin_id IN (SELECT _id FROM origin WHERE origin_type_id=4)";  // Mastodon
        DbUtils.execSQL(db, sql);
    }
}
