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

import org.andstatus.app.net.social.Connection.ApiRoutineEnum;

import java.io.IOException;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpGet;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.impl.client.BasicResponseHandler;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

public class HttpConnectionOAuthApache extends HttpConnectionOAuth implements HttpConnectionApacheSpecific {

    @Override
    public void setHttpConnectionData(HttpConnectionData connectionData) {
        super.setHttpConnectionData(connectionData);
    }  

    @Override
    public OAuthProvider getProvider() throws ConnectionException {
        CommonsHttpOAuthProvider provider = new CommonsHttpOAuthProvider(
                getApiUri(ApiRoutineEnum.OAUTH_REQUEST_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString());
        provider.setHttpClient(ApacheHttpClientUtils.getHttpClient(data.getSslMode()));
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
    public HttpReadResult postRequest(HttpReadResult result) {
        return new HttpConnectionApacheCommon(this, data).postRequest(result);
    }
    
    @Override
    public HttpReadResult httpApachePostRequest(HttpPost post, HttpReadResult result) {
        try {
            // TODO: Redo like for get request
            if (result.request.authenticate) {
                signRequest(post);
            }
            result.strResponse = ApacheHttpClientUtils.getHttpClient(data.getSslMode()).execute(
                    post, new BasicResponseHandler());
        } catch (Exception e) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            result.setException(e);
        }
        return result;
    }

    @Override
    public HttpResponse httpApacheGetResponse(HttpGet httpGet) throws IOException {
        return ApacheHttpClientUtils.getHttpClient(data.getSslMode()).execute(httpGet);
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
    public HttpReadResult getRequest(HttpReadResult result) {
        return new HttpConnectionApacheCommon(this, data).getRequest(result);
    }
}
