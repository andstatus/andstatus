/*
 * Copyright (C) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.util.Base64
import cz.msebera.android.httpclient.HttpResponse
import cz.msebera.android.httpclient.client.methods.HttpGet
import cz.msebera.android.httpclient.client.methods.HttpPost
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.http.HttpConnectionOAuthJavaNet.Companion.setStatusCodeAndHeaders
import org.andstatus.app.net.social.Connection
import java.net.HttpURLConnection
import java.nio.charset.Charset

class HttpConnectionBasic : HttpConnection(), HttpConnectionApacheSpecific {

    var password: String = ""

    override var data: HttpConnectionData
        get() = super.data
        set(connectionData) {
            super.data = connectionData
            password = connectionData.dataReader?.getDataString(Connection.KEY_PASSWORD) ?: ""
        }

    override fun postRequest(result: HttpReadResult): HttpReadResult {
        return HttpConnectionApacheCommon(this, this).postRequest(result)
    }

    override fun httpApachePostRequest(postMethod: HttpPost, result: HttpReadResult): HttpReadResult {
        try {
            val client = ApacheHttpClientUtils.getHttpClient(data.sslMode)
            postMethod.setHeader("User-Agent", USER_AGENT)
            if (credentialsPresent) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials())
            }
            val httpResponse = client.execute(postMethod)
            HttpConnectionApacheCommon.setStatusCodeAndHeaders(result, httpResponse)
            val httpEntity = httpResponse.entity
            result.readStream("") { httpEntity?.content }
        } catch (e: Exception) {
            result.setException(e)
        } finally {
            postMethod.abort()
        }
        return result
    }

    override fun httpApacheGetResponse(httpGet: HttpGet): HttpResponse {
        val client = ApacheHttpClientUtils.getHttpClient(data.sslMode)
        return client.execute(httpGet)
    }

    override val credentialsPresent: Boolean get() = data.getAccountName().getUniqueName().isNotEmpty()
                && password.isNotEmpty()

    override fun clearAuthInformation() {
        password = ""
    }

    override fun isPasswordNeeded(): Boolean {
        return true
    }

    /**
     * Get the HTTP digest authentication. Uses Base64 to encode credentials.
     *
     * @return String
     */
    private fun getCredentials(): String? {
        return Base64.encodeToString(
                (data.getAccountName().username + ":" + password).toByteArray(Charset.forName("UTF-8")),
                Base64.NO_WRAP + Base64.NO_PADDING)
    }

    override fun saveTo(dw: AccountDataWriter): Boolean {
        var changed: Boolean = super<HttpConnection>.saveTo(dw)
        if (password.compareTo(dw.getDataString(Connection.KEY_PASSWORD)) != 0) {
            dw.setDataString(Connection.KEY_PASSWORD, password)
            changed = true
        }
        return changed
    }

    override fun httpApacheSetAuthorization(httpGet: HttpGet) {
        if (credentialsPresent) {
            httpGet.addHeader("Authorization", "Basic " + getCredentials())
        }
    }

    /** Almost like [org.andstatus.app.net.http.HttpConnectionOAuthJavaNet.getRequest] */
    override fun getRequest(result: HttpReadResult): HttpReadResult {
        var connCopy: HttpURLConnection? = null
        try {
            var redirected = false
            var stop: Boolean
            do {
                connCopy = result.requiredUrl("GetBasic")?.openConnection() as HttpURLConnection? ?: return result
                val conn = connCopy
                conn.readTimeout = MyPreferences.getConnectionTimeoutMs()
                conn.connectTimeout = MyPreferences.getConnectionTimeoutMs()
                data.optOriginContentType().ifPresent { value: String -> conn.addRequestProperty("Accept", value) }
                conn.instanceFollowRedirects = false
                if (result.authenticate()) {
                    conn.addRequestProperty("Authorization", "Basic " + getCredentials())
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
                DbUtils.closeSilently(conn)
            } while (!stop)
        } catch (e: Exception) {
            result.setException(e)
        } finally {
            DbUtils.closeSilently(connCopy)
        }
        return result
    }

}
