package org.andstatus.app.net.http

import com.github.scribejava.core.builder.api.DefaultApi20
import com.github.scribejava.core.extractors.TokenExtractor
import com.github.scribejava.core.httpclient.HttpClient
import com.github.scribejava.core.httpclient.HttpClientConfig
import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import org.andstatus.app.net.social.ApiRoutineEnum
import java.io.OutputStream

class OAuth2Api(private val http: HttpConnectionOAuth) : DefaultApi20() {
    override fun getAccessTokenExtractor(): TokenExtractor<OAuth2AccessToken> {
        return MyOAuth2AccessTokenJsonExtractor(http.data)
    }

    override fun getAccessTokenEndpoint(): String {
        return http.getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString()
    }

    override fun getAuthorizationBaseUrl(): String {
        return http.getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString()
    }

    override fun createService(
        apiKey: String?, apiSecret: String?, callback: String?, defaultScope: String?,
        responseType: String?, debugStream: OutputStream?, userAgent: String?, httpClientConfig: HttpClientConfig?,
        httpClient: HttpClient?
    ): OAuth20Service {
        return MyOAuth2Service(
            this, apiKey, apiSecret, callback, defaultScope,
            responseType, debugStream, userAgent, httpClientConfig, httpClient
        )
    }
}
