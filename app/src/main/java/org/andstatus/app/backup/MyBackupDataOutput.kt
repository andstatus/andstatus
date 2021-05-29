/*
 * Copyright (C) 2014-2019 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.backup

import android.app.backup.BackupDataOutput
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.util.MyLog
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/** Allowing to instantiate and to mock BackupDataOutput class  */
class MyBackupDataOutput {
    private val context: Context
    private var docFolder: DocumentFile? = null
    private var backupDataOutput: BackupDataOutput? = null
    private var sizeToWrite = 0
    private var sizeWritten = 0
    private var docFile: DocumentFile? = null
    private var headerOrdinalNumber = 0

    constructor(context: Context, backupDataOutput: BackupDataOutput?) {
        this.context = context
        this.backupDataOutput = backupDataOutput
    }

    constructor(context: Context, docFolder: DocumentFile?) {
        this.context = context
        this.docFolder = docFolder
    }

    /** [BackupDataOutput.writeEntityHeader]  */
    @Throws(IOException::class)
    fun writeEntityHeader(key: String, dataSize: Int, fileExtension: String): Int {
        headerOrdinalNumber++
        return backupDataOutput?.writeEntityHeader(key, dataSize)
                ?:  writeEntityHeader2(key, dataSize, fileExtension)
    }

    @Throws(IOException::class)
    private fun writeEntityHeader2(key: String, dataSize: Int, fileExtension: String): Int {
        MyLog.v(this, "Writing header for '$key', size=$dataSize")
        sizeToWrite = dataSize
        sizeWritten = 0
        writeHeaderFile(key, dataSize, fileExtension)
        createDataFile(key, dataSize, fileExtension)
        return key.length
    }

    @Throws(IOException::class)
    private fun writeHeaderFile(key: String, dataSize: Int, fileExtension: String) {
        val jso = JSONObject()
        try {
            jso.put(KEY_KEYNAME, key)
            jso.put(KEY_ORDINAL_NUMBER, headerOrdinalNumber)
            jso.put(KEY_DATA_SIZE, dataSize)
            jso.put(KEY_FILE_EXTENSION, fileExtension)
            val bytes = jso.toString(2).toByteArray(StandardCharsets.UTF_8)
            appendBytesToChild(key + HEADER_FILE_SUFFIX, bytes, bytes.size)
        } catch (e: JSONException) {
            throw IOException(e)
        }
    }

    @Throws(IOException::class)
    private fun createDataFile(key: String?, dataSize: Int, fileExtension: String?) {
        val childName = key + DATA_FILE_SUFFIX + fileExtension
        docFile = createDocumentIfNeeded(dataSize, childName)
    }

    /** [BackupDataOutput.writeEntityData]  */
    @Throws(IOException::class)
    fun writeEntityData(data: ByteArray, size: Int): Int {
        return backupDataOutput?.writeEntityData(data, size) ?: writeEntityData2(data, size)
    }

    @Throws(IOException::class)
    private fun writeEntityData2(data: ByteArray, size: Int): Int {
        if (docFile?.exists() != true) {
            throw FileNotFoundException("Output document doesn't exist " + docFile?.getUri())
        }
        if (size < 0) {
            throw FileNotFoundException("Wrong number of bytes to write: $size")
        }
        appendBytesToFile(data, size)
        sizeWritten += size
        if (sizeWritten >= sizeToWrite) {
            try {
                if (sizeWritten > sizeToWrite) {
                    throw FileNotFoundException("Data is longer than expected: written=" + sizeWritten
                            + ", expected=" + sizeToWrite)
                }
            } finally {
                docFile = null
                sizeWritten = 0
            }
        }
        return size
    }

    @Throws(IOException::class)
    private fun appendBytesToFile(data: ByteArray, size: Int) {
        getOutputStreamAppend(size)?.use { fileOutputStream -> BufferedOutputStream(fileOutputStream).use { out -> out.write(data, 0, size) } }
    }

    @Throws(IOException::class)
    private fun getOutputStreamAppend(size: Int): OutputStream? =
            docFile?.let { df ->
                MyLog.v(this, "Appending data to document='" + df.name + "', size=" + size)
                context.contentResolver.openOutputStream(df.uri, "wa")
            }

    @Throws(IOException::class)
    private fun appendBytesToChild(childName: String, data: ByteArray, size: Int) {
        MyLog.v(this, "Appending data to file='$childName', size=$size")
        getOutputStreamAppend(childName, size)?.use { outputStream -> BufferedOutputStream(outputStream).use { out -> out.write(data, 0, size) } }
    }

    @Throws(IOException::class)
    private fun getOutputStreamAppend(childName: String, size: Int): OutputStream? {
        return context.getContentResolver().openOutputStream(createDocumentIfNeeded(size, childName).getUri(), "wa")
    }

    @Throws(IOException::class)
    private fun createDocumentIfNeeded(dataSize: Int, childName: String): DocumentFile {
        var documentFile = docFolder?.findFile(childName)
        if (documentFile == null) {
            documentFile = docFolder?.createFile("", childName)
        }
        if (documentFile == null) {
            throw IOException("Couldn't create '" + childName + "' document inside '" + docFolder?.getUri() + "'")
        }
        return documentFile
    }

    fun getDataFolderName(): String {
        return docFolder?.getUri()?.toString() ?: "(empty)"
    }

    companion object {
        val HEADER_FILE_SUFFIX: String = "_header.json"
        val DATA_FILE_SUFFIX: String = "_data"
        val DATA_FILE_EXTENSION_DEFAULT: String = ".dat"
        val KEY_KEYNAME: String = "key"
        val KEY_DATA_SIZE: String = "data_size"
        val KEY_ORDINAL_NUMBER: String = "ordinal_number"
        val KEY_FILE_EXTENSION: String = "file_extension"

        fun getDataFileExtension(dataFile: File): String {
            val name = dataFile.getName()
            val indDot = name.lastIndexOf(".")
            return if (indDot >= 0) {
                name.substring(indDot)
            } else DATA_FILE_EXTENSION_DEFAULT
        }
    }
}
