package org.andstatus.app.net.http

import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.httpclient.HttpClient
import com.github.scribejava.core.httpclient.HttpClientConfig
import com.github.scribejava.core.model.OAuthConstants
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.oauth.AccessTokenRequestParams
import com.github.scribejava.core.oauth.OAuth20Service
import io.vavr.control.Try
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TryUtils
import org.json.JSONObject
import java.io.OutputStream

class MyOAuth2Service(
    api: DefaultApi20?, apiKey: String?, apiSecret: String?, callback: String?, defaultScope: String?,
    responseType: String?, debugStream: OutputStream?, userAgent: String?,
    httpClientConfig: HttpClientConfig?, httpClient: HttpClient?
) : OAuth20Service(
    api, apiKey, apiSecret, callback, defaultScope, responseType, debugStream, userAgent,
    httpClientConfig, httpClient
) {
    override fun createAccessTokenRequest(params: AccessTokenRequestParams?): OAuthRequest {
        return super.createAccessTokenRequest(params).apply {
            // Required as per https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3
            addParameter(OAuthConstants.CLIENT_ID, apiKey)
        }
    }

    override fun createRefreshTokenRequest(refreshToken: String?, scope: String?): OAuthRequest {
        return super.createRefreshTokenRequest(refreshToken, scope).apply {
            // https://www.rfc-editor.org/rfc/rfc6749#section-3.2.1
            // "A client MAY use the "client_id" request parameter to identify itself
            //   when sending requests to the token endpoint..."
            addParameter(OAuthConstants.CLIENT_ID, apiKey)
        }
    }

    /** Token introspection https://www.rfc-editor.org/rfc/rfc7662
     * See https://github.com/scribejava/scribejava/issues/898 */
    fun introspectAccessToken(introspectionEndpoint: String?, accessToken: String?): Try<Boolean> {
        if (introspectionEndpoint.isNullOrEmpty()) return TryUtils.failure("No introspection endpoint")
        if (accessToken.isNullOrEmpty()) return TryUtils.failure("No access token")

        val request = OAuthRequest(api.accessTokenVerb, introspectionEndpoint)
        api.clientAuthentication.addClientAuthentication(request, apiKey, apiSecret)
        request.addParameter("token", accessToken);
        request.addParameter("token_type_hint", "access_token")

        val response = execute(request)
        if (response.code != 200) {
            return TryUtils.failure("Failed to introspect access token: ${response.code} ${response.message}")
        }
        var body: String? = null
        try {
            body = response.body ?: ""
            if (MyPreferences.isLogNetworkLevelMessages()) {
                MyLog.logNetworkLevelMessage(
                    "response", "introspectAccessToken", body,
                    MyStringBuilder.of("")
                        .atNewLine("logger-URL", introspectionEndpoint)
                        .toString()
                )
            }
            val isActive = JSONObject(body).getBoolean("active")
            return Try.success(isActive)
        } catch (e: Exception) {
            return TryUtils.failure("Failed to parse Token introspection response '$body'", e)
        }

    }
}
