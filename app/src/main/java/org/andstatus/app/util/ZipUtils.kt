/*
 * Copyright (c) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import io.vavr.control.Try
import org.andstatus.app.context.MyStorage
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun zipFiles(sourceFolder: File?, zipped: File?): Try<File?>? {
        try {
            newFileOutputStreamWithRetry(zipped).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    for (file in sourceFolder.listFiles()) {
                        if (!file.isDirectory && !MyStorage.isTempFile(file)) addToZip(file, zos)
                    }
                }
            }
        } catch (e: IOException) {
            return Try.failure(IOException("Error zipping " + sourceFolder.getAbsolutePath() + " folder to " +
                    zipped.getAbsolutePath() + ", " + e.message))
        }
        return Try.success(zipped)
    }

    @Throws(IOException::class)
    private fun addToZip(file: File?, zos: ZipOutputStream?) {
        try {
            FileInputStream(file).use { fis ->
                val zipEntry = ZipEntry(file.getName())
                zipEntry.time = file.lastModified()
                zos.putNextEntry(zipEntry)
                val bytes = ByteArray(MyStorage.FILE_CHUNK_SIZE)
                var length: Int
                while (fis.read(bytes).also { length = it } >= 0) {
                    zos.write(bytes, 0, length)
                }
            }
        } finally {
            zos.closeEntry()
        }
    }

    fun unzipFiles(zipped: File?, targetFolder: File?): Try<String?>? {
        if (!targetFolder.exists() && !targetFolder.mkdir()) {
            return Try.failure(IOException("Couldn't create folder: '" + targetFolder.getAbsolutePath() + "'"))
        }
        val unzipped: MutableList<String?> = ArrayList()
        val skipped: MutableList<String?> = ArrayList()
        try {
            ZipFile(zipped).use { zipFile ->
                val enu: Enumeration<*>? = zipFile.entries()
                while (enu.hasMoreElements()) {
                    val zipEntry = enu.nextElement() as ZipEntry
                    val file = File(targetFolder, zipEntry.name)
                    if (FileUtils.isFileInsideFolder(file, targetFolder)) {
                        zipFile.getInputStream(zipEntry).use { `is` ->
                            newFileOutputStreamWithRetry(file).use { fos ->
                                val bytes = ByteArray(MyStorage.FILE_CHUNK_SIZE)
                                var length: Int
                                while (`is`.read(bytes).also { length = it } >= 0) {
                                    fos.write(bytes, 0, length)
                                }
                            }
                        }
                        unzipped.add(file.name)
                        file.setLastModified(zipEntry.time)
                    } else {
                        skipped.add(file.absolutePath)
                        MyLog.i(ZipUtils::class.java,
                                "ZipEntry skipped as file is outside target folder: " + file.absolutePath)
                    }
                }
            }
        } catch (e: Exception) {
            return Try.failure(Exception("Failed to unzip " + zipped.getName() + ", error message: " + e.message))
        }
        return Try.success("Unzipped " + unzipped.size + " files from '" + zipped.getName() + "' file" +
                " to '" + targetFolder.getName() + "' folder." +
                if (skipped.isEmpty()) "" else ", skipped " + skipped.size + " files: " + skipped)
    }
}