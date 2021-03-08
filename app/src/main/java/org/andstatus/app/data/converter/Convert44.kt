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
package org.andstatus.app.data.converter

import android.database.Cursor
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import java.util.*
import java.util.function.Consumer
import java.util.function.Function

internal class Convert44 : ConvertOneStep() {
    private class Data private constructor(val id: Long, val username: String) {
        companion object {
            fun fromCursor(cursor: Cursor?): Optional<Data> {
                val username = DbUtils.getString(cursor, "username")
                val index = if (username.isNullOrEmpty()) -1 else username.indexOf("@")
                return if (index > 0) Optional.of(Data(DbUtils.getLong(cursor, "_id"), username.substring(0, index))) else Optional.empty()
            }
        }
    }

    override fun execute2() {
        progressLogger.logProgress("$stepTitle: Transforming Pump.io actors")
        sql = "SELECT actor._id, username FROM actor INNER JOIN origin on actor.origin_id=origin._id" +
                " WHERE origin.origin_type_id=2"
        MyQuery.foldLeft(db, sql, HashSet(), { set: MutableSet<Data?>? ->
            Function { cursor: Cursor? ->
                Data.fromCursor(cursor).ifPresent(Consumer { e: Data? -> set.add(e) })
                set
            }
        }).forEach(Consumer { data: Data? ->
            DbUtils.execSQL(db, "UPDATE actor SET username='" + data.username + "'" +
                    " WHERE _id=" + data.id)
        }
        )
        progressLogger.logProgress("$stepTitle: Adding previews to downloads")
        sql = "ALTER TABLE download ADD COLUMN preview_of_download_id INTEGER"
        DbUtils.execSQL(db, sql)
    }

    init {
        versionTo = 47
    }
}