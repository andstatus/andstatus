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

import android.net.Uri
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
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets

/**
 * @author yvolk@yurivolkov.com
 */
open class HttpConnectionOAuth2 : HttpConnectionOAuthJavaNet() {

    // See https://www.rfc-editor.org/rfc/rfc7591#section-3.1
    override fun registerClient(): Try<Unit> = registerClient(false)
        .recover(java.lang.Exception::class.java) { e: Exception ->
            MyLog.w(this, "Registration failed with $e, fallback to Mastodon...")
            registerClient(true)
        }
        .recover(java.lang.Exception::class.java) { e: java.lang.Exception ->
            MyLog.w(this, "Registration failed with $e, fallback to Fake Client Registration...")
            oauthClientKeys?.setConsumerKeyAndSecret(CLIENT_URI, "fake_client_secret")
            MyLog.i(this, "Completed Fake Client Registration")
            TryUtils.SUCCESS
        }

    private fun registerClient(forMastodon: Boolean): Try<Unit> {
        val uri = getApiUri(ApiRoutineEnum.OAUTH_REGISTER_CLIENT)
        val logMsg: String = "registerClient;" + (if (forMastodon) " Mastodon specific," else "") +
                " origin: " + data.originUrl + ", url: " + uri
        MyLog.v(this) { logMsg }
        oauthClientKeys?.clear()
        return try {
            HttpRequest.of(ApiRoutineEnum.OAUTH_REGISTER_CLIENT, uri)
                .withPostParams(clientMetadata(forMastodon))
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
                        MyLog.v(this) { "Completed $logMsg" }
                        TryUtils.SUCCESS
                    } else {
                        Try.failure(
                            ConnectionException.fromStatusCodeAndHost(
                                StatusCode.NO_CREDENTIALS_FOR_HOST,
                                "Failed to obtain client keys for host; $logMsg", data.originUrl
                            )
                        )
                    }
                }
        } catch (e: JSONException) {
            Try.failure(ConnectionException.loggedJsonException(this, logMsg, e, null))
        }
    }

    // Client Metadata https://www.rfc-editor.org/rfc/rfc7591#section-2
    private fun clientMetadata(forMastodon: Boolean): JSONObject {
        val params = JSONObject()
        if (forMastodon) {
            // redirect_uris should be an array according to spec, but this doesn't work for Mastodon hosts.
            // So we assign it with string value and not an array
            params.put("redirect_uris", CALLBACK_URI)

            // Legacy, this is not in OAuth 2.0 spec
            params.put("scopes", OAUTH_SCOPE)
            params.put("website", CLIENT_URI)
        } else {
            // According to RFC 7591
            val redirectUris = JSONArray()
            redirectUris.put(CALLBACK_URI)
            params.put("redirect_uris", redirectUris)
        }
        params.put("client_name", USER_AGENT)
        params.put("client_uri", CLIENT_URI)
        params.put("logo_uri", LOGO_URI)
        params.put("scope", OAUTH_SCOPE)
        params.put("policy_uri", POLICY_URI)
        return params
    }

    override fun refreshAccess(): Try<Unit> {
        // TODO
        return super.refreshAccess()
    }

    override fun postRequest(result: HttpReadResult): HttpReadResult {
        return if (doOauthRequest()) {
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
        return if (doOauthRequest()) {
            getRequestOAuth(result)
        } else {
            super.getRequest(result)
        }
    }

    fun getRequestOAuth(result: HttpReadResult): HttpReadResult {
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
            .defaultScope(OAUTH_SCOPE)
            .httpClientConfig(clientConfig)
        if (redirect) {
            serviceBuilder.callback(CALLBACK_URI)
        }
        if (MyPreferences.isLogNetworkLevelMessages() && MyLog.isVerboseEnabled()) {
            serviceBuilder.debugStream(MyLogVerboseStream("ScribeJava"))
        }
        return serviceBuilder.build(OAuth2Api(this))
    }

    private fun signRequest(request: OAuthRequest, service: OAuth20Service, redirected: Boolean) {
        val originUrl = data.originUrl
        val tokenUrl = urlForAccessToken
        if (!credentialsPresent || originUrl == null || tokenUrl == null) {
            return
        }
        try {
            if (originUrl.host == tokenUrl.host) {
                val token = OAuth2AccessToken(accessToken, accessSecret)
                service.signRequest(token, request)
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    val token = OAuth2AccessToken("", null)
                    service.signRequest(token, request)
                } else {
                    request.addParameter("Authorization", "Dialback")
                    request.addParameter("host", tokenUrl.host)
                    request.addParameter("token", accessToken)
                    MyLog.v(this) {
                        ("Dialback authorization at " + originUrl
                                + "; tokenUrl=" + tokenUrl + "; token=" + accessToken)
                    }
                    val token = OAuth2AccessToken(accessToken, null)
                    service.signRequest(token, request)
                }
            }
        } catch (e: Exception) {
            throw ConnectionException.of(e)
        }
    }

    override fun signConnection(conn: HttpURLConnection, consumer: OAuthConsumer, redirected: Boolean) {
        val originUrl = data.originUrl
        val tokenUrl = urlForAccessToken
        if (!credentialsPresent || originUrl == null || tokenUrl == null) {
            return
        }
        try {
            val token: OAuth2AccessToken = if (originUrl.host == tokenUrl.host) {
                OAuth2AccessToken(accessToken, accessSecret)
            } else {
                if (redirected) {
                    OAuth2AccessToken("", null)
                } else {
                    conn.setRequestProperty("Authorization", "Dialback")
                    conn.setRequestProperty("host", tokenUrl.host)
                    conn.setRequestProperty("token", accessToken)
                    MyLog.v(this) {
                        ("Dialback authorization at " + originUrl + "; tokenUrl="
                                + tokenUrl + "; token=" + accessToken)
                    }
                    OAuth2AccessToken(accessToken, null)
                }
            }
            conn.setRequestProperty(OAuthConstants.ACCESS_TOKEN, token.accessToken)
        } catch (e: Exception) {
            throw ConnectionException.of(e)
        }
    }

    override fun getApiUri2(routine: ApiRoutineEnum?): Uri {
        var url: String = when (routine) {
            /** These are Mastodon specific endpoints. So they are default.
            Custom endpoints are obtained via [AuthorizationServerMetadata] */
            ApiRoutineEnum.OAUTH_ACCESS_TOKEN, ApiRoutineEnum.OAUTH_REQUEST_TOKEN -> data.oauthPath + "/token"
            ApiRoutineEnum.OAUTH_REGISTER_CLIENT -> data.basicPath + "/v1/apps"
            else -> super.getApiUri2(routine).toString()
        }
        if (url.isNotEmpty()) {
            url = pathToUrlString(url)
        }
        return UriUtils.fromString(url)
    }

    override fun getProvider(): OAuthProvider? {
        return null
    }

    override fun isOAuth2(): Boolean {
        return true
    }

}
