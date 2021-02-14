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
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.I18n
import org.andstatus.app.util.JsonUtils
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.*
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.UnaryOperator
import java.util.stream.Collector
import java.util.stream.Collectors
import java.util.stream.Stream

class HttpReadResult(val request: HttpRequest?) {
    private var urlString: String? = ""
    private var url: URL? = null
    private var headers: MutableMap<String?, MutableList<String?>?>? = emptyMap<String?, MutableList<String?>?>()
    var redirected = false
    private var location: Optional<String?>? = Optional.empty()
    var retriedWithoutAuthentication = false
    private val logBuilder: StringBuilder? = StringBuilder()
    private var exception: Exception? = null
    var strResponse: String? = ""
    var statusLine: String? = ""
    private var intStatusCode = 0
    private var statusCode: StatusCode? = StatusCode.UNKNOWN
    fun getHeaders(): MutableMap<String?, MutableList<String?>?>? {
        return headers
    }

    fun <T> setHeaders(headers: Stream<T?>?, keyMapper: Function<T?, String?>?, valueMapper: Function<T?, String?>?): HttpReadResult? {
        return setHeaders(headers.collect(toHeaders(keyMapper, valueMapper)))
    }

    private fun setHeaders(headers: MutableMap<String?, MutableList<String?>?>): HttpReadResult? {
        // Header field names are case-insensitive, see https://stackoverflow.com/a/5259004/297710
        val lowercaseKeysMap: MutableMap<String?, MutableList<String?>?> = HashMap()
        headers.entries.forEach(Consumer { entry: MutableMap.MutableEntry<String?, MutableList<String?>?>? -> lowercaseKeysMap[if (entry.key == null) null else entry.key.toLowerCase()] = entry.value })
        this.headers = lowercaseKeysMap
        location = Optional.ofNullable(this.headers.get("location"))
                .orElse(emptyList<String?>()).stream()
                .filter { obj: String? -> StringUtil.nonEmpty() }
                .findFirst()
                .map { l: String? -> l.replace("%3F", "?") }
        return this
    }

    fun getLocation(): Optional<String?>? {
        return location
    }

    fun setUrl(urlIn: String?): HttpReadResult? {
        if (!StringUtil.isEmpty(urlIn) && !urlString.contentEquals(urlIn)) {
            urlString = urlIn
            url = try {
                URL(urlIn)
            } catch (e: MalformedURLException) {
                UrlUtils.MALFORMED
            }
        }
        return this
    }

    fun setStatusCode(intStatusCodeIn: Int) {
        intStatusCode = intStatusCodeIn
        statusCode = StatusCode.Companion.fromResponseCode(intStatusCodeIn)
    }

    fun getStatusCode(): StatusCode? {
        return statusCode
    }

    fun getUrl(): String? {
        return urlString
    }

