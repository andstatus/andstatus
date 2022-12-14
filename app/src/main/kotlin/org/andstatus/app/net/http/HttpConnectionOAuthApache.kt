/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import cz.msebera.android.httpclient.HttpResponse
import cz.msebera.android.httpclient.client.methods.HttpGet
import cz.msebera.android.httpclient.client.methods.HttpPost
import cz.msebera.android.httpclient.impl.client.BasicResponseHandler
import oauth.signpost.OAuthConsumer
import oauth.signpost.OAuthProvider
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider
import oauth.signpost.exception.OAuthCommunicationException
import oauth.signpost.exception.OAuthExpectationFailedException
import oauth.signpost.exception.OAuthMessageSignerException
import org.andstatus.app.net.social.ApiRoutineEnum
import java.io.IOException

class HttpConnectionOAuthApache : HttpConnectionOAuth(), HttpConnectionApacheSpecific {

    override fun getProvider(): OAuthProvider {
        val provider = CommonsHttpOAuthProvider(
                getApiUri(ApiRoutineEnum.OAUTH_REQUEST_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString())
        provider.setHttpClient(ApacheHttpClientUtils.getHttpClient(data.sslMode))
        provider.isOAuth10a = true
        return provider
    }

    override fun getConsumer(): OAuthConsumer {
        val consumer: OAuthConsumer = CommonsHttpOAuthConsumer(data.oauthClientKeys?.getConsumerKey(),
                data.oauthClientKeys?.getConsumerSecret())
        if (credentialsPresent) {
            consumer.setTokenWithSecret(userToken, userSecret)
        }
        return consumer
    }

    override fun postRequest(result: HttpReadResult): HttpReadResult {
        return HttpConnectionApacheCommon(this, data).postRequest(result)
    }

    override fun httpApachePostRequest(httpPost: HttpPost, result: HttpReadResult): HttpReadResult {
        try {
            // TODO: Redo like for get request
            if (result.authenticate()) {
                signRequest(httpPost)
            }
            result.strResponse = ApacheHttpClientUtils.getHttpClient(data.sslMode).execute(
                    httpPost, BasicResponseHandler())
        } catch (e: Exception) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            result.setException(e)
        }
        return result
    }

    override fun httpApacheGetResponse(httpGet: HttpGet): HttpResponse {
        return ApacheHttpClientUtils.getHttpClient(data.sslMode).execute(httpGet)
    }

    private fun signRequest(httpGetOrPost: Any?) {
        if (data.oauthClientKeys?.areKeysPresent() == true) {
            try {
                getConsumer().sign(httpGetOrPost)
            } catch (e: OAuthMessageSignerException) {
                throw IOException(e)
            } catch (e: OAuthExpectationFailedException) {
                throw IOException(e)
            } catch (e: OAuthCommunicationException) {
                throw IOException(e)
            }
        }
    }

    override fun httpApacheSetAuthorization(httpGet: HttpGet) {
        signRequest(httpGet)
    }

    override fun getRequest(result: HttpReadResult): HttpReadResult {
        return HttpConnectionApacheCommon(this, data).getRequest(result)
    }
}
