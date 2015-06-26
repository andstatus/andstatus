/*
 * Copyright (C) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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

import android.text.TextUtils;
import android.util.Base64;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.social.Connection;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPostHC4;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;

public class HttpConnectionBasic extends HttpConnection implements HttpConnectionApacheSpecific  {
    protected String mPassword = "";

    @Override
    public void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
        setPassword(connectionData.dataReader.getDataString(Connection.KEY_PASSWORD, ""));
    }  

    @Override
    protected void postRequest(HttpReadResult result) throws ConnectionException {
        new HttpConnectionApacheCommon(this).postRequest(result);
    }

    @Override
    public void httpApachePostRequest(HttpPostHC4 postMethod, HttpReadResult result) throws ConnectionException {
        try {
            HttpClient client = HttpConnectionApacheCommon.getHttpClient(data.sslMode);
            postMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            if (getCredentialsPresent()) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials());
            }
            HttpResponse httpResponse = client.execute(postMethod);
            StatusLine statusLine = httpResponse.getStatusLine();
            result.statusLine = statusLine.toString();
            result.setStatusCode(statusLine.getStatusCode());
            result.strResponse = HttpConnectionApacheCommon.readHttpResponseToString(httpResponse);
        } catch (Exception e) {
            result.e1 = e;
        } finally {
            postMethod.abort();
        }
    }

    @Override
    public HttpResponse httpApacheGetResponse(HttpGet httpGet) throws IOException {
        HttpClient client = HttpConnectionApacheCommon.getHttpClient(data.sslMode);
        return client.execute(httpGet);
    }

    @Override
    public boolean getCredentialsPresent() {
        return !TextUtils.isEmpty(data.accountUsername) 
                && !TextUtils.isEmpty(mPassword);
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
                (data.accountUsername + ":" + mPassword).getBytes(Charset.forName("UTF-8")),
                Base64.NO_WRAP + Base64.NO_PADDING);
    }

    @Override
    public boolean save(AccountDataWriter dw) {
        boolean changed = super.save(dw);

        if (mPassword.compareTo(dw.getDataString(Connection.KEY_PASSWORD, "")) != 0) {
            dw.setDataString(Connection.KEY_PASSWORD, mPassword);
            changed = true;
        }

        return changed;
    }

    @Override
    public boolean save(JSONObject jso) throws JSONException {
        boolean changed = super.save(jso);

        if (mPassword.compareTo(jso.optString(Connection.KEY_PASSWORD, "")) != 0) {
            jso.put(Connection.KEY_PASSWORD, mPassword);
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
    protected void getRequest(HttpReadResult result) throws ConnectionException {
        new HttpConnectionApacheCommon(this).getRequest(result);
    }
}
