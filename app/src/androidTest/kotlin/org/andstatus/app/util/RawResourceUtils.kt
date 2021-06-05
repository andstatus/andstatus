/*
 * Based on the example: 
 * http://stackoverflow.com/questions/4087674/android-read-text-raw-resource-file
 */
package org.andstatus.app.util

import android.content.Context
import androidx.annotation.RawRes
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object RawResourceUtils {

    fun getString(@RawRes id: Int): String {
        return String(getBytes(id, InstrumentationRegistry.getInstrumentation().context),
                StandardCharsets.UTF_8)
    }

    /**
     * reads resources regardless of their size
     */
    fun getBytes(@RawRes id: Int, context: Context): ByteArray {
        val resources = context.resources
        val bout = ByteArrayOutputStream()
        val readBuffer = ByteArray(4 * 1024)
        return resources.openRawResource(id).use { inputStream ->
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
}
