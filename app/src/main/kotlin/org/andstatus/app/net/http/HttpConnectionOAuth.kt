/*
 * Copyright (C) 2013-2022 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.TextUtils
import io.vavr.control.Try
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.net.social.ApiRoutineEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.UriUtils
import org.json.JSONException
import org.json.JSONObject
import java.net.URL

abstract class HttpConnectionOAuth : HttpConnection() {
    private var logMe = false
    private fun accessTokenKey(): String {
        return "user_token"
    }

    private fun accessSecretKey(): String {
        return "user_secret"
    }

    var oauthClientKeys: OAuthClientKeys? = null
    var urlForAccessToken: URL? = null

    @Volatile
    var authorizationServerMetadata: AuthorizationServerMetadata? = null

    val scopesSupported: String get() = (authorizationServerMetadata?.scopesSupported ?: oauthScopesKnown)
        .joinToString(" ")

    @Volatile
    var accessToken: String = ""

    @Volatile
    var accessSecret: String = ""

    override var data: HttpConnectionData
        get() = super.data
        set(connectionData) {
            super.data = connectionData
            oauthClientKeys = OAuthClientKeys.fromConnectionData(connectionData)
            authorizationServerMetadata = AuthorizationServerMetadata.load(connectionData)
            // We look for saved user keys
            connectionData.dataReader?.let { dataReader ->
                if (dataReader.dataContains(accessTokenKey()) && dataReader.dataContains(accessSecretKey())) {
                    accessToken = dataReader.getDataString(accessTokenKey())
                    accessSecret = dataReader.getDataString(accessSecretKey())
                    setAccessTokenWithSecret(accessToken, accessSecret)
                }
            }
        }

    fun obtainAuthorizationServerMetadata(): Try<Unit> {
        val uri = data.originUrl?.host?.let { host ->
            val port = data.originUrl?.port ?: -1
            val hostPort = host + if (port > 0) ":$port" else ""
            UriUtils.fromString("https://$hostPort$AUTHORIZATION_SERVER_METADATA_PATH")
        }
            ?: return TryUtils.failure("No host in ${data.originUrl}")

        val msgSupplier: () -> String = {
            "obtainAuthorizationServerMetadata; for " + data.originUrl + "; URL='" + uri + "'"
        }
        return HttpRequest.of(ApiRoutineEnum.AUTHORIZATION_SERVER_METADATA, uri)
            .let(::execute)
            .flatMap { obj: HttpReadResult -> obj.getJsonObject() }
            .map { jso: JSONObject ->
                authorizationServerMetadata = AuthorizationServerMetadata.fromJson(jso)
                authorizationServerMetadata?.save(data)
                MyLog.v(this, "Completed: ${msgSupplier()}: $authorizationServerMetadata")
                Unit
            }
            .mapFailure { throwable: Throwable? ->
                if (throwable is JSONException) {
                    ConnectionException.loggedJsonException(this, msgSupplier(), throwable, null)
                } else {
                    if (throwable is ConnectionException && throwable.statusCode == StatusCode.NOT_FOUND) {
                        MyLog.d(this, "Not found: ${msgSupplier()}")
                    } else {
                        MyLog.w(this, "Failed: ${msgSupplier()}", throwable)
                    }
                    throwable
                }
            }
    }

    open fun registerClient(): Try<Unit> {
        // Do nothing in the default implementation
        return TryUtils.SUCCESS
    }

    open fun refreshAccess(): Try<Unit> {
        return TryUtils.SUCCESS
    }

    override val credentialsPresent: Boolean
        get() {
            val yes = areClientKeysPresent()
                && accessToken.isNotEmpty()
                && accessSecret.isNotEmpty()
            if (!yes && logMe) {
                MyLog.v(this) {
                    ("Credentials presence: clientKeys:" + oauthClientKeys?.areKeysPresent()
                        + "; accessToken:" + accessToken.isNotEmpty() + ", accessSecret:" + accessSecret.isNotEmpty())
                }
            }
            return yes
        }

    fun doOauthRequest(): Boolean {
        return areClientKeysPresent()
    }

    fun areClientKeysPresent(): Boolean {
        return oauthClientKeys?.areKeysPresent() == true
    }

    fun clearClientKeys() {
        oauthClientKeys?.clear()
    }

    fun getApiUri(routine: ApiRoutineEnum?): Uri = authorizationServerMetadata?.let { metadata ->
        var url: String? = when (routine) {
            ApiRoutineEnum.OAUTH_ACCESS_TOKEN -> metadata.tokenEndpoint
            ApiRoutineEnum.OAUTH_AUTHORIZE -> metadata.authorizationEndpoint
            ApiRoutineEnum.OAUTH_REQUEST_TOKEN -> metadata.tokenEndpoint
            ApiRoutineEnum.OAUTH_REGISTER_CLIENT -> metadata.registrationEndpoint
            else -> ""
        }
        if (!url.isNullOrEmpty()) {
            url = pathToUrlString(url)
        }
        UriUtils.fromString(url).takeIf { !UriUtils.isEmpty(it) }
    }
        ?: getApiUri2(routine)

    open fun getApiUri2(routine: ApiRoutineEnum?): Uri {
        var url: String = when (routine) {
            /** These are Pump.io specific endpoints */
            ApiRoutineEnum.OAUTH_ACCESS_TOKEN -> data.oauthPath + "/access_token"
            ApiRoutineEnum.OAUTH_AUTHORIZE -> data.oauthPath + "/authorize"
            ApiRoutineEnum.OAUTH_REQUEST_TOKEN -> data.oauthPath + "/request_token"
            ApiRoutineEnum.OAUTH_REGISTER_CLIENT -> data.basicPath + "/client/register"
            else -> ""
        }
        if (url.isNotEmpty()) {
            url = pathToUrlString(url)
        }
        return UriUtils.fromString(url)
    }

    abstract fun getConsumer(): OAuthConsumer?

    abstract fun getProvider(): OAuthProvider?

    open fun getOauth2Service(redirect: Boolean): MyOAuth2Service? {
        return null
    }

    open fun isOAuth2(): Boolean {
        return false
    }

    /**
     * @param token empty value means to clear the old values
     * @param secret
     */
    fun setAccessTokenWithSecret(token: String?, secret: String?) {
        synchronized(this) {
            accessToken = token ?: ""
            accessSecret = secret ?: ""
        }
        if (logMe) {
            MyLog.v(this) {
                ("Credentials set?: " + !token.isNullOrEmpty()
                    + ", " + !secret.isNullOrEmpty())
            }
        }
    }

    override fun saveTo(dw: AccountDataWriter): Boolean {
        var changed = super.saveTo(dw)
        if (!TextUtils.equals(accessToken, dw.getDataString(accessTokenKey())) ||
            !TextUtils.equals(accessSecret, dw.getDataString(accessSecretKey()))
        ) {
            changed = true
            if (accessToken.isEmpty()) {
                dw.setDataString(accessTokenKey(), "")
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Token")
                }
            } else {
                dw.setDataString(accessTokenKey(), accessToken)
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Token: $accessToken")
                }
            }
            if (accessSecret.isEmpty()) {
                dw.setDataString(accessSecretKey(), "")
                if (logMe) {
                    MyLog.d(TAG, "Clearing OAuth Secret")
                }
            } else {
                dw.setDataString(accessSecretKey(), accessSecret)
                if (logMe) {
                    MyLog.d(TAG, "Saving OAuth Secret: $accessSecret")
                }
            }
        }
        return changed
    }

    override fun clearAuthInformation() {
        setAccessTokenWithSecret("", "")
    }

    companion object {
        const val AUTHORIZATION_SERVER_METADATA_PATH = "/.well-known/oauth-authorization-server"
        private val TAG: String = HttpConnectionOAuth::class.simpleName!!
    }
}
