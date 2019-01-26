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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.protocol.HTTP;

class ApacheHttpClientUtils {

    static MultipartFormEntityBytes buildMultipartFormEntityBytes(JSONObject params) throws ConnectionException {
        HttpEntity httpEntity = multiPartFormEntity(params);
        return new MultipartFormEntityBytes(
                httpEntity.getContentType().getName(),
                httpEntity.getContentType().getValue(),
                httpEntityToBytes(httpEntity));
    }

    static HttpEntity multiPartFormEntity(JSONObject formParams) throws
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
        if (!StringUtils.isEmpty(mediaPartName) && !UriUtils.isEmpty(mediaUri) && contentResolver != null) {
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

    private static byte[] httpEntityToBytes(HttpEntity httpEntity) throws ConnectionException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            httpEntity.writeTo(out);
            out.flush();
        } catch (IOException e) {
            throw  new ConnectionException("httpEntityToBytes", e);
        }
        return out.toByteArray();
    }

    static List<NameValuePair> jsonToNameValuePair(JSONObject jso) {
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        Iterator<String> iterator =  jso.keys();
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = jso.optString(name);
            if (!StringUtils.isEmpty(value)) {
                formParams.add(new BasicNameValuePair(name, value));
            }
        }
        return formParams;
    }

    static HttpClient getHttpClient(SslModeEnum sslMode) {
        return sslMode == SslModeEnum.MISCONFIGURED ?
                MisconfiguredSslHttpClientFactory.getHttpClient() :
                    MyHttpClientFactory.getHttpClient(sslMode) ;
    }

    static String readHttpResponseToString(HttpResponse httpResponse) throws IOException {
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
