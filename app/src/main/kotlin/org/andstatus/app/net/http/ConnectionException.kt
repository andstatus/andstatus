/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.util.MyStringBuilder
import java.io.IOException
import java.net.URL

/**
 * @author yvolk@yurivolkov.com
 */
class ConnectionException : IOException {
    enum class StatusCode {
        UNKNOWN, OK, UNSUPPORTED_API, NOT_FOUND, BAD_REQUEST, AUTHENTICATION_ERROR, CREDENTIALS_OF_OTHER_ACCOUNT, NO_CREDENTIALS_FOR_HOST, UNAUTHORIZED, FORBIDDEN, INTERNAL_SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE, MOVED, REQUEST_ENTITY_TOO_LARGE, LENGTH_REQUIRED, CLIENT_ERROR, SERVER_ERROR;

        companion object {
            fun fromResponseCode(responseCode: Int): StatusCode {
                return when (responseCode) {
                    200, 201, 304 -> OK
                    301, 302, 303, 307 -> MOVED
                    400 -> BAD_REQUEST
                    401 -> UNAUTHORIZED
                    403 -> FORBIDDEN
                    404 -> NOT_FOUND
                    411 -> LENGTH_REQUIRED
                    413 -> REQUEST_ENTITY_TOO_LARGE
                    500 -> INTERNAL_SERVER_ERROR
                    502 -> BAD_GATEWAY
                    503 -> SERVICE_UNAVAILABLE
                    else -> {
                        if (responseCode >= 500) {
                            return SERVER_ERROR
                        } else if (responseCode >= 400) {
                            return CLIENT_ERROR
                        }
                        UNKNOWN
                    }
                }
            }
        }
    }

    private val statusCode: StatusCode?
    private val isHardError: Boolean
    private val url: URL?

    private constructor(result: HttpReadResult) : super(result.logMsg(), result.getException()) {
        statusCode = result.getStatusCode()
        url = result.url
        isHardError = isHardFromStatusCode(isHardFromCause(result.getException()), statusCode)
    }

    constructor(throwable: Throwable?) : this(null, throwable) {}
    constructor(detailMessage: String?) : this(StatusCode.UNKNOWN, detailMessage) {}
    constructor(detailMessage: String?, throwable: Throwable?) : this(StatusCode.OK, detailMessage, throwable, null, false) {}

    @JvmOverloads
    constructor(statusCode: StatusCode?, detailMessage: String?, url: URL? = null) : this(statusCode, detailMessage, null, url, false) {
    }

    fun append(toAppend: String?): ConnectionException {
        return ConnectionException(statusCode,
                message + (if (!message.isNullOrEmpty()) ". " else "") + toAppend,
                cause, url, isHardError)
    }

    private constructor(statusCode: StatusCode?, detailMessage: String?,
                        throwable: Throwable?, url: URL?, isHardIn: Boolean) : super(detailMessage, throwable) {
        this.statusCode = statusCode
        this.url = url
        isHardError = isHardFromStatusCode(isHardIn || isHardFromCause(throwable), statusCode)
    }

    fun getStatusCode(): StatusCode? {
        return statusCode
    }

    override fun toString(): String {
        return "Status code: ${statusCode}; " +
                (if (isHardError) "hard" else "soft") +
                (if (url == null) "" else "; URL: $url") +
                "; \n${super.message}\n" +
                (if (super.cause != null) "Caused by ${super.cause.toString()}\n" else "")
    }

    fun isHardError(): Boolean {
        return isHardError
    }

    companion object {
        private const val serialVersionUID = 1L
        fun of(e: Throwable?): ConnectionException {
            if (e is ConnectionException) return e
            return if (e is NotFoundException) {
                fromStatusCode(StatusCode.NOT_FOUND, e.message)
            } else ConnectionException("Unexpected exception", e)
        }

        fun from(result: HttpReadResult): ConnectionException {
            return ConnectionException(result)
        }

        fun loggedHardJsonException(objTag: Any?, detailMessage: String?, e: Exception?, jso: Any?): ConnectionException {
            return loggedJsonException(objTag, detailMessage, e, jso, true)
        }

        fun loggedJsonException(objTag: Any?, detailMessage: String?, e: Exception?, jso: Any?): ConnectionException {
            return loggedJsonException(objTag, detailMessage, e, jso, false)
        }

        private fun loggedJsonException(objTag: Any?, detailMessage: String?, e: Exception?, jso: Any?,
                                        isHard: Boolean): ConnectionException {
            MyLog.d(objTag, detailMessage + if (e != null) ": " + e.message else "")
            if (jso != null) {
                val fileName = MyLog.uniqueDateTimeFormatted()
                if (e != null) {
                    val stackTrace = MyLog.getStackTrace(e)
                    MyLog.writeStringToFile(stackTrace, fileName + "_JsonException.txt")
                    MyLog.v(objTag) { "stack trace: $stackTrace" }
                }
                MyLog.logJson(objTag, "json_exception", jso, fileName)
            }
            return ConnectionException(StatusCode.OK, MyStringBuilder.objToTag(objTag) + ": " + detailMessage, e, null, isHard)
        }

        fun fromStatusCode(statusCode: StatusCode?, detailMessage: String?): ConnectionException {
            return fromStatusCodeAndThrowable(statusCode, detailMessage, null)
        }

        fun fromStatusCodeAndThrowable(statusCode: StatusCode?, detailMessage: String?, throwable: Throwable?): ConnectionException {
            return ConnectionException(statusCode, detailMessage, throwable, null, false)
        }

        fun fromStatusCodeAndHost(statusCode: StatusCode?, detailMessage: String?, host2: URL?): ConnectionException {
            return ConnectionException(statusCode, detailMessage, host2)
        }

        fun hardConnectionException(detailMessage: String?, throwable: Throwable?): ConnectionException {
            return ConnectionException(StatusCode.OK, detailMessage, throwable, null, true)
        }

        private fun isHardFromCause(cause: Throwable?): Boolean = when (cause) {
            null -> false
            is ConnectionException -> cause.isHardError
            is java.net.UnknownHostException -> true
            else -> false
        }

        private fun isHardFromStatusCode(isHardIn: Boolean, statusCode: StatusCode?): Boolean {
            return isHardIn || statusCode != StatusCode.UNKNOWN && statusCode != StatusCode.OK
        }
    }
}
