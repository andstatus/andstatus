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

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.BaseColumns
import org.andstatus.app.database.table.DownloadTable
import org.andstatus.app.graphics.CachedImage
import org.andstatus.app.graphics.MediaMetadata
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.UriUtils

class AttachedMediaFile : MediaFile {
    val uri: Uri?
    val previewOfDownloadId: Long
    val previewOf: AttachedMediaFile

    private constructor() : super("", MyContentType.UNKNOWN, MediaMetadata.EMPTY, 0, DownloadStatus.ABSENT, RelativeTime.DATETIME_MILLIS_NEVER) {
        uri = Uri.EMPTY
        previewOfDownloadId = 0
        previewOf = EMPTY
    }

    private constructor(downloadId: Long, cursor: Cursor?) : super(DbUtils.getString(cursor, DownloadTable.FILE_NAME),
            MyContentType.load(DbUtils.getLong(cursor, DownloadTable.CONTENT_TYPE)),
            MediaMetadata.fromCursor(cursor), downloadId,
            DownloadStatus.load(DbUtils.getLong(cursor, DownloadTable.DOWNLOAD_STATUS)),
            DbUtils.getLong(cursor, DownloadTable.DOWNLOADED_DATE)) {
        uri = UriUtils.fromString(DbUtils.getString(cursor, DownloadTable.URL))
        previewOfDownloadId = DbUtils.getLong(cursor, DownloadTable.PREVIEW_OF_DOWNLOAD_ID)
        previewOf = EMPTY
    }

    constructor(data: DownloadData) : super(data.getFilename(), data.getContentType(), data.mediaMetadata, data.getDownloadId(), data.getStatus(),
            data.getDownloadedDate()) {
        uri = data.getUri()
        previewOfDownloadId = 0
        previewOf = EMPTY
    }

    override fun getDefaultImage(): CachedImage {
        return CachedImage.EMPTY
    }

    fun getTargetUri(): Uri? {
        return if (previewOf.isEmpty) uri else previewOf.uri
    }

    fun isTargetVideo(): Boolean {
        return if (previewOf.isEmpty) isVideo() else previewOf.isVideo()
    }

    override fun requestDownload() {
        if (downloadId == 0L || uri === Uri.EMPTY || !contentType.getDownloadMediaOfThisType()) return
        MyServiceManager.sendCommand(CommandData.newFetchAttachment(0, downloadId))
    }

    internal constructor(previewFile: AttachedMediaFile, previewOf: AttachedMediaFile) :
            super(previewFile.downloadFile.getFilename(),
                    previewFile.contentType,
                    previewFile.mediaMetadata,
                    previewFile.downloadId,
                    previewFile.downloadStatus,
                    previewFile.downloadedDate) {
        uri = previewFile.uri
        previewOfDownloadId = previewFile.previewOfDownloadId
        this.previewOf = previewOf
    }

    fun imageOrLinkMayBeShown(): Boolean {
        return contentType.isImage() &&
                (super.imageMayBeShown() || uri !== Uri.EMPTY)
    }

    fun intentToView(): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        val fileToView = if (previewOf.isEmpty) this else previewOf
        val mediaFileUri = if (fileToView.downloadFile.existsNow()) FileProvider.downloadFilenameToUri(
                fileToView.downloadFile.getFilename()) else fileToView.uri
        if (UriUtils.isEmpty(mediaFileUri)) {
            intent.type = "text/*"
        } else {
            intent.setDataAndType(mediaFileUri, fileToView.contentType.generalMimeType)
            intent.putExtra(Intent.EXTRA_STREAM, mediaFileUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return intent
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val imageFile = o as AttachedMediaFile
        return if (previewOfDownloadId != imageFile.previewOfDownloadId) false else uri == imageFile.uri
    }

    override fun hashCode(): Int {
        var result = uri.hashCode()
        result = 31 * result + (previewOfDownloadId xor (previewOfDownloadId ushr 32)).toInt()
        return result
    }

    companion object {
        val EMPTY: AttachedMediaFile = AttachedMediaFile()
        fun fromCursor(cursor: Cursor): AttachedMediaFile {
            val downloadId = DbUtils.getLong(cursor, BaseColumns._ID)
            return if (downloadId == 0L) EMPTY else AttachedMediaFile(downloadId, cursor)
        }
    }
}