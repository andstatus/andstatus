/*
 * Copyright (C) 2013-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.net.http

import android.text.format.Formatter
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.service.ConnectionRequired
import org.andstatus.app.util.FileUtils.newFileOutputStreamWithRetry
import org.andstatus.app.util.StopWatch
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object HttpConnectionUtils {
    private val UTF_8: String = "UTF-8"
    private const val BUFFER_LENGTH = 20000
    @Throws(ConnectionException::class)
    fun encode(params: MutableMap<String, String>): String {
        return try {
            val sb = StringBuilder()
            for ((key, value) in params) {
                if (sb.length > 0) {
                    sb.append('&')
                }
                sb.append(URLEncoder.encode(key, UTF_8))
                sb.append('=')
                sb.append(URLEncoder.encode(value, UTF_8))
            }
            sb.toString()
        } catch (e: UnsupportedEncodingException) {
            throw ConnectionException("Encoding params", e)
        }
    }

    fun readStream(result: HttpReadResult, msgLog: String?, supplier: CheckedFunction<Void, InputStream>): Try<HttpReadResult> {
        try {
            supplier.apply(null).use { inputStream ->
                if (inputStream == null) {
                    val exception: ConnectionException = ConnectionException.fromStatusCode(
                            StatusCode.CLIENT_ERROR, "$msgLog Input stream is null")
                    return result.setException(exception).toFailure()
                }
                return if (result.request.fileResult == null || !result.isStatusOk()) readStreamToString(result, inputStream)
                else readStreamToFile(result, inputStream)
            }
        } catch (e: Exception) {
            return result.setException(if (msgLog.isNullOrEmpty()) e else ConnectionException(msgLog, e))
                    .toFailure()
        }
    }

    @Throws(IOException::class)
    private fun readStreamToString(resultIn: HttpReadResult, inputStream: InputStream): Try<HttpReadResult> {
        val buffer = CharArray(BUFFER_LENGTH)
        val checker = ReadChecker(resultIn)
        val builder = StringBuilder()
        var count: Int
        try {
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                while (reader.read(buffer).also { count = it } != -1) {
                    if (checker.isFailed(count)) return resultIn.toFailure()
                    builder.append(buffer, 0, count)
                }
            }
        } finally {
            closeSilently(inputStream)
        }
        resultIn.strResponse = builder.toString()
        return Try.success(resultIn)
    }

    @Throws(IOException::class)
    private fun readStreamToFile(resultIn: HttpReadResult, inputStream: InputStream): Try<HttpReadResult> {
        val buffer = ByteArray(BUFFER_LENGTH)
        val checker = ReadChecker(resultIn)
        var count: Int
        try {
            newFileOutputStreamWithRetry(resultIn.request.fileResult)?.use { fileOutputStream ->
                BufferedOutputStream(fileOutputStream).use { out ->
                    while (inputStream.read(buffer).also { count = it } != -1) {
                        if (checker.isFailed(count)) return resultIn.toFailure()
                        out.write(buffer, 0, count)
                    }
                }
            }
        } finally {
            closeSilently(inputStream)
        }
        return Try.success(resultIn)
    }

    private class ReadChecker(val result: HttpReadResult) {
        val stopWatch: StopWatch = StopWatch.createStarted()
        val request: HttpRequest = result.request
        var size: Long = 0
        fun isFailed(count: Int): Boolean {
            size += count.toLong()
            if (!request.myContext().isReady()) {
                result.setException(ConnectionException("App restarted?!"))
                return true
            }
            if (size > request.maxSizeBytes) {
                result.setException(ConnectionException.hardConnectionException(
                    "File, downloaded from '${result.url}', is too large: at least " +
                            Formatter.formatShortFileSize(request.myContext().context(), size),
                        null))
                return true
            }
            if (request.connectionRequired != ConnectionRequired.ANY && stopWatch.hasPassed(5000)) {
                val connectionState = request.myContext().getConnectionState()
                if (!request.connectionRequired.isConnectionStateOk(connectionState)) {
                    result.setException(
                            ConnectionException("Expected '" + request.connectionRequired +
                                    "', but was '" + connectionState + "' connection"))
                    return true
                }
            }
            return false
        }

    }
}
