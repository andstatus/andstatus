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
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyLog;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;

public final class DbUtils {
    private static final int MS_BETWEEN_RETRIES = 500;
    private static final String TAG = DbUtils.class.getSimpleName();
    
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
                MyLog.v(method, "Error inserting row, table=" + tableName + "; pass=" + pass);
            } catch (NullPointerException e) {
                MyLog.i(method, "NullPointerException, table=" + tableName + "; pass=" + pass, e);
                break;
            } catch (SQLiteException e) {
                MyLog.i(method, "Database is locked, table=" + tableName + "; pass=" + pass, e);
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

    // Couldn't use "Closeable" as a Type due to incompatibility with API <= 10
    public static void closeSilently(Object closeable) {
        closeSilently(closeable, "");
    }
    
    public static void closeSilently(Object closeable, String message) {
        if (closeable != null) {
            try {
                closeLegacy(closeable, message);
            } catch ( IOException e) {
                MyLog.ignored(closeable, e);
            }
        }
    }
    
    private static void closeLegacy(Object closeable, String message) throws IOException {
        if (Closeable.class.isAssignableFrom(closeable.getClass()) ) {
            ((Closeable) closeable).close();
        } else if (Cursor.class.isAssignableFrom(closeable.getClass())) {
            if (!((Cursor) closeable).isClosed()) {
                ((Cursor) closeable).close();
            }
        } else if (FileChannel.class.isAssignableFrom(closeable.getClass())) {
            ((FileChannel) closeable).close();
        } else if (InputStream.class.isAssignableFrom(closeable.getClass())) {
            ((InputStream) closeable).close();
        } else if (Reader.class.isAssignableFrom(closeable.getClass())) {
            ((Reader) closeable).close();
        } else if (SQLiteStatement.class.isAssignableFrom(closeable.getClass())) {
            ((SQLiteStatement) closeable).close();
        } else if (OutputStream.class.isAssignableFrom(closeable.getClass())) {
            ((OutputStream) closeable).close();
        } else if (OutputStreamWriter.class.isAssignableFrom(closeable.getClass())) {
            ((OutputStreamWriter) closeable).close();
        } else if (Writer.class.isAssignableFrom(closeable.getClass())) {
            ((Writer) closeable).close();
        } else {
            String detailMessage = "Couldn't close silently an object of the class: "
                    + closeable.getClass().getCanonicalName() 
                    + (TextUtils.isEmpty(message) ? "" : "; " + message) ;
            MyLog.w(TAG, MyLog.getStackTrace(new IllegalArgumentException(detailMessage)));
        }
    }

    public static String getNotNullStringColumn(Cursor cursor, String columnName) {
        String value = "";
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            String value2 = cursor.getString(columnIndex);
            if (!TextUtils.isEmpty(value2)) {
                value = value2;
            }
        }
        return value;
    }
}
