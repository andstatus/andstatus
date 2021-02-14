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

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

object FileDescriptorUtils {
    private val TAG: String? = FileDescriptorUtils::class.java.simpleName
    fun getJSONObject(fileDescriptor: FileDescriptor?): JSONObject? {
        var jso: JSONObject? = null
        val fileString = utf8FileDescriptor2String(fileDescriptor)
        if (!StringUtil.isEmpty(fileString)) {
            jso = try {
                JSONObject(fileString)
            } catch (e: JSONException) {
                MyLog.v(TAG, e)
                null
            }
        }
        if (jso == null) {
            jso = JSONObject()
        }
        return jso
    }

    fun utf8FileDescriptor2String(fileDescriptor: FileDescriptor?): String? {
        return String(getBytes(fileDescriptor), Charset.forName("UTF-8"))
    }

    fun getBytes(fileDescriptor: FileDescriptor?): ByteArray? {
        val bout = ByteArrayOutputStream()
        if (fileDescriptor != null) {
            val `is`: InputStream = FileInputStream(fileDescriptor)
            val readBuffer = ByteArray(4 * 1024)
            try {
                var read: Int
                do {
                    read = `is`.read(readBuffer, 0, readBuffer.size)
                    if (read == -1) {
                        break
                    }
                    bout.write(readBuffer, 0, read)
                } while (true)
                return bout.toByteArray()
            } catch (e: IOException) {
                MyLog.v(TAG, e)
            } finally {
                closeSilently(`is`)
            }
        }
        return ByteArray(0)
    }
}