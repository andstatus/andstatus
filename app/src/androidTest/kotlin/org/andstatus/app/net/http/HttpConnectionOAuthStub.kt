/*
 * Copyright (c) 2022 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.RawRes
import io.vavr.control.CheckedFunction
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.StatusCode.Companion.STATUS_CODE_INT_NOT_FOUND
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONObject
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.stream.Collectors

class HttpConnectionOAuthStub : HttpConnectionOAuth() {
    private val instanceId = InstanceId.next()

    init {
        isStub = true
        userToken = "token"
        userSecret = "secret"
        MyLog.v(this, "Created, instanceId:$instanceId")
    }

    private val results: MutableList<HttpReadResult> = CopyOnWriteArrayList()
    val responses: MutableList<StubResponse> = CopyOnWriteArrayList()

    @Volatile
    var responsesCounter = 0
    var sameResponse: Boolean = false

    private val networkDelayMs: Long = 1000

    override fun errorOnInvalidUrls(): Boolean {
        return false
    }

    fun addResponse(@RawRes responseResourceId: Int) {
        addResponse(RawResourceUtils.getString(responseResourceId))
    }

    fun addResponse(responseString: String) {
        responses.add(StubResponse(strResponse = responseString))
    }

    fun addResponseStreamSupplier(responseStreamSupplier: CheckedFunction<Unit, InputStream>?) {
        responses.add(StubResponse(streamSupplier = responseStreamSupplier))
    }

    fun addException(exception: Exception) {
        responses.add(StubResponse(exception = exception))
    }

    override fun pathToUrlString(path: String): String {
        if (data.originUrl == null) {
            data.originUrl = UrlUtils.buildUrl("stubbed.example.com", true)
        }
        return super.pathToUrlString(path)
    }

    override var data: HttpConnectionData
        get() = super.data
        set(value) {
            super.data = value
            if (!areClientKeysPresent()) {
                if (oauthClientKeys == null) {
                    oauthClientKeys = OAuthClientKeys.fromConnectionData(data)
                }
                oauthClientKeys?.setConsumerKeyAndSecret("fakeConsumerKey", "fakeConsumerSecret")
            }
        }

    override fun postRequest(result: HttpReadResult): HttpReadResult = onRequest("postRequest", result)
    override fun getRequest(result: HttpReadResult): HttpReadResult = onRequest("getRequest", result)

    private fun onRequest(method: String, result: HttpReadResult): HttpReadResult {
        getNextResponse().run {
            strResponse?.let {
                result.strResponse = it
            }
            location?.let {
                result.location = Optional.of(it)
            }
            streamSupplier?.let {
                result.readStream("", it)
            }
            exception?.let {
                if (it is RuntimeException) throw it
                result.setException(exception)
            }
            if (isEmpty) {
                result.setStatusCodeInt(STATUS_CODE_INT_NOT_FOUND)
            }
        }
        results.add(result)
        MyLog.v(
            this,
            method + " num:" + results.size + "; path:'" + result.url + "'" +
                ", originUrl:'" + data.originUrl + "', instanceId:" + instanceId
        )
        MyLog.v(this, Arrays.toString(Thread.currentThread().stackTrace))
        DbUtils.waitMs("networkDelay", Math.toIntExact(networkDelayMs))
        return result
    }

    @Synchronized
    private fun getNextResponse(): StubResponse = if (sameResponse) {
        if (responses.isEmpty()) StubResponse.EMPTY
        else responses.get(responses.size - 1)
    } else {
        if (responsesCounter < responses.size) responses.get(responsesCounter++)
        else StubResponse.EMPTY
    }

    override fun getConsumer(): OAuthConsumer? = null
    override fun getProvider(): OAuthProvider? = null

    fun getLatestPostedJSONObject(): JSONObject? {
        return results.get(results.size - 1).request.postParams.orElse(JSONObject())
    }

    fun getResults(): MutableList<HttpReadResult> {
        return results
    }

    fun substring2PostedPath(substringToSearch: String): String {
        var found = ""
        for (result in getResults()) {
            val urlString = result.url?.toExternalForm() ?: ""
            if (urlString.contains(substringToSearch)) {
                found = urlString
                break
            }
        }
        return found
    }

    fun getRequestsCounter(): Int {
        return results.size
    }

    fun getPostedObjects(): MutableList<JSONObject> {
        return getResults().stream()
            .map { r: HttpReadResult -> r.request.postParams.orElse(null) }
            .collect(Collectors.toList())
    }

    fun getPostedCounter(): Int {
        return getResults().stream().reduce(0,
            { a: Int, r: HttpReadResult -> r.request.postParams.map { a + 1 }.orElse(a) },
            { a1: Int, a2: Int -> a1 + a2 })
    }

    fun clearData() {
        results.clear()
        responses.clear()
        responsesCounter = 0
        sameResponse = false
    }

    fun getInstanceId(): Long {
        return instanceId
    }

    fun waitForPostContaining(substring: String): HttpReadResult {
        for (attempt in 0..9) {
            val result = getResults().stream()
                .filter { r: HttpReadResult -> r.request.postParams.toString().contains(substring) }
                .findFirst()
            if (result.isPresent) return result.get()
            if (DbUtils.waitMs("waitForPostContaining", 2000)) break
        }
        throw IllegalStateException("The content should be sent: '$substring' Results:${getResults()}")
    }

    override fun toString(): String {
        val builder = MyStringBuilder()
        builder.append("Requests sent", getRequestsCounter())
        builder.append("Data posted, times", getPostedCounter())
        builder.atNewLine("Responses sent", responsesCounter)
        builder.atNewLine("Results", results.size)
        results.forEach { builder.atNewLine("Result", it) }
        builder.atNewLine("Responses", responses.size)
        responses.forEach(builder::atNewLine)
        builder.atNewLine("userToken", userToken)
        builder.append("userSecret", userSecret)
        builder.append("networkDelayMs", networkDelayMs)
        builder.append("instanceId", instanceId)
        return builder.toKeyValue("HttpConnectionStub")
    }
}
