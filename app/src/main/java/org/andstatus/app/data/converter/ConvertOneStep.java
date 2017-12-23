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

import android.database.sqlite.SQLiteDatabase;

import org.andstatus.app.backup.ProgressLogger;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.MyLog;

abstract class ConvertOneStep {
    SQLiteDatabase db;
    int oldVersion;
    ProgressLogger progressLogger;
    int versionTo;
    String sql = "";
    protected String lastError = "?";
    protected String stepTitle = "";

    int execute(SQLiteDatabase db, int oldVersion, ProgressLogger progressLogger) {
        boolean ok = false;
        this.db = db;
        this.oldVersion = oldVersion;
        this.progressLogger = progressLogger;
        try {
            stepTitle = "Database upgrading step from version " + oldVersion + " to version " + versionTo;
            MyLog.i(this, stepTitle);
            execute2();
            ok = true;
        } catch (Exception e) {
            lastError = e.getMessage();
            MyLog.e(this, e);
        }
        if (ok) {
            MyLog.i(this, "Database upgrading step successfully upgraded database from "
                    + oldVersion + " to version " + versionTo);
        } else {
            MyLog.e(this, "Database upgrading step failed to upgrade database from " + oldVersion
                    + " to version " + versionTo
                    + " SQL='" + sql + "'"
                    + " Error: " + lastError);
        }
        return ok ? versionTo : oldVersion;
    }

    protected abstract void execute2();

    public String getLastError() {
        return lastError;
    }

    void dropOldTable(String tableName) {
        sql = "DROP TABLE IF EXISTS " + tableName;
        DbUtils.execSQL(db, sql);
    }
}
