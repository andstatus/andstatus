/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.provider.BaseColumns
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Note
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
internal class SearchIndexUpdate : DataChecker() {
    override fun fixInternal(): Long {
        val sql: String = Note.getSqlToLoadContent(0) +
                " ORDER BY " + BaseColumns._ID + " DESC" +
                if (includeLong) "" else " LIMIT 0, 10000"
        val notesToFix: MutableList<Note> = ArrayList()
        val counter = AtomicInteger()
        try {
            myContext.getDatabase()?.rawQuery(sql, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (logger.isCancelled) break
                    counter.incrementAndGet()
                    val note: Note = Note.contentFromCursor(myContext, cursor)
                    val contentToSearchStored = DbUtils.getString(cursor, NoteTable.CONTENT_TO_SEARCH)
                    if (contentToSearchStored != note.getContentToSearch()) {
                        notesToFix.add(note)
                        logger.logProgressIfLongProcess {
                            ("Need to fix " + notesToFix.size + " of " + counter.get() + " notes, "
                                    + ", id=" + note.noteId + "; "
                                    + I18n.trimTextAt(note.getContentToSearch(), 120))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val logMsg = "Error: " + e.message + ", SQL:" + sql
            logger.logProgress(logMsg)
            MyLog.e(this, logMsg, e)
        }
        if (!countOnly) notesToFix.forEach(Consumer { note: Note -> fixOneNote(note) })
        logger.logProgress(if (notesToFix.isEmpty()) "No changes to search index were needed. $counter notes"
        else "Updated search index for " + notesToFix.size + " of " + counter + " notes")
        return notesToFix.size.toLong()
    }

    private fun fixOneNote(note: Note) {
        if (logger.isCancelled) return
        var sql = ""
        try {
            sql = ("UPDATE " + NoteTable.TABLE_NAME
                    + " SET "
                    + NoteTable.CONTENT_TO_SEARCH + "=" + MyQuery.quoteIfNotQuoted(note.getContentToSearch())
                    + " WHERE " + BaseColumns._ID + "=" + note.noteId)
            myContext.getDatabase()?.execSQL(sql)
            logger.logProgressIfLongProcess {
                "Updating search index for " +
                        I18n.trimTextAt(note.getContentToSearch(), 120) +
                        " id=" + note.noteId
            }
            MyServiceManager.setServiceUnavailable()
        } catch (e: Exception) {
            val logMsg = "Error: " + e.message + ", SQL:" + sql
            logger.logProgress(logMsg)
            MyLog.e(this, logMsg, e)
        }
    }
}
