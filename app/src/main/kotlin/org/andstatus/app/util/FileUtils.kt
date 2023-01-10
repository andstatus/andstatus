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
package org.andstatus.app.util

import org.andstatus.app.data.DbUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.*

object FileUtils {
    val ROOT_FOLDER: String = "/"
    private const val BUFFER_LENGTH = 4 * 1024

    fun getJSONArray(file: File?): JSONArray {
        var jso: JSONArray? = null
        val fileString = utf8File2String(file)
        if (fileString.isNotEmpty()) {
            jso = try {
                JSONArray(fileString)
            } catch (e: JSONException) {
                MyLog.v(FileUtils::class.java, e)
                null
            }
        }
        if (jso == null) {
            jso = JSONArray()
        }
        return jso
    }

    fun getJSONObject(file: File?): JSONObject {
        var jso: JSONObject? = null
        val fileString = utf8File2String(file)
        if (fileString.isNotEmpty()) {
            jso = try {
                JSONObject(fileString)
            } catch (e: JSONException) {
                MyLog.v(FileUtils::class.java, e)
                null
            }
        }
        if (jso == null) {
            jso = JSONObject()
        }
        return jso
    }

    private fun utf8File2String(file: File?): String {
        return String(getBytes(file), Charset.forName("UTF-8"))
    }

    /** Reads the whole file  */
    fun getBytes(file: File?): ByteArray {
        if (file != null) {
            FileInputStream(file).use { ins -> return getBytes(ins) }
        }
        return ByteArray(0)
    }

    /** Read the stream into an array; the stream is not closed  */
    fun getBytes(ins: InputStream?): ByteArray {
        if (ins != null) {
            val readBuffer = ByteArray(BUFFER_LENGTH)
            ByteArrayOutputStream().use { bout ->
                var read: Int
                do {
                    read = ins.read(readBuffer, 0, readBuffer.size)
                    if (read == -1) {
                        break
                    }
                    bout.write(readBuffer, 0, read)
                } while (true)
                return bout.toByteArray()
            }
        }
        return ByteArray(0)
    }

    /** Reads up to 'size' bytes, starting from 'offset'  */
    fun getBytes(file: File?, offset: Int, size: Int): ByteArray {
        if (file == null) return ByteArray(0)
        FileInputStream(file).use { ins -> return getBytes(ins, file.absolutePath, offset, size) }
    }

    /** Reads up to 'size' bytes, starting from 'offset'  */
    fun getBytes(ins: InputStream, path: String?, offset: Int, size: Int): ByteArray {
        val readBuffer = ByteArray(size)
        val bytesSkipped = ins.skip(offset.toLong())
        if (bytesSkipped < offset) {
            throw FileNotFoundException("Skipped only $bytesSkipped of $offset bytes in path='$path'")
        }
        val bytesRead = ins.read(readBuffer, 0, size)
        if (bytesRead == readBuffer.size) {
            return readBuffer
        } else if (bytesRead > 0) {
            return Arrays.copyOf(readBuffer, bytesRead)
        }
        return ByteArray(0)
    }

    fun deleteFilesRecursively(rootDirectory: File?) {
        if (rootDirectory == null) {
            return
        }
        MyLog.i(FileUtils::class.java, "On delete all files inside '" + rootDirectory.absolutePath + "'")
        MyLog.i(FileUtils::class.java, "Deleted files and dirs: " + deleteFilesRecursively(rootDirectory, 1))
    }

    private fun deleteFilesRecursively(rootDirectory: File?, level: Long): Long {
        if (rootDirectory == null) {
            return 0
        }
        val files = rootDirectory.listFiles()
        if (files == null) {
            MyLog.v(FileUtils::class.java) { "No files inside " + rootDirectory.absolutePath }
            return 0
        }
        var nDeleted: Long = 0
        for (file in files) {
            if (file.isDirectory) {
                nDeleted += deleteFilesRecursively(file, level + 1)
                if (level > 1) {
                    nDeleted += deleteAndCountFile(file)
                }
            } else {
                nDeleted += deleteAndCountFile(file)
            }
        }
        return nDeleted
    }

    private fun deleteAndCountFile(file: File): Long {
        var nDeleted: Long = 0
        if (file.delete()) {
            nDeleted++
        } else {
            MyLog.w(FileUtils::class.java, "Couldn't delete " + file.getAbsolutePath())
        }
        return nDeleted
    }

    /**
     * Accepts null argument
     */
    fun exists(file: File?): Boolean {
        return file?.exists() ?: false
    }

    /**
     * Based on [Backing up your Android SQLite database to the SD card](http://www.screaming-penguin.com/node/7749)
     *
     * @param src
     * @param dst
     * @return true if success
     */
    fun copyFile(anyTag: Any?, src: File?, dst: File): Boolean {
        var sizeIn: Long = -1
        var sizeCopied: Long = 0
        var ok = false
        if (src != null && src.exists()) {
            sizeIn = src.length()
            if (!dst.createNewFile()) {
                MyLog.w(anyTag, "New file was not created: '" + dst.getCanonicalPath() + "'")
            } else if (src.canonicalPath.compareTo(dst.getCanonicalPath()) == 0) {
                MyLog.i(anyTag, "Cannot copy to itself: '" + src.canonicalPath + "'")
            } else {
                FileInputStream(src).use { fileInputStream ->
                    fileInputStream.channel.use { inChannel ->
                        newFileOutputStreamWithRetry(dst)?.use { fileOutputStream ->
                            fileOutputStream.getChannel().use { outChannel ->
                                sizeCopied = inChannel.transferTo(0, inChannel.size(), outChannel)
                                ok = sizeIn == sizeCopied
                            }
                        }
                    }
                }
                dst.setLastModified(src.lastModified())
            }
        }
        MyLog.d(anyTag, "Copied $sizeCopied bytes of $sizeIn")
        return ok
    }

    fun newFileOutputStreamWithRetry(file: File?, append: Boolean = false): FileOutputStream? {
        if (file == null) return null

        return try {
            FileOutputStream(file, append)
        } catch (e: FileNotFoundException) {
            MyLog.i(
                FileUtils::class.java, "Retrying to create FileOutputStream for " +
                    file.getAbsolutePath() + " : " + e.message
            )
            DbUtils.waitMs(FileUtils::class.java, 100)
            FileOutputStream(file, append)
        }
    }

    fun isFileInsideFolder(file: File, folder: File): Boolean = try {
        file.canonicalPath.startsWith(folder.canonicalPath)
    } catch (e: Exception) {
        MyLog.d(
            FileUtils::class.java, "Failed to check path of the file: " + file.absolutePath +
                ". Error message:" + e.message
        )
        false
    }
}
