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
import org.andstatus.app.util.FileUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
    public void registerClient(String path) throws ConnectionException {		
		MyStringBuilder logmsg = MyStringBuilder.of("registerClient; for " + data.originUrl
                + "; URL='" + pathToUrlString(path) + "'");
        MyLog.v(this, logmsg::toString);
        String consumerKey = "";
        String consumerSecret = "";
        data.oauthClientKeys.clear();
        Writer writer = null;
        try {
			URL endpoint = new URL(pathToUrlString(path));
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
            
            if(conn.getResponseCode() != 200) {
                String msg = HttpConnectionUtils.readStreamToString(conn.getErrorStream());
                logmsg.atNewLine("Server returned an error response", msg);
                logmsg.atNewLine("Response message from server", conn.getResponseMessage());
                MyLog.i(this, logmsg.toString());
            } else {
                String response = HttpConnectionUtils.readStreamToString(conn.getInputStream());
                JSONObject jso = new JSONObject(response);
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
            throw ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST,
                    "Failed to obtain client keys for host; " + logmsg, data.originUrl);
        }
    }

    @Override
    public OAuthProvider getProvider() throws ConnectionException {
        OAuthProvider provider = null;
        provider = new DefaultOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));
        provider.setOAuth10a(true);
        return provider;

    }
    
    @Override
    protected void postRequest(HttpReadResult result) throws ConnectionException {
        try {
            HttpURLConnection conn = (HttpURLConnection) result.getUrlObj().openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            
            if (!result.hasFormParams()) {
                // Nothing to do at this step
            } else if (result.getFormParams().has(HttpConnection.KEY_MEDIA_PART_URI)) {
                writeMedia(conn, result.getFormParams());
            } else {
                writeJson(conn, result.getFormParams());
            }
                        
            result.setStatusCode(conn.getResponseCode());
            switch(result.getStatusCode()) {
                case OK:
                    result.strResponse = HttpConnectionUtils.readStreamToString(conn.getInputStream());
                    break;
                default:
                    result.strResponse = HttpConnectionUtils.readStreamToString(conn.getErrorStream());
                    throw result.getExceptionFromJsonErrorResponse();
            }
        } catch (JSONException | IOException e) {
            result.setException(e);
        }
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
        conn.setRequestProperty("Content-Type", "application/json");
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

    protected void getRequest(HttpReadResult result) throws ConnectionException {
        String method = "getRequest; ";
        StringBuilder logBuilder = new StringBuilder(method);
        try {
            OAuthConsumer consumer = getConsumer();
            logBuilder.append("URL='" + result.getUrl() + "';");
            HttpURLConnection conn;
            boolean redirected = false;
            boolean stop = false;
            do {
                conn = (HttpURLConnection) result.getUrlObj().openConnection();
                conn.setInstanceFollowRedirects(false);
                if (result.authenticate) {
                    signConnection(conn, consumer, redirected);
                }
                conn.connect();
                result.setStatusCode(conn.getResponseCode());
                switch(result.getStatusCode()) {
                    case OK:
                        if (result.fileResult != null) {
                            FileUtils.readStreamToFile(conn.getInputStream(), result.fileResult);
                        } else {
                            result.strResponse = HttpConnectionUtils.readStreamToString(conn.getInputStream());
                        }
                        stop = true;
                        break;
                    case MOVED:
                        redirected = true;
                        final String location = conn.getHeaderField("Location");
                        result.setUrl(location.replace("%3F", "?"));
                        stop = StringUtils.isEmpty(location);
                        if (stop) {
                            result.onNoLocationHeaderOnMoved();
                        } else {
                            String logMsg3 = (result.redirected ? "Following redirect to "
                                    : "Not redirected to ") + "'" + result.getUrl() + "'";
                            logBuilder.append(logMsg3 + "; ");
                            MyLog.v(this, () -> method + logMsg3);
                            if (MyLog.isVerboseEnabled()) {
                                StringBuilder message = new StringBuilder(method + "Headers: ");
                                for (Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                                    for (String value : entry.getValue()) {
                                        message.append(entry.getKey() +": " + value + ";\n");
                                    }
                                }
                                MyLog.v(this, message::toString);
                            }
                            conn.disconnect();
                        }
                        break;
                    default:
                        result.strResponse = HttpConnectionUtils.readStreamToString(conn.getErrorStream());
                        stop = result.fileResult == null || !result.authenticate;
                        if (!stop) {
                            result.authenticate = false;
                            String logMsg4 = "Retrying without authentication connection to '" + result.getUrl() + "'";
                            logBuilder.append(logMsg4 + "; ");
                            MyLog.v(this, () -> method + logMsg4);
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
        } catch (OAuthMessageSignerException | OAuthExpectationFailedException
                | OAuthCommunicationException e) {
            throw new ConnectionException(e);
        }
    }

}
