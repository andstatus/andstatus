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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;

import androidx.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StopWatch;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.nio.channels.FileChannel;
import java.util.Random;
import java.util.function.Supplier;

import io.vavr.control.Try;

public final class DbUtils {
    private static final int MS_BETWEEN_RETRIES = 500;
    private static final String TAG = DbUtils.class.getSimpleName();
    
    private DbUtils() {
    }

    /**
     * @return rowId
     */
    public static Try<Long> addRowWithRetry(MyContext myContext, String tableName, ContentValues values, int nRetries) {
        String method = "addRowWithRetry";
        long rowId = -1;
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> method);
            return TryUtils.failure("Database is null");
        }
        Exception lastException = null;
        for (int pass = 0; pass < nRetries; pass++) {
            try {
                rowId = db.insert(tableName, null, values);
                if (rowId != -1) {
                    break;
                }
                MyLog.v(method, "Error inserting row, table=" + tableName + "; pass=" + pass);
            } catch (Exception e) {
                lastException = e;
                MyLog.i(method, "Exception, table=" + tableName + "; pass=" + pass, e);
                break;
            }
            waitBetweenRetries(method);
        }
        if (rowId == -1) {
            return TryUtils.failure("Failed to insert row into " + tableName + "; values=" + values.toString(), lastException);
        }
        return Try.success(rowId);
    }

    /**
     * @return Number of rows updated
     */
    public static Try<Void> updateRowWithRetry(MyContext myContext, String tableName, long rowId, ContentValues values, int nRetries) {
        String method = "updateRowWithRetry";
        int rowsUpdated = 0;
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.databaseIsNull(() -> method);
            return TryUtils.failure("Database is null");
        }
        for (int pass=0; pass<nRetries; pass++) {
            try {
                rowsUpdated = db.update(tableName, values, BaseColumns._ID + "=" + Long.toString(rowId), null);
                break;
            } catch (SQLiteException e) {
                MyLog.d(method, " Database is locked, pass=" + pass, e);
            }
            waitBetweenRetries(method);
        }
        if (rowsUpdated != 1) {
            String msgLog = "Failed to update rowId=" + rowId + " updated " + rowsUpdated + " rows";
            MyLog.e(method, msgLog, null);
            TryUtils.failure(msgLog);
        }
        return TryUtils.SUCCESS;
    }

    /** @return true if current thread was interrupted */
    public static boolean waitBetweenRetries(String method) {
        return waitMs(method, MS_BETWEEN_RETRIES);
    }

    /** @return true if current thread was interrupted
     * Starting with Android 7 this is constantly interrupted by Android system
     * */
    public static boolean waitMs(Object tag, int delayMs) {
        boolean wasInterrupted = false;
        if (delayMs > 1) {
            int delay = (delayMs/2) + new Random().nextInt(delayMs);
            StopWatch stopWatch = StopWatch.createStarted();
            while (stopWatch.getTime() < delay) {
                long remainingDelay = delay - stopWatch.getTime();
                if (remainingDelay < 2) break;
                try {
                    Thread.sleep(remainingDelay);
                } catch (InterruptedException e) {
                    if (!wasInterrupted) {
                        wasInterrupted = true;
                        MyLog.v(tag, () -> "Interrupted after waiting " + stopWatch.getTime() + " of " + delay + "ms");
                    }
                }
            }
        }
        return wasInterrupted;
    }

    // Couldn't use "Closeable" as a Type due to incompatibility with API <= 10
    public static void closeSilently(Object closeable) {
        closeSilently(closeable, "");
    }
    
    public static void closeSilently(Object closeable, String message) {
        if (closeable != null) {
            try {
                closeLegacy(closeable, message);
            } catch (Throwable e) {
                MyLog.ignored(closeable, e);
            }
        }
    }
    
    private static void closeLegacy(Object toClose, String message) throws IOException {
        if (toClose instanceof Closeable) {
            ((Closeable) toClose).close();
        } else if (toClose instanceof Cursor) {
            if (!((Cursor) toClose).isClosed()) {
                ((Cursor) toClose).close();
            }
        } else if (toClose instanceof FileChannel) {
            ((FileChannel) toClose).close();
        } else if (toClose instanceof InputStream) {
            ((InputStream) toClose).close();
        } else if (toClose instanceof Reader) {
            ((Reader) toClose).close();
        } else if (toClose instanceof SQLiteStatement) {
            ((SQLiteStatement) toClose).close();
        } else if (toClose instanceof OutputStream) {
            ((OutputStream) toClose).close();
        } else if (toClose instanceof OutputStreamWriter) {
            ((OutputStreamWriter) toClose).close();
        } else if (toClose instanceof Writer) {
            ((Writer) toClose).close();
        } else if (toClose instanceof HttpURLConnection) {
            ((HttpURLConnection) toClose).disconnect();
        } else {
            String detailMessage = "Couldn't close silently an object of the class: "
                    + toClose.getClass().getCanonicalName()
                    + (StringUtil.isEmpty(message) ? "" : "; " + message) ;
            MyLog.w(TAG, MyLog.getStackTrace(new IllegalArgumentException(detailMessage)));
        }
    }

    @NonNull
    public static String getString(Cursor cursor, String columnName, Supplier<String> ifEmpty) {
        String value = getString(cursor, columnName);
        return StringUtil.isEmpty(value) ? ifEmpty.get() : value;
    }

    @NonNull
    public static String getString(Cursor cursor, String columnName) {
        return cursor == null ? "" : getString(cursor, cursor.getColumnIndex(columnName));
    }

    @NonNull
    public static String getString(Cursor cursor, int columnIndex) {
        String value = "";
        if (cursor != null && columnIndex >= 0) {
            String value2 = cursor.getString(columnIndex);
            if (!StringUtil.isEmpty(value2)) {
                value = value2;
            }
        }
        return value;
    }

    public static TriState getTriState(Cursor cursor, String columnName) {
        return TriState.fromId(getInt(cursor, columnName));
    }

    public static boolean getBoolean(Cursor cursor, String columnName) {
        return getInt(cursor, columnName) == 1;
    }

    public static long getLong(Cursor cursor, String columnName) {
        if (cursor == null) {
            return 0;
        }
        long value = 0;
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            try {
                value = cursor.getLong(columnIndex);
            } catch (Exception e){
                MyLog.d(TAG, "getLong column " + columnName, e);
            }
        }
        return value;
    }

    public static int getInt(Cursor cursor, String columnName) {
        if (cursor == null) {
            return 0;
        }
        int value = 0;
        int columnIndex = cursor.getColumnIndex(columnName);
        if (columnIndex >= 0) {
            try {
                value = cursor.getInt(columnIndex);
            } catch (Exception e){
                MyLog.d(TAG, "getInt column " + columnName, e);
            }
        }
        return value;
    }

    public static void execSQL(SQLiteDatabase db, String sql) {
        MyLog.v("execSQL", () -> "sql = \"" + sql + "\";");
        db.execSQL(sql);
    }

    public static String sqlZeroToNull(long value) {
        return value == 0 ? null : Long.toString(value);
    }

    public static String sqlEmptyToNull(String value) {
        return StringUtil.isEmpty(value) ? null : "'" + value + "'";
    }
}
