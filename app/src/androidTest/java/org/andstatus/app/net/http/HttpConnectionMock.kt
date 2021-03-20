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
import org.andstatus.app.data.DbUtils
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RawResourceUtils
import org.andstatus.app.util.UrlUtils
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer
import java.util.stream.Collectors

class HttpConnectionMock : HttpConnection() {
    private val results: MutableList<HttpReadResult> = CopyOnWriteArrayList()
    private val responses: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var responsesCounter = 0
    private var sameResponse = false

    @Volatile
    private var responseStreamSupplier: CheckedFunction<Void, InputStream>? = null

    @Volatile
    private var runtimeException: RuntimeException? = null

    @Volatile
    private var exception: ConnectionException? = null

    @Volatile
    override var password: String = "password"

    @Volatile
    override var userToken: String = "token"

    @Volatile
    override var userSecret: String = "secret"

    private val networkDelayMs: Long = 1000
    private val mInstanceId = InstanceId.next()
    fun setSameResponse(sameResponse: Boolean) {
        this.sameResponse = sameResponse
    }

    override fun errorOnInvalidUrls(): Boolean {
        return false
    }

    @Throws(IOException::class)
    fun addResponse(@RawRes responseResourceId: Int) {
        addResponse(RawResourceUtils.getString(responseResourceId))
    }

    fun addResponse(responseString: String) {
        responses.add(responseString)
    }

    fun setResponseStreamSupplier(responseStreamSupplier: CheckedFunction<Void, InputStream>?) {
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
            data.originUrl = UrlUtils.buildUrl("mocked.example.com", true)
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
        results.add(result)
        MyLog.v(this, method + " num:" + results.size + "; path:'" + result.getUrl()
                + "', originUrl:'" + data.originUrl + "', instanceId:" + mInstanceId)
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

    override val credentialsPresent: Boolean get() =
        password.isNotEmpty() || !TextUtils.isDigitsOnly(userToken) && userSecret.isNotEmpty()

    fun getLatestPostedJSONObject(): JSONObject? {
        return results.get(results.size - 1).request.postParams.orElse(JSONObject())
    }

    fun getResults(): MutableList<HttpReadResult> {
        return results
    }

    fun substring2PostedPath(substringToSearch: String): String {
        var found = ""
        for (result in getResults()) {
            if (result.getUrl().contains(substringToSearch)) {
                found = result.getUrl()
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

    fun clearPostedData() {
        results.clear()
    }

    fun getInstanceId(): Long {
        return mInstanceId
    }

    override fun getNewInstance(): HttpConnection {
        return this
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
        builder.append("HttpConnectionMock [")
        builder.append("Requests sent: " + getRequestsCounter())
        builder.append("; Data posted " + getPostedCounter() + " times")
        builder.append("\nSent $responsesCounter responses")
        if (results.size > 0) {
            builder.append("""
    
    results:${results.size}
    """.trimIndent())
            results.forEach(Consumer { r: HttpReadResult? ->
                builder.append("""
    
    Result: ${r.toString()}
    """.trimIndent())
            })
        }
        if (responses.size > 0) {
            builder.append("""
    
    
    responses:${responses.size}
    """.trimIndent())
            responses.forEach(Consumer { r: String? ->
                builder.append("""
    
    Response: ${r.toString()}
    """.trimIndent())
            })
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