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
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.social.Connection.ApiRoutineEnum;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import io.vavr.control.Try;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

public class HttpConnectionOAuthJavaNet extends HttpConnectionOAuth {
    private static final String UTF_8 = "UTF-8";

    /**
     * Partially borrowed from the "Impeller" code !
     */
    @Override
    public Try<Void> registerClient() {
        Uri uri = getApiUri(ApiRoutineEnum.OAUTH_REGISTER_CLIENT);
		MyStringBuilder logmsg = MyStringBuilder.of("registerClient; for " + data.originUrl
                + "; URL='" + uri + "'");
        MyLog.v(this, logmsg::toString);
        String consumerKey = "";
        String consumerSecret = "";
        data.oauthClientKeys.clear();
        Writer writer = null;
        try {
			URL endpoint = new URL(uri.toString());
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                    
            Map<String, String> params = new HashMap<>();
            params.put("type", "client_associate");
            params.put("application_type", "native");
            params.put("redirect_uris", HttpConnection.CALLBACK_URI.toString());
            params.put("client_name", HttpConnection.USER_AGENT);
            params.put("application_name", HttpConnection.USER_AGENT);
            String requestBody = HttpConnectionUtils.encode(params);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            
            writer = new OutputStreamWriter(conn.getOutputStream(), UTF_8);
            writer.write(requestBody);
            writer.close();

            HttpReadResult result = new HttpReadResult(uri, new JSONObject());
            if(conn.getResponseCode() != 200) {
                HttpConnectionUtils.readStream(result, conn.getErrorStream());
                logmsg.atNewLine("Server returned an error response", result.strResponse);
                logmsg.atNewLine("Response message from server", conn.getResponseMessage());
                MyLog.i(this, logmsg.toString());
            } else {
                HttpConnectionUtils.readStream(result, conn.getInputStream());
                JSONObject jso = new JSONObject(result.strResponse);
                consumerKey = jso.getString("client_id");
                consumerSecret = jso.getString("client_secret");
                data.oauthClientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);
            }
        } catch (IOException | JSONException e) {
            logmsg.withComma("Exception", e.getMessage());
            MyLog.i(this, logmsg.toString(), e);
        } finally {
            DbUtils.closeSilently(writer);
        }
        if (data.oauthClientKeys.areKeysPresent()) {
            MyLog.v(this, () -> "Completed " + logmsg);
        } else {
            Try.failure(ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST,
                    "Failed to obtain client keys for host; " + logmsg, data.originUrl));
        }
        return Try.success(null);
    }

    @Override
    public OAuthProvider getProvider() throws ConnectionException {
        OAuthProvider provider = null;
        provider = new DefaultOAuthProvider(
                getApiUri(ApiRoutineEnum.OAUTH_REQUEST_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_ACCESS_TOKEN).toString(),
                getApiUri(ApiRoutineEnum.OAUTH_AUTHORIZE).toString());
        provider.setOAuth10a(true);
        return provider;

    }
    
    @Override
    public HttpReadResult postRequest(HttpReadResult result) {
        try {
            HttpURLConnection conn = (HttpURLConnection) result.getUrlObj().openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            result.formParams.ifPresent(params -> {
                try {
                    if (params.has(HttpConnection.KEY_MEDIA_PART_URI)) {
                        writeMedia(conn, params);
                    } else {
                        writeJson(conn, params);
                    }
                } catch (Exception e) {
                    result.setException(e);
                }
            });
            setStatusCodeAndHeaders(result, conn);
            switch(result.getStatusCode()) {
                case OK:
                    HttpConnectionUtils.readStream(result, conn.getInputStream());
                    break;
                default:
                    HttpConnectionUtils.readStream(result, conn.getErrorStream());
                    result.setException(result.getExceptionFromJsonErrorResponse());
            }
        } catch (Exception e) {
            result.setException(e);
        }
        return result;
    }

    /** This method is not legacy HTTP */
    private void writeMedia(HttpURLConnection conn, JSONObject formParams)
            throws IOException, JSONException {
        final ContentResolver contentResolver = MyContextHolder.get().context().getContentResolver();
        Uri mediaUri = Uri.parse(formParams.getString(KEY_MEDIA_PART_URI));
        conn.setChunkedStreamingMode(0);
        conn.setRequestProperty("Content-Type", MyContentType.uri2MimeType(contentResolver, mediaUri));
        signConnection(conn, getConsumer(), false);

        try (InputStream in = contentResolver.openInputStream(mediaUri)) {
            byte[] buffer = new byte[16384];
            try (OutputStream out = new BufferedOutputStream(conn.getOutputStream())) {
                int length;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
            }
        }
    }

    private void writeJson(HttpURLConnection conn, JSONObject formParams) throws IOException {
        conn.setRequestProperty("Content-Type", data.getContentType().orElse("application/json"));
        signConnection(conn, getConsumer(), false);
        try (
                OutputStream os = conn.getOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(os, UTF_8);
        ) {
            writer.write(formParams.toString());
        }
    }

    @Override public OAuthConsumer getConsumer() {
        OAuthConsumer consumer = new DefaultOAuthConsumer(
                data.oauthClientKeys.getConsumerKey(),
                data.oauthClientKeys.getConsumerSecret());
        if (getCredentialsPresent()) {
            consumer.setTokenWithSecret(getUserToken(), getUserSecret());
        }
        return consumer;
    }

    public void getRequest(HttpReadResult result) {
        try {
            OAuthConsumer consumer = getConsumer();
            boolean redirected = false;
            boolean stop = false;
            do {
                HttpURLConnection conn = (HttpURLConnection) result.getUrlObj().openConnection();
                data.getContentType().ifPresent(value -> conn.addRequestProperty("Accept", value));
                conn.setInstanceFollowRedirects(false);
                if (result.authenticate) {
                    signConnection(conn, consumer, redirected);
                }
                conn.connect();
                setStatusCodeAndHeaders(result, conn);
                switch(result.getStatusCode()) {
                    case OK:
                        HttpConnectionUtils.readStream(result, conn.getInputStream());
                        stop = true;
                        break;
                    case MOVED:
                        redirected = true;
                        stop = onMoved(result);
                        conn.disconnect();
                        break;
                    default:
                        HttpConnectionUtils.readStream(result, conn.getErrorStream());
                        stop = result.fileResult == null || !result.authenticate;
                        if (!stop) {
                            result.onRetryWithoutAuthentication();
                        }
                        break;
                }
            } while (!stop);
        } catch(Exception e) {
            result.setException(e);
        }
    }

    private void setStatusCodeAndHeaders(HttpReadResult result, HttpURLConnection conn) throws IOException {
        result.setStatusCode(conn.getResponseCode());
        result.setHeaders(
                conn.getHeaderFields().entrySet().stream().flatMap(entry -> entry.getValue().stream()
                        .map(value -> new ImmutablePair<>(entry.getKey(), value))),
                Map.Entry::getKey,
                Map.Entry::getValue);
    }

    protected void signConnection(HttpURLConnection conn, OAuthConsumer consumer, boolean redirected)
            throws ConnectionException {
        if (!getCredentialsPresent() || consumer == null) {
            return;
        }
        try {
            if (data.originUrl.getHost().contentEquals(data.urlForUserToken.getHost())) {
                consumer.sign(conn);
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    consumer.setTokenWithSecret("", "");
                    consumer.sign(conn);
                } else {
                    conn.setRequestProperty("Authorization", "Dialback");
                    conn.setRequestProperty("host", data.urlForUserToken.getHost());
                    conn.setRequestProperty("token", getUserToken());
                    MyLog.v(this, () -> "Dialback authorization at " + data.originUrl
                            + "; urlForUserToken=" + data.urlForUserToken + "; token=" + getUserToken());
                    consumer.sign(conn);
                }
            }
        } catch (Exception e) {
            throw new ConnectionException(e);
        }
    }

}
