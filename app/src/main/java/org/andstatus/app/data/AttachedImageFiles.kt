/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.text.format.Formatter
import org.andstatus.app.context.MyContext
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.graphics.CacheName
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyStringBuilder
import java.util.*
import java.util.function.Consumer
import java.util.stream.Collectors

class AttachedImageFiles(val list: MutableList<AttachedMediaFile>) : IsEmpty {
    override val isEmpty: Boolean
        get() {
            return list.isEmpty()
        }

    fun size(): Int {
        return list.size
    }

    fun imageOrLinkMayBeShown(): Boolean {
        for (mediaFile in list) {
            if (mediaFile.imageOrLinkMayBeShown()) {
                return true
            }
        }
        return false
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, list)
    }

    fun preloadImagesAsync() {
        for (mediaFile in list) {
            if (mediaFile.contentType.isImage()) {
                mediaFile.preloadImageAsync(CacheName.ATTACHED_IMAGE)
            }
        }
    }

    fun toMediaSummary(context: Context): String {
        val builder = MyStringBuilder()
        list.forEach(Consumer { item: AttachedMediaFile ->
            builder.withComma(
                    item.mediaMetadata?.toDetails() + " "
                            + Formatter.formatShortFileSize(context, item.downloadFile.getSize()))
        })
        return builder.toString()
    }

    fun tooLargeAttachment(maxBytes: Long): Optional<AttachedMediaFile> {
        return list.stream().filter { item: AttachedMediaFile -> item.downloadFile.getSize() > maxBytes }.findAny()
    }

    fun forUri(uri: Uri): Optional<AttachedMediaFile> {
        return list.stream().filter { item: AttachedMediaFile -> uri == item.uri }.findAny()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as AttachedImageFiles
        return list == that.list
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }

    companion object {
        val EMPTY: AttachedImageFiles = AttachedImageFiles(mutableListOf())

        fun load(myContext: MyContext, noteId: Long): AttachedImageFiles {
            val sql = "SELECT *" +
                    " FROM " + DownloadTable.TABLE_NAME +
                    " WHERE " + DownloadTable.NOTE_ID + "=" + noteId +
                    " AND " + DownloadTable.DOWNLOAD_TYPE + "=" + DownloadType.ATTACHMENT.save() +
                    " AND " + DownloadTable.CONTENT_TYPE +
                    " IN(" + MyContentType.IMAGE.save() + ", " + MyContentType.ANIMATED_IMAGE.save() + ", " +
                    MyContentType.VIDEO.save() + ")" +
                    " ORDER BY " + DownloadTable.DOWNLOAD_NUMBER
            val mediaFiles1 = MyQuery.getList(myContext, sql) { cursor: Cursor -> AttachedMediaFile.fromCursor(cursor) }
            val mediaFiles2 = foldPreviews(mediaFiles1.toMutableList())
            return AttachedImageFiles(mediaFiles2.toMutableList())
        }

        private fun foldPreviews(mediaFiles: MutableList<AttachedMediaFile>): MutableList<AttachedMediaFile> {
            val out: MutableList<AttachedMediaFile> = ArrayList()
            val toSkip = mediaFiles.stream().map { i: AttachedMediaFile -> i.previewOfDownloadId }.filter { i: Long? -> i != 0L }
                    .collect(Collectors.toList())
            for (mediaFile in mediaFiles) {
                if (mediaFile.isEmpty || toSkip.contains(mediaFile.downloadId)) continue
                if (mediaFile.previewOfDownloadId == 0L) {
                    out.add(mediaFile)
                } else {
                    var fullImage: AttachedMediaFile? = AttachedMediaFile.EMPTY
                    for (other in mediaFiles) {
                        if (other.downloadId == mediaFile.previewOfDownloadId) {
                            fullImage = other
                            break
                        }
                    }
                    out.add(AttachedMediaFile(mediaFile, fullImage))
                }
            }
            return out
        }
    }
}