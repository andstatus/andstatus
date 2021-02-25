/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.social

import android.content.ContentResolver
import android.net.Uri
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DownloadData
import org.andstatus.app.data.DownloadType
import org.andstatus.app.data.FileProvider
import org.andstatus.app.data.MyContentType
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.UriUtils
import java.util.*

class Attachment : Comparable<Attachment>, IsEmpty {
    val uri: Uri
    val mimeType: String
    val contentType: MyContentType
    var previewOf = EMPTY
    var downloadData: DownloadData = DownloadData.EMPTY
    private var optNewDownloadNumber: Optional<Long> = Optional.empty()

    /** #previewOf cannot be set here  */
    internal constructor(downloadData: DownloadData) {
        this.downloadData = downloadData
        uri = downloadData.getUri()
        mimeType = downloadData.getMimeType()
        contentType = downloadData.getContentType()
    }

    private constructor(contentResolver: ContentResolver?, uri: Uri, mimeType: String) {
        this.uri = uri
        this.mimeType = MyContentType.uri2MimeType(contentResolver, uri, mimeType)
        contentType = MyContentType.fromUri(DownloadType.ATTACHMENT, contentResolver, uri, mimeType)
    }

    fun setPreviewOf(previewOf: Attachment): Attachment {
        this.previewOf = previewOf
        return this
    }

    fun isValid(): Boolean {
        return nonEmpty() && contentType != MyContentType.UNKNOWN
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + contentType.hashCode()
        result = prime * result + uri.hashCode()
        result = prime * result + mimeType.hashCode()
        return result
    }

    fun getDownloadNumber(): Long {
        return optNewDownloadNumber.orElse(downloadData.getDownloadNumber())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Attachment) return false
        return contentType == other.contentType && uri == other.uri && mimeType == other.mimeType
    }

    override fun toString(): String {
        return "Attachment [uri='$uri', $contentType, mime=$mimeType]"
    }

    fun mediaUriToPost(): Uri? {
        return if (downloadData.isEmpty() || UriUtils.isDownloadable(uri)) {
            Uri.EMPTY
        } else FileProvider.downloadFilenameToUri(downloadData.getFile().getFilename())
    }

    override operator fun compareTo(other: Attachment): Int {
        if (previewOf == other) return -1
        if (other.previewOf == this) return 1
        return if (contentType != other.contentType) {
            if (contentType.attachmentsSortOrder > other.contentType.attachmentsSortOrder) 1 else -1
        } else 0
    }

    override fun isEmpty(): Boolean {
        return uri === Uri.EMPTY
    }

    fun getDownloadId(): Long {
        return downloadData.getDownloadId()
    }

    fun setDownloadNumber(downloadNumber: Long) {
        optNewDownloadNumber = Optional.of(downloadNumber)
    }

    companion object {
        val EMPTY: Attachment = Attachment(null, Uri.EMPTY, "")
        fun fromUri(uriString: String?): Attachment {
            return fromUriAndMimeType(uriString, "")
        }

        fun fromUri(uriIn: Uri): Attachment {
            return fromUriAndMimeType(uriIn, "")
        }

        fun fromUriAndMimeType(uriString: String?, mimeTypeIn: String): Attachment {
            return fromUriAndMimeType(Uri.parse(uriString), mimeTypeIn)
        }

        fun fromUriAndMimeType(uriIn: Uri, mimeTypeIn: String): Attachment {
            Objects.requireNonNull(uriIn)
            Objects.requireNonNull(mimeTypeIn)
            return Attachment( MyContextHolder.myContextHolder.getNow().context()?.getContentResolver(), uriIn, mimeTypeIn)
        }
    }
}