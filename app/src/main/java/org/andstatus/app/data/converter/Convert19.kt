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
package org.andstatus.app.data.converter

import org.andstatus.app.data.DbUtils

internal class Convert19 : ConvertOneStep() {
    protected override fun execute2() {
        versionTo = 20
        sql = "ALTER TABLE origin ADD COLUMN ssl_mode INTEGER DEFAULT 1"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE origin ADD COLUMN in_combined_global_search BOOLEAN DEFAULT 1"
        DbUtils.execSQL(db, sql)
        sql = "ALTER TABLE origin ADD COLUMN in_combined_public_reload BOOLEAN DEFAULT 1"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE origin SET ssl_mode=1, in_combined_global_search=1, in_combined_public_reload=1"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE origin SET ssl_mode=2 WHERE origin_url LIKE '%quitter.zone%'"
        DbUtils.execSQL(db, sql)
    }
}
