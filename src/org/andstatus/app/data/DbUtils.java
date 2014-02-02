/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteException;
import android.provider.BaseColumns;

import org.andstatus.app.MyContextHolder;
import org.andstatus.app.util.MyLog;

public final class DbUtils {
    private static final int MS_BETWEEN_RETRIES = 500;
    
    private DbUtils() {
    }

    /**
     * @return rowId
     */
    public static long addRowWithRetry(String tableName, ContentValues values, int nRetries) {
        String method = "addRowWithRetry";
        long rowId = -1;
        for (int pass = 0; pass < nRetries; pass++) {
            try {
                rowId = MyContextHolder.get().getDatabase().getReadableDatabase()
                        .insert(tableName, null, values);
                if (rowId != -1) {
                    break;
                }
                MyLog.v(method, " Error inserting row, pass=" + pass);
            } catch (SQLiteException e) {
                MyLog.i(method, " Database is locked, pass=" + pass, e);
                rowId = -1;
            }
            waitBetweenRetries(method);
        }
        if (rowId == -1) {
            MyLog.e(method, "Failed to insert row into " + tableName + "; values=" + values.toString(), null);
        }
        return rowId;
    }

    /**
     * @return Number of rows updated
     */
    public static int updateRowWithRetry(String tableName, long rowId, ContentValues values, int nRetries) {
        String method = "updateRowWithRetry";
        int rowsUpdated = 0;
        for (int pass=0; pass<nRetries; pass++) {
            try {
                rowsUpdated = MyContextHolder.get().getDatabase().getReadableDatabase()
                        .update(tableName, values, BaseColumns._ID + "=" + Long.toString(rowId), null);
                break;
            } catch (SQLiteException e) {
                MyLog.i(method, " Database is locked, pass=" + pass, e);
            }
            waitBetweenRetries(method);
        }
        if (rowsUpdated != 1) {
            MyLog.e(method, " Failed to update rowId=" + rowId + " updated " + rowsUpdated + " rows", null);
        }
        return rowsUpdated;
    }

    private static void waitBetweenRetries(String method) {
        try {
            Thread.sleep(Math.round((Math.random() + 1) * MS_BETWEEN_RETRIES));
        } catch (InterruptedException e2) {
            MyLog.e(method, e2);
        }
    }

}
