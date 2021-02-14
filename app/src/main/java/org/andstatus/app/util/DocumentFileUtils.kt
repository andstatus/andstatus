/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.andstatus.app.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object DocumentFileUtils {
    private val TAG: String? = DocumentFileUtils::class.java.simpleName
    fun getJSONObject(context: Context?, fileDescriptor: DocumentFile?): JSONObject? {
        var jso: JSONObject? = null
        val fileString = uri2String(context, fileDescriptor.getUri())
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

    private fun uri2String(context: Context?, uri: Uri?): String? {
        val BUFFER_LENGTH = 10000
        try {
            context.getContentResolver().openInputStream(uri).use { `is` ->
                InputStreamReader(`is`, StandardCharsets.UTF_8).use { reader ->
                    val buffer = CharArray(BUFFER_LENGTH)
                    val builder = StringBuilder()
                    var count: Int
                    while (reader.read(buffer).also { count = it } != -1) {
                        builder.append(buffer, 0, count)
                    }
                    return builder.toString()
                }
            }
        } catch (e: Exception) {
            val msg = """Error while reading ${context.getText(R.string.app_name)} settings from $uri
${e.message}"""
            Log.w(TAG, msg, e)
        }
        return ""
    }

    fun getJSONArray(context: Context?, fileDescriptor: DocumentFile?): JSONArray? {
        var jso: JSONArray? = null
        val fileString = uri2String(context, fileDescriptor.getUri())
        if (!StringUtil.isEmpty(fileString)) {
            jso = try {
                JSONArray(fileString)
            } catch (e: JSONException) {
                MyLog.v(TAG, e)
                null
            }
        }
        if (jso == null) {
            jso = JSONArray()
        }
        return jso
    }

    /** Reads up to 'size' bytes, starting from 'offset'  */
    @Throws(IOException::class)
    fun getBytes(context: Context?, file: DocumentFile?, offset: Int, size: Int): ByteArray? {
        if (file == null) return ByteArray(0)
        context.getContentResolver().openInputStream(file.uri).use { `is` -> return FileUtils.getBytes(`is`, file.uri.path, offset, size) }
    }
}