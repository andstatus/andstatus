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

package org.andstatus.app.net;

import android.text.TextUtils;

import org.andstatus.app.util.MyLog;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class HttpApacheUtils {
    private HttpApacheRequest request;
    
    HttpApacheUtils(HttpApacheRequest request) {
        this.request = request;
    }

    final JSONArray getRequestAsArray(HttpGet get) throws ConnectionException {
        String method = "getRequestAsArray";
        JSONArray jsa = null;
        JSONTokener jst = request.getRequest(get);
        try {
            jsa = (JSONArray) jst.nextValue();
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, e, jst, method);
        } catch (ClassCastException e) {
            ConnectionException connectionException = ConnectionException.loggedJsonException(this, e, jst, method);
            connectionException.setHardError(true);
            throw connectionException;
        }
        return jsa;
    }
    
    final JSONObject getRequestAsObject(HttpGet get) throws ConnectionException {
        String method = "getRequestAsObject";
        JSONObject jso = null;
        JSONTokener jst = request.getRequest(get);
        try {
            jso = (JSONObject) jst.nextValue();
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, e, jst, method);
        } catch (ClassCastException e) {
            ConnectionException connectionException = ConnectionException.loggedJsonException(this, e, jst, method);
            connectionException.setHardError(true);
            throw connectionException;
        }
        return jso;
    }

    protected JSONObject postRequest(String path) throws ConnectionException {
        HttpPost post = new HttpPost(request.pathToUrl(path));
        return request.postRequest(post);
    }
    
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        List<NameValuePair> formParams = HttpApacheUtils.jsonToNameValuePair(jso);
        HttpPost postMethod = new HttpPost(request.pathToUrl(path));
        try {
            if (formParams != null) {
                UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams, HTTP.UTF_8);
                postMethod.setEntity(formEntity);
            }
            jso = request.postRequest(postMethod);
        } catch (UnsupportedEncodingException e) {
            MyLog.e(this, e);
        }
        return jso;
    }
    
    /**
     * @throws ConnectionException
     */
    static final List<NameValuePair> jsonToNameValuePair(JSONObject jso) throws ConnectionException {
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        @SuppressWarnings("unchecked")
        Iterator<String> iterator =  jso.keys();
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = jso.optString(name);
            if (!TextUtils.isEmpty(value)) {
                formParams.add(new BasicNameValuePair(name, value));
            }
        }
        return formParams;
    }
    
    static HttpClient getHttpClient() {
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        
        SSLSocketFactory socketFactory = SSLSocketFactory.getSocketFactory();
        // This is done to get rid of the "javax.net.ssl.SSLException: hostname in certificate didn't match" error
        // See e.g. http://stackoverflow.com/questions/8839541/hostname-in-certificate-didnt-match
        socketFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);        
        schemeRegistry.register(new Scheme("https", socketFactory, 443));

        HttpParams params = getHttpParams();        
        ClientConnectionManager clientConnectionManager = new ThreadSafeClientConnManager(params, schemeRegistry);
        return new DefaultHttpClient(clientConnectionManager, params);
    }

    private static HttpParams getHttpParams() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        HttpConnectionParams.setStaleCheckingEnabled(params, true);

        HttpProtocolParams.setUseExpectContinue(params, false);
        HttpConnectionParams.setSoTimeout(params, 30000);
        HttpConnectionParams.setSocketBufferSize(params, 2*8192);
        return params;
    }
    
}
