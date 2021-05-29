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

internal class Convert14 : ConvertOneStep() {
    protected override fun execute2() {
        versionTo = 15
        sql = "ALTER TABLE msg ADD COLUMN public BOOLEAN DEFAULT 0 NOT NULL"
        DbUtils.execSQL(db, sql)
        sql = "UPDATE msg SET public=0"
        DbUtils.execSQL(db, sql)
    }
}
