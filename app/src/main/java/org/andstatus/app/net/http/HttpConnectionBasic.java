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

package org.andstatus.app.net.http;

import android.util.Base64;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.StringUtils;

import java.io.IOException;
import java.nio.charset.Charset;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;

public class HttpConnectionBasic extends HttpConnection implements HttpConnectionApacheSpecific  {
    protected String mPassword = "";

    @Override
    public void setHttpConnectionData(HttpConnectionData connectionData) {
        super.setHttpConnectionData(connectionData);
        setPassword(connectionData.dataReader.getDataString(Connection.KEY_PASSWORD));
    }  

    @Override
    public HttpReadResult postRequest(HttpReadResult result) {
        return new HttpConnectionApacheCommon(this, data).postRequest(result);
    }

    @Override
    public HttpReadResult httpApachePostRequest(HttpPost postMethod, HttpReadResult result) {
        try {
            HttpClient client = ApacheHttpClientUtils.getHttpClient(data.getSslMode());
            postMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            if (getCredentialsPresent()) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials());
            }
            HttpResponse httpResponse = client.execute(postMethod);
            HttpConnectionApacheCommon.setStatusCodeAndHeaders(result, httpResponse);
            HttpEntity httpEntity = httpResponse.getEntity();
            HttpConnectionUtils.readStream(result, httpEntity == null ? null : httpEntity.getContent());
        } catch (Exception e) {
            result.setException(e);
        } finally {
            postMethod.abort();
        }
        return result;
    }

    @Override
    public HttpResponse httpApacheGetResponse(HttpGet httpGet) throws IOException {
        HttpClient client = ApacheHttpClientUtils.getHttpClient(data.getSslMode());
        return client.execute(httpGet);
    }

    @Override
    public boolean getCredentialsPresent() {
        return !StringUtils.isEmpty(data.getAccountName().getUniqueName())
                && !StringUtils.isEmpty(mPassword);
    }

    @Override
    public void clearAuthInformation() {
        setPassword("");
    }

    @Override
    public boolean isPasswordNeeded() {
        return true;
    }
    @Override
    public void setPassword(String passwordIn) {
        mPassword = passwordIn == null ? "" : passwordIn;
    }
    @Override
    public String getPassword() {
        return mPassword;
    }

    /**
     * Get the HTTP digest authentication. Uses Base64 to encode credentials.
     * 
     * @return String
     */
    private String getCredentials() {
        return Base64.encodeToString(
                (data.getAccountName().username + ":" + mPassword).
                        getBytes(Charset.forName("UTF-8")),
                Base64.NO_WRAP + Base64.NO_PADDING);
    }

    @Override
    public boolean saveTo(AccountDataWriter dw) {
        boolean changed = super.saveTo(dw);

        if (mPassword.compareTo(dw.getDataString(Connection.KEY_PASSWORD)) != 0) {
            dw.setDataString(Connection.KEY_PASSWORD, mPassword);
            changed = true;
        }

        return changed;
    }

    @Override
    public void httpApacheSetAuthorization(HttpGet httpGet) {
        if (getCredentialsPresent()) {
            httpGet.addHeader("Authorization", "Basic " + getCredentials());
        }
    }

    @Override
    public void getRequest(HttpReadResult result) {
        new HttpConnectionApacheCommon(this, data).getRequest(result);
    }
}
