/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.account.MyAccount
import org.andstatus.app.data.DataUpdater
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DownloadStatus
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Note
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.origin.Origin
import org.andstatus.app.origin.OriginType
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import java.util.function.Function

/**
 * @author yvolk@yurivolkov.com
 */
internal class CheckAudience : DataChecker() {
    override fun fixInternal(): Long {
        return myContext.origins().collection().map { o: Origin -> fixOneOrigin(o, countOnly) }.sum().toLong()
    }

    private class FixSummary {
        var rowsCount: Long = 0
        var toFixCount = 0
    }

    private fun fixOneOrigin(origin: Origin, countOnly: Boolean): Int {
        if (logger.isCancelled) return 0
        val ma = myContext.accounts().getFirstPreferablySucceededForOrigin(origin)
        if (ma.isEmpty) return 0
        val dataUpdater = DataUpdater(ma)
        val sql = "SELECT " + BaseColumns._ID + ", " +
                NoteTable.INS_DATE + ", " +
                NoteTable.VISIBILITY + ", " +
                NoteTable.CONTENT + ", " +
                NoteTable.ORIGIN_ID + ", " +
                NoteTable.AUTHOR_ID + ", " +
                NoteTable.IN_REPLY_TO_ACTOR_ID +
                " FROM " + NoteTable.TABLE_NAME +
                " WHERE " + NoteTable.ORIGIN_ID + "=" + origin.id +
                " AND " + NoteTable.NOTE_STATUS + "=" + DownloadStatus.LOADED.save() +
                " ORDER BY " + BaseColumns._ID + " DESC" +
                if (includeLong) "" else " LIMIT 0, 500"
        val summary = MyQuery.foldLeft(myContext, sql, FixSummary(),
                { fixSummary: FixSummary -> Function { cursor: Cursor -> foldOneNote(ma, dataUpdater, countOnly, fixSummary, cursor) } })
        logger.logProgress(origin.name + ": " +
                if (summary.toFixCount == 0) "No changes to Audience were needed. " + summary.rowsCount + " notes" else (if (countOnly) "Need to update " else "Updated") + " Audience for " + summary.toFixCount +
                        " of " + summary.rowsCount + " notes")
        DbUtils.waitMs(this, 1000)
        return summary.toFixCount
    }

    private fun foldOneNote(ma: MyAccount, dataUpdater: DataUpdater, countOnly: Boolean, fixSummary: FixSummary, cursor: Cursor): FixSummary {
        if (logger.isCancelled) return fixSummary
        val origin = ma.origin
        fixSummary.rowsCount++
        val noteId = DbUtils.getLong(cursor, BaseColumns._ID)
        val insDate = DbUtils.getLong(cursor, NoteTable.INS_DATE)
        val storedVisibility: Visibility = Visibility.fromCursor(cursor)
        val content = DbUtils.getString(cursor, NoteTable.CONTENT)
        val author: Actor = Actor.load(myContext, DbUtils.getLong(cursor, NoteTable.AUTHOR_ID))
        val inReplyToActor: Actor = Actor.load(myContext, DbUtils.getLong(cursor, NoteTable.IN_REPLY_TO_ACTOR_ID))
        if (origin.originType === OriginType.GNUSOCIAL || origin.originType === OriginType.TWITTER) {

            // See org.andstatus.app.note.NoteEditorData.recreateAudience
            val audience = Audience(origin).withVisibility(storedVisibility)
            audience.add(inReplyToActor)
            audience.addActorsFromContent(content, author, inReplyToActor)
            audience.lookupUsers()
            val actorsToSave = audience.evaluateAndGetActorsToSave(author)
            if (!countOnly) {
                actorsToSave.stream().filter { a: Actor -> a.actorId == 0L }
                        .forEach { actor: Actor -> dataUpdater.updateObjActor(ma.actor.update(actor), 0) }
            }
            compareVisibility(fixSummary, countOnly, noteId, audience, storedVisibility)
            if (audience.save(author, noteId, audience.visibility, countOnly)) {
                fixSummary.toFixCount += 1
            }
        } else {
            val audience: Audience = Audience.fromNoteId(origin, noteId, storedVisibility)
            compareVisibility(fixSummary, countOnly, noteId, audience, storedVisibility)
        }
        logger.logProgressIfLongProcess {
            """${origin.name}: need to fix ${fixSummary.toFixCount} of ${fixSummary.rowsCount} audiences;
${RelativeTime.getDifference(myContext.context(), insDate)}, ${I18n.trimTextAt(MyHtml.htmlToCompactPlainText(content), 120)}"""
        }
        return fixSummary
    }

    private fun compareVisibility(s: FixSummary, countOnly: Boolean, noteId: Long,
                                  audience: Audience, storedVisibility: Visibility) {
        if (storedVisibility == audience.visibility) return
        s.toFixCount += 1
        var msgLog = (s.toFixCount.toString() + ". Fix visibility for " + noteId + " " + storedVisibility
                + " -> " + audience.visibility)
        if (s.toFixCount < 20) {
            msgLog += "; " + Note.loadContentById(myContext, noteId)
        }
        MyLog.i(TAG, msgLog)
        if (!countOnly) {
            val sql = ("UPDATE " + NoteTable.TABLE_NAME
                    + " SET "
                    + NoteTable.VISIBILITY + "=" + audience.visibility.id
                    + " WHERE " + BaseColumns._ID + "=" + noteId)
            myContext.getDatabase()?.execSQL(sql)
        }
    }

    companion object {
        private val TAG: String = CheckAudience::class.java.simpleName
    }
}