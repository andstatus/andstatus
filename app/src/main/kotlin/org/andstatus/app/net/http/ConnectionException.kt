/* 
 * Copyright (c) 2014-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.res.Resources.NotFoundException
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.Taggable
import java.io.IOException
import java.net.URL

/**
 * @author yvolk@yurivolkov.com
 */
class ConnectionException : IOException {

    val httpResult: HttpReadResult?
    val statusCode: StatusCode
    val isHardError: Boolean
    private val url: URL?

    private constructor(result: HttpReadResult) : super(result.logMsg(), result.getException()) {
        httpResult = result
        statusCode = result.statusCode
        url = result.url
        isHardError = statusCode.isHard || isHardFromCause(result.getException())
    }

    constructor(throwable: Throwable?) : this(null, throwable)
    constructor(detailMessage: String?) : this(StatusCode.UNKNOWN, detailMessage)
    constructor(detailMessage: String?, throwable: Throwable?) : this(
        StatusCode.OK,
        detailMessage,
        throwable,
        null,
        false
    )

    constructor(statusCode: StatusCode, detailMessage: String?, url: URL? = null) :
        this(statusCode, detailMessage, null, url, false)

    fun append(toAppend: String?): ConnectionException =
        if (toAppend.isNullOrBlank()) this
        else ConnectionException(statusCode, "$message. $toAppend", cause, url, isHardError)

    private constructor(
        statusCode: StatusCode, detailMessage: String?,
        throwable: Throwable?, url: URL?, isHardIn: Boolean
    ) : super(detailMessage, throwable) {
        httpResult = null
        this.statusCode = statusCode
        this.url = url
        isHardError = isHardIn || statusCode.isHard || isHardFromCause(throwable)
    }

    override fun toString(): String {
        return "Status code: $statusCode; " +
            (if (isHardError) "hard" else "soft") +
            (if (url == null) "; " else "; URL: $url;\n") +
            (if (message.isNullOrBlank()) "" else "$message\n") +
            (if (super.cause != null) "Caused by ${super.cause.toString()}\n" else "")
    }

    companion object {
        private const val serialVersionUID = 1L

        fun of(e: Throwable?, messageIn: String? = null): ConnectionException =
            when (e) {
                is ConnectionException -> e
                is NotFoundException -> fromStatusCode(StatusCode.NOT_FOUND, e.message)
                else -> ConnectionException("Unexpected exception", e)
            }.append(messageIn)

        fun from(result: HttpReadResult): ConnectionException {
            return ConnectionException(result)
        }

        fun loggedHardJsonException(
            anyTag: Any?,
            detailMessage: String?,
            e: Exception?,
            jso: Any?
        ): ConnectionException {
            return loggedJsonException(anyTag, detailMessage, e, jso, true)
        }

        fun loggedJsonException(anyTag: Any?, detailMessage: String?, e: Exception?, jso: Any?): ConnectionException {
            return loggedJsonException(anyTag, detailMessage, e, jso, false)
        }

        private fun loggedJsonException(
            anyTag: Any?, detailMessage: String?, e: Exception?, jso: Any?,
            isHard: Boolean
        ): ConnectionException {
            MyLog.d(anyTag, detailMessage + if (e != null) ": " + e.message else "")
            if (jso != null) {
                val fileName = MyLog.uniqueDateTimeFormatted()
                if (e != null) {
                    val stackTrace = MyLog.getStackTrace(e)
                    MyLog.writeStringToFile(stackTrace, fileName + "_JsonException.txt")
                    MyLog.v(anyTag) { "stack trace: $stackTrace" }
                }
                MyLog.logJson(anyTag, "json_exception", jso, fileName)
            }
            return ConnectionException(StatusCode.OK, Taggable.anyToTag(anyTag) + ": " + detailMessage, e, null, isHard)
        }

        fun fromStatusCode(statusCode: StatusCode, detailMessage: String?): ConnectionException {
            return fromStatusCodeAndThrowable(statusCode, detailMessage, null)
        }

        fun fromStatusCodeAndThrowable(
            statusCode: StatusCode,
            detailMessage: String?,
            throwable: Throwable?
        ): ConnectionException {
            return ConnectionException(statusCode, detailMessage, throwable, null, false)
        }

        fun fromStatusCodeAndHost(statusCode: StatusCode, detailMessage: String?, host2: URL?): ConnectionException {
            return ConnectionException(statusCode, detailMessage, host2)
        }

        fun hardConnectionException(detailMessage: String?, throwable: Throwable? = null): ConnectionException {
            return ConnectionException(StatusCode.UNKNOWN, detailMessage, throwable, null, true)
        }

        private fun isHardFromCause(cause: Throwable?): Boolean = when (cause) {
            is ConnectionException -> cause.isHardError
            is java.net.UnknownHostException -> true
            else -> false
        }

    }
}
