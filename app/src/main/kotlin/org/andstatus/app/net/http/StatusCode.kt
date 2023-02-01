/*
 * Copyright (c) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

enum class StatusCode(val isHard: Boolean) {
    UNKNOWN(false),
    OK(false),
    UNSUPPORTED_API(true),
    NOT_FOUND(true),
    BAD_REQUEST(true),
    AUTHENTICATION_ERROR(true),
    CREDENTIALS_OF_OTHER_ACCOUNT(true),
    NO_CREDENTIALS_FOR_HOST(true),
    UNAUTHORIZED(false),
    FORBIDDEN(true),
    INTERNAL_SERVER_ERROR(true),
    BAD_GATEWAY(true),
    SERVICE_UNAVAILABLE(false),
    MOVED(true),
    REQUEST_ENTITY_TOO_LARGE(true),
    LENGTH_REQUIRED(true),
    TOO_MANY_REQUESTS(false),
    DELAYED(false),
    CLIENT_ERROR(true),
    SERVER_ERROR(true);

    companion object {
        const val STATUS_CODE_INT_NOT_FOUND = 404

        fun fromResponseCode(responseCode: Int): StatusCode {
            return when (responseCode) {
                200, 201, 304 -> OK
                301, 302, 303, 307 -> MOVED
                400 -> BAD_REQUEST
                401 -> UNAUTHORIZED
                403 -> FORBIDDEN
                STATUS_CODE_INT_NOT_FOUND -> NOT_FOUND
                411 -> LENGTH_REQUIRED
                413 -> REQUEST_ENTITY_TOO_LARGE
                429 -> TOO_MANY_REQUESTS
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
