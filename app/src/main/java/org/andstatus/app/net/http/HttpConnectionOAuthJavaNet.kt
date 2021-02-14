/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.ContentResolver
import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import oauth.signpost.basic.DefaultOAuthConsumer
import oauth.signpost.basic.DefaultOAuthProvider
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.apache.commons.lang3.tuple.ImmutablePair
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.Map
import java.util.function.Function

open class HttpConnectionOAuthJavaNet : HttpConnectionOAuth() {
    /**
     * Partially borrowed from the "Impeller" code !
     */
    override fun registerClient(): Try<Void?>? {
        val uri = getApiUri(ApiRoutineEnum.OAUTH_REGISTER_CLIENT)
        val logmsg: MyStringBuilder = MyStringBuilder.Companion.of("registerClient; for " + data.originUrl
                + "; URL='" + uri + "'")
        MyLog.v(this) { logmsg.toString() }
        var consumerKey = ""
        var consumerSecret = ""
        data.oauthClientKeys.clear()
        var writer: Writer? = null
        try {
            val endpoint = URL(uri.toString())
            val conn = endpoint.openConnection() as HttpURLConnection
            val params: MutableMap<String?, String?> = HashMap()
            params["type"] = "client_associate"
            params["application_type"] = "native"
            params["redirect_uris"] = HttpConnectionInterface.Companion.CALLBACK_URI.toString()
            params["client_name"] = HttpConnectionInterface.Companion.USER_AGENT
            params["application_name"] = HttpConnectionInterface.Companion.USER_AGENT
            val requestBody = HttpConnectionUtils.encode(params)
            conn.doOutput = true
            conn.doInput = true
            writer = OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8)
            writer.write(requestBody)
            writer.close()
            val result: HttpReadResult = HttpRequest.Companion.of(ApiRoutineEnum.OAUTH_REGISTER_CLIENT, uri)
                    .withConnectionData(getData())
                    .newResult()
            setStatusCodeAndHeaders(result, conn)
            if (result.isStatusOk) {
                result.readStream("") { o: Void? -> conn.inputStream }
                val jso = JSONObject(result.strResponse)
                consumerKey = jso.getString("client_id")
                consumerSecret = jso.getString("client_secret")
                data.oauthClientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret)
            } else {
                result.readStream("") { o: Void? -> conn.errorStream }
                logmsg.atNewLine("Server returned an error response", result.strResponse)
                logmsg.atNewLine("Response message from server", conn.responseMessage)
                MyLog.i(this, logmsg.toString())
            }
        } catch (e: Exception) {
            logmsg.withComma("Exception", e.message)
            MyLog.i(this, logmsg.toString(), e)
        } finally {
            closeSilently(writer)
        }
        if (data.oauthClientKeys.areKeysPresent()) {
            MyLog.v(this) { "Completed $logmsg" }
        } else {
            return Try.failure(ConnectionException.Companion.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST,
                    "Failed to obtain client keys for host; $logmsg", data.originUrl))
        }
        return Try.success(null)
    }

    @Throws(ConnectionException::class)
    override fun getProvider(): OAuthProvider? {
        var provider: OAuthProvider? = null
        provider = DefaultOAuthProvider(
                getApiUri(ApiRoutineEnum.OAUTH_REQUEST_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString())
        provider.setOAuth10a(true)
        return provider
    }

    override fun postRequest(result: HttpReadResult?): HttpReadResult? {
        try {
            val conn = result.getUrlObj().openConnection() as HttpURLConnection
            conn.doOutput = true
            conn.doInput = true
            conn.requestMethod = "POST"
            try {
                if (result.request.mediaUri.isPresent) {
                    writeMedia(conn, result.request)
                } else {
                    result.request.postParams.ifPresent { params: JSONObject? ->
                        try {
                            writeJson(conn, result.request, params)
                        } catch (e: Exception) {
                            result.setException(e)
                        }
                    }
                }
            } catch (e: Exception) {
                result.setException(e)
            }
            setStatusCodeAndHeaders(result, conn)
            when (result.getStatusCode()) {
                StatusCode.OK -> result.readStream("", CheckedFunction { o: Void? -> conn.inputStream })
                else -> {
                    result.readStream("", CheckedFunction { o: Void? -> conn.errorStream })
                    result.setException(result.getExceptionFromJsonErrorResponse())
                }
            }
        } catch (e: Exception) {
            result.setException(e)
        }
        return result
    }

    /** This method is not legacy HTTP  */
    @Throws(IOException::class)
    private fun writeMedia(conn: HttpURLConnection?, request: HttpRequest?) {
        val contentResolver: ContentResolver = MyContextHolder.Companion.myContextHolder.getNow().context().getContentResolver()
        val mediaUri = request.mediaUri.get()
        conn.setChunkedStreamingMode(0)
        conn.setRequestProperty("Content-Type", uri2MimeType(contentResolver, mediaUri))
        signConnection(conn, consumer, false)
        contentResolver.openInputStream(mediaUri).use { `in` ->
            val buffer = ByteArray(16384)
            BufferedOutputStream(conn.getOutputStream()).use { out ->
                var length: Int
                while (`in`.read(buffer).also { length = it } != -1) {
                    out.write(buffer, 0, length)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun writeJson(conn: HttpURLConnection?, request: HttpRequest?, formParams: JSONObject?) {
        conn.setRequestProperty("Content-Type", data.jsonContentType(request.apiRoutine))
        signConnection(conn, consumer, false)
        conn.getOutputStream().use { os -> OutputStreamWriter(os, UTF_8).use { writer -> writer.write(formParams.toString()) } }
    }

    override fun getConsumer(): OAuthConsumer? {
        val consumer: OAuthConsumer = DefaultOAuthConsumer(
                data.oauthClientKeys.consumerKey,
                data.oauthClientKeys.consumerSecret)
        if (credentialsPresent) {
            consumer.setTokenWithSecret(userToken, userSecret)
        }
        return consumer
    }

    override fun getRequest(result: HttpReadResult?): HttpReadResult? {
        var connCopy: HttpURLConnection? = null
        try {
            val consumer = consumer
            var redirected = false
            var stop = false
            do {
                connCopy = result.getUrlObj().openConnection() as HttpURLConnection
                val conn = connCopy
                data.optOriginContentType().ifPresent { value: String? -> conn.addRequestProperty("Accept", value) }
                conn.instanceFollowRedirects = false
                if (result.authenticate()) {
                    signConnection(conn, consumer, redirected)
                }
                conn.connect()
                setStatusCodeAndHeaders(result, conn)
                when (result.getStatusCode()) {
                    StatusCode.OK -> {
                        result.readStream("", CheckedFunction { o: Void? -> conn.inputStream })
                        stop = true
                    }
                    StatusCode.MOVED -> {
                        redirected = true
                        stop = onMoved(result)
                    }
                    else -> {
                        result.readStream("", CheckedFunction { o: Void? -> conn.errorStream })
                        stop = result.noMoreHttpRetries()
                    }
                }
                closeSilently(conn)
            } while (!stop)
        } catch (e: Exception) {
            result.setException(e)
        } finally {
            closeSilently(connCopy)
        }
        return result
    }

    @Throws(IOException::class)
    private fun setStatusCodeAndHeaders(result: HttpReadResult?, conn: HttpURLConnection?) {
        result.setStatusCode(conn.getResponseCode())
        try {
            result.setHeaders(
                    conn.getHeaderFields().entries.stream().flatMap { entry: MutableMap.MutableEntry<String?, MutableList<String?>?>? ->
                        entry.value.stream()
                                .map { value: String? -> ImmutablePair(entry.key, value) }
                    }, Function { Map.Entry.key }, Function { Map.Entry.value })
        } catch (ignored: Exception) {
            // ignore
        }
    }

    @Throws(ConnectionException::class)
    protected open fun signConnection(conn: HttpURLConnection?, consumer: OAuthConsumer?, redirected: Boolean) {
        if (!credentialsPresent || consumer == null) {
            return
        }
        try {
            if (data.originUrl.host.contentEquals(data.urlForUserToken.host)) {
                consumer.sign(conn)
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    consumer.setTokenWithSecret("", "")
                    consumer.sign(conn)
                } else {
                    conn.setRequestProperty("Authorization", "Dialback")
                    conn.setRequestProperty("host", data.urlForUserToken.host)
                    conn.setRequestProperty("token", userToken)
                    MyLog.v(this) {
                        ("Dialback authorization at " + data.originUrl
                                + "; urlForUserToken=" + data.urlForUserToken + "; token=" + userToken)
                    }
                    consumer.sign(conn)
                }
            }
        } catch (e: Exception) {
            throw ConnectionException(e)
        }
    }

    companion object {
        private val UTF_8: String? = "UTF-8"
    }
}