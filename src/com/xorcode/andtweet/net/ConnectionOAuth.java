/*
 * Copyright (C) 2010 yvolk (Yuri Volkov), http://yurivolkov.com
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

package com.xorcode.andtweet.net;

import com.xorcode.andtweet.AndTweetService;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;

/**
 * Handles connection to the Twitter REST API using OAuth
 * Based on "BLOA" example, Copyright 2010 - Brion N. Emde
 * 
 * @see <a
 *      href="http://github.com/brione/Brion-Learns-OAuth">Brion-Learns-OAuth</a>
 */
public class ConnectionOAuth extends Connection {
    private static final String TAG = ConnectionOAuth.class.getSimpleName();

    public static final String USER_TOKEN = "user_token";
    public static final String USER_SECRET = "user_secret";
    public static final String REQUEST_TOKEN = "request_token";
    public static final String REQUEST_SECRET = "request_secret";
    public static final String REQUEST_SUCCEEDED = "request_succeeded";
    public static final String TWITTER_REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
    public static final String TWITTER_ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
    public static final String TWITTER_AUTHORIZE_URL = "https://api.twitter.com/oauth/authorize";
    private OAuthConsumer mConsumer = null;

    /**
     * Saved User token
     */
    private String mToken;

    /**
     * Saved User secret
     */
    private String mSecret;

    private HttpClient mClient;

    public ConnectionOAuth(SharedPreferences sp) {
        super(sp);

        HttpParams parameters = new BasicHttpParams();
        HttpProtocolParams.setVersion(parameters, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setContentCharset(parameters, HTTP.DEFAULT_CONTENT_CHARSET);
        HttpProtocolParams.setUseExpectContinue(parameters, false);
        HttpConnectionParams.setTcpNoDelay(parameters, true);
        HttpConnectionParams.setSocketBufferSize(parameters, 8192);

        SchemeRegistry schReg = new SchemeRegistry();
        schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        ClientConnectionManager tsccm = new ThreadSafeClientConnManager(parameters, schReg);
        mClient = new DefaultHttpClient(tsccm, parameters);

        mConsumer = new CommonsHttpOAuthConsumer(OAuthKeys.TWITTER_CONSUMER_KEY,
                OAuthKeys.TWITTER_CONSUMER_SECRET);
        loadSavedKeys();
    }
    
    private void loadSavedKeys() {
        // We look for saved user keys
        if (mSp.contains(ConnectionOAuth.USER_TOKEN) && mSp.contains(ConnectionOAuth.USER_SECRET)) {
            mToken = mSp.getString(ConnectionOAuth.USER_TOKEN, null);
            mSecret = mSp.getString(ConnectionOAuth.USER_SECRET, null);
            if (!(mToken == null || mSecret == null)) {
                mConsumer.setTokenWithSecret(mToken, mSecret);
            }
        }
    }

    /**
     * @param token null means to clear the old values
     * @param secret
     */
    public void saveAuthInformation(String token, String secret) {
        synchronized (mSp) {
            SharedPreferences.Editor editor = mSp.edit();
            if (token == null) {
                editor.remove(ConnectionOAuth.USER_TOKEN);
                Log.d(TAG, "Clearing OAuth Token");
            } else {
                editor.putString(ConnectionOAuth.USER_TOKEN, token);
                Log.d(TAG, "Saving OAuth Token: " + token);
            }
            if (secret == null) {
                editor.remove(ConnectionOAuth.USER_SECRET);
                Log.d(TAG, "Clearing OAuth Secret");
            } else {
                editor.putString(ConnectionOAuth.USER_SECRET, secret);
                Log.d(TAG, "Saving OAuth Secret: " + secret);
            }
            editor.commit();
            // Keys changed so we have to reload them
            loadSavedKeys();
        }
    }

    @Override
    public void clearAuthInformation() {
        saveAuthInformation(null, null);
    }

    @Override
    public JSONObject createFavorite(long statusId) throws UnsupportedEncodingException,
            ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        StringBuilder url = new StringBuilder(FAVORITES_CREATE_BASE_URL);
        url.append(String.valueOf(statusId));
        url.append(EXTENSION);
        HttpPost post = new HttpPost(url.toString());
        return postRequest(post);
    }

