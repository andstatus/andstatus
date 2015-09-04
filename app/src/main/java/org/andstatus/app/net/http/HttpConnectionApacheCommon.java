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

package org.andstatus.app.net.http;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPostHC4;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

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

    protected void postRequest(HttpReadResult result) throws ConnectionException {
        HttpPostHC4 httpPost = new HttpPostHC4(result.getUrl());
        if (result.isLegacyHttpProtocol()) {
            httpPost.setProtocolVersion(HttpVersion.HTTP_1_0);
        }
        try {
            if ( !result.hasFormParams()) {
                // Nothing to do at this step
            } else if (result.getFormParams().has(HttpConnection.KEY_MEDIA_PART_URI)) {
                fillMultiPartPost(httpPost, result.getFormParams());
            } else {
                fillSinglePartPost(httpPost, result.getFormParams());
            }
            specific.httpApachePostRequest(httpPost, result);
        } catch (UnsupportedEncodingException e) {
            MyLog.i(this, e);
        }
    }

    private void fillMultiPartPost(HttpPostHC4 httpPost, JSONObject formParams) throws ConnectionException {
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
                if (httpPost.getProtocolVersion() == HttpVersion.HTTP_1_0 ) {
                    builder.addBinaryBody(mediaPartName, FileUtils.getBytes(ins), contentType2, mediaUri.getPath());
                } else {
                    builder.addBinaryBody(mediaPartName, ins, contentType2, mediaUri.getPath());
                }
            } catch (SecurityException | IOException e) {
                throw ConnectionException.hardConnectionException("mediaUri='" + mediaUri + "'", e);
            }
        }
        httpPost.setEntity(builder.build()); 
    }
    
    private void fillSinglePartPost(HttpPostHC4 httpPost, JSONObject formParams)
            throws UnsupportedEncodingException {
        List<NameValuePair> nvFormParams = HttpConnectionApacheCommon.jsonToNameValuePair(formParams);
        if (nvFormParams != null) {
            HttpEntity formEntity = new UrlEncodedFormEntityHC4(nvFormParams, HTTP.UTF_8);
            httpPost.setEntity(formEntity);
        }
    }
    
    /**
     * @throws ConnectionException
     */
    static List<NameValuePair> jsonToNameValuePair(JSONObject jso) {
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

    public static HttpClient getHttpClient(SslModeEnum sslMode) {
        return sslMode == SslModeEnum.MISCONFIGURED ?
                MisconfiguredSslHttpClientFactory.getHttpClient() :
                    MyHttpClientFactory.getHttpClient(sslMode) ;
    }

    protected void getRequest(HttpReadResult result) {
        String method = "getRequest; ";
        // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
        HttpResponse httpResponse = null;
        try {
            boolean stop = false;
            do {
                HttpGet httpGet = newHttpGet(result.getUrl());
                if (result.authenticate) {
                    specific.httpApacheSetAuthorization(httpGet);
                }
                httpResponse = specific.httpApacheGetResponse(httpGet);
                StatusLine statusLine = httpResponse.getStatusLine();
                result.statusLine = statusLine.toString();
                result.setStatusCode(statusLine.getStatusCode());
                switch (result.getStatusCode()) {
                    case OK:
                    case UNKNOWN:
                        HttpEntity entity = httpResponse.getEntity();
                        if (entity != null) {
                            if (result.fileResult != null) {
                                FileUtils.readStreamToFile(entity.getContent(), result.fileResult);
                            } else {
                                result.strResponse = HttpConnectionUtils.readStreamToString(entity.getContent());
                            }
                        }
                        stop = true;
                        break;
                    case MOVED:
                        result.appendToLog( "statusLine:'" + statusLine + "'");
                        result.redirected = true;
                        result.setUrl(httpResponse.getHeaders("Location")[0].getValue().replace("%3F", "?"));
                        String logMsg3 = "Following redirect to '" + result.getUrl() + "'";
                        MyLog.v(this, method + logMsg3);
                        if (MyLog.isVerboseEnabled()) {
                            StringBuilder message = new StringBuilder(method + "Headers: ");
                            for (Header header: httpResponse.getAllHeaders()) {
                                message.append(header.getName() +": " + header.getValue() + ";\n");
                            }
                            MyLog.v(this, message.toString());
                        }
                        break;
                    default:
                        result.appendToLog( "statusLine:'" + statusLine + "'");
                        entity = httpResponse.getEntity();
                        if (entity != null) {
                            result.strResponse = HttpConnectionUtils.readStreamToString(entity.getContent());
                        }
                        stop =  result.fileResult == null || !result.authenticate;
                        if (!stop) {
                            result.authenticate = false;
                            result.appendToLog("retrying without authentication");
                            DbUtils.closeSilently(httpResponse);
                            MyLog.v(this, result.toString());
                        }
                        break;
                }
            } while (!stop);
        } catch (IOException e) {
            result.setException(e);
        } finally {
            DbUtils.closeSilently(httpResponse);
        }
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

}
