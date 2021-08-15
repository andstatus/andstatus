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
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.UriUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThrottlerTest {
    val host = "throttle.example.com"
    val uri1 = UriUtils.fromString("http://$host/someFile.jpg")
    val key = "$host:-1"
    private val delays get() = Throttler.delays
    val storedDelays = delays.toMap()

    @Before
    fun setup() {
        delays.clear()
    }

    @After
    fun tearDown() {
        delays.clear()
        delays.putAll(storedDelays)
    }

    @Test
    fun noThrottlingTest() = oneRequest(299, null)

    @Test
    fun delayIfNoRemainingLimit() = oneRequest(0, 700)

    @Test
    fun secondRequestDelayed() {
        val delayedFor: Long = 400
        oneRequest(0, delayedFor)

        val request2 = HttpRequest.of(ApiRoutineEnum.DOWNLOAD_FILE, uri1)
        val try2: Try<HttpRequest> = request2.let(Throttler::beforeExecution)
        assertTrue(try2.toString(), try2.isFailure)

        val result2 = try2.toHttpReadResult()
        requireNotNull(result2, { "result2" })
        val delayedForOut: Long? = result2.delayedTill.toDelaySeconds()
        assertNotNull("$key should be delayed $result2", delayedForOut)
        assertTrue("$key delayed for $delayedForOut seconds in $result2, should be near $delayedFor",
            delayedForOut in (delayedFor - 1)..delayedFor)
        assertEquals("Should be delayed $result2", StatusCode.DELAYED, result2.statusCode)
    }

    private fun oneRequest(rateLimitRemainingValue: Long?, delayedForExpected: Long?) {
        val rateLimitResetIn: Long = delayedForExpected ?: 350

        val request = HttpRequest.of(ApiRoutineEnum.DOWNLOAD_FILE, uri1)
        assertEquals("Key for throttler, $request", key, request.hostAndPort)
        val try1 = request.let(Throttler::beforeExecution)
        assertTrue(try1.isSuccess)

        val result = request.newResult()
        val inputHeaders = listOf(("x-RateLimit-Remaining" to rateLimitRemainingValue.toString()),
            ("RateLimit-Reset" to rateLimitResetIn.toString()))
        result.setHeaders(inputHeaders.stream(), { it.first }, { it.second })
        assertEquals("RateLimit-Remaining $result", rateLimitRemainingValue, result.rateLimitRemaining)
        val rateLimitReset: Long? = result.rateLimitReset
        val resetIn = rateLimitReset.toDelaySeconds()
        assertTrue("RateLimit-Reset is $resetIn seconds in $result",
            resetIn in (rateLimitResetIn - 1)..rateLimitResetIn)

        val try2: Try<HttpReadResult> = result.toTryResult().also(Throttler::afterExecution)
        assertTrue(try1.isSuccess)
        val result2 = try2.toHttpReadResult()
        requireNotNull(result2, { "result2" })
        val delayedForOut: Long? = delays.get(key).toDelaySeconds()
        if (delayedForExpected == null) {
            assertEquals("Should not be delayed $result2", null, result2.delayedTill)
            assertNull("$key should not be delayed $delays", delayedForOut)
        } else {
            assertNotNull("$key should be delayed $delays", delayedForOut)
            assertTrue("$key delayed for $delayedForOut seconds in $delays, should be near $delayedForExpected",
                delayedForOut in (delayedForExpected - 1)..delayedForExpected)
        }
    }

    private fun Long?.toDelaySeconds(): Long? = this?.let { (it - System.currentTimeMillis()) / 1000 }
}
