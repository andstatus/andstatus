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

import android.net.Uri;

import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.httpclient.jdk.JDKHttpClientConfig;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.model.OAuthConstants;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth20Service;

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.vavr.control.Try;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;

import static org.andstatus.app.net.http.ConnectionException.StatusCode.OK;

/**
 * @author yvolk@yurivolkov.com
 */
public class HttpConnectionOAuth2JavaNet extends HttpConnectionOAuthJavaNet {
    public static final String OAUTH_SCOPES = "read write follow";

    @Override
    public Try<Void> registerClient() {
        Uri uri = getApiUri(Connection.ApiRoutineEnum.OAUTH_REGISTER_CLIENT);
        MyStringBuilder logmsg = MyStringBuilder.of("registerClient; for " + data.originUrl
                + "; URL='" + uri + "'");
        MyLog.v(this, logmsg::toString);
        data.oauthClientKeys.clear();
        try {
            JSONObject params = new JSONObject();
            params.put("client_name", HttpConnection.USER_AGENT);
            params.put("redirect_uris", HttpConnection.CALLBACK_URI.toString());
            params.put("scopes", OAUTH_SCOPES);
            params.put("website", "http://andstatus.org");

            HttpRequest request = HttpRequest.of(MyContextHolder.get(), Connection.ApiRoutineEnum.OAUTH_REGISTER_CLIENT, uri)
                .withPostParams(params);
            return execute(request)
            .flatMap(HttpReadResult::getJsonObject)
            .map(jso -> {
                String consumerKey = jso.getString("client_id");
                String consumerSecret = jso.getString("client_secret");
                data.oauthClientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);
                return data.oauthClientKeys.areKeysPresent();
            })
            .flatMap(keysArePresent -> {
                if (keysArePresent) {
                    MyLog.v(this, () -> "Completed " + logmsg);
                    return Try.success(null);
                } else {
                    return Try.failure(ConnectionException.fromStatusCodeAndHost(
                            ConnectionException.StatusCode.NO_CREDENTIALS_FOR_HOST,
                            "Failed to obtain client keys for host; " + logmsg, data.originUrl));
                }
            });
        } catch (JSONException e) {
            return Try.failure(ConnectionException.loggedJsonException(this, logmsg.toString(), e, null));
        }
    }

    @Override
    public HttpReadResult postRequest(HttpReadResult result) {
        if (data.areOAuthClientKeysPresent()) {
            return postRequestOauth(result);
        } else {
            return super.postRequest(result);
        }
    }

    private HttpReadResult postRequestOauth(HttpReadResult result) {
        try {
            OAuth20Service service = getService(false);
            final OAuthRequest request = new OAuthRequest(Verb.POST, result.getUrlObj().toString());
            result.request.postParams.ifPresent(params -> {
                try {
                    if (params.has(HttpConnection.KEY_MEDIA_PART_URI)) {
                        MultipartFormEntityBytes bytes = ApacheHttpClientUtils.buildMultipartFormEntityBytes(params);
                        request.addHeader(bytes.contentTypeName, bytes.contentTypeValue);
                        request.setPayload(bytes.bytes);
                    } else {
                        if (data.getContentType().map(value -> {
                            request.addHeader("Content-Type", value);
                            request.setPayload(params.toString().getBytes(StandardCharsets.UTF_8));
                            return false;
                        }).orElse(true))
                        {
                            Iterator<String> iterator = params.keys();
                            while (iterator.hasNext()) {
                                String key = iterator.next();
                                try {
                                    Object value = params.get(key);
                                    if (value != null) {
                                        if (value instanceof List) {
                                            ((List<String>) value).forEach( v -> request.addBodyParameter(key, v));
                                        } else {
                                            request.addBodyParameter(key, value.toString());
                                        }
                                    }
                                } catch (JSONException e) {
                                    MyLog.w(this, "Failed to get key " + key, e);
                                    result.setException(e);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    result.setException(e);
                    MyLog.w(this, result.toString(), e);
                }
            });
            signRequest(request, service, false);
            final Response response = service.execute(request);
            setStatusCodeAndHeaders(result, response);
            result.readStream("", o -> response.getStream());
            if (result.getStatusCode() != OK) {
                result.setException(result.getExceptionFromJsonErrorResponse());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setException(e);
        } catch (Exception e) {
            result.setException(e);
        }
        return result;
    }

    @Override
    public HttpReadResult getRequest(HttpReadResult result) {
        try {
            OAuth20Service service = getService(false);
            boolean redirected = false;
            boolean stop = false;
            do {
                OAuthRequest request = new OAuthRequest(Verb.GET, result.getUrlObj().toString());
                data.getContentType().ifPresent(value -> request.addHeader("Accept", value));
                if (result.request.authenticate) {
                    signRequest(request, service, redirected);
                }
                Response response = service.execute(request);
                setStatusCodeAndHeaders(result, response);
                switch(result.getStatusCode()) {
                    case OK:
                        result.readStream("", o -> response.getStream());
                        stop = true;
                        break;
                    case MOVED:
                        redirected = true;
                        stop = onMoved(result);
                        // TODO: ?! ...disconnect();
                        break;
                    default:
                        result.readStream("", o -> response.getStream());
                        stop = result.request.fileResult == null || result.retriedWithoutAuthentication;
                        if (!stop) {
                            result.onRetryWithoutAuthentication();
                        }
                        break;
                }
            } while (!stop);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setException(e);
        } catch(Exception e) {
            result.setException(e);
        }
        return result;
    }

    private void setStatusCodeAndHeaders(HttpReadResult result, Response response) {
        result.setStatusCode(response.getCode());
        result.setHeaders(response.getHeaders().entrySet().stream(), Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public OAuth20Service getService(boolean redirect) {
        final JDKHttpClientConfig clientConfig = JDKHttpClientConfig.defaultConfig();
        clientConfig.setConnectTimeout(MyPreferences.getConnectionTimeoutMs());
        clientConfig.setReadTimeout(2*MyPreferences.getConnectionTimeoutMs());
        clientConfig.setFollowRedirects(false);
        final ServiceBuilder serviceBuilder = new ServiceBuilder(data.oauthClientKeys.getConsumerKey())
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
                    MyLog.v(this, () -> "Dialback authorization at " + data.originUrl
                            + "; urlForUserToken=" + data.urlForUserToken + "; token=" + getUserToken());
                    OAuth2AccessToken token = new OAuth2AccessToken(getUserToken(), null);
                    service.signRequest(token, request);
                }
            }
        } catch (Exception e) {
            throw ConnectionException.of(e);
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
                    MyLog.v(this, () -> "Dialback authorization at " + data.originUrl + "; urlForUserToken="
                            + data.urlForUserToken + "; token=" + getUserToken());
                    token = new OAuth2AccessToken(getUserToken(), null);
                }
            }
            conn.setRequestProperty(OAuthConstants.ACCESS_TOKEN, token.getAccessToken());
        } catch (Exception e) {
            throw ConnectionException.of(e);
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
