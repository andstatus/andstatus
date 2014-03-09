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

import android.text.TextUtils;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.andstatus.app.net.Connection.ApiRoutineEnum;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class HttpConnectionOAuthJavaNet extends HttpConnectionOAuth {
    private static final String NON_JSON_RESPONSE = ", non-JSON response: '";
    private static final String ERROR_GETTING = "Error getting '";
    private static final String COMMA_STATUS = "', status=";

    /**
     * Partially borrowed from the "Impeller" code !
     */
    @Override
    public void registerClient(String path) throws ConnectionException {
        MyLog.v(this, "Registering client for " + data.host);
        String consumerKey = "";
        String consumerSecret = "";
        data.oauthClientKeys.clear();

        try {
            URL endpoint = new URL(pathToUrl(path));
            HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
                    
            HashMap<String, String> params = new HashMap<String, String>();
            params.put("type", "client_associate");
            params.put("application_type", "native");
            params.put("redirect_uris", HttpConnection.CALLBACK_URI.toString());
            params.put("client_name", HttpConnection.USER_AGENT);
            params.put("application_name", HttpConnection.USER_AGENT);
            String requestBody = HttpJavaNetUtils.encode(params);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            
            Writer w = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            w.write(requestBody);
            w.close();
            
            if(conn.getResponseCode() != 200) {
                String msg = HttpJavaNetUtils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                MyLog.e(this, "Server returned an error response: " + msg);
                MyLog.e(this, "Server returned an error response: " + conn.getResponseMessage());
            } else {
                String response = HttpJavaNetUtils.readAll(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                JSONObject jso = new JSONObject(response);
                if (jso != null) {
                    consumerKey = jso.getString("client_id");
                    consumerSecret = jso.getString("client_secret");
                    data.oauthClientKeys.setConsumerKeyAndSecret(consumerKey, consumerSecret);
                }
            }
        } catch (IOException e) {
            MyLog.e(this, "registerClient Exception", e);
        } catch (JSONException e) {
            MyLog.e(this, "registerClient Exception", e);
        }
        if (data.oauthClientKeys.areKeysPresent()) {
            MyLog.v(this, "Registered client for " + data.host);
        } else {
            throw ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST, data.host, "No client keys for the host yet");
        }
    }

    @Override
    public OAuthProvider getProvider() {
        OAuthProvider provider = null;
        provider = new DefaultOAuthProvider(getApiUrl(ApiRoutineEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(ApiRoutineEnum.OAUTH_ACCESS_TOKEN), getApiUrl(ApiRoutineEnum.OAUTH_AUTHORIZE));
        provider.setOAuth10a(true);
        return provider;

    }

    @Override
    protected JSONObject postRequest(String path, JSONObject jso) throws ConnectionException {
        JSONObject result = null;
        try {
            MyLog.v(this, "Posting " + (jso == null ? "(empty)" : jso.toString(2)));
        
            URL url = new URL(pathToUrl(path));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json ; charset=UTF-8");
            setAuthorization(conn, getConsumer(), false);
            
            if (jso != null) {
                OutputStream os = conn.getOutputStream();
                OutputStreamWriter wr = new OutputStreamWriter(os, "UTF-8");
                String toWrite = jso.toString(); 
                wr.write(toWrite);
                try {
                    wr.close();
                } catch (IOException e) {
                    MyLog.d(this, "Error closing output stream", e);
                }
            }
                        
            int responseCode = conn.getResponseCode();
            switch(responseCode) {
                case 200:
                    result = new JSONObject(HttpJavaNetUtils.readAll(conn.getInputStream()));
                    break;
                default:
                    String responseString = HttpJavaNetUtils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                    throw exceptionFromJsonErrorResponse(path, responseCode, responseString, StatusCode.UNKNOWN);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, e, result, ERROR_GETTING + path + "'");
        } catch (ConnectionException e) {
            throw e;
        } catch(Exception e) {
            throw new ConnectionException(ERROR_GETTING + path + "'", e);
        }
        return result;
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
    
    @Override
    protected JSONObject postRequest(String path) throws ConnectionException {
        return postRequest(path, null);
    }

    @Override
    protected JSONObject getRequest(String path) throws ConnectionException {
        if (TextUtils.isEmpty(path)) {
            throw new IllegalArgumentException("path is empty");
        }
        String responseString = "";
        JSONObject result = null;
        try {
            OAuthConsumer consumer = getConsumer();
            
            URL url = new URL(pathToUrl(path));
            HttpURLConnection conn;
            boolean redirected = false;
            boolean done=false;
            do {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                setAuthorization(conn, consumer, redirected);
                conn.connect();
                int responseCode = conn.getResponseCode();
                StatusCode statusCode = StatusCode.fromResponseCode(responseCode);
                switch(responseCode) {
                    case 200:
                        try {
                            responseString = HttpJavaNetUtils.readAll(conn.getInputStream());
                            result = new JSONObject(responseString);
                            done = true;
                        } catch (JSONException e) {
                            throw ConnectionException.loggedJsonException(this, e, null,
                                    "Error reading response from '"
                                            + path + COMMA_STATUS
                                            + responseCode + NON_JSON_RESPONSE + responseString
                                            + "'");
                        }
                        break;
                    case 301:
                    case 302:
                    case 303:
                    case 307:
                        url = new URL(conn.getHeaderField("Location").replace("%3F", "?"));
                        MyLog.v(this, "Following redirect to " + url);
                        redirected = true;
                        if (MyLog.isLoggable(MyLog.APPTAG, MyLog.VERBOSE)) {
                            StringBuilder message = new StringBuilder("Headers: ");
                            for (int posn=0 ; ; posn++) {
                                String fieldName = conn.getHeaderFieldKey(posn);
                                if ( fieldName == null) {
                                    MyLog.v(this, message.toString());
                                    break;
                                }
                                message.append(fieldName +": " + conn.getHeaderField(fieldName) + "; ");
                            }
                        }
                        break;                        
                    default:
                        responseString = HttpJavaNetUtils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
                        throw exceptionFromJsonErrorResponse(path, responseCode, responseString, statusCode);
                }
            } while (!done);
        } catch (ConnectionException e) {
            throw e;
        } catch(Exception e) {
            throw new ConnectionException(ERROR_GETTING + path + "'", e);
        }
        return result;
    }

    public ConnectionException exceptionFromJsonErrorResponse(String path, int responseCode, String responseString,
            StatusCode statusCode) {
        ConnectionException ce = null;
        try {
            JSONObject jsonError = new JSONObject(responseString);
            String error = jsonError.optString("error");
            if (statusCode == StatusCode.UNKNOWN) {
                statusCode = (error.indexOf("not found") < 0 ? StatusCode.UNKNOWN : StatusCode.NOT_FOUND);
            }
            ce = new ConnectionException(statusCode, ERROR_GETTING + path + COMMA_STATUS + responseCode + ", error='" + error + "'");
        } catch (JSONException e) {
            ce = new ConnectionException(statusCode, ERROR_GETTING + path + COMMA_STATUS + responseCode + NON_JSON_RESPONSE + responseString + "'", e);
        }
        return ce;
    }

    private void setAuthorization(HttpURLConnection conn, OAuthConsumer consumer, boolean redirected)
            throws OAuthMessageSignerException, OAuthExpectationFailedException,
            OAuthCommunicationException {
        if (getCredentialsPresent()) {
            if (data.host.contentEquals(data.hostForUserToken)) {
                consumer.sign(conn);
            } else {
                // See http://tools.ietf.org/html/draft-prodromou-dialback-00
                if (redirected) {
                    consumer.setTokenWithSecret("", "");
                    consumer.sign(conn);
                } else {
                    conn.setRequestProperty("Authorization", "Dialback");
                    conn.setRequestProperty("host", data.hostForUserToken);
                    conn.setRequestProperty("token", getUserToken());
                    MyLog.v(this, "Dialback authorization at " + data.host + "; host=" + data.hostForUserToken + "; token=" + getUserToken());
                    consumer.sign(conn);
                }
            }
        }
    }

    @Override
    protected JSONArray getRequestAsArray(String path) throws ConnectionException {
        JSONObject jso = getRequest(path);
        JSONArray jsa = null;
        if (jso == null) {
            throw new ConnectionException("Response is null");
        }
        if (jso.has("items")) {
            try {
                jsa = jso.getJSONArray("items");
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, e, jso, "'items' is not an array?!");
            }
        } else {
            try {
                MyLog.d(this, "Response from server: " + jso.toString(4));
            } catch (JSONException e) {
                MyLog.e(this, e);
            }
            throw ConnectionException.loggedJsonException(this, null, jso, "No array was returned");
        }
        return jsa;
    }
}
