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

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import cz.msebera.android.httpclient.HttpEntity;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;

/**
 * @author yvolk@yurivolkov.com
 */
public class HttpConnectionOAuth2JavaNet extends HttpConnectionOAuthJavaNet {
    public static final String OAUTH_SCOPES = "read write follow";

    @Override
    public void registerClient(String path) throws ConnectionException {
        String logmsg = "registerClient; for " + data.originUrl + "; URL='" + pathToUrlString(path) + "'";
        MyLog.v(this, logmsg);
        data.oauthClientKeys.clear();
        try {
            JSONObject params = new JSONObject();
            params.put("client_name", HttpConnection.USER_AGENT);
            params.put("redirect_uris", HttpConnection.CALLBACK_URI.toString());
            params.put("scopes", OAUTH_SCOPES);
            params.put("website", "http://andstatus.org");

            JSONObject jso = postRequest(path, params);
            String consumerKey = jso.getString("client_id");
            String consumerSecret = jso.getString("client_secret");
            data.oauthClientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);
        } catch (IOException e) {
            MyLog.i(this, logmsg, e);
        } catch (JSONException e) {
            MyLog.i(this, logmsg, e);
        }
        if (data.oauthClientKeys.areKeysPresent()) {
            MyLog.v(this, "Completed " + logmsg);
        } else {
            throw ConnectionException.fromStatusCodeAndHost(ConnectionException.StatusCode.NO_CREDENTIALS_FOR_HOST,
                    "No client keys for the host yet; " + logmsg, data.originUrl);
        }
    }

    @Override
    protected void postRequest(HttpReadResult result) throws ConnectionException {
        if (data.areOAuthClientKeysPresent()) {
            postRequestOauth(result);
        } else {
            super.postRequest(result);
        }
    }

    private void postRequestOauth(HttpReadResult result) throws ConnectionException {
        try {
            OAuth20Service service = getService(false);
            final OAuthRequest request = new OAuthRequest(Verb.POST, result.getUrlObj().toString());
            if (result.getFormParams().has(HttpConnection.KEY_MEDIA_PART_URI)) {
                HttpEntity httpEntity = HttpConnectionApacheCommon.multiPartFormEntity(result.getFormParams(), true);
                request.addHeader(httpEntity.getContentType().getName(), httpEntity.getContentType().getValue());
                request.setPayload(httpEntityToBytes(httpEntity));
            } else {
                Iterator<String> iterator = result.getFormParams().keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    request.addBodyParameter(key, result.getFormParams().optString(key));
                }
            }
            signRequest(request, service, false);
            final Response response = service.execute(request);
            result.setStatusCode(response.getCode());
            switch(result.getStatusCode()) {
                case OK:
                    result.strResponse = HttpConnectionUtils.readStreamToString(response.getStream());
                    break;
                default:
                    result.strResponse = HttpConnectionUtils.readStreamToString(response.getStream());
                    throw result.getExceptionFromJsonErrorResponse();
            }
        } catch (IOException | ExecutionException e) {
            result.e1 = e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.e1 = e;
        }
    }

    byte[] httpEntityToBytes(HttpEntity httpEntity) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        httpEntity.writeTo(out);
        out.flush();
        return out.toByteArray();
    }

    @Override
    protected void getRequest(HttpReadResult result) throws ConnectionException {
        String method = "getRequest; ";
        StringBuilder logBuilder = new StringBuilder(method);
        try {
            logBuilder.append("URL='" + result.getUrl() + "';");
            OAuth20Service service = getService(false);
            OAuthRequest request;
            boolean redirected = false;
            boolean stop = false;
            do {
                request = new OAuthRequest(Verb.GET, result.getUrlObj().toString());
                if (result.authenticate) {
                    signRequest(request, service, redirected);
                }
                Response response = service.execute(request);
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
        } catch(IOException | ExecutionException e) {
            throw new ConnectionException(logBuilder.toString(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException(logBuilder.toString(), e);
        }
    }

    @Override
    public OAuth20Service getService(boolean redirect) {
        final JDKHttpClientConfig clientConfig = JDKHttpClientConfig.defaultConfig();
        clientConfig.setConnectTimeout(MyPreferences.getConnectionTimeoutMs());
        clientConfig.setReadTimeout(2*MyPreferences.getConnectionTimeoutMs());
        clientConfig.setFollowRedirects(false);
        final ServiceBuilder serviceBuilder = new ServiceBuilder()
                .apiKey(data.oauthClientKeys.getConsumerKey())
                .apiSecret(data.oauthClientKeys.getConsumerSecret())
                .httpClientConfig(clientConfig);
        if (redirect) {
            serviceBuilder.callback(HttpConnection.CALLBACK_URI.toString());
        }
        return serviceBuilder.build(new OAuthApi20(this));
    }

    private void signRequest(OAuthRequest request, OAuth20Service service, boolean redirected) throws ConnectionException {
        if (!getCredentialsPresent()) {
            return;
        }
        try {
            if (data.originUrl.getHost().contentEquals(data.urlForUserToken.getHost())) {
                OAuth2AccessToken token = new OAuth2AccessToken(getUserToken(), getUserSecret());
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
    protected void signConnection(HttpURLConnection conn, OAuthConsumer consumer,  boolean redirected) throws
            ConnectionException {
        if (!getCredentialsPresent()) {
            return;
        }
        try {
            OAuth2AccessToken token;
            if (data.originUrl.getHost().contentEquals(data.urlForUserToken.getHost())) {
                token = new OAuth2AccessToken(getUserToken(), getUserSecret());
            } else {
                if (redirected) {
                    token = new OAuth2AccessToken("", null);
                } else {
                    conn.setRequestProperty("Authorization", "Dialback");
                    conn.setRequestProperty("host", data.urlForUserToken.getHost());
                    conn.setRequestProperty("token", getUserToken());
                    MyLog.v(this, "Dialback authorization at " + data.originUrl + "; urlForUserToken="
                            + data.urlForUserToken + "; token=" + getUserToken());
                    token = new OAuth2AccessToken(getUserToken(), null);
                }
            }
            conn.setRequestProperty(OAuthConstants.ACCESS_TOKEN, token.getAccessToken());
        } catch (Exception e) {
            throw new ConnectionException(e);
        }
    }

    @Override
    public OAuthConsumer getConsumer() {
        return null;
    }

    @Override
    public OAuthProvider getProvider() throws ConnectionException {
        return null;
    }

    @Override
    public boolean isOAuth2() {
        return true;
    }

}
