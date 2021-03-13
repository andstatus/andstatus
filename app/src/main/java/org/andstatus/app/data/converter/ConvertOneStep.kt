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

import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.data.DbUtils
import org.andstatus.app.util.MyLog

internal abstract class ConvertOneStep {
    var db: SQLiteDatabase? = null
    var oldVersion = 0
    var progressLogger: ProgressLogger = ProgressLogger.getEmpty("convert")
    var versionTo = 0
    var sql: String = ""
    private var lastError: String = "?"
    protected var stepTitle: String = ""

    fun execute(db: SQLiteDatabase, oldVersion: Int, progressLogger: ProgressLogger): Int {
        var ok = false
        this.db = db
        this.oldVersion = oldVersion
        this.progressLogger = progressLogger
        try {
            stepTitle = "Database upgrading step from version $oldVersion to version $versionTo"
            MyLog.i(this, stepTitle)
            execute2()
            ok = true
        } catch (e: Exception) {
            lastError = e.message ?: "(no error message)"
            MyLog.e(this, e)
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from "
                    + oldVersion + " to version " + versionTo)
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion
                    + " to version " + versionTo
                    + " SQL='" + sql + "'"
                    + " Error: " + lastError)
        }
        return if (ok) versionTo else oldVersion
    }

    protected abstract fun execute2()
    fun getLastError(): String {
        return lastError
    }

    fun dropOldIndex(indexName: String) {
        "DROP INDEX IF EXISTS $indexName".also {
            sql = it
            DbUtils.execSQL(db, it)
        }
    }

    fun dropOldTable(tableName: String) {
        "DROP TABLE IF EXISTS $tableName".also {
            sql = it
            DbUtils.execSQL(db, it)
        }
    }
}