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

import android.content.ContentResolver;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpVersion;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.protocol.HTTP;

public class HttpConnectionApacheCommon {
    private HttpConnectionApacheSpecific specific;

    HttpConnectionApacheCommon(HttpConnectionApacheSpecific specificIn) {
        this.specific = specificIn;
    }

    protected void postRequest(HttpReadResult result) throws ConnectionException {
        HttpPost httpPost = new HttpPost(result.getUrl());
        if (result.isLegacyHttpProtocol()) {
            httpPost.setProtocolVersion(HttpVersion.HTTP_1_0);
        }
        try {
            if ( !result.hasFormParams()) {
                // Nothing to do at this step
            } else if (result.getFormParams().has(HttpConnection.KEY_MEDIA_PART_URI)) {
                httpPost.setEntity(multiPartFormEntity(result.getFormParams()));
            } else {
                fillSinglePartPost(httpPost, result.getFormParams());
            }
            specific.httpApachePostRequest(httpPost, result);
        } catch (UnsupportedEncodingException e) {
            MyLog.i(this, e);
        }
    }

    public static HttpEntity multiPartFormEntity(JSONObject formParams) throws
            ConnectionException {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        Uri mediaUri = null;
        String mediaPartName = "";
        Iterator<String> iterator =  formParams.keys();
        ContentType textContentType = ContentType.create(HTTP.PLAIN_TEXT_TYPE, HTTP.UTF_8);
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = formParams.optString(name);
            if (HttpConnection.KEY_MEDIA_PART_NAME.equals(name)) {
                mediaPartName = value;
            } else if (HttpConnection.KEY_MEDIA_PART_URI.equals(name)) {
                mediaUri = UriUtils.fromString(value);
            } else {
                // see http://stackoverflow.com/questions/19292169/multipartentitybuilder-and-charset
                builder.addTextBody(name, value, textContentType);
            }
        }
        final ContentResolver contentResolver = MyContextHolder.get().context().getContentResolver();
        if (!TextUtils.isEmpty(mediaPartName) && !UriUtils.isEmpty(mediaUri) && contentResolver != null) {
            try (InputStream ins = contentResolver.openInputStream(mediaUri)) {
                ContentType mediaContentType = ContentType.create(
                        MyContentType.uri2MimeType(contentResolver, mediaUri));
                builder.addBinaryBody(mediaPartName, FileUtils.getBytes(ins), mediaContentType, mediaUri.getPath());
            } catch (SecurityException | IOException e) {
                throw ConnectionException.hardConnectionException("mediaUri='" + mediaUri + "'", e);
            }
        }
        return builder.build();
    }

    private void fillSinglePartPost(HttpPost httpPost, JSONObject formParams)
            throws UnsupportedEncodingException {
        List<NameValuePair> nvFormParams = HttpConnectionApacheCommon.jsonToNameValuePair(formParams);
        if (nvFormParams != null) {
            HttpEntity formEntity = new UrlEncodedFormEntity(nvFormParams, HTTP.UTF_8);
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
                        final Header[] locations = httpResponse.getHeaders("Location");
                        final String location = locations != null && locations.length > 0 ? locations[0].getValue() : "";
                        stop = StringUtils.isEmpty(location);
                        if (stop) {
                            result.onNoLocationHeaderOnMoved();
                        } else {
                            result.setUrl(location.replace("%3F", "?"));
                            String logMsg3 = "Following redirect to '" + result.getUrl() + "'";
                            MyLog.v(this, method + logMsg3);
                            if (MyLog.isVerboseEnabled()) {
                                StringBuilder message = new StringBuilder(method + "Headers: ");
                                for (Header header: httpResponse.getAllHeaders()) {
                                    message.append(header.getName() +": " + header.getValue() + ";\n");
                                }
                                MyLog.v(this, message.toString());
                            }
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
        } catch (IllegalArgumentException e) {
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
