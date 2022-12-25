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
package org.andstatus.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteStatement
import android.provider.BaseColumns
import io.vavr.control.Try
import org.andstatus.app.context.MyContext
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TriState
import org.andstatus.app.util.TryUtils
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.net.HttpURLConnection
import java.nio.channels.FileChannel
import java.util.*
import java.util.function.Supplier

object DbUtils {
    private const val MS_BETWEEN_RETRIES = 500
    private val TAG: String = DbUtils::class.simpleName!!

    /**
     * @return rowId
     */
    fun addRowWithRetry(myContext: MyContext, tableName: String?, values: ContentValues?, nRetries: Int): Try<Long> {
        val method = "addRowWithRetry"
        var rowId: Long = -1
        val db = myContext.database
        if (db == null) {
            MyLog.databaseIsNull { method }
            return TryUtils.failure("Database is null")
        }
        var lastException: Exception? = null
        for (pass in 0 until nRetries) {
            try {
                rowId = db.insert(tableName, null, values)
                if (rowId != -1L) {
                    break
                }
                MyLog.v(method, "Error inserting row, table=$tableName; pass=$pass")
            } catch (e: Exception) {
                lastException = e
                MyLog.i(method, "Exception, table=$tableName; pass=$pass", e)
                break
            }
            waitBetweenRetries(method)
        }
        return if (rowId == -1L) {
            TryUtils.failure("Failed to insert row into " + tableName + "; values=" + values.toString(), lastException)
        } else Try.success(rowId)
    }

    /**
     * @return Number of rows updated
     */
    fun updateRowWithRetry(
        myContext: MyContext,
        tableName: String?,
        rowId: Long,
        values: ContentValues?,
        nRetries: Int
    ): Try<Unit> {
        val method = "updateRowWithRetry"
        var rowsUpdated = 0
        val db = myContext.database
        if (db == null) {
            MyLog.databaseIsNull { method }
            return TryUtils.failure("Database is null")
        }
        for (pass in 0 until nRetries) {
            try {
                rowsUpdated = db.update(tableName, values, BaseColumns._ID + "=" + java.lang.Long.toString(rowId), null)
                break
            } catch (e: SQLiteException) {
                MyLog.d(method, " Database is locked, pass=$pass", e)
            }
            waitBetweenRetries(method)
        }
        if (rowsUpdated != 1) {
            val msgLog = "Failed to update rowId=$rowId updated $rowsUpdated rows"
            MyLog.e(method, msgLog, null)
            TryUtils.failure<Any?>(msgLog)
        }
        return TryUtils.SUCCESS
    }

    /** @return true if current thread was interrupted
     */
    fun waitBetweenRetries(method: String?): Boolean {
        return waitMs(method, MS_BETWEEN_RETRIES)
    }

    /** @return true if current thread was interrupted
     * Starting with Android 7 this is constantly interrupted by Android system
     */
    fun waitMs(tag: Any?, delayMs: Int): Boolean {
        var wasInterrupted = false
        if (delayMs > 1) {
            val delay = delayMs / 2 + Random().nextInt(delayMs)
            val stopWatch: StopWatch = StopWatch.createStarted()
            while (stopWatch.time < delay) {
                val remainingDelay = delay - stopWatch.time
                if (remainingDelay < 2) break
                try {
                    Thread.sleep(remainingDelay)
                } catch (e: InterruptedException) {
                    if (!wasInterrupted) {
                        wasInterrupted = true
                        MyLog.v(tag) { "Interrupted after waiting " + stopWatch.time + " of " + delay + "ms" }
                    }
                }
            }
        }
        return wasInterrupted
    }

    // Couldn't use "Closeable" as a Type due to incompatibility with API <= 10
    fun closeSilently(closeable: Any?, message: String? = "") {
        if (closeable != null) {
            try {
                closeLegacy(closeable, message)
            } catch (e: Throwable) {
                MyLog.ignored(closeable, e)
            }
        }
    }

    private fun closeLegacy(toClose: Any?, message: String?) {
        if (toClose is AutoCloseable) {
            try {
                toClose.close()
            } catch (e: Exception) {
                MyLog.e("Failed to close AutoClosable $message", e)
            }
        } else if (toClose is Closeable) {
            toClose.close()
        } else if (toClose is Cursor) {
            if (!toClose.isClosed()) {
                toClose.close()
            }
        } else if (toClose is FileChannel) {
            toClose.close()
        } else if (toClose is InputStream) {
            toClose.close()
        } else if (toClose is Reader) {
            toClose.close()
        } else if (toClose is SQLiteStatement) {
            toClose.close()
        } else if (toClose is OutputStream) {
            toClose.close()
        } else if (toClose is OutputStreamWriter) {
            toClose.close()
        } else if (toClose is Writer) {
            toClose.close()
        } else if (toClose is HttpURLConnection) {
            toClose.disconnect()
        } else {
            val detailMessage = ("Couldn't close silently an object of the class: "
                + toClose?.javaClass?.canonicalName
                + if (message.isNullOrEmpty()) "" else "; $message")
            MyLog.w(TAG, "$detailMessage \n${MyLog.currentStackTrace}")
        }
    }

    fun getString(cursor: Cursor?, columnName: String?, ifEmpty: Supplier<String>): String {
        val value = getString(cursor, columnName)
        return if (value.isEmpty()) ifEmpty.get() else value
    }

    fun getString(cursor: Cursor?, columnName: String?): String {
        return if (cursor == null) "" else getString(cursor, cursor.getColumnIndex(columnName))
    }

    fun getString(cursor: Cursor?, columnIndex: Int): String {
        var value = ""
        if (cursor != null && columnIndex >= 0) {
            val value2 = cursor.getString(columnIndex)
            if (!value2.isNullOrEmpty()) {
                value = value2
            }
        }
        return value
    }

    fun getTriState(cursor: Cursor?, columnName: String?): TriState {
        return TriState.fromId(getInt(cursor, columnName).toLong())
    }

    fun getBoolean(cursor: Cursor?, columnName: String?): Boolean {
        return getInt(cursor, columnName) == 1
    }

    fun getLong(cursor: Cursor?, columnName: String?): Long {
        if (cursor == null) {
            return 0
        }
        var value: Long = 0
        val columnIndex = cursor.getColumnIndex(columnName)
        if (columnIndex >= 0) {
            try {
                value = cursor.getLong(columnIndex)
            } catch (e: Exception) {
                MyLog.d(TAG, "getLong column $columnName", e)
            }
        }
        return value
    }

    fun getInt(cursor: Cursor?, columnName: String?): Int {
        if (cursor == null) {
            return 0
        }
        var value = 0
        val columnIndex = cursor.getColumnIndex(columnName)
        if (columnIndex >= 0) {
            try {
                value = cursor.getInt(columnIndex)
            } catch (e: Exception) {
                MyLog.d(TAG, "getInt column $columnName", e)
            }
        }
        return value
    }

    fun execSQL(db: SQLiteDatabase?, sql: String) {
        if (db == null) {
            MyLog.databaseIsNull { "execSQL, sql = \"$sql\";" }
        } else {
            MyLog.v("execSQL") { "sql = \"$sql\";" }
            db.execSQL(sql)
        }
    }

    fun sqlZeroToNull(value: Long): String? {
        return if (value == 0L) null else java.lang.Long.toString(value)
    }

    fun sqlEmptyToNull(value: String?): String? {
        return if (value.isNullOrEmpty()) null else "'$value'"
    }
}
