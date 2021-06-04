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
package org.andstatus.app.data.checker

import android.database.Cursor
import android.provider.BaseColumns
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.DownloadTable
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.function.Function

internal class CheckDownloads : DataChecker() {
    private class Results {
        var totalCount: Long = 0
        var toFix: MutableList<Result> = ArrayList()
    }

    private class Result(val downloadId: Long)

    override fun fixInternal(): Long {
        val results = getResults()
        if (countOnly) return results.toFix.size.toLong()
        logger.logProgress("Marking " + getSomeOfTotal(results.toFix.size.toLong(), results.totalCount) + " downloads as absent")
        val fixedCount = AtomicLong()
        results.toFix.forEach(Consumer { result: Result ->
            val sql = "UPDATE " + DownloadTable.TABLE_NAME +
                    " SET " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.ABSENT.save() +
                    ", " + DownloadTable.FILE_NAME + "=null" +
                    ", " + DownloadTable.FILE_SIZE + "=0" +
                    " WHERE " + BaseColumns._ID + "=" + result.downloadId
            myContext.database?.execSQL(sql)
            fixedCount.incrementAndGet()
        })
        return fixedCount.get()
    }

    private fun getResults(): Results {
        val sql = ("SELECT *"
                + " FROM " + DownloadTable.TABLE_NAME
                + " WHERE " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.LOADED.save())
        return MyQuery.foldLeft(myContext, sql, Results(), { results: Results ->
            Function { cursor: Cursor ->
                if (!logger.isCancelled) {
                    val dd: DownloadData = DownloadData.fromCursor(cursor)
                    results.totalCount++
                    if (!dd.getFile().existsNow()) {
                        results.toFix.add(Result(dd.getDownloadId()))
                    }
                    logger.logProgressIfLongProcess {
                        "Will mark " +
                                getSomeOfTotal(results.toFix.size.toLong(), results.totalCount) + " downloads as absent"
                    }
                }
                results
            }
        })
    }
}
