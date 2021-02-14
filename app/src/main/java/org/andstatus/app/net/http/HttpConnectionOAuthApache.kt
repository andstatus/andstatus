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
import org.andstatus.app.net.http.ConnectionException
import org.andstatus.app.net.social.ApiRoutineEnum

cz.msebera.android.httpclient.HttpResponse
import org.andstatus.app.context.CompletableFutureTest.TestData
import org.andstatus.app.service.MyServiceTest
import org.andstatus.app.service.AvatarDownloaderTest
import org.andstatus.app.service.RepeatingFailingCommandTest
import org.hamcrest.core.Is
import org.hamcrest.core.IsNot
import org.andstatus.app.timeline.meta.TimelineSyncTrackerTest
import org.andstatus.app.timeline.TimelinePositionTest
import org.andstatus.app.util.EspressoUtils
import org.andstatus.app.timeline.TimeLineActivityLayoutToggleTest
import org.andstatus.app.appwidget.MyAppWidgetProviderTest.DateTest
import org.andstatus.app.appwidget.MyAppWidgetProviderTest
import org.andstatus.app.notification.NotifierTest
import org.andstatus.app.ActivityTestHelper.MenuItemClicker
import org.andstatus.app.MenuItemMockimport

java.io.IOExceptionimport java.lang.Exception
class HttpConnectionOAuthApache : HttpConnectionOAuth(), HttpConnectionApacheSpecific {
    override fun setHttpConnectionData(connectionData: HttpConnectionData?) {
        super.setHttpConnectionData(connectionData)
    }

    @Throws(ConnectionException::class)
    override fun getProvider(): OAuthProvider? {
        val provider = CommonsHttpOAuthProvider(
                getApiUri(ApiRoutineEnum.OAUTH_REQUEST_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString())
        provider.setHttpClient(ApacheHttpClientUtils.getHttpClient(data.sslMode))
        provider.isOAuth10a = true
        return provider
    }

    override fun getConsumer(): OAuthConsumer? {
        val consumer: OAuthConsumer = CommonsHttpOAuthConsumer(data.oauthClientKeys.consumerKey,
                data.oauthClientKeys.consumerSecret)
        if (credentialsPresent) {
            consumer.setTokenWithSecret(userToken, userSecret)
        }
        return consumer
    }

    override fun postRequest(result: HttpReadResult?): HttpReadResult? {
        return HttpConnectionApacheCommon(this, data).postRequest(result)
    }

    override fun httpApachePostRequest(post: HttpPost?, result: HttpReadResult?): HttpReadResult? {
        try {
            // TODO: Redo like for get request
            if (result.authenticate()) {
                signRequest(post)
            }
            result.strResponse = ApacheHttpClientUtils.getHttpClient(data.sslMode).execute(
                    post, BasicResponseHandler())
        } catch (e: Exception) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            result.setException(e)
        }
        return result
    }

    @Throws(IOException::class)
    override fun httpApacheGetResponse(httpGet: HttpGet?): HttpResponse? {
        return ApacheHttpClientUtils.getHttpClient(data.sslMode).execute(httpGet)
    }

    @Throws(IOException::class)
    private fun signRequest(httpGetOrPost: Any?) {
        if (data.oauthClientKeys.areKeysPresent()) {
            try {
                consumer.sign(httpGetOrPost)
            } catch (e: OAuthMessageSignerException) {
                throw IOException(e)
            } catch (e: OAuthExpectationFailedException) {
                throw IOException(e)
            } catch (e: OAuthCommunicationException) {
                throw IOException(e)
            }
        }
    }

    @Throws(IOException::class)
    override fun httpApacheSetAuthorization(httpGet: HttpGet?) {
        signRequest(httpGet)
    }

    override fun getRequest(result: HttpReadResult?): HttpReadResult? {
        return HttpConnectionApacheCommon(this, data).getRequest(result)
    }
}