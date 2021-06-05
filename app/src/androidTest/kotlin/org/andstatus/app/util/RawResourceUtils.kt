/*
 * Based on the example: 
 * http://stackoverflow.com/questions/4087674/android-read-text-raw-resource-file
 */
package org.andstatus.app.util

import android.content.Context
import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import io.vavr.control.Try
import org.andstatus.app.context.MyStorage
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

object RawResourceUtils {

    fun getString(@RawRes id: Int): String {
        return String(getBytes(id, InstrumentationRegistry.getInstrumentation().context),
                StandardCharsets.UTF_8)
    }

    /**
     * reads resources regardless of their size
     */
    private fun getBytes(@RawRes resourceId: Int, context: Context): ByteArray {
        val resources = context.resources
        val bout = ByteArrayOutputStream()
        val readBuffer = ByteArray(MyStorage.FILE_CHUNK_SIZE)
        return resources.openRawResource(resourceId).use { inputStream ->
            var read: Int
            do {
                read = inputStream.read(readBuffer, 0, readBuffer.size)
                if (read == -1) {
                    break
                }
                bout.write(readBuffer, 0, read)
            } while (true)
            bout.toByteArray()
        }
    }

    fun rawResourceToFile(@RawRes resourceId: Int, outFile: File): Try<File> {
        val context = InstrumentationRegistry.getInstrumentation().context
        try {
            FileUtils.newFileOutputStreamWithRetry(outFile).use { fos ->
                context.resources.openRawResource(resourceId).use { inputStream ->
                    val buffer = ByteArray(MyStorage.FILE_CHUNK_SIZE)
                    var count: Int
                    BufferedOutputStream(fos).use { out ->
                        while (inputStream.read(buffer).also { count = it } != -1) {
                            out.write(buffer, 0, count)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return Try.failure(
                IOException("Error copying resource id:$resourceId to " + outFile.absolutePath + ", " + e.message)
            )
        }
        return Try.success(outFile)
    }

}
