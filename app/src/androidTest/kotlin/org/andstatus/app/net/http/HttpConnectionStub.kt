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

import android.text.TextUtils
import androidx.annotation.RawRes
import io.vavr.control.CheckedFunction
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.StatusCode.Companion.STATUS_CODE_INT_NOT_FOUND
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONObject
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.stream.Collectors

class HttpConnectionStub : HttpConnectionOAuth() {
    init {
        userToken = "token"
        userSecret = "secret"
    }

    private val results: MutableList<HttpReadResult> = CopyOnWriteArrayList()
    private val responses: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var responsesCounter = 0
    private var sameResponse = false

    @Volatile
    private var responseStreamSupplier: CheckedFunction<Unit, InputStream>? = null

    @Volatile
    private var runtimeException: RuntimeException? = null

    @Volatile
    private var exception: ConnectionException? = null

    @Volatile
    override var password: String = "password"

    private val networkDelayMs: Long = 1000
    private val mInstanceId = InstanceId.next()

    fun setSameResponse(sameResponse: Boolean) {
        this.sameResponse = sameResponse
    }

    override fun errorOnInvalidUrls(): Boolean {
        return false
    }

    fun addResponse(@RawRes responseResourceId: Int) {
        addResponse(RawResourceUtils.getString(responseResourceId))
    }

    fun addResponse(responseString: String) {
        responses.add(responseString)
    }

    fun setResponseStreamSupplier(responseStreamSupplier: CheckedFunction<Unit, InputStream>?) {
        this.responseStreamSupplier = responseStreamSupplier
    }

    fun setRuntimeException(exception: RuntimeException?) {
        runtimeException = exception
    }

    fun setException(exception: ConnectionException?) {
        this.exception = exception
    }

    override fun pathToUrlString(path: String): String {
        if (data.originUrl == null) {
            data.originUrl = UrlUtils.buildUrl("stubbed.example.com", true)
        }
        return super.pathToUrlString(path)
    }

    override fun postRequest(result: HttpReadResult): HttpReadResult {
        onRequest("postRequestWithObject", result)
        setExceptions(result)
        return result
    }

    private fun setExceptions(result: HttpReadResult) {
        if (runtimeException != null) {
            result.setException(runtimeException)
        } else if (exception != null) {
            result.setException(exception)
        }
    }

    override fun setUserTokenWithSecret(token: String?, secret: String?) {
        userToken = token ?: ""
        userSecret = secret ?: ""
    }

    private fun onRequest(method: String, result: HttpReadResult) {
        result.strResponse = getNextResponse()
        responseStreamSupplier?.let {
            result.readStream("", it)
        }
        if (result.strResponse.isEmpty() &&
            (result.request.fileResult == null || responseStreamSupplier == null)
        ) {
            result.setStatusCodeInt(STATUS_CODE_INT_NOT_FOUND)
        }
        results.add(result)
        MyLog.v(
            this,
            method + " num:" + results.size + "; path:'" + result.url + "'" +
                    ", originUrl:'" + data.originUrl + "', instanceId:" + mInstanceId
        )
        MyLog.v(this, Arrays.toString(Thread.currentThread().stackTrace))
        DbUtils.waitMs("networkDelay", Math.toIntExact(networkDelayMs))
    }

    @Synchronized
    private fun getNextResponse(): String {
        return if (sameResponse) if (responses.isEmpty()) "" else responses.get(responses.size - 1)
        else if (responsesCounter < responses.size) responses.get(responsesCounter++) else ""
    }

    private fun getRequestInner(method: String, result: HttpReadResult): HttpReadResult {
        onRequest(method, result)
        setExceptions(result)
        return result
    }

    override fun clearAuthInformation() {
        password = ""
        userToken = ""
        userSecret = ""
    }

    override val credentialsPresent: Boolean
        get() =
            password.isNotEmpty() || !TextUtils.isDigitsOnly(userToken) && userSecret.isNotEmpty()

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
            { a: Int, r: HttpReadResult -> r.request.postParams.map { p: JSONObject? -> a + 1 }.orElse(a) },
            { a1: Int, a2: Int -> a1 + a2 })
    }

    fun clearData() {
        results.clear()
        responses.clear()
        responsesCounter = 0
        setSameResponse(false)
        setRuntimeException(null)
    }

    fun getInstanceId(): Long {
        return mInstanceId
    }

    override fun <T : HttpConnectionInterface> getNewInstance(): T {
        return this as T
    }

    override fun getRequest(result: HttpReadResult): HttpReadResult {
        return getRequestInner("getRequest", result)
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
        val builder = StringBuilder()
        builder.append("HttpConnectionStub [")
        builder.append("Requests sent: " + getRequestsCounter())
        builder.append("; Data posted " + getPostedCounter() + " times")
        builder.append("\nSent $responsesCounter responses")
        if (results.size > 0) {
            builder.append("\nresults:${results.size}")
            results.forEach(Consumer { r: HttpReadResult? ->
                builder.append("\nResult: ${r.toString()}")
            })
        }
        if (responses.size > 0) {
            builder.append("\n\nresponses:${responses.size}")
            responses.forEach(Consumer { r: String? ->
                builder.append("\nResponse: ${r.toString()}")
            })
        }
        responseStreamSupplier?.let {
            builder.append("\nResponse stream supplier: $it")
        }
        if (exception != null) {
            builder.append("\nexception=")
            builder.append(exception)
        }
        builder.append("\npassword=")
        builder.append(password)
        builder.append(", userToken=")
        builder.append(userToken)
        builder.append(", userSecret=")
        builder.append(userSecret)
        builder.append(", networkDelayMs=")
        builder.append(networkDelayMs)
        builder.append(", mInstanceId=")
        builder.append(mInstanceId)
        builder.append("]")
        return builder.toString()
    }

    init {
        MyLog.v(this, "Created, instanceId:$mInstanceId")
    }
}
