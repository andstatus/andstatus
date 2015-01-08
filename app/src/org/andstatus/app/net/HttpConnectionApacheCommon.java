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
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HttpConnectionApacheCommon {
    private HttpConnectionApacheSpecific specific;

    HttpConnectionApacheCommon(HttpConnectionApacheSpecific specificIn) {
        this.specific = specificIn;
    }

    protected JSONObject postRequest(String path) throws ConnectionException {
        HttpPost post = new HttpPost(specific.pathToUrlString(path));
        return specific.httpApachePostRequest(post);
    }

    /**
     * @return empty {@link JSONObject} in a case of error
     */
    protected JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        HttpPost postMethod = new HttpPost(specific.pathToUrlString(path));
        JSONObject out = new JSONObject();
        MyLog.logNetworkLevelMessage("postRequest_formParams", formParams);
        try {
            if (formParams == null || formParams.length() == 0) {
                // Nothing to do
            } else if (formParams.has(HttpConnection.KEY_MEDIA_PART_URI)) {
                fillMultiPartPost(postMethod, formParams);
            } else {
                fillSinglePartPost(postMethod, formParams);
            }
            out = specific.httpApachePostRequest(postMethod);
        } catch (UnsupportedEncodingException e) {
            MyLog.i(this, e);
        }
        MyLog.logNetworkLevelMessage("postRequest_result", out);
        return out;
    }

    private void fillMultiPartPost(HttpPost postMethod, JSONObject formParams) throws ConnectionException {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create(); 
        Uri mediaUri = null;
        String mediaPartName = "";
        Iterator<String> iterator =  formParams.keys();
        ContentType contentType = ContentType.create(HTTP.PLAIN_TEXT_TYPE, HTTP.UTF_8);
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = formParams.optString(name);
            if (HttpConnection.KEY_MEDIA_PART_NAME.equals(name)) {
                mediaPartName = value;
            } else if (HttpConnection.KEY_MEDIA_PART_URI.equals(name)) {
                mediaUri = UriUtils.fromString(value);
            } else {
                // see http://stackoverflow.com/questions/19292169/multipartentitybuilder-and-charset
                builder.addTextBody(name, value, contentType);
            }
        }
        if (!TextUtils.isEmpty(mediaPartName) && !UriUtils.isEmpty(mediaUri)) {
            try {
                InputStream ins = MyContextHolder.get().context().getContentResolver().openInputStream(mediaUri);
                ContentType contentType2 = ContentType.create(MyContentType.uri2MimeType(mediaUri, null)); 
                builder.addBinaryBody(mediaPartName, ins, contentType2, mediaUri.getPath());
            } catch (SecurityException e) {
                throw new ConnectionException("mediaUri='" + mediaUri + "'", e);
            } catch (FileNotFoundException e) {
                throw new ConnectionException("mediaUri='" + mediaUri + "'", e);
            }
        }
        postMethod.setEntity(builder.build()); 
    }

    private void fillSinglePartPost(HttpPost postMethod, JSONObject formParams)
            throws ConnectionException, UnsupportedEncodingException {
        List<NameValuePair> nvFormParams = HttpConnectionApacheCommon.jsonToNameValuePair(formParams);
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

    public static HttpClient getHttpClient() {
        return MyPreferences.getBoolean(MyPreferences.KEY_ALLOW_MISCONFIGURED_SSL, false) ?
                MisconfiguredSslHttpClientFactory.getHttpClient() :
                    MyHttpClientFactory.getHttpClient() ;
    }

    protected void downloadFile(String url, File file) throws ConnectionException {
        HttpReadResult result = new HttpReadResult(url, file);
        getRequest(result);
    }

    protected void getRequest(HttpReadResult result) throws ConnectionException {
        String method = "getRequest; ";
        // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
        StringBuilder logBuilder =  new StringBuilder(method + "URL='" + result.getUrl() + "'; ");
        StatusLine statusLine = null;
        String strResponse = "";
        Exception e1 = null;
        HttpResponse httpResponse = null;
        try {
            boolean stop = false;
            boolean authenticate = true;
            do {
                HttpGet httpGet = newHttpGet(result.getUrl());
                if (authenticate) {
                    specific.httpApacheSetAuthorization(httpGet);
                }
                httpResponse = specific.httpApacheGetResponse(httpGet);
                statusLine = httpResponse.getStatusLine();
                result.statusCode = ConnectionException.StatusCode.fromResponseCode(statusLine.getStatusCode());
                switch (result.statusCode) {
                    case OK:
                    case UNKNOWN:
                        HttpEntity entity = httpResponse.getEntity();
                        if (entity != null) {
                            if (result.fileResult != null) {
                                HttpConnectionUtils.readStreamToFile(entity.getContent(), result.fileResult);
                            } else {
                                result.strResponse = HttpConnectionUtils.readStreamToString(entity.getContent());
                            }
                        }
                        stop = true;
                        break;
                    case MOVED:
                        result.redirected = true;
                        result.setUrl(httpResponse.getHeaders("Location")[0].getValue().replace("%3F", "?"));
                        String logMsg3 = "Following redirect to '" + result.getUrl() + "'";
                        logBuilder.append(logMsg3 + "; ");
                        MyLog.v(this, method + logMsg3);
                        if (MyLog.isLoggable(MyLog.APPTAG, MyLog.VERBOSE)) {
                            StringBuilder message = new StringBuilder(method + "Headers: ");
                            for (Header header: httpResponse.getAllHeaders()) {
                                message.append(header.getName() +": " + header.getValue() + ";\n");
                            }
                            MyLog.v(this, message.toString());
                        }
                        break;
                    default:
                        entity = httpResponse.getEntity();
                        if (entity != null) {
                            result.strResponse = HttpConnectionUtils.readStreamToString(entity.getContent());
                        }
                        stop = isStatusLineOk(statusLine) || !authenticate;
                        if (!stop) {
                            authenticate = false;
                            DbUtils.closeSilently(httpResponse);
                            MyLog.v(this, logBuilder.toString() + "; retrying without authentication; statusLine='" + statusLine + "'");
                        }
                        break;
                }
            } while (!stop);
        } catch (IOException e) {
            e1 = e;
        } finally {
            DbUtils.closeSilently(httpResponse);
        }
        parseStatusLine(statusLine, e1, logBuilder.toString(), strResponse);
    }
    
    private HttpGet newHttpGet(String url) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", HttpConnection.USER_AGENT);
        return httpGet;
    }

    public static String readHttpResponseToString(HttpResponse httpResponse) throws IOException {
        HttpEntity httpEntity = httpResponse.getEntity();
        if (httpEntity != null) {
            try {
                return HttpConnectionUtils.readStreamToString(httpEntity.getContent());
            } catch (IllegalStateException e) {
                throw new IOException(e);
            }
        }
        return null;
    }
    
    
    /**
     * Parse and throw appropriate exceptions when necessary
     */
    void parseStatusLine(StatusLine statusLine, Throwable tr, String logMsgIn, String strResponse) throws ConnectionException {
        String logMsg = (logMsgIn == null) ? "" : logMsgIn;
        ConnectionException.StatusCode statusCode = StatusCode.UNKNOWN;
        if (statusLine != null) {
            if (isStatusLineOk(statusLine)) {
                return;
            }
            statusCode = ConnectionException.StatusCode.fromResponseCode(statusLine.getStatusCode());
            logMsg += "; statusLine='" + statusLine + "'";
        }
        logMsg = appendStringResponse(logMsg, strResponse);
        if (tr != null) {
            MyLog.i(this, statusCode.toString() + "; " + logMsg, tr);
        }
        throw ConnectionException.fromStatusCodeAndThrowable(statusCode, logMsg, tr);
    }

    boolean isStatusLineOk(StatusLine statusLine) {
        if (statusLine != null) {
            switch (ConnectionException.StatusCode.fromResponseCode(statusLine.getStatusCode())) {
                case OK:
                case UNKNOWN:
                    return true;
                default:
                    break;
            }
        }
        return false;
    }
    
    private String appendStringResponse(String logMsgIn, String strResponse) {
        String logMsg = (logMsgIn == null) ? "" : logMsgIn;
        if (!TextUtils.isEmpty(strResponse)) {
            logMsg += "; response='" + I18n.trimTextAt(strResponse, 120) + "'";
        }
        return logMsg;
    }
    
}
