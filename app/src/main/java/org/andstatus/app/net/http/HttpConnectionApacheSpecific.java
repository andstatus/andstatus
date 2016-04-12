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

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;

import java.io.IOException;

/**
 * Implementation, specific to Basic or OAuth 
 * @author yvolk@yurivolkov.com
 */
public interface HttpConnectionApacheSpecific {
    void httpApachePostRequest(HttpPost httpPost, HttpReadResult result) throws ConnectionException;
    HttpResponse httpApacheGetResponse(HttpGet httpGet) throws IOException;
    void httpApacheSetAuthorization(HttpGet httpGet) throws IOException;
    SslModeEnum getSslMode();
}
