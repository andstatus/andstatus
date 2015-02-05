/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;

import java.io.IOException;

public class HttpConnectionOAuthApache extends HttpConnectionOAuth implements HttpConnectionApacheSpecific {

    @Override
    public void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
    }  

    @Override
    public OAuthProvider getProvider() {
        CommonsHttpOAuthProvider provider = null;
        provider = new CommonsHttpOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));

        provider.setHttpClient(HttpConnectionApacheCommon.getHttpClient(data.sslMode));
        provider.setOAuth10a(true);
        return provider;
    }

    @Override
    public OAuthConsumer getConsumer() {
        OAuthConsumer consumer = new CommonsHttpOAuthConsumer(data.oauthClientKeys.getConsumerKey(),
                data.oauthClientKeys.getConsumerSecret());
        if (getCredentialsPresent()) {
            consumer.setTokenWithSecret(getUserToken(), getUserSecret());
        }
        return consumer;
    }
    
    @Override
    protected void postRequest(HttpReadResult result) throws ConnectionException {
        new HttpConnectionApacheCommon(this).postRequest(result);
    }
    
    @Override
    public void httpApachePostRequest(HttpPost post, HttpReadResult result) throws ConnectionException {
        try {
            // TODO: Redo like for get request
            if (result.authenticate) {
                signRequest(post);
            }
            result.strResponse = HttpConnectionApacheCommon.getHttpClient(data.sslMode).execute(
                    post, new BasicResponseHandler());
        } catch (Exception e) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            result.e1 = e;
        }
    }

    @Override
    public HttpResponse httpApacheGetResponse(HttpGet httpGet) throws IOException {
        return HttpConnectionApacheCommon.getHttpClient(data.sslMode).execute(httpGet);
    }

    private void signRequest(Object httpGetOrPost) throws IOException {
        if (data.oauthClientKeys.areKeysPresent()) {
            try {
                getConsumer().sign(httpGetOrPost);
            } catch (OAuthMessageSignerException | OAuthExpectationFailedException
                    | OAuthCommunicationException e) {
                throw new IOException(e);
            }
        }
    }

    @Override
    public void httpApacheSetAuthorization(HttpGet httpGet) throws IOException {
        signRequest(httpGet);
    }

    @Override
    protected void getRequest(HttpReadResult result) throws ConnectionException {
        new HttpConnectionApacheCommon(this).getRequest(result);
    }
}
