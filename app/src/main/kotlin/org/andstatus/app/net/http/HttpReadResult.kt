/* Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.TextUtils
import android.text.format.Formatter
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.I18n
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MagnetUri.Companion.getDownloadableUrl
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TryUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.net.URL
import java.util.*
import java.util.stream.Collector
import java.util.stream.Collectors
import java.util.stream.Stream

class HttpReadResult(val request: HttpRequest) {
    var url: URL? = null
        private set
    private var headers: MutableMap<String, MutableList<String>> = mutableMapOf()
    var redirected = false
    private var location: Optional<String> = Optional.empty()
    var retriedWithoutAuthentication = false
    private val logBuilder: StringBuilder = StringBuilder()
    private var exception: Exception? = null
    var strResponse: String = ""
    var statusLine: String = ""
    private var intStatusCode = 0
    private var statusCode: StatusCode? = StatusCode.UNKNOWN

    fun requiredUrl(errorMessage: String): URL? = url ?: let {
        setException(Exception("Mo URL for $errorMessage"))
        null
    }

    fun getHeaders(): MutableMap<String, MutableList<String>> {
        return headers
    }

    fun <T> setHeaders(headers: Stream<T>, keyMapper: (T) -> String?, valueMapper: (T) -> String?): HttpReadResult {
        return setHeaders(headers.collect(toHeaders(keyMapper, valueMapper)))
    }

    private fun setHeaders(headers: MutableMap<String, MutableList<String>>): HttpReadResult {
        // Header field names are case-insensitive, see https://stackoverflow.com/a/5259004/297710
        val lowercaseKeysMap: MutableMap<String, MutableList<String>> = HashMap()
        headers.entries.forEach { entry ->
            lowercaseKeysMap[if (entry.key.isEmpty()) "" else entry.key.toLowerCase()] = entry.value
        }
        this.headers = lowercaseKeysMap
        location = Optional.ofNullable(this.headers.get("location"))
                .orElse(mutableListOf<String>()).stream()
                .filter { obj: String -> obj.isNotEmpty() }
                .findFirst()
                .map { l: String -> l.replace("%3F", "?") }
        return this
    }

    fun getLocation(): Optional<String> {
        return location
    }

    fun setUrl(urlIn: URL?): HttpReadResult {
        if (urlIn != null && urlIn != url) {
            url = urlIn
        }
        return this
    }

    fun setStatusCode(intStatusCodeIn: Int) {
        intStatusCode = intStatusCodeIn
        statusCode = StatusCode.fromResponseCode(intStatusCodeIn)
    }

    fun getStatusCode(): StatusCode? {
        return statusCode
    }

    fun appendToLog(chars: CharSequence?) {
        if (TextUtils.isEmpty(chars)) {
            return
        }
        if (logBuilder.length > 0) {
            logBuilder.append("; ")
        }
        logBuilder.append(chars)
    }

    fun logMsg(): String {
        return logBuilder.toString()
    }

    override fun toString(): String {
        return (logMsg()
                + (if (statusCode == StatusCode.OK || statusLine.isEmpty()) "" else "; statusLine:'$statusLine'")
                + (if (intStatusCode == 0) "" else "; statusCode:$statusCode ($intStatusCode)")
                + (if (redirected) "; redirected" else "")
                + "; url:'$url'"
                + (if (retriedWithoutAuthentication) "; retried without auth" else "")
                + (if (strResponse.isEmpty()) "" else "; response:'" + I18n.trimTextAt(strResponse, 40) + "'")
                + location.map { str: String -> "; location:'$str'" }.orElse("")
                + (if (exception == null) "" else ";\nexception: $exception")
                + "\nRequested: " + request)
    }

    fun getJsonArrayInObject(arrayName: String): Try<JSONArray> {
        val method = "getRequestArrayInObject"
        return getJsonObject()
                .flatMap { jso: JSONObject? ->
                    if (jso != null) {
                        try {
                            Try.success(jso.getJSONArray(arrayName))
                        } catch (e: JSONException) {
                            Try.failure(ConnectionException.loggedJsonException(this, "$method, arrayName=$arrayName", e, jso))
                        }
                    } else Try.success(JSONArray())
                }
    }

    fun getJsonObject(): Try<JSONObject> {
        return innerGetJsonObject(strResponse)
    }

    fun getResponse(): String {
        return strResponse
    }

    private fun innerGetJsonObject(strJson: String?): Try<JSONObject> {
        val method = "getJsonObject; "
        val jso: JSONObject?
        try {
            if (strJson.isNullOrEmpty()) {
                jso = JSONObject()
            } else {
                jso = JSONObject(strJson)
                val error = JsonUtils.optString(jso, "error")
                if ("Could not authenticate you." == error) {
                    appendToLog("error:$error")
                    return Try.failure(ConnectionException(toString()))
                }
            }
        } catch (e: JSONException) {
            return Try.failure(ConnectionException.loggedJsonException(this, method + I18n.trimTextAt(toString(), 500), e, strJson))
        }
        return Try.success(jso)
    }

    fun getJsonArray(): Try<JSONArray> {
        return getJsonArray("items")
    }

    fun getJsonArray(arrayKey: String): Try<JSONArray> {
        val method = "getJsonArray; "
        if (strResponse.isEmpty()) {
            MyLog.v(this) { "$method; response is empty" }
            return Try.success(JSONArray())
        }
        val jst = JSONTokener(strResponse)
        val jsa: JSONArray?
        try {
            var obj = jst.nextValue()
            if (obj is JSONObject) {
                val jso = obj
                if (jso.has(arrayKey)) {
                    obj = try {
                        jso.getJSONArray(arrayKey)
                    } catch (e: JSONException) {
                        return Try.failure(ConnectionException.loggedJsonException(this, "'" + arrayKey + "' is not an array?!"
                                + method + toString(), e, jso))
                    }
                } else {
                    val iterator = jso.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        val obj2 = jso[key]
                        if (obj2 is JSONArray) {
                            MyLog.v(this) { "$method; found array inside '$key' object" }
                            obj = obj2
                            break
                        }
                    }
                }
            }
            jsa = obj as JSONArray
        } catch (e: JSONException) {
            return Try.failure(ConnectionException.loggedJsonException(this, method + toString(), e, strResponse))
        } catch (e: Exception) {
            return Try.failure(ConnectionException.loggedHardJsonException(this, method + toString(), e, strResponse))
        }
        return Try.success(jsa)
    }

    fun getExceptionFromJsonErrorResponse(): ConnectionException {
        var statusCode = statusCode
        var error = "?"
        return if (TextUtils.isEmpty(strResponse)) {
            ConnectionException(statusCode, "Empty response; " + toString())
        } else try {
            val jsonError = JSONObject(strResponse)
            error = JsonUtils.optString(jsonError, "error", error)
            if (statusCode == StatusCode.UNKNOWN && error.contains("not found")) {
                statusCode = StatusCode.NOT_FOUND
            }
            ConnectionException(statusCode, "Error='" + error + "'; " + toString())
        } catch (e: JSONException) {
            ConnectionException(statusCode, toString())
        }
    }

    fun setException(e: Throwable?): HttpReadResult {
        TryUtils.checkException(e)
        if (exception == null) {
            exception = if (e is Exception) e else ConnectionException("Unexpected exception", e)
        }
        return this
    }

    fun getException(): Exception? {
        return exception
    }

    fun tryToParse(): Try<HttpReadResult> {
        if (exception is ConnectionException) {
            return Try.failure(exception)
        }
        if (isStatusOk()) {
            if (request.isFileTooLarge()) {
                setException(ConnectionException.hardConnectionException(
                        "File, downloaded from '$url', is too large: "
                                + Formatter.formatShortFileSize( MyContextHolder.myContextHolder.getNow().context,
                                request.fileResult?.length() ?: 0),
                        null))
                return Try.failure(exception)
            }
            MyLog.v(this) { this.toString() }
        } else {
            if (strResponse.isNotEmpty()) {
                setException(getExceptionFromJsonErrorResponse())
            } else {
                setException(ConnectionException.fromStatusCodeAndThrowable(statusCode, toString(), exception))
            }
            return Try.failure(exception)
        }
        return Try.success(this)
    }

    fun isStatusOk(): Boolean {
        return exception == null && (statusCode == StatusCode.OK || statusCode == StatusCode.UNKNOWN)
    }

    fun toFailure(): Try<HttpReadResult> {
        return Try.failure(ConnectionException.from(this))
    }

    fun logResponse(): HttpReadResult {
        if (MyPreferences.isLogNetworkLevelMessages()) {
            val objTag: Any = "response"
            MyLog.logNetworkLevelMessage(objTag, request.getLogName(), strResponse,
                    MyStringBuilder.of("")
                            .atNewLine("logger-URL", url.toString())
                            .atNewLine("logger-account", request.connectionData().getAccountName().name)
                            .atNewLine("logger-authenticated", java.lang.Boolean.toString(authenticate()))
                            .apply { builder: MyStringBuilder -> appendHeaders(builder) }.toString())
        }
        return this
    }

    fun appendHeaders(builder: MyStringBuilder): MyStringBuilder {
        builder.atNewLine("Headers:")
        for ((key, value) in getHeaders()) {
            builder.atNewLine(key, value.toString())
        }
        return builder
    }

    fun authenticate(): Boolean {
        return !retriedWithoutAuthentication && request.authenticate
    }

    fun noMoreHttpRetries(): Boolean {
        if (authenticate() && request.apiRoutine == ApiRoutineEnum.DOWNLOAD_FILE) {
            onRetryWithoutAuthentication()
            return false
        }
        return true
    }

    private fun onRetryWithoutAuthentication() {
        retriedWithoutAuthentication = true
        appendToLog("Retrying without authentication" + if (exception == null) "" else ", exception: $exception")
        exception = null
        MyLog.v(this) { this.toString() }
    }

    fun readStream(msgLog: String?, supplier: CheckedFunction<Unit, InputStream>): Try<HttpReadResult> {
        return HttpConnectionUtils.readStream(this, msgLog, supplier)
    }

    companion object {
        private fun <T> toHeaders(
                keyMapper: (T) -> String?,
                valueMapper: (T) -> String?): Collector<T, *, MutableMap<String, MutableList<String>>> {
            return Collectors.toMap(
                    { e: T -> keyMapper(e) ?: "" },
                    { e: T -> valueMapper(e)?.let { mutableListOf(it) } ?: mutableListOf() },
                    { a: MutableList<String>, b: MutableList<String> ->
                        val out: MutableList<String> = ArrayList(a)
                        out.addAll(b)
                        out
                    })
        }
    }

    init {
        setUrl(request.uri.getDownloadableUrl())
    }
}
