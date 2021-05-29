/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils

enum class MyContentType(// See https://developer.mozilla.org/en-US/docs/Web/HTTP/Basics_of_HTTP/MIME_types
        val generalMimeType: String, private val code: Long, val attachmentsSortOrder: Int) {
    IMAGE("image/*", 2, 1), ANIMATED_IMAGE("image/*", 6, 2), VIDEO("video/*", 4, 3), TEXT("text/*", 3, 4), APPLICATION("application/*", 5, 5), UNKNOWN("*/*", 0, 6);

    fun isImage(): Boolean {
        return this == IMAGE || this == ANIMATED_IMAGE
    }

    fun save(): String {
        return java.lang.Long.toString(code)
    }

    fun getDownloadMediaOfThisType(): Boolean {
        return when (this) {
            IMAGE, ANIMATED_IMAGE -> MyPreferences.getDownloadAndDisplayAttachedImages()
            VIDEO -> MyPreferences.getDownloadAttachedVideo()
            else -> false
        }
    }

    companion object {
        private val TAG: String = MyContentType::class.java.simpleName
        val APPLICATION_JSON: String = "application/json"
        fun fromPathOfSavedFile(mediaFilePath: String?): MyContentType {
            return if (mediaFilePath.isNullOrEmpty()) UNKNOWN else fromUri(DownloadType.UNKNOWN, null, Uri.parse(mediaFilePath), UNKNOWN.generalMimeType)
        }

        fun fromUri(downloadType: DownloadType?, contentResolver: ContentResolver?, uri: Uri,
                    defaultMimeType: String): MyContentType {
            val myContentType = fromMimeType(uri2MimeType(contentResolver, uri, defaultMimeType))
            return if (myContentType == ANIMATED_IMAGE || downloadType != DownloadType.AVATAR) myContentType else IMAGE
        }

        private fun fromMimeType(mimeType: String): MyContentType {
            return if (mimeType.startsWith("image")) {
                if (mimeType.endsWith("/gif") || mimeType.endsWith("/apng")) {
                    ANIMATED_IMAGE
                } else IMAGE
            } else if (mimeType.startsWith("video")) {
                VIDEO
            } else if (mimeType.startsWith("text")) {
                TEXT
            } else if (mimeType.startsWith("application")) {
                APPLICATION
            } else {
                UNKNOWN
            }
        }

        /** Returns the enum or UNKNOWN  */
        fun load(strCode: String?): MyContentType {
            try {
                return load(strCode?.toLong() ?: 0)
            } catch (e: NumberFormatException) {
                MyLog.v(TAG, "Error converting '$strCode'", e)
            }
            return UNKNOWN
        }

        fun load(code: Long): MyContentType {
            for (`val` in values()) {
                if (`val`.code == code) {
                    return `val`
                }
            }
            return UNKNOWN
        }

        @JvmOverloads
        fun uri2MimeType(contentResolver: ContentResolver?, uri: Uri, defaultValue: String = ""): String {
            if (UriUtils.isEmpty(uri)) return getDefaultValue(defaultValue)
            if (contentResolver != null) {
                val mimeType = contentResolver.getType(uri)
                if (mimeType != null && !isEmptyMime(mimeType)) return mimeType
            }
            return path2MimeType(uri.getPath(), getDefaultValue(defaultValue))
        }

        private fun getDefaultValue(defaultValue: String?): String {
            return if (defaultValue.isNullOrEmpty()) UNKNOWN.generalMimeType else defaultValue
        }

        fun isEmptyMime(mimeType: String?): Boolean {
            return mimeType.isNullOrEmpty() || mimeType.startsWith("*")
        }

        /** @return empty string if no extension found
         */
        fun mimeToFileExtension(mimeType: String?): String {
            if (isEmptyMime(mimeType)) return ""
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            return if (extension.isNullOrEmpty()) "" else extension
        }

        fun path2MimeType(path: String?, defaultValue: String): String {
            if (path.isNullOrEmpty()) return defaultValue
            var fileExtension = MimeTypeMap.getFileExtensionFromUrl(path)
            var clarifyType = false
            if (fileExtension.isNullOrEmpty() && defaultValue.endsWith("/*") && !path.isNullOrEmpty()) {
                // Hack allowing to set actual extension after underscore
                fileExtension = MimeTypeMap.getFileExtensionFromUrl(path.replace("_".toRegex(), "."))
                clarifyType = true
            }
            var mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension)
            if (clarifyType && mimeType != null && !isEmptyMime(mimeType) && !isEmptyMime(defaultValue)) {
                val indSlash = defaultValue.indexOf("/")
                if (indSlash >= 0 && !mimeType.startsWith(defaultValue.substring(0, indSlash + 1))) {
                    mimeType = ""
                }
            }
            return if (mimeType == null || isEmptyMime(mimeType)) defaultValue else mimeType
        }
    }
}
