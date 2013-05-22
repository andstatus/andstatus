/*
 * Copyright (C) 2011-2013 yvolk (Yuri Volkov), http://yurivolkov.com
 * Copyright (C) 2008 Torgny Bjers
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
import android.util.Log;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.account.Origin;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.Connection;
import org.andstatus.app.util.MyLog;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Handles connection to the API of the Microblogging System (i.e. to the {@link Origin})
 * Authenticated User info (User account in the Microblogging system) and connection properties 
 * are provided in the constructor.
 * 
 * @author yvolk, torgny.bjers
 */
public abstract class Connection {
    private static final String TAG = Connection.class.getSimpleName();
    
    /**
     * Base URL for connection to the System
     */
    private String mBaseUrl = "";
    
    /**
     * Constants independent of the system
     */
    protected static final String EXTENSION = ".json";
    
    protected static final Integer DEFAULT_GET_REQUEST_TIMEOUT = 15000;
    protected static final Integer DEFAULT_POST_REQUEST_TIMEOUT = 20000;

    /**
     * Connection APIs known
     */
    public enum ApiEnum {
        /** Twitter API v.1 https://dev.twitter.com/docs/api/1     */
        TWITTER1P0,
        /** Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1 */
        TWITTER1P1
    }

    /**
     * API of this Connection
     */
    private ApiEnum mApi;
    
    /**
     * API routines (functions, "resources" in terms of Twitter)  enumerated
     */
    public enum ApiRoutineEnum {
        ACCOUNT_RATE_LIMIT_STATUS,
        ACCOUNT_VERIFY_CREDENTIALS,
        /** Returns most recent direct messages sent to the authenticating user */
        DIRECT_MESSAGES,
        FAVORITES_CREATE_BASE,
        FAVORITES_DESTROY_BASE,
        FOLLOW_USER,
        POST_DIRECT_MESSAGE,
        POST_REBLOG,
        STATUSES_DESTROY,
        /**
         * Get the Home timeline (whatever it is...).
         * This is the equivalent of /home on the Web.
         */
        STATUSES_HOME_TIMELINE,
        /**
         * Get the user's replies.
         * 
         * Returns most recent @replies (status updates prefixed with @username) 
         * for the authenticating user.
         */
        STATUSES_MENTIONS_TIMELINE,
        /**
         * Get the User timeline for the user with the selectedUserId. We use credentials of Account which may be
         * not the same user. 
         */
        STATUSES_USER_TIMELINE,
        STATUSES_SHOW,
        STATUSES_UPDATE,
        STOP_FOLLOWING_USER,
        
        /**
         * OAuth APIs
         */
        OAUTH_ACCESS_TOKEN,
        OAUTH_AUTHORIZE,
        OAUTH_REQUEST_TOKEN
    }

    public Connection() {}
    
    protected Connection(AccountDataReader dr, ApiEnum api, String apiBaseUrl) {
        mApi = api;
        mBaseUrl = apiBaseUrl;
    }

    /**
     * @return API of this Connection
     */
    public ApiEnum getApi() {
        return mApi;
    }
    
    /**
     * @return Base URL for connection to the System
     */
    public String getBaseUrl() {
        return mBaseUrl;
    }

    /**
     * URL of the API. Not logged
     * @param routine
     * @return URL or an empty string in case the API routine is not supported
     */
    protected abstract String getApiUrl1(ApiRoutineEnum routine);

