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

package org.andstatus.app.net;


import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.util.MyLog;
import org.apache.http.HttpVersion;
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
import org.json.JSONObject;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;

/**
 * Handles connection to the Twitter REST API using OAuth
 * Based on "BLOA" example, Copyright 2010 - Brion N. Emde
 * 
 * @see <a
 *      href="http://github.com/brione/Brion-Learns-OAuth">Brion-Learns-OAuth</a>
 */
public class ConnectionOAuth extends Connection implements MyOAuth {
    private static final String TAG = ConnectionOAuth.class.getSimpleName();

    public static final String USER_TOKEN = "user_token";
    public static final String USER_SECRET = "user_secret";
    public static final String REQUEST_TOKEN = "request_token";
    public static final String REQUEST_SECRET = "request_secret";
    public static final String REQUEST_SUCCEEDED = "request_succeeded";

    private String mOauthBaseUrl = "";
    
    private OAuthConsumer mConsumer = null;
    private OAuthProvider mProvider = null;

    /**
     * Saved User token
     */
    private String mToken;

    /**
     * Saved User secret
     */
    private String mSecret;

    private HttpClient mClient;

    public ConnectionOAuth(MyAccount ma) {
        super(ma);
        mOauthBaseUrl = ma.getOauthBaseUrl();

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

        OAuthKeys oak = new OAuthKeys(ma.getOriginId());
        mConsumer = new CommonsHttpOAuthConsumer(oak.getConsumerKey(),
                oak.getConsumerSecret());

        mProvider = new CommonsHttpOAuthProvider(getApiUrl(apiEnum.OAUTH_REQUEST_TOKEN),
                getApiUrl(apiEnum.OAUTH_ACCESS_TOKEN), getApiUrl(apiEnum.OAUTH_AUTHORIZE));

        
        // It turns out this was the missing thing to making standard
        // Activity launch mode work
        mProvider.setOAuth10a(true);
        
        // We look for saved user keys
        if (ma.dataContains(ConnectionOAuth.USER_TOKEN) && ma.dataContains(ConnectionOAuth.USER_SECRET)) {
            setAuthInformation(
                    ma.getDataString(ConnectionOAuth.USER_TOKEN, null),
                    ma.getDataString(ConnectionOAuth.USER_SECRET, null)
                    );
        }
    }
    
    /**
     * @see org.andstatus.app.net.Connection#getApiUrl(org.andstatus.app.net.Connection.apiEnum)
     */
    @Override
    protected String getApiUrl(apiEnum api) {
        String url = "";
        switch(api) {
            case OAUTH_ACCESS_TOKEN:
                url = mOauthBaseUrl + "/oauth/access_token";
                break;
            case OAUTH_AUTHORIZE:
                url = mOauthBaseUrl + "/oauth/authorize";
                break;
            case OAUTH_REQUEST_TOKEN:
                url = mOauthBaseUrl + "/oauth/request_token";
                break;
            default:
                url = super.getApiUrl(api);
                break;
        }
        MyLog.v(TAG, "URL=" + url);
        return url;
    }

