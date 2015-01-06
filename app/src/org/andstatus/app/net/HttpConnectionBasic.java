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

package org.andstatus.app.net;

import android.text.TextUtils;
import android.util.Base64;

import org.andstatus.app.account.AccountDataWriter;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class HttpConnectionBasic extends HttpConnection implements HttpConnectionApacheSpecific  {
    protected String mPassword = "";

    @Override
    protected void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
        setPassword(connectionData.dataReader.getDataString(Connection.KEY_PASSWORD, ""));
    }  

    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return new HttpConnectionApacheCommon(this).postRequest(path);
    }

    @Override
    protected JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        return new HttpConnectionApacheCommon(this).postRequest(path, formParams);
    }

    @Override
    public JSONObject httpApachePostRequest(HttpPost postMethod) throws ConnectionException {
        String method = "postRequest";
        String result = "?";
        JSONObject jObj = null;
        StatusLine statusLine = null;
        Exception e1 = null;
        String logmsg = method + "; URI='" + postMethod.getURI().toString() + "'";
        try {
            HttpClient client = HttpConnectionApacheCommon.getHttpClient();
            postMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            if (getCredentialsPresent()) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials());
            }
            HttpResponse httpResponse = client.execute(postMethod);
            statusLine = httpResponse.getStatusLine();
            result = HttpConnectionApacheCommon.readHttpResponseToString(httpResponse);
            if (!TextUtils.isEmpty(result)) {
                jObj = new JSONObject(result);
                String error = jObj.optString("error");
                if ("Could not authenticate you.".equals(error)) {
                    throw new ConnectionException(logmsg + "; " + error);
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, logmsg, e, result);
        } catch (Exception e) {
            e1 = e;
        } finally {
            postMethod.abort();
        }
        new HttpConnectionApacheCommon(this).parseStatusLine(statusLine, e1, logmsg, result);
        return jObj;
    }

    @Override
    protected final JSONObject getRequest(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrlString(path));
        return new HttpConnectionApacheCommon(this).getRequestAsObject(get);
    }

    @Override
    protected final JSONArray getRequestAsArray(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrlString(path));
        return new HttpConnectionApacheCommon(this).getRequestAsArray(get);
    }

    @Override
    public HttpResponse httpApacheGetResponse(HttpGet getMethod) throws IOException {
        HttpClient client = HttpConnectionApacheCommon.getHttpClient();
        getMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
        if (getCredentialsPresent()) {
            getMethod.addHeader("Authorization", "Basic " + getCredentials());
        }
        return client.execute(getMethod);
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
    public void downloadFile(String url, File file) throws ConnectionException {
        new HttpConnectionApacheCommon(this).downloadFile(url, file);
    }
}
