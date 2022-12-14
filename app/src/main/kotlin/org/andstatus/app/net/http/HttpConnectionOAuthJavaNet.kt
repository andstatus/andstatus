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
import io.vavr.control.Try
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import oauth.signpost.basic.DefaultOAuthConsumer
import oauth.signpost.basic.DefaultOAuthProvider
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.data.MyContentType.Companion.uri2MimeType
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.apache.commons.lang3.tuple.ImmutablePair
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

open class HttpConnectionOAuthJavaNet : HttpConnectionOAuth() {
    /**
     * Partially borrowed from the "Impeller" code !
     */
    override fun registerClient(): Try<Unit> {
        val uri = getApiUri(ApiRoutineEnum.OAUTH_REGISTER_CLIENT)
        val logmsg: MyStringBuilder = MyStringBuilder.of(
            "registerClient; for " + data.originUrl + "; URL='" + uri + "'"
        )
        MyLog.v(this) { logmsg.toString() }
        data.oauthClientKeys?.clear()
        var writer: Writer? = null
        try {
            val endpoint = URL(uri.toString())
            val conn = endpoint.openConnection() as HttpURLConnection
            val params: MutableMap<String, String> = HashMap()
            params["type"] = "client_associate"
            params["application_type"] = "native"
            params["redirect_uris"] = HttpConnectionInterface.CALLBACK_URI.toString()
            params["client_name"] = HttpConnectionInterface.USER_AGENT
            params["application_name"] = HttpConnectionInterface.USER_AGENT
            val requestBody = HttpConnectionUtils.encode(params)
            conn.doOutput = true
            conn.doInput = true
            writer = OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8)
            writer.write(requestBody)
            writer.close()
            HttpRequest.of(ApiRoutineEnum.OAUTH_REGISTER_CLIENT, uri)
                .withConnectionData(data)
                .let { request ->
                    val result: HttpReadResult = request.newResult()
                    setStatusCodeAndHeaders(result, conn)
                    if (result.isStatusOk()) {
                        result.readStream("") { conn.inputStream }
                        val jso = JSONObject(result.strResponse)
                        val consumerKey = jso.getString("client_id")
                        val consumerSecret = jso.getString("client_secret")
                        data.oauthClientKeys?.setConsumerKeyAndSecret(consumerKey, consumerSecret)
                    } else {
                        result.readStream("") { conn.errorStream }
                        logmsg.atNewLine("Server returned an error response", result.strResponse)
                        logmsg.atNewLine("Response message from server", conn.responseMessage)
                        MyLog.i(this, logmsg.toString())
                    }
                    result.toTryResult()
                }
        } catch (e: Exception) {
            logmsg.withComma("Exception", e.message)
            MyLog.i(this, logmsg.toString(), e)
        } finally {
            closeSilently(writer)
        }
        if (data.oauthClientKeys?.areKeysPresent() == true) {
            MyLog.v(this) { "Completed $logmsg" }
        } else {
            return Try.failure(
                ConnectionException.fromStatusCodeAndHost(
                    StatusCode.NO_CREDENTIALS_FOR_HOST,
                    "Failed to obtain client keys for host; $logmsg", data.originUrl
                )
            )
        }
        return Try.success(null)
    }

    override fun getProvider(): OAuthProvider? {
        val provider: OAuthProvider
        provider = DefaultOAuthProvider(
            getApiUri(ApiRoutineEnum.OAUTH_REQUEST_TOKEN).toString(),
            getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString(),
            getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString()
        )
        provider.setOAuth10a(true)
        return provider
    }

    override fun postRequest(result: HttpReadResult): HttpReadResult {
        try {
            val conn = result.requiredUrl("PostOAuth")?.openConnection() as HttpURLConnection? ?: return result
            conn.doOutput = true
            conn.doInput = true
            conn.requestMethod = "POST"
            conn.readTimeout = MyPreferences.getConnectionTimeoutMs()
            conn.connectTimeout = MyPreferences.getConnectionTimeoutMs()
            try {
                if (result.request.mediaUri.isPresent) {
                    writeMedia(conn, result.request)
                } else {
                    result.request.postParams.ifPresent { params: JSONObject ->
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
            when (result.statusCode) {
                StatusCode.OK -> result.readStream("") { conn.inputStream }
                else -> {
                    result.readStream("") { conn.errorStream }
                    result.setException(result.getExceptionFromJsonErrorResponse())
                }
            }
        } catch (e: Exception) {
            result.setException(e)
        }
        return result
    }

    /** This method is not legacy HTTP  */
    private fun writeMedia(conn: HttpURLConnection, request: HttpRequest) {
        val contentResolver: ContentResolver = MyContextHolder.myContextHolder.getNow().context.contentResolver
        val mediaUri = request.mediaUri.get()
        conn.setChunkedStreamingMode(0)
        conn.setRequestProperty("Content-Type", uri2MimeType(contentResolver, mediaUri))
        signConnection(conn, getConsumer(), false)
        contentResolver.openInputStream(mediaUri)?.use { inputStream ->
            val buffer = ByteArray(16384)
            BufferedOutputStream(conn.outputStream).use { out ->
                var length: Int
                while (inputStream.read(buffer).also { length = it } != -1) {
                    out.write(buffer, 0, length)
                }
            }
        }
    }

    private fun writeJson(conn: HttpURLConnection, request: HttpRequest, formParams: JSONObject) {
        conn.setRequestProperty("Content-Type", data.jsonContentType(request.apiRoutine))
        signConnection(conn, getConsumer(), false)
        conn.outputStream.use { os ->
            OutputStreamWriter(os, UTF_8)
                .use { writer -> writer.write(formParams.toString()) }
        }
    }

    override fun getConsumer(): OAuthConsumer {
        val consumer: OAuthConsumer = DefaultOAuthConsumer(
            data.oauthClientKeys?.getConsumerKey(),
            data.oauthClientKeys?.getConsumerSecret()
        )
        if (credentialsPresent) {
            consumer.setTokenWithSecret(userToken, userSecret)
        }
        return consumer
    }

    override fun getRequest(result: HttpReadResult): HttpReadResult {
        var connCopy: HttpURLConnection? = null
        try {
            val consumer = getConsumer()
            var redirected = false
            var stop: Boolean
            do {
                connCopy = result.requiredUrl("GetOAuth")?.openConnection() as HttpURLConnection? ?: return result
                val conn = connCopy
                conn.readTimeout = MyPreferences.getConnectionTimeoutMs()
                conn.connectTimeout = MyPreferences.getConnectionTimeoutMs()
                data.optOriginContentType().ifPresent { value: String -> conn.addRequestProperty("Accept", value) }
                conn.instanceFollowRedirects = false
                if (result.authenticate()) {
                    signConnection(conn, consumer, redirected)
                }
                conn.connect()
                setStatusCodeAndHeaders(result, conn)
                when (result.statusCode) {
                    StatusCode.OK -> {
                        result.readStream("") { conn.inputStream }
                        stop = true
                    }
                    StatusCode.MOVED -> {
                        redirected = true
                        stop = onMoved(result)
                    }
                    else -> {
                        result.readStream("") { conn.errorStream }
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

    protected open fun signConnection(conn: HttpURLConnection, consumer: OAuthConsumer, redirected: Boolean) {
        val originUrl = data.originUrl
        val urlForUserToken = data.urlForUserToken
        if (!credentialsPresent || originUrl == null || urlForUserToken == null) {
            return
        }
        try {
            if (originUrl.host == urlForUserToken.host) {
                consumer.sign(conn)
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    consumer.setTokenWithSecret("", "")
                    consumer.sign(conn)
                } else {
                    conn.setRequestProperty("Authorization", "Dialback")
                    conn.setRequestProperty("host", urlForUserToken.host)
                    conn.setRequestProperty("token", userToken)
                    MyLog.v(this) {
                        ("Dialback authorization at " + originUrl
                                + "; urlForUserToken=" + urlForUserToken + "; token=" + userToken)
                    }
                    consumer.sign(conn)
                }
            }
        } catch (e: Exception) {
            throw ConnectionException(e)
        }
    }

    companion object {
        private val UTF_8: String = "UTF-8"

        fun setStatusCodeAndHeaders(result: HttpReadResult, conn: HttpURLConnection) {
            result.setStatusCodeInt(conn.responseCode)
            try {
                result.setHeaders(
                    conn.headerFields.entries.stream().flatMap { entry ->
                        entry.value.stream()
                            .map { value: String -> ImmutablePair(entry.key, value) }
                    }, { it.key }, { it.value })
            } catch (ignored: Exception) {
                // ignore
            }
        }
    }
}
