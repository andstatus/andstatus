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

import cz.msebera.android.httpclient.Header
import cz.msebera.android.httpclient.HttpEntity
import cz.msebera.android.httpclient.HttpResponse
import cz.msebera.android.httpclient.HttpVersion
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity
import cz.msebera.android.httpclient.client.methods.HttpGet
import cz.msebera.android.httpclient.client.methods.HttpPost
import cz.msebera.android.httpclient.protocol.HTTP
import org.andstatus.app.data.DbUtils.closeSilently
import org.andstatus.app.net.http.ConnectionException.StatusCode
import org.andstatus.app.util.MyLog
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.util.*

class HttpConnectionApacheCommon internal constructor(private val specific: HttpConnectionApacheSpecific, private val data: HttpConnectionData) {

    fun postRequest(result: HttpReadResult): HttpReadResult {
        val httpPost = HttpPost(result.getUrl())
        if (result.request.isLegacyHttpProtocol()) {
            httpPost.protocolVersion = HttpVersion.HTTP_1_0
        }
        if (result.request.mediaUri.isPresent) {
            try {
                httpPost.entity = ApacheHttpClientUtils.multiPartFormEntity(result.request)
            } catch (e: Exception) {
                result.setException(e)
                MyLog.i(this, e)
            }
        } else {
            result.request.postParams.ifPresent { params: JSONObject ->
                try {
                    data.optOriginContentType().ifPresent { value: String -> httpPost.addHeader("Content-Type", value) }
                    fillSinglePartPost(httpPost, params)
                } catch (e: Exception) {
                    result.setException(e)
                    MyLog.i(this, e)
                }
            }
        }
        return specific.httpApachePostRequest(httpPost, result)
    }

    @Throws(UnsupportedEncodingException::class)
    private fun fillSinglePartPost(httpPost: HttpPost, formParams: JSONObject) {
        val nvFormParams = ApacheHttpClientUtils.jsonToNameValuePair(formParams)
        val formEntity: HttpEntity = UrlEncodedFormEntity(nvFormParams, HTTP.UTF_8)
        httpPost.setEntity(formEntity)
    }

    fun getRequest(result: HttpReadResult): HttpReadResult {
        var response: HttpResponse? = null
        try {
            var stop: Boolean
            do {
                val httpGet = newHttpGet(result.getUrl())
                data.optOriginContentType().ifPresent { value: String? -> httpGet.addHeader("Accept", value) }
                if (result.authenticate()) {
                    specific.httpApacheSetAuthorization(httpGet)
                }
                // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
                response = specific.httpApacheGetResponse(httpGet)
                setStatusCodeAndHeaders(result, response)
                when (result.getStatusCode()) {
                    StatusCode.OK, StatusCode.UNKNOWN -> {
                        val entity = response.entity
                        if (entity != null) {
                            result.readStream("") { o: Void? -> entity.content }
                        }
                        stop = true
                    }
                    StatusCode.MOVED -> stop = specific.onMoved(result)
                    else -> {
                        val entity = response.entity
                        if (entity != null) {
                            result.readStream("") { o: Void? -> entity.getContent() }
                        }
                        stop = result.noMoreHttpRetries()
                    }
                }
                closeSilently(response)
            } while (!stop)
        } catch (e: Exception) {
            result.setException(e)
        } finally {
            closeSilently(response)
        }
        return result
    }

    private fun newHttpGet(url: String): HttpGet {
        val httpGet = HttpGet(url)
        httpGet.setHeader("User-Agent", HttpConnectionInterface.USER_AGENT)
        return httpGet
    }

    companion object {
        fun setStatusCodeAndHeaders(result: HttpReadResult, httpResponse: HttpResponse) {
            val statusLine = httpResponse.getStatusLine()
            result.statusLine = statusLine.toString()
            result.setStatusCode(statusLine.statusCode)
            result.setHeaders(Arrays.stream(httpResponse.getAllHeaders()), { obj: Header -> obj.getName() }, { obj: Header -> obj.getValue() })
        }
    }
}