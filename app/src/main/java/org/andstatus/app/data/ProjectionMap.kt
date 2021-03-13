/* 
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.database.table.ActorTable
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.database.table.NoteTable
import java.util.*

object ProjectionMap {
    val ACTIVITY_TABLE_ALIAS: String = "act1"
    val NOTE_TABLE_ALIAS: String = "msg1"
    val ATTACHMENT_IMAGE_TABLE_ALIAS: String = "img"

    /**
     * Projection map used by SQLiteQueryBuilder
     * Projection map for a Timeline
     * @see android.database.sqlite.SQLiteQueryBuilder.setProjectionMap
     */
    val TIMELINE: MutableMap<String, String> = HashMap()

    init {
        TIMELINE[ActivityTable.ACTIVITY_ID] = (ACTIVITY_TABLE_ALIAS + "." + BaseColumns._ID
                + " AS " + ActivityTable.ACTIVITY_ID)
        TIMELINE[ActivityTable.ORIGIN_ID] = ActivityTable.ORIGIN_ID
        TIMELINE[ActivityTable.ACCOUNT_ID] = ActivityTable.ACCOUNT_ID
        TIMELINE[ActivityTable.ACTIVITY_TYPE] = ActivityTable.ACTIVITY_TYPE
        TIMELINE[ActivityTable.ACTOR_ID] = ActivityTable.ACTOR_ID
        TIMELINE[ActivityTable.AUTHOR_ID] = ActivityTable.AUTHOR_ID
        TIMELINE[ActivityTable.NOTE_ID] = ActivityTable.NOTE_ID
        TIMELINE[ActivityTable.OBJ_ACTOR_ID] = ActivityTable.OBJ_ACTOR_ID
        TIMELINE[ActivityTable.SUBSCRIBED] = ActivityTable.SUBSCRIBED
        TIMELINE[ActivityTable.NOTIFIED] = ActivityTable.NOTIFIED
        TIMELINE[ActivityTable.INS_DATE] = ActivityTable.INS_DATE
        TIMELINE[ActivityTable.UPDATED_DATE] = ActivityTable.UPDATED_DATE
        TIMELINE[BaseColumns._ID] = NOTE_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + BaseColumns._ID
        TIMELINE[NoteTable.NOTE_ID] = NOTE_TABLE_ALIAS + "." + BaseColumns._ID + " AS " + NoteTable.NOTE_ID
        TIMELINE[NoteTable.ORIGIN_ID] = NoteTable.ORIGIN_ID
        TIMELINE[NoteTable.NOTE_OID] = NoteTable.NOTE_OID
        TIMELINE[NoteTable.CONVERSATION_ID] = NoteTable.CONVERSATION_ID
        TIMELINE[NoteTable.AUTHOR_ID] = NoteTable.AUTHOR_ID
        TIMELINE[ActorTable.AUTHOR_NAME] = ActorTable.AUTHOR_NAME
        TIMELINE[DownloadTable.DOWNLOAD_STATUS] = DownloadTable.DOWNLOAD_STATUS
        TIMELINE[DownloadTable.FILE_NAME] = DownloadTable.FILE_NAME
        TIMELINE[DownloadTable.IMAGE_FILE_NAME] = (ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.FILE_NAME
                + " AS " + DownloadTable.IMAGE_FILE_NAME)
        TIMELINE[DownloadTable.IMAGE_ID] = (ATTACHMENT_IMAGE_TABLE_ALIAS + "." + BaseColumns._ID
                + " AS " + DownloadTable.IMAGE_ID)
        TIMELINE[DownloadTable.IMAGE_URI] = (ATTACHMENT_IMAGE_TABLE_ALIAS + "." + DownloadTable.URL
                + " AS " + DownloadTable.IMAGE_URI)
        TIMELINE[DownloadTable.PREVIEW_OF_DOWNLOAD_ID] = DownloadTable.PREVIEW_OF_DOWNLOAD_ID
        TIMELINE[DownloadTable.WIDTH] = DownloadTable.WIDTH
        TIMELINE[DownloadTable.HEIGHT] = DownloadTable.HEIGHT
        TIMELINE[DownloadTable.DURATION] = DownloadTable.DURATION
        TIMELINE[NoteTable.NAME] = NoteTable.NAME
        TIMELINE[NoteTable.SUMMARY] = NoteTable.SUMMARY
        TIMELINE[NoteTable.SENSITIVE] = NoteTable.SENSITIVE
        TIMELINE[NoteTable.CONTENT] = NoteTable.CONTENT
        TIMELINE[NoteTable.CONTENT_TO_SEARCH] = NoteTable.CONTENT_TO_SEARCH
        TIMELINE[NoteTable.VIA] = NoteTable.VIA
        TIMELINE[NoteTable.URL] = NoteTable.URL
        TIMELINE[NoteTable.IN_REPLY_TO_NOTE_ID] = NoteTable.IN_REPLY_TO_NOTE_ID
        TIMELINE[NoteTable.IN_REPLY_TO_ACTOR_ID] = NoteTable.IN_REPLY_TO_ACTOR_ID
        TIMELINE[NoteTable.VISIBILITY] = NoteTable.VISIBILITY
        TIMELINE[NoteTable.FAVORITED] = NoteTable.FAVORITED
        TIMELINE[NoteTable.REBLOGGED] = NoteTable.REBLOGGED
        TIMELINE[NoteTable.LIKES_COUNT] = NoteTable.LIKES_COUNT
        TIMELINE[NoteTable.REBLOGS_COUNT] = NoteTable.REBLOGS_COUNT
        TIMELINE[NoteTable.REPLIES_COUNT] = NoteTable.REPLIES_COUNT
        TIMELINE[NoteTable.UPDATED_DATE] = NoteTable.UPDATED_DATE
        TIMELINE[NoteTable.NOTE_STATUS] = NoteTable.NOTE_STATUS
        TIMELINE[NoteTable.INS_DATE] = NoteTable.INS_DATE
        TIMELINE[NoteTable.ATTACHMENTS_COUNT] = NoteTable.ATTACHMENTS_COUNT
    }
}