    /**
     * @param token null means to clear the old values
     * @param secret
     */
    public void setAuthInformation(String token, String secret) {
        synchronized (this) {
            mToken = token;
            mSecret = secret;
            if (!(mToken == null || mSecret == null)) {
                getConsumer().setTokenWithSecret(mToken, mSecret);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.andstatus.app.net.Connection#save(android.content.SharedPreferences, android.content.SharedPreferences.Editor)
     */
    @Override
    public boolean save(MyAccount ma) {
        boolean changed = super.save(ma);

        if ( !TextUtils.equals(mToken, ma.getDataString(ConnectionOAuth.USER_TOKEN, null)) ||
                !TextUtils.equals(mSecret, ma.getDataString(ConnectionOAuth.USER_SECRET, null)) 
                ) {
            changed = true;

            if (TextUtils.isEmpty(mToken)) {
                ma.setDataString(ConnectionOAuth.USER_TOKEN, null);
                MyLog.d(TAG, "Clearing OAuth Token");
            } else {
                ma.setDataString(ConnectionOAuth.USER_TOKEN, mToken);
                MyLog.d(TAG, "Saving OAuth Token: " + mToken);
            }
            if (TextUtils.isEmpty(mSecret)) {
                ma.setDataString(ConnectionOAuth.USER_SECRET, null);
                MyLog.d(TAG, "Clearing OAuth Secret");
            } else {
                ma.setDataString(ConnectionOAuth.USER_SECRET, mSecret);
                MyLog.d(TAG, "Saving OAuth Secret: " + mSecret);
            }
        }
        return changed;
    }

    @Override
    public void clearAuthInformation() {
        setAuthInformation(null, null);
    }

    @Override
    public JSONObject createFavorite(String statusId) throws ConnectionException {
        StringBuilder url = new StringBuilder(getApiUrl(apiEnum.FAVORITES_CREATE_BASE));
        url.append(statusId);
        url.append(EXTENSION);
        HttpPost post = new HttpPost(url.toString());
        return postRequest(post);
    }

    @Override
    public JSONObject destroyFavorite(String statusId) throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(apiEnum.FAVORITES_DESTROY_BASE) + statusId + EXTENSION);
        return postRequest(post);
    }

    @Override
    public JSONObject destroyStatus(String statusId) throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(apiEnum.STATUSES_DESTROY) + statusId + EXTENSION);
        return postRequest(post);
    }

    @Override
    public JSONObject getStatus(String statusId) throws ConnectionException {
        JSONObject jso = null;
        try {
            Uri sUri = Uri.parse(getApiUrl(apiEnum.STATUSES_SHOW));
            Uri.Builder builder = sUri.buildUpon();
            builder.appendQueryParameter("id", statusId);
            jso = getUrl(builder.build().toString());
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        return jso;
    }
    
    /**
     * Returns the 20 most recent direct messages sent to the authenticating user
     *
     * TODO: GET direct_messages/sent: Returns the 20 most recent direct messages sent by the authenticating user. 
     * See https://dev.twitter.com/docs/api/1/get/direct_messages/sent
     */
    @Override
    public JSONArray getDirectMessages(String sinceId, int limit) throws ConnectionException {
        String url = getApiUrl(apiEnum.DIRECT_MESSAGES);
        return getTimeline(url, sinceId, "", limit, 0);
    }

    @Override
    public JSONArray getHomeTimeline(String sinceId, int limit) throws ConnectionException {
        String url = getApiUrl(apiEnum.STATUSES_HOME_TIMELINE);
        return getTimeline(url, sinceId, "", limit, 0);
    }

    private JSONObject getRequest(HttpGet get) throws ConnectionException {
        JSONObject jso = null;
        boolean ok = false;
        try {
            getConsumer().sign(get);
            String response = mClient.execute(get, new BasicResponseHandler());
            jso = new JSONObject(response);
            // Log.d(TAG, "authenticatedQuery: " + jso.toString(2));
            ok = true;
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        if (!ok) {
            jso = null;
        }
        return jso;
    }

    private JSONObject getUrl(String url) throws ConnectionException {
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
     */
    private JSONArray getTimeline(String url, String sinceId, String maxId, int limit, int page)
            throws ConnectionException {
        setSinceId(sinceId);
        setLimit(limit);

        boolean ok = false;
        JSONArray jArr = null;
        HttpGet get = null;
        try {
            Uri sUri = Uri.parse(url);
            Uri.Builder builder = sUri.buildUpon();
            if (getSinceId().length() > 1) {
                // For some reason "0" results in "bad request"
                builder.appendQueryParameter("since_id", getSinceId());
            } else if (maxId.length() > 1) { // these are mutually exclusive
                builder.appendQueryParameter("max_id", String.valueOf(maxId));
            }
            if (getLimit() != 0) {
                builder.appendQueryParameter("count", String.valueOf(getLimit()));
            }
            if (page != 0) {
                builder.appendQueryParameter("page", String.valueOf(page));
            }
            get = new HttpGet(builder.build().toString());
            getConsumer().sign(get);
            String response = mClient.execute(get, new BasicResponseHandler());
            jArr = new JSONArray(response);
            ok = (jArr != null);
        } catch (NullPointerException e) {
            // It looks like a bug in the library, but we have to catch it 
            Log.e(TAG, "NullPointerException was caught, URL='" + url + "'");
            e.printStackTrace();
        } catch (Exception e) {
            if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
                MyLog.v(TAG, get != null ? get.getRequestLine().toString() : " get is null");
            }
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getTimeline '" + url + "' "
                    + (ok ? "OK, " + jArr.length() + " statuses" : "FAILED"));
        }
        return jArr;
    }

    @Override
    public JSONArray getMentionsTimeline(String sinceId, int limit) throws ConnectionException {
        String url = getApiUrl(apiEnum.STATUSES_MENTIONS_TIMELINE);
        return getTimeline(url, sinceId, "", limit, 0);
    }

    @Override
    public JSONObject rateLimitStatus() throws ConnectionException {
        return getUrl(getApiUrl(apiEnum.ACCOUNT_RATE_LIMIT_STATUS));
    }

    private JSONObject postRequest(HttpPost post) throws ConnectionException {
        JSONObject jso = null;
        boolean ok = false;
        try {
            // Maybe we'll need this:
            // post.setParams(...);

            // sign the request to authenticate
            getConsumer().sign(post);
            String response = mClient.execute(post, new BasicResponseHandler());
            jso = new JSONObject(response);
            ok = true;
        } catch (HttpResponseException e) {
            ConnectionException e2 = new ConnectionException(e.getStatusCode(), e.getLocalizedMessage());
            Log.w(TAG, e2.getLocalizedMessage());
            throw e2;
        } catch (Exception e) {
            // We don't catch other exceptions because in fact it's vary difficult to tell
            // what was a real cause of it. So let's make code clearer.
            e.printStackTrace();
            throw new ConnectionException(e.getLocalizedMessage());
        }
        if (!ok) {
            jso = null;
        }
        return jso;
    }

    @Override
    public JSONObject updateStatus(String message, String inReplyToId)
            throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(apiEnum.STATUSES_UPDATE));
        LinkedList<BasicNameValuePair> out = new LinkedList<BasicNameValuePair>();
        out.add(new BasicNameValuePair("status", message));
        if ( !TextUtils.isEmpty(inReplyToId)) {
            out.add(new BasicNameValuePair("in_reply_to_status_id", inReplyToId));
        }
        try {
            post.setEntity(new UrlEncodedFormEntity(out, HTTP.UTF_8));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        }
        return postRequest(post);
    }

    @Override
    public JSONObject postDirectMessage(String userId, String screenName, String message) throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(apiEnum.POST_DIRECT_MESSAGE));
        LinkedList<BasicNameValuePair> out = new LinkedList<BasicNameValuePair>();
        out.add(new BasicNameValuePair("text", message));
        if ( !TextUtils.isEmpty(userId)) {
            out.add(new BasicNameValuePair("user_id", userId));
        }
        if ( !TextUtils.isEmpty(screenName)) {
            out.add(new BasicNameValuePair("screen_name", screenName));
        }
        try {
            post.setEntity(new UrlEncodedFormEntity(out, HTTP.UTF_8));
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        }
        return postRequest(post);
    }
    
    @Override
    public JSONObject postReblog(String rebloggedId) throws ConnectionException {
        HttpPost post = new HttpPost(getApiUrl(apiEnum.POST_REBLOG) + rebloggedId + EXTENSION);
        return postRequest(post);
    }

    /**
     * @see org.andstatus.app.net.Connection#getCredentialsPresent()
     */
    @Override
    public boolean getCredentialsPresent(MyAccount ma) {
        boolean yes = false;
        if (ma.dataContains(ConnectionOAuth.USER_TOKEN) && ma.dataContains(ConnectionOAuth.USER_SECRET)) {
            mToken = ma.getDataString(ConnectionOAuth.USER_TOKEN, null);
            mSecret = ma.getDataString(ConnectionOAuth.USER_SECRET, null);
            if (!(mToken == null || mSecret == null)) {
                yes = true;
            }
        }
        return yes;
    }

    @Override
    public JSONObject verifyCredentials() throws ConnectionException {
        return getUrl(getApiUrl(apiEnum.ACCOUNT_VERIFY_CREDENTIALS));
    }

    @Override
    public boolean isOAuth() {
        return true;
    }

    @Override
    public OAuthConsumer getConsumer() {
        return mConsumer;
    }

    @Override
    public OAuthProvider getProvider() {
        return mProvider;
    }
}
