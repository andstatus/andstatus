/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth20Service
import io.vavr.control.Try
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyLogVerboseStream
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TryUtils
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

/**
 * @author yvolk@yurivolkov.com
 */
open class HttpConnectionOAuth2JavaNet : HttpConnectionOAuthJavaNet() {

    override fun registerClient(): Try<Unit> {
        val uri = getApiUri(ApiRoutineEnum.OAUTH_REGISTER_CLIENT)
        val logmsg: MyStringBuilder = MyStringBuilder.of(
            "registerClient; for " + data.originUrl + "; URL='" + uri + "'"
        )
        MyLog.v(this) { logmsg.toString() }
        oauthClientKeys?.clear()
        return try {
            val params = JSONObject()
            params.put("client_name", USER_AGENT)
            params.put("redirect_uris", CALLBACK_URI.toString())
            params.put("scopes", OAUTH_SCOPES)
            params.put("website", "http://andstatus.org")
            HttpRequest.of(ApiRoutineEnum.OAUTH_REGISTER_CLIENT, uri)
                .withPostParams(params)
                .let(::execute)
                .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
                .map { jso: JSONObject ->
                    val consumerKey = jso.getString("client_id")
                    val consumerSecret = jso.getString("client_secret")
                    oauthClientKeys?.setConsumerKeyAndSecret(consumerKey, consumerSecret)
                    oauthClientKeys?.areKeysPresent() ?: false
                }
                .flatMap { keysArePresent: Boolean ->
                    if (keysArePresent) {
                        MyLog.v(this) { "Completed $logmsg" }
                        TryUtils.SUCCESS
                    } else {
                        Try.failure(
                            ConnectionException.fromStatusCodeAndHost(
                                StatusCode.NO_CREDENTIALS_FOR_HOST,
                                "Failed to obtain client keys for host; $logmsg", data.originUrl
                            )
                        )
                    }
                }
        } catch (e: JSONException) {
            Try.failure(ConnectionException.loggedJsonException(this, logmsg.toString(), e, null))
        }
    }

    override fun postRequest(result: HttpReadResult): HttpReadResult {
        return if (areClientKeysPresent()) {
            postRequestOauth(result)
        } else {
            super.postRequest(result)
        }
    }

