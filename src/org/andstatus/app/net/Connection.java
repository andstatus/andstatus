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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles connection to the API of the Microblogging System (i.e. to the {@link Origin})
 * 
 * @author yvolk, torgny.bjers
 */
public abstract class Connection {
    
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
        DIRECT_MESSAGES,
        FAVORITES_CREATE_BASE,
        FAVORITES_DESTROY_BASE,        
        POST_DIRECT_MESSAGE,
        POST_REBLOG,
        STATUSES_DESTROY,
        STATUSES_HOME_TIMELINE,
        STATUSES_MENTIONS_TIMELINE,
        STATUSES_USER_TIMELINE,
        STATUSES_SHOW,
        STATUSES_UPDATE,
        
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
     * Not logged
     * @param routine
     * @return URL or an empty string in case the API routine is not supported
     */
    protected String getApiUrl1(ApiRoutineEnum routine) {
        String url = "";
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = getBaseUrl() + "/account/rate_limit_status" + EXTENSION;
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = getBaseUrl() + "/account/verify_credentials" + EXTENSION;
                break;
            case DIRECT_MESSAGES:
                url = getBaseUrl() + "/direct_messages" + EXTENSION;
                break;
            case FAVORITES_CREATE_BASE:
                url = getBaseUrl() + "/favorites/create/";
                break;
            case FAVORITES_DESTROY_BASE:
                url = getBaseUrl() + "/favorites/destroy/";
                break;
            case POST_DIRECT_MESSAGE:
                url = getBaseUrl() + "/direct_messages/new" + EXTENSION;
                break;
            case POST_REBLOG:
                url = getBaseUrl() + "/statuses/retweet/";
                break;
            case STATUSES_DESTROY:
                url = getBaseUrl() + "/statuses/destroy/";
                break;
            case STATUSES_HOME_TIMELINE:
                url = getBaseUrl() + "/statuses/home_timeline" + EXTENSION;
                break;
            case STATUSES_MENTIONS_TIMELINE:
                url = getBaseUrl()  + "/statuses/mentions" + EXTENSION;
                break;
            case STATUSES_USER_TIMELINE:
                url = getBaseUrl() + "/statuses/user_timeline" + EXTENSION;
                break;
            case STATUSES_SHOW:
                url = getBaseUrl() + "/statuses/show" + EXTENSION;
                break;
            case STATUSES_UPDATE:
                url = getBaseUrl() + "/statuses/update" + EXTENSION;
                break;
        }
        return url;
    }

    /**
     * If the url is empty, log error 
     * Otherwise Log verbosely  
     * @param url
     */
    protected String getApiUrl(ApiRoutineEnum routine) {
        String url = this.getApiUrl1(routine);
        if (TextUtils.isEmpty(url)) {
            Log.e(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");
        } else if (MyLog.isLoggable(null, Log.VERBOSE )) {
            Log.v(this.getClass().getSimpleName(), "API '" + routine + "' URL=" + url);  
        }
        return url;
    }
    
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
    
    // Get stuff from the two types of Twitter JSONObject we deal with: credentials and status 
    private static String getCurrentTweet(JSONObject status) {
        return status.optString("text");
    }
    
    public static String getScreenName(JSONObject credentials) {
        return credentials.optString("screen_name");
    }

    public static String getLastTweet(JSONObject credentials) {
        try {
            JSONObject status = credentials.getJSONObject("status");
            return getCurrentTweet(status);
        } catch (JSONException e) {
            e.printStackTrace();
            return "";
        }
    }

    public abstract void clearAuthInformation();
    
    /**
     * Check API requests status.
     * 
     * Returns the remaining number of API requests available to the requesting 
     * user before the API limit is reached for the current hour. Calls to 
     * rate_limit_status do not count against the rate limit.  If authentication 
     * credentials are provided, the rate limit status for the authenticating 
     * user is returned.  Otherwise, the rate limit status for the requester's 
     * IP address is returned.
     * @see <a
           href="https://dev.twitter.com/docs/api/1/get/account/rate_limit_status">GET 
           account/rate_limit_status</a>
     * 
     * @return JSONObject
     * @throws ConnectionException
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
     * @return true if something changed
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
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/get/statuses/show/%3Aid">Twitter
     *      REST API Method: statuses/destroy</a>
     * 
     * @throws ConnectionException
     */
    public abstract JSONObject getStatus(String statusId) throws ConnectionException;
    
    /**
     * Update user status by posting to the Twitter REST API.
     * 
     * Updates the authenticating user's status, also known as tweeting.
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
     * @param url URL predefined for this timeline
     * @param sinceId
     * @param limit
     * @param userId For the {@link ApiRoutineEnum#STATUSES_USER_TIMELINE}
     * @return
     * @throws ConnectionException
     */
    protected abstract JSONArray getTimeline(String url, String sinceId, int limit, String userId)
            throws ConnectionException;
    
    
    /**
     * Returns the 20 most recent direct messages sent to the authenticating user
     *
     * TODO: GET direct_messages/sent: Returns the 20 most recent direct messages sent by the authenticating user. 
     * See https://dev.twitter.com/docs/api/1/get/direct_messages/sent
     */
    public JSONArray getDirectMessages(String sinceId, int limit) throws ConnectionException {
        String url = this.getApiUrl(ApiRoutineEnum.DIRECT_MESSAGES);
        return this.getTimeline(url, sinceId, limit, "");
    }
    
    /**
     * Get the user's replies.
     * 
     * Returns the 20 most recent @replies (status updates prefixed with @username) 
     * for the authenticating user.
     * 
     * @return JSONArray
     * @throws ConnectionException 
     */
    public JSONArray getMentionsTimeline(String sinceId, int limit) throws ConnectionException {
        String url = this.getApiUrl(ApiRoutineEnum.STATUSES_MENTIONS_TIMELINE);
        return this.getTimeline(url, sinceId, limit, "");
    }

    /**
     * Get the Home timeline (whatever it is...).
     * 
     * This is the equivalent of /home on the Web.
     * 
     * @return JSONArray
     * @throws ConnectionException 
     */
    public JSONArray getHomeTimeline(String sinceId, int limit) throws ConnectionException {
        String url = this.getApiUrl(ApiRoutineEnum.STATUSES_HOME_TIMELINE);
        return this.getTimeline(url, sinceId, limit, "");
    }

    /**
     * Get the User timeline for the user with the selectedUserId. We use credentials of Account which may be
     * not the same user. 
     * @param userId  The ID of the user for whom to return results for
     * @param sinceId  Returns results with an ID greater than (that is, more recent than) the specified MessageID
     * @param limit
     * @return
     * @throws ConnectionException
     */
    public JSONArray getUserTimeline(String userId, String sinceId, int limit)
            throws ConnectionException {
        String url = getApiUrl(ApiRoutineEnum.STATUSES_USER_TIMELINE);
        return getTimeline(url, sinceId, limit, userId);
    }

    protected String fixSinceId(String sinceId) {
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
    protected int fixLimit(int limit) {
        int out = 200;
        if (limit > 0 && limit < 200) {
            out = limit;
        }
        return out;
    }

}
