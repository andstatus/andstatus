/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.provider.BaseColumns
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextEmpty
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.NoteTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.net.social.Audience
import org.andstatus.app.net.social.Audience.Companion.fromNoteId
import org.andstatus.app.net.social.Visibility
import org.andstatus.app.note.ConversationLoader
import org.andstatus.app.note.ConversationViewItem
import org.andstatus.app.note.NoteDownloads
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyHtml
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil

/**
 * Helper class to find out a relation of a Note with [.noteId] to MyAccount-s
 * @author yvolk@yurivolkov.com
 */
class NoteForAnyAccount(val myContext: MyContext, activityId: Long, val noteId: Long) {
    val origin: Origin = if (myContext.isEmpty) Origin.EMPTY else myContext.origins.fromId(MyQuery.noteIdToOriginId(noteId))
    private var noteOid: String = ""
    val status: DownloadStatus
    val author: Actor
    val actor: Actor
    val conversationParticipants: List<ConversationViewItem>
    val downloads: NoteDownloads
    val visibility: Visibility
    var audience: Audience
    private var content: String = ""

    fun isLoaded(): Boolean {
        return status == DownloadStatus.LOADED
    }

    fun getBodyTrimmed(): String {
        return I18n.trimTextAt(MyHtml.htmlToCompactPlainText(content), 80).toString()
    }

    fun isPresentAtServer(): Boolean {
        return status.isPresentAtServer() || StringUtil.nonEmptyNonTemp(noteOid)
    }

    companion object {
        private val TAG: String = NoteForAnyAccount::class.java.simpleName
        val EMPTY: NoteForAnyAccount = NoteForAnyAccount(MyContextEmpty.EMPTY, 0, 0)
    }

    init {
        val db = myContext.database
        var authorId: Long = 0
        var statusLoc = DownloadStatus.UNKNOWN
        var visibilityLoc = Visibility.UNKNOWN
        if (noteId != 0L && origin.isValid && db != null) {
            val sql = ("SELECT " + NoteTable.NOTE_STATUS + ", "
                    + NoteTable.CONTENT + ", "
                    + NoteTable.AUTHOR_ID + ","
                    + NoteTable.NOTE_OID + ", "
                    + NoteTable.VISIBILITY
                    + " FROM " + NoteTable.TABLE_NAME
                    + " WHERE " + BaseColumns._ID + "=" + noteId)
            try {
                db.rawQuery(sql, null).use { cursor ->
                    if (cursor.moveToNext()) {
                        statusLoc = DownloadStatus.load(DbUtils.getLong(cursor, NoteTable.NOTE_STATUS))
                        content = DbUtils.getString(cursor, NoteTable.CONTENT)
                        authorId = DbUtils.getLong(cursor, NoteTable.AUTHOR_ID)
                        noteOid = DbUtils.getString(cursor, NoteTable.NOTE_OID)
                        visibilityLoc = Visibility.fromCursor(cursor)
                    }
                }
            } catch (e: Exception) {
                MyLog.i(TAG, "SQL:'$sql'", e)
            }
        }
        status = statusLoc
        visibility = visibilityLoc
        audience = fromNoteId(origin, noteId) // Now all users, mentioned in a body, are members of Audience
        conversationParticipants = if (myContext.isEmpty || origin.isEmpty || noteId == 0L) emptyList()
        else ConversationLoader.newLoader(ConversationViewItem.EMPTY, myContext, origin, noteId, false)
            .load()
            .getList()
            .filter { conversationViewItem -> conversationViewItem.isActorAConversationParticipant() }
        author = Actor.load(myContext, authorId)
        downloads = NoteDownloads.fromNoteId(myContext, noteId)
        val actorId: Long = if (activityId == 0L) {
            authorId
        } else {
            MyQuery.activityIdToLongColumnValue(ActivityTable.ACTOR_ID, activityId)
        }
        actor = Actor.load(myContext, actorId)
    }
}
