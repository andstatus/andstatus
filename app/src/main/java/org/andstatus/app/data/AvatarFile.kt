/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor
import org.andstatus.app.R
import org.andstatus.app.context.MyContext
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.graphics.MediaMetadata
import org.andstatus.app.net.social.Actor
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime

class AvatarFile private constructor(private val actor: Actor?, filename: String?, mediaMetadata: MediaMetadata?, downloadStatus: DownloadStatus?,
                                     downloadedDate: Long) : MediaFile(filename, MyContentType.IMAGE, mediaMetadata, 0, downloadStatus, downloadedDate) {
    override fun getId(): Long {
        return getActor().actorId
    }

    fun getActor(): Actor {
        return actor ?: Actor.Companion.EMPTY
    }

    public override fun getDefaultImage(): CachedImage? {
        return if (getActor().groupType.isGroupLike) {
            ImageCaches.getStyledImage(R.drawable.ic_people_black_24dp, R.drawable.ic_people_white_24dp)
        } else {
            ImageCaches.getStyledImage(R.drawable.ic_person_black_36dp, R.drawable.ic_person_white_36dp)
        }
    }

    public override fun requestDownload() {
        if (getActor().actorId == 0L || !getActor().hasAvatar() || !contentType.downloadMediaOfThisType) return
        MyLog.v(this) {
            """
     Requesting download ${getActor()}
     $this
     """.trimIndent()
        }
        MyServiceManager.Companion.sendCommand(
                CommandData.Companion.newActorCommandAtOrigin(CommandEnum.GET_AVATAR, getActor(),
                        getActor().username, getActor().origin))
    }

    override fun isDefaultImageRequired(): Boolean {
        return true
    }

    fun resetAvatarErrors(myContext: MyContext) {
        val db = myContext.getDatabase()
        if (getActor().actorId == 0L || db == null) return
        db.execSQL("UPDATE " + DownloadTable.TABLE_NAME +
                " SET " + DownloadTable.DOWNLOAD_STATUS + "=" + DownloadStatus.ABSENT.save() +
                " WHERE " + DownloadTable.ACTOR_ID + "=" + getActor().actorId +
                " AND " + DownloadTable.DOWNLOAD_STATUS + "<>" + DownloadStatus.LOADED.save()
        )
    }

    companion object {
        val EMPTY: AvatarFile = AvatarFile(Actor.Companion.EMPTY, "", MediaMetadata.Companion.EMPTY,
                DownloadStatus.ABSENT, RelativeTime.DATETIME_MILLIS_NEVER)
        const val AVATAR_SIZE_DIP = 48
        fun fromCursor(actor: Actor?, cursor: Cursor?): AvatarFile {
            val filename = DbUtils.getString(cursor, DownloadTable.AVATAR_FILE_NAME)
            return if (actor.isEmpty()) EMPTY else AvatarFile(actor, filename, MediaMetadata.Companion.fromCursor(cursor),
                    DownloadStatus.Companion.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS)),
                    DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE))
        }

        fun fromActorOnly(actor: Actor): AvatarFile {
            return if (actor.isEmpty()) EMPTY else AvatarFile(actor, "", MediaMetadata.Companion.EMPTY, DownloadStatus.UNKNOWN, RelativeTime.DATETIME_MILLIS_NEVER)
        }
    }
}