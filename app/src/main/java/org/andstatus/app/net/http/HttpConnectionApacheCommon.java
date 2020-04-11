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

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpVersion;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.protocol.HTTP;

public class HttpConnectionApacheCommon {
    private final HttpConnectionApacheSpecific specific;
    private final HttpConnectionData data;

    HttpConnectionApacheCommon(HttpConnectionApacheSpecific specificIn, HttpConnectionData data) {
        this.specific = specificIn;
        this.data = data;
    }

    protected HttpReadResult postRequest(HttpReadResult result) {
        HttpPost httpPost = new HttpPost(result.getUrl());
        if (result.isLegacyHttpProtocol()) {
            httpPost.setProtocolVersion(HttpVersion.HTTP_1_0);
        }
        result.formParams.ifPresent(params -> {
            try {
                if (params.has(HttpConnection.KEY_MEDIA_PART_URI)) {
                    httpPost.setEntity(ApacheHttpClientUtils.multiPartFormEntity(params));
                } else {
                    data.getContentType().ifPresent(value -> httpPost.addHeader("Content-Type", value));
                    fillSinglePartPost(httpPost, params);
                }
            } catch (Exception e) {
                result.setException(e);
                MyLog.i(this, e);
            }
        });
        return specific.httpApachePostRequest(httpPost, result);
    }

    private void fillSinglePartPost(HttpPost httpPost, JSONObject formParams)
            throws UnsupportedEncodingException {
        List<NameValuePair> nvFormParams = ApacheHttpClientUtils.jsonToNameValuePair(formParams);
        if (nvFormParams != null) {
            HttpEntity formEntity = new UrlEncodedFormEntity(nvFormParams, HTTP.UTF_8);
            httpPost.setEntity(formEntity);
        }
    }

    protected void getRequest(HttpReadResult result) {
        HttpResponse httpResponse = null;
        try {
            boolean stop = false;
            do {
                HttpGet httpGet = newHttpGet(result.getUrl());
                data.getContentType().ifPresent(value -> httpGet.addHeader("Accept", value));
                if (result.authenticate) {
                    specific.httpApacheSetAuthorization(httpGet);
                }
                // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
                httpResponse = specific.httpApacheGetResponse(httpGet);
                setStatusCodeAndHeaders(result, httpResponse);
                switch (result.getStatusCode()) {
                    case OK:
                    case UNKNOWN:
                        HttpEntity entity = httpResponse.getEntity();
                        if (entity != null) {
                            result.readStream("", o -> entity.getContent());
                        }
                        stop = true;
                        break;
                    case MOVED:
                        stop = specific.onMoved(result);
                        break;
                    default:
                        entity = httpResponse.getEntity();
                        if (entity != null) {
                            result.readStream("", o -> entity.getContent());
                        }
                        stop =  result.fileResult == null || !result.authenticate;
                        if (!stop) {
                            result.onRetryWithoutAuthentication();
                            DbUtils.closeSilently(httpResponse);
                        }
                        break;
                }
            } while (!stop);
        } catch (Exception e) {
            result.setException(e);
        } finally {
            DbUtils.closeSilently(httpResponse);
        }
    }

    static void setStatusCodeAndHeaders(HttpReadResult result, HttpResponse httpResponse) {
        StatusLine statusLine = httpResponse.getStatusLine();
        result.statusLine = statusLine.toString();
        result.setStatusCode(statusLine.getStatusCode());
        result.setHeaders(Arrays.stream(httpResponse.getAllHeaders()), Header::getName, Header::getValue);
    }

    private HttpGet newHttpGet(String url) {
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", HttpConnection.USER_AGENT);
        return httpGet;
    }

}
