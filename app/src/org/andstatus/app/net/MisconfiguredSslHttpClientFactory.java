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

import org.andstatus.app.context.MyPreferences;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

public class MisconfiguredSslHttpClientFactory {
    private MisconfiguredSslHttpClientFactory() {
        // Empty
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
        HttpClient client = new DefaultHttpClient(clientConnectionManager, params);
        client.getParams()
                .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT,
                        MyPreferences.getConnectionTimeoutMs())
                .setIntParameter(CoreConnectionPNames.SO_TIMEOUT,
                        MyPreferences.getConnectionTimeoutMs());
        return client;
    }

    private static HttpParams getHttpParams() {
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        HttpConnectionParams.setStaleCheckingEnabled(params, true);

        HttpProtocolParams.setUseExpectContinue(params, false);
        HttpConnectionParams.setSoTimeout(params, MyPreferences.getConnectionTimeoutMs());
        HttpConnectionParams.setSocketBufferSize(params, 2*8192);
        return params;
    }
    
}
