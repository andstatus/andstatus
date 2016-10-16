/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.text.TextUtils;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;

import java.io.IOException;
import java.util.Map;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;

public class HttpConnectionOAuthMastodon extends HttpConnectionOAuthJavaNet {
    @Override
    public String getApiUrl(Connection.ApiRoutineEnum routine) throws ConnectionException {
        String url;

        switch (routine) {
            case OAUTH_ACCESS_TOKEN:
            case OAUTH_REQUEST_TOKEN:
                url = data.getOauthPath() + "/token";
                break;
            case OAUTH_AUTHORIZE:
                url = data.getOauthPath() + "/authorize";
                break;
            case OAUTH_REGISTER_CLIENT:
                url = data.getBasicPath() + "/apps";
                break;
            default:
                url = "";
                break;
        }

        if (!TextUtils.isEmpty(url)) {
            url = pathToUrlString(url);
        }

        return url;
    }

    @Override
    public OAuthConsumer getConsumer() {
        return null;
    }

    @Override
    public OAuthProvider getProvider() throws ConnectionException {
        return null;
    }

    private void setAuthorization(OAuthRequest request, OAuth20Service service,  boolean redirected) throws ConnectionException {
        if (!getCredentialsPresent()) {
            return;
        }
        try {
            if (data.originUrl.getHost().contentEquals(data.urlForUserToken.getHost())) {
                OAuth2AccessToken token = new OAuth2AccessToken(getUserToken(), null);
                service.signRequest(token, request);
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    OAuth2AccessToken token = new OAuth2AccessToken("", null);
                    service.signRequest(token, request);
                } else {
                    request.addParameter("Authorization", "Dialback");
                    request.addParameter("host", data.urlForUserToken.getHost());
                    request.addParameter("token", getUserToken());
                    MyLog.v(this, "Dialback authorization at " + data.originUrl + "; urlForUserToken=" + data.urlForUserToken + "; token=" + getUserToken());
                    OAuth2AccessToken token = new OAuth2AccessToken(getUserToken(), null);
                    service.signRequest(token, request);
                }
            }
        } catch (Exception e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    protected void postRequest(HttpReadResult result) throws ConnectionException {
        try {
            OAuth20Service service = getService();
            final OAuthRequest request = new OAuthRequest(Verb.POST, result.getUrlObj().toString(), service);
            if (!result.hasFormParams()) {
                // Nothing to do at this step
            } else if (result.getFormParams().has(HttpConnection.KEY_MEDIA_PART_URI)) {
                super.postRequest(result);
                return;
            } else {
                request.addPayload(result.getFormParams().toString());
            }
            setAuthorization(request, service, false);
            final Response response = request.send();
            result.setStatusCode(response.getCode());
            switch(result.getStatusCode()) {
                case OK:
                    result.strResponse = HttpConnectionUtils.readStreamToString(response.getStream());
                    break;
                default:
                    result.strResponse = HttpConnectionUtils.readStreamToString(response.getStream());
                    throw result.getExceptionFromJsonErrorResponse();
            }
        } catch (IOException e) {
            result.e1 = e;
        }
    }

    private OAuth20Service getService() {
        OAuth20Service service = new ServiceBuilder()
                .apiKey(data.oauthClientKeys.getConsumerKey())
                .apiSecret(data.oauthClientKeys.getConsumerSecret())
                .build(MastodonApi.instance(this));
        return service;
    }

    @Override
    protected void getRequest(HttpReadResult result) throws ConnectionException {
        String method = "getRequest; ";
        StringBuilder logBuilder = new StringBuilder(method);
        try {
            logBuilder.append("URL='" + result.getUrl() + "';");
            OAuth20Service service = getService();
            OAuthRequest request;
            boolean redirected = false;
            boolean stop = false;
            do {
                request = new OAuthRequest(Verb.GET, result.getUrlObj().toString(), service);
                request.setFollowRedirects(false);
                if (result.authenticate) {
                    setAuthorization(request, service, redirected);
                }
                Response response = request.send();
                result.setStatusCode(response.getCode());
                switch(result.getStatusCode()) {
                    case OK:
                        if (result.fileResult != null) {
                            FileUtils.readStreamToFile(response.getStream(), result.fileResult);
                        } else {
                            result.strResponse = HttpConnectionUtils.readStreamToString(response.getStream());
                        }
                        stop = true;
                        break;
                    case MOVED:
                        redirected = true;
                        result.setUrl(response.getHeader("Location").replace("%3F", "?"));
                        String logMsg3 = (result.redirected ? "Following redirect to "
                                : "Not redirected to ") + "'" + result.getUrl() + "'";
                        logBuilder.append(logMsg3 + "; ");
                        MyLog.v(this, method + logMsg3);
                        if (MyLog.isVerboseEnabled()) {
                            StringBuilder message = new StringBuilder(method + "Headers: ");
                            for (Map.Entry<String, String> entry : response.getHeaders().entrySet()) {
                                message.append(entry.getKey() +": " + entry.getValue() + ";\n");
                            }
                            MyLog.v(this, message.toString());
                        }
                        // TODO: ?! ...disconnect();
                        break;
                    default:
                        result.strResponse = HttpConnectionUtils.readStreamToString(response.getStream());
                        stop = result.fileResult == null || !result.authenticate;
                        if (!stop) {
                            result.authenticate = false;
                            String logMsg4 = "Retrying without authentication connection to '" + result.getUrl() + "'";
                            logBuilder.append(logMsg4 + "; ");
                            MyLog.v(this, method + logMsg4);
                        }
                        break;
                }
            } while (!stop);
        } catch(ConnectionException e) {
            throw e;
        } catch(IOException e) {
            throw new ConnectionException(logBuilder.toString(), e);
        }
    }
}
