/*
 * Copyright (C) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import io.vavr.control.Try
import org.andstatus.app.net.http.HttpReadResult.Companion.toHttpReadResult
import org.andstatus.app.service.QueueAccessor.Companion.MIN_RETRY_PERIOD_SECONDS
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

object Throttler {
    val delays: MutableMap<String, Long> = ConcurrentHashMap()

    fun beforeExecution(request: HttpRequest): Try<HttpRequest> =
        delays.get(request.hostAndPort)?.let { delayedTill ->
            if (delayedTill > System.currentTimeMillis()) delayedTill else null
        }
            ?.let { request.newResult().delayTill(it).toTryRequest() } ?: Try.success(request)

    fun afterExecution(tryResult: Try<HttpReadResult>) {
        tryResult.toHttpReadResult()?.let(Throttler::afterExecution)
    }

    private fun afterExecution(result: HttpReadResult) {
        if (result.statusCode == StatusCode.DELAYED) return

        (retryAfterDate(result)
            ?: rateLimitDate(result)
            ?: when (result.statusCode) {
                StatusCode.TOO_MANY_REQUESTS, StatusCode.SERVICE_UNAVAILABLE ->
                    MIN_RETRY_PERIOD_SECONDS + System.currentTimeMillis()
                else -> null
            })
            ?.let { delayedTill ->
                result.delayTill(delayedTill)
                result.request.hostAndPort ?.let { hostAndPort ->
                    delays.compute(hostAndPort) { key, oldValue ->
                        max(oldValue ?: 0, delayedTill)
                    }
                }
            }
    }

    private fun retryAfterDate(result: HttpReadResult) = result.retryAfterDate

    private fun rateLimitDate(result: HttpReadResult) = result.rateLimitRemaining
        ?.let { remaining ->
            if (remaining == 0L) {
                result.rateLimitReset ?: MIN_RETRY_PERIOD_SECONDS + System.currentTimeMillis()
            } else null
        }
}
