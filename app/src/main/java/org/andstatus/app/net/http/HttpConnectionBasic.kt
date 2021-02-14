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
import io.vavr.control.CheckedFunction
import org.andstatus.app.account.AccountDataWriter
import org.andstatus.app.net.social.Connection
import org.andstatus.app.util.StringUtil
import java.io.IOException
import java.nio.charset.Charset

class HttpConnectionBasic : HttpConnection(), HttpConnectionApacheSpecific {
    protected var mPassword: String? = ""
    override fun setHttpConnectionData(connectionData: HttpConnectionData?) {
        super.setHttpConnectionData(connectionData)
        password = connectionData.dataReader.getDataString(Connection.Companion.KEY_PASSWORD)
    }

    override fun postRequest(result: HttpReadResult?): HttpReadResult? {
        return HttpConnectionApacheCommon(this, data).postRequest(result)
    }

    override fun httpApachePostRequest(postMethod: HttpPost?, result: HttpReadResult?): HttpReadResult? {
        try {
            val client = ApacheHttpClientUtils.getHttpClient(data.sslMode)
            postMethod.setHeader("User-Agent", HttpConnectionInterface.Companion.USER_AGENT)
            if (credentialsPresent) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials())
            }
            val httpResponse = client.execute(postMethod)
            HttpConnectionApacheCommon.Companion.setStatusCodeAndHeaders(result, httpResponse)
            val httpEntity = httpResponse.entity
            result.readStream("", CheckedFunction { o: Void? -> httpEntity?.content })
        } catch (e: Exception) {
            result.setException(e)
        } finally {
            postMethod.abort()
        }
        return result
    }

    @Throws(IOException::class)
    override fun httpApacheGetResponse(httpGet: HttpGet?): HttpResponse? {
        val client = ApacheHttpClientUtils.getHttpClient(data.sslMode)
        return client.execute(httpGet)
    }

    override fun getCredentialsPresent(): Boolean {
        return (!StringUtil.isEmpty(data.accountName.uniqueName)
                && !StringUtil.isEmpty(mPassword))
    }

    override fun clearAuthInformation() {
        password = ""
    }

    override fun isPasswordNeeded(): Boolean {
        return true
    }

    override fun setPassword(passwordIn: String?) {
        mPassword = passwordIn ?: ""
    }

    override fun getPassword(): String? {
        return mPassword
    }

    /**
     * Get the HTTP digest authentication. Uses Base64 to encode credentials.
     *
     * @return String
     */
    private fun getCredentials(): String? {
        return Base64.encodeToString(
                (data.accountName.username + ":" + mPassword).toByteArray(Charset.forName("UTF-8")),
                Base64.NO_WRAP + Base64.NO_PADDING)
    }

    override fun saveTo(dw: AccountDataWriter?): Boolean {
        var changed: Boolean = super.saveTo(dw)
        if (mPassword.compareTo(dw.getDataString(Connection.Companion.KEY_PASSWORD)) != 0) {
            dw.setDataString(Connection.Companion.KEY_PASSWORD, mPassword)
            changed = true
        }
        return changed
    }

    override fun httpApacheSetAuthorization(httpGet: HttpGet?) {
        if (credentialsPresent) {
            httpGet.addHeader("Authorization", "Basic " + getCredentials())
        }
    }

    override fun getRequest(result: HttpReadResult?): HttpReadResult? {
        return HttpConnectionApacheCommon(this, data).getRequest(result)
    }
}