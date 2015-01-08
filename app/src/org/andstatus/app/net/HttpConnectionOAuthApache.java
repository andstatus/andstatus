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

package org.andstatus.app.net;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.util.MyLog;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicResponseHandler;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class HttpConnectionOAuthApache extends HttpConnectionOAuth implements HttpConnectionApacheSpecific {
    private static final String NULL_JSON = "(null)";
    private HttpClient mClient;

    @Override
    protected void setConnectionData(HttpConnectionData connectionData) {
        super.setConnectionData(connectionData);
        mClient = HttpConnectionApacheCommon.getHttpClient();
    }  

    @Override
    public OAuthProvider getProvider() {
        CommonsHttpOAuthProvider provider = null;
        provider = new CommonsHttpOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));

        provider.setHttpClient(mClient);
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
    protected final JSONObject postRequest(String path) throws ConnectionException {
        return new HttpConnectionApacheCommon(this).postRequest(path);
    }

    @Override
    protected JSONObject postRequest(String path, JSONObject formParams) throws ConnectionException {
        return new HttpConnectionApacheCommon(this).postRequest(path, formParams);
    }
    
    @Override
    public JSONObject httpApachePostRequest(HttpPost post) throws ConnectionException {
        JSONObject jso = null;
        String response = null;
        boolean ok = false;
		String logmsg = "postRequest; URI='" + post.getURI().toString() + "'";
        try {
            // TODO: Redo like for get request
            signRequest(post);
            response = mClient.execute(post, new BasicResponseHandler());
            jso = new JSONObject(response);
            ok = true;
        } catch (HttpResponseException e) {
            ConnectionException e2 = ConnectionException.fromStatusCodeHttp(e.getStatusCode(), logmsg, e);
            MyLog.i(this, logmsg, e2);
            throw e2;
        } catch (JSONException e) {
			logmsg += "; response=" + (response == null ? NULL_JSON : response);
            MyLog.i(this, logmsg, e);
            throw new ConnectionException(logmsg, e);
        } catch (Exception e) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            throw new ConnectionException(logmsg, e);
        }
        if (!ok) {
            jso = null;
        }
        return jso;
    }

    @Override
    public HttpResponse httpApacheGetResponse(HttpGet httpGet) throws IOException {
        return mClient.execute(httpGet);
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
    public void downloadFile(String url, File file) throws ConnectionException {
        new HttpConnectionApacheCommon(this).downloadFile(url, file);
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
