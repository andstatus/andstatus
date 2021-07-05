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
package org.andstatus.app.graphics

import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.vavr.control.Try
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.data.MyContentType
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TaggedClass
import org.apache.commons.lang3.time.DurationFormatUtils
import java.util.concurrent.TimeUnit

class MediaMetadata(val width: Int, val height: Int, val duration: Long) : IsEmpty, TaggedClass {
    fun size(): Point {
        return Point(width, height)
    }

    override val isEmpty: Boolean
        get() {
            return width <= 0 || height <= 0
        }

    fun toContentValues(values: ContentValues) {
        values.put(DownloadTable.WIDTH, width)
        values.put(DownloadTable.HEIGHT, height)
        values.put(DownloadTable.DURATION, duration)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        if (this === EMPTY) {
            builder.append("EMPTY")
        } else {
            if (width > 0) builder.append("width:$width,")
            if (height > 0) builder.append("height:$height,")
            if (duration > 0) builder.append("duration:$height,")
        }
        return MyStringBuilder.formatKeyValue(this, builder.toString())
    }

    fun toDetails(): String {
        return if (nonEmpty) width.toString() + "x" + height +
                (if (duration == 0L) "" else " " + formatDuration())
        else ""
    }

    fun formatDuration(): String {
        return DurationFormatUtils.formatDuration(duration,
                (if (duration >= TimeUnit.HOURS.toMillis(1)) "HH:" else "") + "mm:ss"
        )
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = MediaMetadata::class.java.simpleName
        val EMPTY: MediaMetadata = MediaMetadata(0, 0, 0)

        fun fromFilePath(path: String?): Try<MediaMetadata> {
            try {
                if (MyContentType.fromPathOfSavedFile(path) == MyContentType.VIDEO) {
                    var retriever: MediaMetadataRetriever? = null
                    return try {
                        retriever = MediaMetadataRetriever()
                        retriever.setDataSource( MyContextHolder.myContextHolder.getNow().context, Uri.parse(path))
                        Try.success(
                            MediaMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt(),
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt(),
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong())
                        )
                    } finally {
                        closeSilently(retriever)
                    }
                }
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(path, options)
                return Try.success(MediaMetadata(options.outWidth, options.outHeight, 0))
            } catch (e: Exception) {
                return Try.failure(e)
            }
        }

        fun fromCursor(cursor: Cursor?): MediaMetadata {
            return MediaMetadata(
                    DbUtils.getInt(cursor, DownloadTable.WIDTH),
                    DbUtils.getInt(cursor, DownloadTable.HEIGHT),
                    DbUtils.getLong(cursor, DownloadTable.DURATION)
            )
        }
    }
}