    fun getUrlObj(): URL? {
        return url
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

    fun logMsg(): String? {
        return logBuilder.toString()
    }

    override fun toString(): String {
        return (logMsg()
                + (if (statusCode == StatusCode.OK || StringUtil.isEmpty(statusLine)) "" else "; statusLine:'$statusLine'")
                + (if (intStatusCode == 0) "" else "; statusCode:$statusCode ($intStatusCode)")
                + (if (redirected) "; redirected" else "")
                + "; url:'" + urlString + "'"
                + (if (retriedWithoutAuthentication) "; retried without auth" else "")
                + (if (StringUtil.isEmpty(strResponse)) "" else "; response:'" + I18n.trimTextAt(strResponse, 40) + "'")
                + location.map(Function { str: String? -> "; location:'$str'" }).orElse("")
                + (if (exception == null) "" else """
     ; 
     exception: ${exception.toString()}
     """.trimIndent())
                + "\nRequested: " + request)
    }

    fun getJsonArrayInObject(arrayName: String?): Try<JSONArray?>? {
        val method = "getRequestArrayInObject"
        return getJsonObject()
                .flatMap(CheckedFunction<JSONObject?, Try<out JSONArray?>?> { jso: JSONObject? ->
                    var jArr: JSONArray? = null
                    if (jso != null) {
                        jArr = try {
                            jso.getJSONArray(arrayName)
                        } catch (e: JSONException) {
                            return@flatMap Try.failure<JSONArray?>(ConnectionException.Companion.loggedJsonException(this, "$method, arrayName=$arrayName", e, jso))
                        }
                    }
                    Try.success(jArr)
                })
    }

    fun getJsonObject(): Try<JSONObject?>? {
        return innerGetJsonObject(strResponse)
    }

    fun getResponse(): String? {
        return strResponse
    }

    private fun innerGetJsonObject(strJson: String?): Try<JSONObject?>? {
        val method = "getJsonObject; "
        var jso: JSONObject? = null
        try {
            if (StringUtil.isEmpty(strJson)) {
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
            return Try.failure(ConnectionException.Companion.loggedJsonException(this, method + I18n.trimTextAt(toString(), 500), e, strJson))
        }
        return Try.success(jso)
    }

    fun getJsonArray(): Try<JSONArray?>? {
        return getJsonArray("items")
    }

    fun getJsonArray(arrayKey: String?): Try<JSONArray?>? {
        val method = "getJsonArray; "
        if (StringUtil.isEmpty(strResponse)) {
            MyLog.v(this) { "$method; response is empty" }
            return Try.success(JSONArray())
        }
        val jst = JSONTokener(strResponse)
        var jsa: JSONArray? = null
        try {
            var obj = jst.nextValue()
            if (obj is JSONObject) {
                val jso = obj as JSONObject
                if (jso.has(arrayKey)) {
                    obj = try {
                        jso.getJSONArray(arrayKey)
                    } catch (e: JSONException) {
                        return Try.failure(ConnectionException.Companion.loggedJsonException(this, "'" + arrayKey + "' is not an array?!"
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
            return Try.failure(ConnectionException.Companion.loggedJsonException(this, method + toString(), e, strResponse))
        } catch (e: Exception) {
            return Try.failure(ConnectionException.Companion.loggedHardJsonException(this, method + toString(), e, strResponse))
        }
        return Try.success(jsa)
    }

    fun getExceptionFromJsonErrorResponse(): ConnectionException? {
        var statusCode = statusCode
        var error: String? = "?"
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

    fun setException(e: Throwable?): HttpReadResult? {
        TryUtils.checkException(e)
        if (exception == null) {
            exception = if (e is Exception) e as Exception? else ConnectionException("Unexpected exception", e)
        }
        return this
    }

    fun getException(): Exception? {
        return exception
    }

    fun tryToParse(): Try<HttpReadResult?>? {
        if (exception is ConnectionException) {
            return Try.failure(exception)
        }
        if (isStatusOk()) {
            if (request.isFileTooLarge()) {
                setException(ConnectionException.Companion.hardConnectionException(
                        "File, downloaded from \"" + urlString + "\", is too large: "
                                + Formatter.formatShortFileSize(MyContextHolder.Companion.myContextHolder.getNow().context(), request.fileResult.length()),
                        null))
                return Try.failure(exception)
            }
            MyLog.v(this) { this.toString() }
        } else {
            if (!StringUtil.isEmpty(strResponse)) {
                setException(getExceptionFromJsonErrorResponse())
            } else {
                setException(ConnectionException.Companion.fromStatusCodeAndThrowable(statusCode, toString(), exception))
            }
            return Try.failure(exception)
        }
        return Try.success(this)
    }

    fun isStatusOk(): Boolean {
        return exception == null && (statusCode == StatusCode.OK || statusCode == StatusCode.UNKNOWN)
    }

    fun toFailure(): Try<HttpReadResult?>? {
        return Try.failure(ConnectionException.Companion.from(this))
    }

    fun logResponse(): HttpReadResult? {
        if (strResponse != null && MyPreferences.isLogNetworkLevelMessages()) {
            val objTag: Any = "response"
            MyLog.logNetworkLevelMessage(objTag, request.getLogName(), strResponse,
                    MyStringBuilder.Companion.of("")
                            .atNewLine("logger-URL", urlString)
                            .atNewLine("logger-account", request.connectionData().accountName.name)
                            .atNewLine("logger-authenticated", java.lang.Boolean.toString(authenticate()))
                            .apply(UnaryOperator { builder: MyStringBuilder? -> appendHeaders(builder) }).toString())
        }
        return this
    }

    fun appendHeaders(builder: MyStringBuilder?): MyStringBuilder? {
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

    fun readStream(msgLog: String?, supplier: CheckedFunction<Void?, InputStream?>?): Try<HttpReadResult?>? {
        return HttpConnectionUtils.readStream(this, msgLog, supplier)
    }

    companion object {
        private fun <T> toHeaders(
                fieldNameMapper: Function<T?, String?>?,
                valueMapper: Function<T?, String?>?): Collector<T?, *, MutableMap<String?, MutableList<String?>?>?>? {
            return Collectors.toMap(
                    fieldNameMapper,
                    Function<T?, MutableList<String?>?> { v: T? -> listOf(valueMapper.apply(v)) },
                    BinaryOperator { a: MutableList<String?>?, b: MutableList<String?>? ->
                        val out: MutableList<String?> = ArrayList(a)
                        out.addAll(b)
                        out
                    })
        }
    }

    init {
        setUrl(request.uri.toString())
    }
}