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
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class HttpConnectionBasic extends HttpConnection implements HttpApacheRequest  {
    protected String mPassword = "";

    @Override
    protected void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
        setPassword(connectionData.dataReader.getDataString(Connection.KEY_PASSWORD, ""));
    }  

    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return new HttpApacheUtils(this).postRequest(path);
    }

    @Override
    protected JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        return new HttpApacheUtils(this).postRequest(path, formParams);
    }

    @Override
    public JSONObject postRequest(HttpPost postMethod) throws ConnectionException {
        String method = "postRequest";
        String result = "?";
        JSONObject jObj = null;
        StatusLine statusLine = null;
        Exception e1 = null;
        String logmsg = method + "; URI='" + postMethod.getURI().toString() + "'";
        try {
            HttpClient client = HttpApacheUtils.getHttpClient();
            postMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            if (getCredentialsPresent()) {
                postMethod.addHeader("Authorization", "Basic " + getCredentials());
            }
            HttpResponse httpResponse = client.execute(postMethod);
            statusLine = httpResponse.getStatusLine();
            result = retrieveInputStream(httpResponse.getEntity());
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
        parseStatusLine(statusLine, e1, logmsg, result);
        return jObj;
    }

    @Override
    protected final JSONObject getRequest(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return new HttpApacheUtils(this).getRequestAsObject(get);
    }

    @Override
    protected final JSONArray getRequestAsArray(String path) throws ConnectionException {
        HttpGet get = new HttpGet(pathToUrl(path));
        return new HttpApacheUtils(this).getRequestAsArray(get);
    }

    @Override
    public JSONTokener getRequest(HttpGet getMethod) throws ConnectionException {
        JSONTokener jso = null;
        String response = null;
        StatusLine statusLine = null;
        Exception e1 = null;
        String logmsg = "getRequest; URI='" + getMethod.getURI().toString() + "'";
        HttpClient client = HttpApacheUtils.getHttpClient();
        try {
            getMethod.setHeader("User-Agent", HttpConnection.USER_AGENT);
            if (getCredentialsPresent()) {
                getMethod.addHeader("Authorization", "Basic " + getCredentials());
            }
            HttpResponse httpResponse = client.execute(getMethod);
            statusLine = httpResponse.getStatusLine();
            response = retrieveInputStream(httpResponse.getEntity());
            jso = new JSONTokener(response);
        } catch (Exception e) {
            e1 = e;
        } finally {
            getMethod.abort();
        }
        MyLog.logNetworkLevelMessage("getRequest_basic", response);
        parseStatusLine(statusLine, e1, logmsg, response);
        return jso;
    }

    private String appendResponse(String logMsgIn, String response) {
        String logMsg = (logMsgIn == null) ? "" : logMsgIn;
        if (!TextUtils.isEmpty(response)) {
            logMsg += "; response='" + I18n.trimTextAt(response, 120) + "'";
        }
        return logMsg;
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
     * Retrieve the input stream from the HTTP connection.
     * 
     * @param httpEntity
     * @return String
     */
    private String retrieveInputStream(HttpEntity httpEntity) {
        int length = (int) httpEntity.getContentLength();
        if ( length <= 0 ) {
            // Length is unknown or large
            length = 1024;
        }
        StringBuilder stringBuffer = new StringBuilder(length);
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(httpEntity.getContent(), HTTP.UTF_8);
            char[] buffer = new char[length];
            int count;
            while ((count = inputStreamReader.read(buffer, 0, length - 1)) > 0) {
                stringBuffer.append(buffer, 0, count);
            }
        } catch (UnsupportedEncodingException e) {
            MyLog.e(this, e);
        } catch (IllegalStateException e) {
            MyLog.e(this, e);
        } catch (IOException e) {
            MyLog.e(this, e);
        } finally {
            DbUtils.closeSilently(inputStreamReader);
        }
        return stringBuffer.toString();
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

    /**
     * Parse the status code and throw appropriate exceptions when necessary.
     * 
     * @param code
     * @param response 
     * @throws ConnectionException
     */
    private void parseStatusLine(StatusLine statusLine, Throwable tr, String logMsgIn, String response) throws ConnectionException {
        String logMsg = (logMsgIn == null) ? "" : logMsgIn;
        ConnectionException.StatusCode statusCode = StatusCode.UNKNOWN;
        if (statusLine != null) {
            switch (ConnectionException.StatusCode.fromResponseCode(statusLine.getStatusCode())) {
                case OK:
                case UNKNOWN:
                    return;
                default:
                    break;
            }
            statusCode = ConnectionException.StatusCode.fromResponseCode(statusLine.getStatusCode());
            logMsg += "; statusLine='" + statusLine + "'";
        }
        logMsg = appendResponse(logMsg, response);
        if (tr != null) {
            MyLog.i(this, statusCode.toString() + "; " + logMsg, tr);
        }
        throw ConnectionException.fromStatusCodeAndThrowable(statusCode, logMsg, tr);
    }
}
