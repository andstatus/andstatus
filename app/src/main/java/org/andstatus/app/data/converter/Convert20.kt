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

package org.andstatus.app.data.converter;

import org.andstatus.app.data.DbUtils;

class Convert20 extends ConvertOneStep {
    @Override
    protected void execute2() {
        versionTo = 21;

        sql = "ALTER TABLE origin ADD COLUMN mention_as_webfinger_id INTEGER DEFAULT 3";
        DbUtils.execSQL(db, sql);
        sql = "UPDATE origin SET mention_as_webfinger_id=3";
        DbUtils.execSQL(db, sql);
        sql = "CREATE INDEX idx_msg_in_reply_to_msg_id ON msg (in_reply_to_msg_id)";
        DbUtils.execSQL(db, sql);
    }
}
