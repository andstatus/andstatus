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

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.StringUtil;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.entity.ContentType;
import cz.msebera.android.httpclient.entity.mime.MultipartEntityBuilder;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import cz.msebera.android.httpclient.protocol.HTTP;

class ApacheHttpClientUtils {

    static MultipartFormEntityBytes buildMultipartFormEntityBytes(HttpRequest request) throws ConnectionException {
        HttpEntity httpEntity = multiPartFormEntity(request);
        return new MultipartFormEntityBytes(
                httpEntity.getContentType().getName(),
                httpEntity.getContentType().getValue(),
                httpEntityToBytes(httpEntity));
    }

    static HttpEntity multiPartFormEntity(HttpRequest request) throws ConnectionException {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        request.postParams.ifPresent(formParams -> {
            Iterator<String> iterator = formParams.keys();
            ContentType textContentType = ContentType.create(HTTP.PLAIN_TEXT_TYPE, HTTP.UTF_8);
            while (iterator.hasNext()) {
                String name = iterator.next();
                String value = JsonUtils.optString(formParams, name);
                // see http://stackoverflow.com/questions/19292169/multipartentitybuilder-and-charset
                builder.addTextBody(name, value, textContentType);
            }
        });
        final ContentResolver contentResolver = request.myContext().context().getContentResolver();
        if (contentResolver == null) {
            throw ConnectionException.fromStatusCode(ConnectionException.StatusCode.NOT_FOUND,
                    "Content Resolver is null in " + request.myContext().context());
        }
        if (request.mediaUri.isPresent()) {
            Uri mediaUri = request.mediaUri.get();
            try (InputStream ins = contentResolver.openInputStream(mediaUri)) {
                ContentType mediaContentType = ContentType.create(
                        MyContentType.uri2MimeType(contentResolver, mediaUri));
                builder.addBinaryBody(request.mediaPartName, FileUtils.getBytes(ins), mediaContentType, mediaUri.getPath());
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
            throw new ConnectionException("httpEntityToBytes", e);
        }
        return out.toByteArray();
    }

    static List<NameValuePair> jsonToNameValuePair(JSONObject jso) {
        List<NameValuePair> formParams = new ArrayList<>();
        Iterator<String> iterator =  jso.keys();
        while (iterator.hasNext()) {
            String name = iterator.next();
            String value = JsonUtils.optString(jso, name);
            if (!StringUtil.isEmpty(value)) {
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
}
