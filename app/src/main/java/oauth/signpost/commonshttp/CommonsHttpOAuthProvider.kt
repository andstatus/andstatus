/*
 * Copyright (c) 2009 Matthias Kaeppler Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package oauth.signpost.commonshttp

import cz.msebera.android.httpclient.client.HttpClient
import cz.msebera.android.httpclient.client.methods.HttpPost
import cz.msebera.android.httpclient.client.methods.HttpUriRequest
import cz.msebera.android.httpclient.impl.client.DefaultHttpClient
import oauth.signpost.AbstractOAuthProvider
import oauth.signpost.http.HttpRequest
import oauth.signpost.http.HttpResponse
import org.andstatus.app.util.MyLog
import java.io.IOException

/**
 * This implementation uses the Apache Commons [HttpClient] 4.x HTTP
 * implementation to fetch OAuth tokens from a service provider. Android users
 * should use this provider implementation in favor of the default one, since
 * the latter is known to cause problems with Android's Apache Harmony
 * underpinnings.
 *
 * @author Matthias Kaeppler
 */
class CommonsHttpOAuthProvider : AbstractOAuthProvider {
    @Transient
    private var httpClient: HttpClient

    constructor(requestTokenEndpointUrl: String?, accessTokenEndpointUrl: String?,
                authorizationWebsiteUrl: String?) : super(requestTokenEndpointUrl, accessTokenEndpointUrl, authorizationWebsiteUrl) {
        httpClient = DefaultHttpClient()
    }

    constructor(requestTokenEndpointUrl: String?, accessTokenEndpointUrl: String?,
                authorizationWebsiteUrl: String?, httpClient: HttpClient) : super(requestTokenEndpointUrl, accessTokenEndpointUrl, authorizationWebsiteUrl) {
        this.httpClient = httpClient
    }

    fun setHttpClient(httpClient: HttpClient) {
        this.httpClient = httpClient
    }

    @Throws(Exception::class)
    override fun createRequest(endpointUrl: String?): HttpRequest {
        val request = HttpPost(endpointUrl)
        return HttpRequestAdapter(request)
    }

    @Throws(Exception::class)
    override fun sendRequest(request: HttpRequest): HttpResponse {
        val response = httpClient.execute(request.unwrap() as HttpUriRequest)
        return HttpResponseAdapter(response)
    }

    override fun closeConnection(request: HttpRequest?, response: HttpResponse?) {
        if (response == null) return
        val entity = (response.unwrap() as cz.msebera.android.httpclient.HttpResponse).entity
        if (entity != null) {
            try {
                // free the connection
                entity.consumeContent()
            } catch (e: IOException) {
                MyLog.v(this, "HTTP keep-alive is not possible", e)
            }
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