    /**
     * URL of the API. Logged
     * @param routine
     * @return URL or an empty string in case the API routine is not supported
     */
    protected final String getApiUrl(ApiRoutineEnum routine) {
        String url = this.getApiUrl1(routine);
        if (TextUtils.isEmpty(url)) {
            Log.e(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");
        } else if (MyLog.isLoggable(null, Log.VERBOSE )) {
            Log.v(this.getClass().getSimpleName(), "API '" + routine + "' URL=" + url);  
        }
        return url;
    }
    
    /**
     * Use this method to check the connection's (Account's) capability before attempting to use it
     * and even before presenting corresponding action to the User. 
     * @param routine
     * @return true if supported
     */
    public boolean isApiSupported(ApiRoutineEnum routine) {
        boolean is = !TextUtils.isEmpty(this.getApiUrl1(routine));
        if (!is && MyLog.isLoggable(null, Log.VERBOSE )) {
          Log.v(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");  
        }
        return (is);
    }
    
    /**
     * @return Does this connection use OAuth?
     */
    public boolean isOAuth() {
        return false;
    }
    
    public static String getScreenName(JSONObject credentials) {
        return credentials.optString("screen_name");
    }

    public abstract void clearAuthInformation();
    
    /**
     * Check API requests status.
     * TODO: Formalize this for different microblogging systems.
     */
    public abstract JSONObject rateLimitStatus() throws ConnectionException;

    /**
     * Do we need password to be set?
     * By default password is not needed and is ignored
     */
    public boolean isPasswordNeeded() {
        return false;
    }
    
    /**
     * Set User's password if the Connection object needs it
     */
    public void setPassword(String password) { }

    
    public String getPassword() {
        return "";
    }
    
    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    public boolean save(AccountDataWriter dw) {
        boolean changed = false;

        // Nothing to save
        
        return changed;
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public abstract boolean getCredentialsPresent(AccountDataReader dr);  
    
    /**
     * Verify the user's credentials.
     * 
     * Returns true if authentication was successful
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
     *      REST API Method: account verify_credentials</a>
     * 
     * @return JSONObject - user
     * @throws ConnectionException 
     */
    public abstract JSONObject verifyCredentials() throws ConnectionException;

    /**
     * 
     * @param statusId
     * @return JSONObject
     * @throws ConnectionException
     */
    public abstract JSONObject destroyFavorite(String statusId) throws ConnectionException;

    /**
     * Favorites the status specified in the ID parameter as the authenticating user.
     * Returns the favorite status when successful.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-favorites%C2%A0create">Twitter
     *      REST API Method: favorites create</a>
     * 
     * @param statusId
     * @return JSONObject
     * @throws ConnectionException
     */
    public abstract JSONObject createFavorite(String statusId) throws ConnectionException;

    /**
     * Destroys the status specified by the required ID parameter.
     * The authenticating user must be the author of the specified status.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-statuses%C2%A0destroy">Twitter
     *      REST API Method: statuses/destroy</a>
     * 
     * @param statusId
     * @return JSONObject
     * @throws ConnectionException
     */
    public abstract JSONObject destroyStatus(String statusId) throws ConnectionException;

    /**
     * Returns a single status, specified by the id parameter below.
     * The status's author will be returned inline.
     */
    public abstract JSONObject getStatus(String statusId) throws ConnectionException;
    
    /**
     * Update user status by posting to the Twitter REST API.
     * 
     * Updates the authenticating user's status, also known as tweeting/blogging.
     * To upload an image to accompany the tweet, use POST statuses/update_with_media.
     * 
     * @param message       Text of the "status"
     * @param inReplyToId   The ID of an existing status that the update is in reply to.
     * @throws ConnectionException 
     *
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/statuses/update">Twitter
     *      POST statuses/update</a>
     */
    public abstract JSONObject updateStatus(String message, String inReplyToId)
            throws ConnectionException;

    /**
     * Post Direct Message
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/direct_messages/new">POST direct_messages/new</a>
     * 
     * @param message
     * @param userId {@link User#USER_OID} - The ID of the user who should receive the direct message
     * @return The sent message in the requested format if successful.
     * @throws ConnectionException
     */
    public abstract JSONObject postDirectMessage(String message, String userId)
            throws ConnectionException;

    /**
     * Post reblog ("Retweet")
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/statuses/retweet/%3Aid">POST statuses/retweet/:id</a>
     * 
     * @param rebloggedId id of the Reblogged message
     * @return
     * @throws ConnectionException
     */
    public abstract JSONObject postReblog(String rebloggedId)
            throws ConnectionException;

    /**
     * Universal method for several Timeline Types...
     * 
     * @param apiRoutine - which timeline to retrieve
     * @param sinceId
     * @param limit
     * @param userId For the {@link ApiRoutineEnum#STATUSES_USER_TIMELINE}, null for the other timelines
     * @return
     * @throws ConnectionException
     */
    public abstract JSONArray getTimeline(ApiRoutineEnum apiRoutine, String sinceId, int limit, String userId)
            throws ConnectionException;

    /**
     * Allows this User to follow the user specified in the userId parameter
     * Allows this User to stop following the user specified in the userId parameter
     * @param userId
     * @param follow true - Follow, false - Stop following
     * @return User object with 'following' flag set/reset
     * @throws ConnectionException
     */
    public abstract JSONObject followUser(String userId, Boolean follow) throws ConnectionException;
    
    /**
     * Execute a POST request against the API url
     * 
     * @throws ConnectionException 
     */
    protected JSONObject postRequest(String url) throws ConnectionException {
        HttpPost post = new HttpPost(url);
        return postRequest(post);
    }

    
    /**
     * Execute a POST request against enumerated API.
     * 
     * @param ApiRoutineEnum apiRoutine
     * @param List<NameValuePair> - Post form parameters
     * @throws ConnectionException
     */
    protected final JSONObject postRequest(ApiRoutineEnum apiRoutine, List<NameValuePair> formParams) throws ConnectionException {
        JSONObject jso = null;
        String url = getApiUrl(apiRoutine);
        HttpPost postMethod = new HttpPost(url);
        try {
            if (formParams != null) {
                UrlEncodedFormEntity formEntity = new UrlEncodedFormEntity(formParams, HTTP.UTF_8);
                postMethod.setEntity(formEntity);
            }
            jso = postRequest(postMethod);
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, e.toString());
        }
        return jso;
    }
    
    protected abstract JSONObject postRequest(HttpPost post) throws ConnectionException;

    protected final JSONObject getRequest(String url) throws ConnectionException {
        HttpGet get = new HttpGet(url);
        return getRequestAsObject(get);
    }

    protected final JSONObject getRequestAsObject(HttpGet get) throws ConnectionException {
        JSONObject jso = null;
        JSONTokener jst = getRequest(get);
        try {
            jso = (JSONObject) jst.nextValue();
        } catch (JSONException e) {
            Log.w(TAG, "getRequestAsObject, response=" + (jst == null ? "(null)" : jst.toString()));
            throw new ConnectionException(e.getLocalizedMessage());
        }
        return jso;
    }
    
    protected abstract JSONTokener getRequest(HttpGet get) throws ConnectionException;
    
    protected final String fixSinceId(String sinceId) {
        String out = "";
        if (!TextUtils.isEmpty(sinceId) && sinceId.length()>1) {
            // For some reason "0" results in "bad request"
            out = sinceId;
        }
        return out;
    }

    /**
     * Restrict the limit to 1 - 200
     * @param limit
     * @return
     */
    protected final int fixLimit(int limit) {
        int out = 200;
        if (limit > 0 && limit < 200) {
            out = limit;
        }
        return out;
    }

}
