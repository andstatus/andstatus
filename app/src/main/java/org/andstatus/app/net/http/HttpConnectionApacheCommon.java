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
import org.andstatus.app.util.StringUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

    protected void postRequest(HttpReadResult result) throws ConnectionException {
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
            } catch (ConnectionException | UnsupportedEncodingException e) {
                result.setException(e);
                MyLog.i(this, e);
            }
        });
        specific.httpApachePostRequest(httpPost, result);
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
        String method = "getRequest; ";
        // See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/fundamentals.html
        HttpResponse httpResponse = null;
        try {
            boolean stop = false;
            do {
                HttpGet httpGet = newHttpGet(result.getUrl());
                data.getContentType().ifPresent(value -> httpGet.addHeader("Accept", value));
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
                            HttpConnectionUtils.readStream(result, entity.getContent());
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
                            if (MyLog.isVerboseEnabled()) {
                                MyLog.v(this, method + logMsg3);

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
                            HttpConnectionUtils.readStream(result, entity.getContent());
                        }
                        stop =  result.fileResult == null || !result.authenticate;
                        if (!stop) {
                            result.authenticate = false;
                            result.appendToLog("retrying without authentication");
                            DbUtils.closeSilently(httpResponse);
                            MyLog.v(this, result::toString);
                        }
                        break;
                }
            } while (!stop);
        } catch (IOException | IllegalArgumentException e) {
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

}
