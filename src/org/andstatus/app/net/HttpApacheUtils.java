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

package org.andstatus.app.net;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileNotFoundException;
import java.io.InputStream;
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
        return jsonTokenerToArray(request.getRequest(get));
    }

    final JSONArray jsonTokenerToArray(JSONTokener jst) throws ConnectionException {
        String method = "jsonTokenerToArray";
        JSONArray jsa = null;
        try {
            Object obj = jst.nextValue();
            if (JSONObject.class.isInstance(obj)) {
                JSONObject jso = (JSONObject) obj;
                @SuppressWarnings("unchecked")
                Iterator<String> iterator =  jso.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    Object obj2 = jso.get(key);                    
                    if (JSONArray.class.isInstance(obj2)) {
                        MyLog.v(this, method + " found array inside '" + key + "' object");
                        obj = obj2;
                        break;
                    }
                }
            }
            jsa = (JSONArray) obj;
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
    
    /**
     * @return empty {@link JSONObject} in a case of error
     */
    protected JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        HttpPost postMethod = new HttpPost(request.pathToUrl(path));
        JSONObject out = new JSONObject();
        try {
            if (formParams == null || formParams.length() == 0) {
                // Nothing to do
            } else if (formParams.has(HttpConnection.KEY_MEDIA_PART_URI)) {
                fillMultiPartPost(postMethod, formParams);
            } else {
                fillSinglePartPost(postMethod, formParams);
            }
            out = request.postRequest(postMethod);
        } catch (UnsupportedEncodingException e) {
            MyLog.i(this, e);
        }
        return out;
    }

    private void fillMultiPartPost(HttpPost postMethod, JSONObject formParams) throws ConnectionException {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create(); 
        Uri mediaUri = null;
        String mediaPartName = "";
        @SuppressWarnings("unchecked")
        Iterator<String> iterator =  formParams.keys();
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = formParams.optString(name);
            if (HttpConnection.KEY_MEDIA_PART_NAME.equals(name)) {
                mediaPartName = value;
            } else if (HttpConnection.KEY_MEDIA_PART_URI.equals(name)) {
                mediaUri = UriUtils.fromString(value);
            } else {
                builder.addTextBody(name, value);
            }
        }
        if (!TextUtils.isEmpty(mediaPartName) && !UriUtils.isEmpty(mediaUri)) {
            try {
                InputStream ins = MyContextHolder.get().context().getContentResolver().openInputStream(mediaUri);
                ContentType contentType = ContentType.create(MyContentType.uri2MimeType(mediaUri, null)); 
                builder.addBinaryBody(mediaPartName, ins, contentType, mediaUri.getPath());
            } catch (FileNotFoundException e) {
                throw new ConnectionException("mediaUri='" + mediaUri + "'", e);
            }
        }
        postMethod.setEntity(builder.build()); 
    }

    private void fillSinglePartPost(HttpPost postMethod, JSONObject formParams)
            throws ConnectionException, UnsupportedEncodingException {
        List<NameValuePair> nvFormParams = HttpApacheUtils.jsonToNameValuePair(formParams);
        if (nvFormParams != null) {
            UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(nvFormParams, HTTP.UTF_8);
            postMethod.setEntity(formEntity);
        }
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

    /** Based on: https://github.com/rfc2822/davdroid/blob/master/src/at/bitfire/davdroid/webdav/DavHttpClient.java */
    private final static RequestConfig REQUEST_CONFIG;
    private final static Registry<ConnectionSocketFactory> SOCKET_FACTORY_REGISTRY;

    static {
        SOCKET_FACTORY_REGISTRY = RegistryBuilder.<ConnectionSocketFactory> create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", TlsSniSocketFactory.INSTANCE)
                .build();
        
        // use request defaults from AndroidHttpClient
        REQUEST_CONFIG = RequestConfig.copy(RequestConfig.DEFAULT)
                .setConnectTimeout(MyPreferences.getConnectionTimeoutMs())
                .setSocketTimeout(2*MyPreferences.getConnectionTimeoutMs())
                .setStaleConnectionCheckEnabled(false)
                .build();
    }
    
    static HttpClient getHttpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(SOCKET_FACTORY_REGISTRY);
        connectionManager.setMaxTotal(3);               // max.  3 connections in total
        connectionManager.setDefaultMaxPerRoute(2);     // max.  2 connections per host
        
        HttpClientBuilder builder = HttpClients.custom()
                .useSystemProperties()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(REQUEST_CONFIG)
                /* TODO maybe:  
                .setRetryHandler(DavHttpRequestRetryHandler.INSTANCE)
                .setRedirectStrategy(DavRedirectStrategy.INSTANCE)  
                */
                .setUserAgent(HttpConnection.USER_AGENT)
                .disableCookieManagement();

        return builder.build();
    }
    
}