    private fun postRequestOauth(result: HttpReadResult): HttpReadResult {
        try {
            val service = getService(false)
            val request = OAuthRequest(Verb.POST, result.requiredUrl("PostOauth2")?.toExternalForm() ?: return result)
            if (result.request.mediaUri.isPresent) {
                val bytes = ApacheHttpClientUtils.buildMultipartFormEntityBytes(result.request)
                request.addHeader(bytes.contentTypeName, bytes.contentTypeValue)
                request.setPayload(bytes.bytes)
            } else if (result.request.postParams.isPresent) {
                val params = result.request.postParams.get()
                if (data.optOriginContentType().map { value: String? ->
                        request.addHeader("Content-Type", value)
                        request.setPayload(params.toString().toByteArray(StandardCharsets.UTF_8))
                        false
                    }.orElse(true)) {
                    val iterator = params.keys()
                    while (iterator.hasNext()) {
                        val key = iterator.next()
                        try {
                            val value = params[key]
                            if (value is List<*>) {
                                value.forEach { v -> request.addBodyParameter(key, v.toString()) }
                            } else {
                                request.addBodyParameter(key, value.toString())
                            }
                        } catch (e: JSONException) {
                            MyLog.w(this, "Failed to get key $key", e)
                            result.setException(e)
                        }
                    }
                }
            }
            signRequest(request, service, false)
            val response = service.execute(request)
            setStatusCodeAndHeaders(result, response)
            result.readStream("") { response.stream }
            if (result.statusCode != StatusCode.OK) {
                result.setException(result.getExceptionFromJsonErrorResponse())
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            result.setException(e)
        } catch (e: Exception) {
            result.setException(e)
        }
        return result
    }

    override fun getRequest(result: HttpReadResult): HttpReadResult {
        var responseCopy: Response? = null
        try {
            val service = getService(false)
            var redirected = false
            var stop: Boolean
            do {
                val request = OAuthRequest(Verb.GET, result.requiredUrl("GetOauth2")?.toExternalForm() ?: return result)
                data.optOriginContentType().ifPresent { value: String? -> request.addHeader("Accept", value) }
                if (result.authenticate()) {
                    signRequest(request, service, redirected)
                }
                responseCopy = service.execute(request)
                val response = responseCopy
                setStatusCodeAndHeaders(result, response)
                when (result.statusCode) {
                    StatusCode.OK -> {
                        result.readStream("") { response.stream }
                        stop = true
                    }
                    StatusCode.MOVED -> {
                        redirected = true
                        stop = onMoved(result)
                    }
                    else -> {
                        result.readStream("") { response.stream }
                        stop = result.noMoreHttpRetries()
                    }
                }
            } while (!stop)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            result.setException(e)
        } catch (e: Exception) {
            result.setException(e)
        } finally {
            DbUtils.closeSilently(responseCopy)
        }
        return result
    }

    private fun setStatusCodeAndHeaders(result: HttpReadResult, response: Response) {
        result.setStatusCodeInt(response.code)
        result.setHeaders(response.headers.entries.stream(), { it.key }, { it.value })
    }

    override fun getService(redirect: Boolean): OAuth20Service {
        val clientConfig = JDKHttpClientConfig.defaultConfig()
        clientConfig.connectTimeout = MyPreferences.getConnectionTimeoutMs()
        clientConfig.readTimeout = 2 * MyPreferences.getConnectionTimeoutMs()
        clientConfig.isFollowRedirects = false
        val serviceBuilder = ServiceBuilder(oauthClientKeys?.getConsumerKey())
            .apiSecret(oauthClientKeys?.getConsumerSecret())
            .httpClientConfig(clientConfig)
        if (redirect) {
            serviceBuilder.callback(CALLBACK_URI.toString())
        }
        if (MyPreferences.isLogNetworkLevelMessages() && MyLog.isVerboseEnabled()) {
            serviceBuilder.debugStream(MyLogVerboseStream("ScribeJava"))
        }
        return serviceBuilder.build(OAuthApi20(this))
    }

    private fun signRequest(request: OAuthRequest, service: OAuth20Service, redirected: Boolean) {
        val originUrl = data.originUrl
        val urlForUserToken = urlForUserToken
        if (!credentialsPresent || originUrl == null || urlForUserToken == null) {
            return
        }
        try {
            if (originUrl.host == urlForUserToken.host) {
                val token = OAuth2AccessToken(userToken, userSecret)
                service.signRequest(token, request)
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    val token = OAuth2AccessToken("", null)
                    service.signRequest(token, request)
                } else {
                    request.addParameter("Authorization", "Dialback")
                    request.addParameter("host", urlForUserToken.host)
                    request.addParameter("token", userToken)
                    MyLog.v(this) {
                        ("Dialback authorization at " + originUrl
                                + "; urlForUserToken=" + urlForUserToken + "; token=" + userToken)
                    }
                    val token = OAuth2AccessToken(userToken, null)
                    service.signRequest(token, request)
                }
            }
        } catch (e: Exception) {
            throw ConnectionException.of(e)
        }
    }

    override fun signConnection(conn: HttpURLConnection, consumer: OAuthConsumer, redirected: Boolean) {
        val originUrl = data.originUrl
        val tokenUrl = urlForUserToken
        if (!credentialsPresent || originUrl == null || tokenUrl == null) {
            return
        }
        try {
            val token: OAuth2AccessToken = if (originUrl.host == tokenUrl.host) {
                OAuth2AccessToken(userToken, userSecret)
            } else {
                if (redirected) {
                    OAuth2AccessToken("", null)
                } else {
                    conn.setRequestProperty("Authorization", "Dialback")
                    conn.setRequestProperty("host", tokenUrl.host)
                    conn.setRequestProperty("token", userToken)
                    MyLog.v(this) {
                        ("Dialback authorization at " + originUrl + "; urlForUserToken="
                                + tokenUrl + "; token=" + userToken)
                    }
                    OAuth2AccessToken(userToken, null)
                }
            }
            conn.setRequestProperty(OAuthConstants.ACCESS_TOKEN, token.accessToken)
        } catch (e: Exception) {
            throw ConnectionException.of(e)
        }
    }

    override fun getProvider(): OAuthProvider? {
        return null
    }

    override fun isOAuth2(): Boolean {
        return true
    }

    companion object {
        const val OAUTH_SCOPES: String = "read write follow"
    }
}