    @Override
    public JSONObject destroyFavorite(long statusId) throws UnsupportedEncodingException,
            ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        StringBuilder url = new StringBuilder(FAVORITES_DESTROY_BASE_URL);
        url.append(String.valueOf(statusId));
        url.append(EXTENSION);
        HttpPost post = new HttpPost(url.toString());
        return postRequest(post);
    }

    @Override
    public JSONObject destroyStatus(long statusId) throws UnsupportedEncodingException,
            ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        StringBuilder url = new StringBuilder(STATUSES_DESTROY_URL);
        url.append(String.valueOf(statusId));
        url.append(EXTENSION);
        HttpPost post = new HttpPost(url.toString());
        return postRequest(post);
    }

    @Override
    public JSONArray getDirectMessages(long sinceId, int limit) throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        String url = DIRECT_MESSAGES_URL;
        return getTimeline(url, sinceId, 0, limit, 0);
    }

    @Override
    public JSONArray getFriendsTimeline(long sinceId, int limit) throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        String url = STATUSES_FRIENDS_TIMELINE_URL;
        return getTimeline(url, sinceId, 0, limit, 0);
    }

    private JSONObject getRequest(HttpGet get) throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        JSONObject jso = null;
        try {
            mConsumer.sign(get);
            String response = mClient.execute(get, new BasicResponseHandler());
            jso = new JSONObject(response);
            // Log.d(TAG, "authenticatedQuery: " + jso.toString(2));
        } catch (JSONException e) {
            e.printStackTrace();
            throw new ConnectionException(e);
        } catch (OAuthMessageSignerException e) {
            e.printStackTrace();
        } catch (OAuthExpectationFailedException e) {
            e.printStackTrace();
        } catch (OAuthCommunicationException e) {
            e.printStackTrace();
        } catch (HttpResponseException e) {
            e.printStackTrace();
            throw new ConnectionAuthenticationException(e.getLocalizedMessage());
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jso;
    }

    private JSONObject getUrl(String url) throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        HttpGet get = new HttpGet(url);
        return getRequest(get);
    }

    /**
     * Universal method for several Timelines...
     * 
     * @param url URL predefined for this timeline
     * @param sinceId
     * @param maxId
     * @param limit
     * @param page
     * @return
     * @throws ConnectionException
     * @throws ConnectionAuthenticationException
     * @throws ConnectionUnavailableException
     * @throws SocketTimeoutException
     */
    private JSONArray getTimeline(String url, long sinceId, long maxId, int limit, int page)
            throws ConnectionException, ConnectionAuthenticationException,
            ConnectionUnavailableException, SocketTimeoutException {
        setSinceId(sinceId);
        setLimit(limit);

        boolean ok = false;
        JSONArray jArr = null;
        try {
            Uri sUri = Uri.parse(url);
            Uri.Builder builder = sUri.buildUpon();
            if (getSinceId() != 0) {
                builder.appendQueryParameter("since_id", String.valueOf(getSinceId()));
            } else if (maxId != 0) { // these are mutually exclusive
                builder.appendQueryParameter("max_id", String.valueOf(maxId));
            }
            if (getLimit() != 0) {
                builder.appendQueryParameter("count", String.valueOf(getLimit()));
            }
            if (page != 0) {
                builder.appendQueryParameter("page", String.valueOf(page));
            }
            HttpGet get = new HttpGet(builder.build().toString());
            mConsumer.sign(get);
            String response = mClient.execute(get, new BasicResponseHandler());
            jArr = new JSONArray(response);
            ok = (jArr != null);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new ConnectionException(e);
        } catch (OAuthMessageSignerException e) {
            e.printStackTrace();
        } catch (OAuthExpectationFailedException e) {
            e.printStackTrace();
        } catch (HttpResponseException e) {
            e.printStackTrace();
            throw new ConnectionAuthenticationException(e.getLocalizedMessage());
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (OAuthCommunicationException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // It looks like a bug in the library, but we have to catch it 
            Log.e(TAG, "NullPointerException was caught, URL='" + url + "'");
            e.printStackTrace();
        }
        if (Log.isLoggable(AndTweetService.APPTAG, Log.DEBUG)) {
            Log.d(TAG, "getTimeline '" + url + "' "
                    + (ok ? "OK, " + jArr.length() + " statuses" : "FAILED"));
        }
        return jArr;
    }

    @Override
    public JSONArray getMentionsTimeline(long sinceId, int limit) throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        String url = STATUSES_MENTIONS_TIMELINE_URL;
        return getTimeline(url, sinceId, 0, limit, 0);
    }

    @Override
    public JSONObject rateLimitStatus() throws JSONException, ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        return getUrl(ACCOUNT_RATE_LIMIT_STATUS_URL);
    }

    private JSONObject postRequest(HttpPost post) throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        JSONObject jso = null;
        try {
            // Maybe we'll need this:
            // post.setParams(...);

            // sign the request to authenticate
            mConsumer.sign(post);
            String response = mClient.execute(post, new BasicResponseHandler());
            jso = new JSONObject(response);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (OAuthMessageSignerException e) {
            e.printStackTrace();
        } catch (OAuthExpectationFailedException e) {
            e.printStackTrace();
        } catch (OAuthCommunicationException e) {
            e.printStackTrace();
        } catch (HttpResponseException e) {
            e.printStackTrace();
            throw new ConnectionAuthenticationException(e.getLocalizedMessage());
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        } finally {

        }
        return jso;
    }

    @Override
    public JSONObject updateStatus(String message, long inReplyToId)
            throws UnsupportedEncodingException, ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        HttpPost post = new HttpPost(STATUSES_UPDATE_URL);
        LinkedList<BasicNameValuePair> out = new LinkedList<BasicNameValuePair>();
        out.add(new BasicNameValuePair("status", message));
        if (inReplyToId > 0) {
            out.add(new BasicNameValuePair("in_reply_to_status_id", String.valueOf(inReplyToId)));
        }
        post.setEntity(new UrlEncodedFormEntity(out, HTTP.UTF_8));
        return postRequest(post);
    }

    /**
     * @see com.xorcode.andtweet.net.Connection#getCredentialsPresent()
     */
    @Override
    public boolean getCredentialsPresent() {
        boolean yes = false;
        if (mSp.contains(ConnectionOAuth.USER_TOKEN) && mSp.contains(ConnectionOAuth.USER_SECRET)) {
            mToken = mSp.getString(ConnectionOAuth.USER_TOKEN, null);
            mSecret = mSp.getString(ConnectionOAuth.USER_SECRET, null);
            if (!(mToken == null || mSecret == null)) {
                yes = true;
            }
        }
        return yes;
    }

    @Override
    public JSONObject verifyCredentials() throws ConnectionException,
            ConnectionAuthenticationException, ConnectionUnavailableException,
            SocketTimeoutException {
        return getUrl(ACCOUNT_VERIFY_CREDENTIALS_URL);
    }
}
