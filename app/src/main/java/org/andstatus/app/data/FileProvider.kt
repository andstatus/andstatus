/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.andstatus.app.ClassInApplicationPackage
import java.io.FileNotFoundException

class FileProvider : ContentProvider() {

    @Throws(FileNotFoundException::class)
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val downloadFile = DownloadFile(uriToFilename(uri))
        if (!downloadFile.existed) {
            throw FileNotFoundException(downloadFile.getFilename())
        }
        return ParcelFileDescriptor.open(downloadFile.getFile(), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun uriToFilename(uri: Uri): String {
        return when (uri.getPathSegments()[0]) {
            DOWNLOAD_FILE_PATH -> uri.getPathSegments()[1]
            else -> throw IllegalArgumentException("Unknown URI $uri")
        }
    }

    override fun onCreate(): Boolean {
        return false
    }

    override fun query(uri: Uri, projection: Array<String?>?, selection: String?, selectionArgs: Array<String?>?,
                       sortOrder: String?): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String?>?): Int {
        return 0
    }

    companion object {
        val AUTHORITY: String = ClassInApplicationPackage.PACKAGE_NAME + ".data.FileProvider"
        val DOWNLOAD_FILE_PATH: String = "downloadfile"
        val DOWNLOAD_FILE_URI = Uri.parse("content://" + AUTHORITY + "/" + DOWNLOAD_FILE_PATH)
        fun downloadFilenameToUri(filename: String?): Uri? {
            return if (filename.isNullOrEmpty()) {
                Uri.EMPTY
            } else {
                Uri.withAppendedPath(DOWNLOAD_FILE_URI, filename)
            }
        }
    }
}