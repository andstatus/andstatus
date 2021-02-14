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

import android.app.backup.BackupDataInput
import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.backup.MyBackupDataOutput
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyStorage
import org.andstatus.app.util.DocumentFileUtils
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

class MyBackupDataInput {
    private val context: Context?
    private var myContext: MyContext? = null
    private var backupDataInput: BackupDataInput? = null
    private var docFolder: DocumentFile? = null
    private val headers: MutableSet<BackupHeader?>? = TreeSet()
    private var keysIterator: MutableIterator<BackupHeader?>? = null
    private var mHeaderReady = false
    private var dataOffset = 0
    private var header = BackupHeader.getEmpty()

    internal class BackupHeader(var key: String?, var ordinalNumber: Long, var dataSize: Int, var fileExtension: String?) : Comparable<BackupHeader?> {
        override fun compareTo(another: BackupHeader?): Int {
            return java.lang.Long.compare(ordinalNumber, another.ordinalNumber)
        }

        override fun hashCode(): Int {
            val prime = 31
            var result = 1
            result = prime * result + dataSize
            result = prime * result + if (fileExtension == null) 0 else fileExtension.hashCode()
            result = prime * result + if (key == null) 0 else key.hashCode()
            result = prime * result + (ordinalNumber xor (ordinalNumber ushr 32)) as Int
            return result
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val other = o as BackupHeader?
            if (dataSize != other.dataSize) {
                return false
            }
            if (fileExtension == null) {
                if (other.fileExtension != null) {
                    return false
                }
            } else if (fileExtension != other.fileExtension) {
                return false
            }
            if (key == null) {
                if (other.key != null) {
                    return false
                }
            } else if (key != other.key) {
                return false
            }
            return if (ordinalNumber != other.ordinalNumber) {
                false
            } else true
        }

        override fun toString(): String {
            return ("BackupHeader [key=" + key + ", ordinalNumber=" + ordinalNumber + ", dataSize="
                    + dataSize + "]")
        }

        companion object {
            fun getEmpty(): BackupHeader? {
                return BackupHeader("", 0, 0, "")
            }

            fun fromJson(jso: JSONObject?): BackupHeader? {
                return BackupHeader(
                        JsonUtils.optString(jso, MyBackupDataOutput.Companion.KEY_KEYNAME),
                        jso.optLong(MyBackupDataOutput.Companion.KEY_ORDINAL_NUMBER, 0),
                        jso.optInt(MyBackupDataOutput.Companion.KEY_DATA_SIZE, 0),
                        JsonUtils.optString(jso, MyBackupDataOutput.Companion.KEY_FILE_EXTENSION, MyBackupDataOutput.Companion.DATA_FILE_EXTENSION_DEFAULT))
            }
        }
    }

    internal constructor(context: Context?, backupDataInput: BackupDataInput?) {
        this.context = context
        this.backupDataInput = backupDataInput
    }

    internal constructor(context: Context?, fileFolder: DocumentFile?) {
        this.context = context
        docFolder = fileFolder
        for (file in docFolder.listFiles()) {
            val filename = file.name
            if (filename != null && filename.endsWith(MyBackupDataOutput.Companion.HEADER_FILE_SUFFIX)) {
                headers.add(BackupHeader.fromJson(DocumentFileUtils.getJSONObject(context, file)))
            }
        }
        keysIterator = headers.iterator()
    }

    fun listKeys(): MutableSet<BackupHeader?>? {
        return headers
    }

    /** [BackupDataInput.readNextHeader]   */
    @Throws(IOException::class)
    fun readNextHeader(): Boolean {
        return if (backupDataInput != null) {
            backupDataInput.readNextHeader()
        } else {
            readNextHeader2()
        }
    }

    @Throws(IOException::class)
    private fun readNextHeader2(): Boolean {
        mHeaderReady = false
        dataOffset = 0
        if (keysIterator.hasNext()) {
            header = keysIterator.next()
            mHeaderReady = if (header.dataSize > 0) {
                true
            } else {
                throw FileNotFoundException("Header is invalid, $header")
            }
        }
        return mHeaderReady
    }

    /** [BackupDataInput.getKey]   */
    fun getKey(): String? {
        return if (backupDataInput != null) {
            backupDataInput.getKey()
        } else {
            getKey2()
        }
    }

    private fun getKey2(): String? {
        return if (mHeaderReady) {
            header.key
        } else {
            throw IllegalStateException(ENTITY_HEADER_NOT_READ)
        }
    }

    /** [BackupDataInput.getDataSize]   */
    fun getDataSize(): Int {
        return if (backupDataInput != null) {
            backupDataInput.getDataSize()
        } else {
            getDataSize2()
        }
    }

    private fun getDataSize2(): Int {
        return if (mHeaderReady) {
            header.dataSize
        } else {
            throw IllegalStateException(ENTITY_HEADER_NOT_READ)
        }
    }

    /** [BackupDataInput.readEntityData]   */
    @Throws(IOException::class)
    fun readEntityData(data: ByteArray?, offset: Int, size: Int): Int {
        return if (backupDataInput != null) {
            backupDataInput.readEntityData(data, offset, size)
        } else {
            readEntityData2(data, offset, size)
        }
    }

    @Throws(IOException::class)
    private fun readEntityData2(data: ByteArray?, offset: Int, size: Int): Int {
        var bytesRead = 0
        if (size > MyStorage.FILE_CHUNK_SIZE) {
            throw FileNotFoundException("Size to read is too large: $size")
        } else if (size < 1 || dataOffset >= header.dataSize) {
            // skip
        } else if (mHeaderReady) {
            val readData = getBytes(size)
            bytesRead = readData.size
            System.arraycopy(readData, 0, data, offset, bytesRead)
        } else {
            throw IllegalStateException("Entity header not read")
        }
        MyLog.v(this, "key=" + header.key + ", offset=" + dataOffset + ", bytes read=" + bytesRead)
        dataOffset += bytesRead
        return bytesRead
    }

    @Throws(IOException::class)
    private fun getBytes(size: Int): ByteArray? {
        val childName = header.key + MyBackupDataOutput.Companion.DATA_FILE_SUFFIX + header.fileExtension
        val childDocFile = docFolder.findFile(childName)
                ?: throw IOException("File '" + childName + "' not found in folder '" + docFolder.getName() + "'")
        return DocumentFileUtils.getBytes(context, childDocFile, dataOffset, size)
    }

    /** [BackupDataInput.skipEntityData]   */
    @Throws(IOException::class)
    fun skipEntityData() {
        if (backupDataInput != null) {
            backupDataInput.skipEntityData()
        } else {
            skipEntityData2()
        }
    }

    private fun skipEntityData2() {
        mHeaderReady = if (mHeaderReady) {
            false
        } else {
            throw IllegalStateException("Entity header not read")
        }
    }

    fun getDataFolderName(): String {
        return if (docFolder == null) "(empty)" else docFolder.getUri().toString()
    }

    fun setMyContext(myContext: MyContext?) {
        this.myContext = myContext
    }

    fun getMyContext(): MyContext? {
        return myContext
    }

    companion object {
        private val ENTITY_HEADER_NOT_READ: String? = "Entity header not read"
    }
}