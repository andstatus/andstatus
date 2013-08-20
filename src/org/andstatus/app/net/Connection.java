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
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.Connection;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.apache.http.NameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Handles connection to the API of the Microblogging System (i.e. to the {@link Origin})
 * Authenticated User info (User account in the Microblogging system) and connection properties 
 * are provided in the constructor.
 * 
 * @author yvolk, torgny.bjers
 */
public abstract class Connection {

    public static final String KEY_PASSWORD = "password";
    
    /**
     * Constants independent of the system
     */
    protected static final String EXTENSION = ".json";
    
    /**
     * Connection APIs known
     */
    public enum ApiEnum {
        UNKNOWN_API,
        /** Twitter API v.1 https://dev.twitter.com/docs/api/1     */
        TWITTER1P0,
        /** Twitter API v.1.1 https://dev.twitter.com/docs/api/1.1 */
        TWITTER1P1,
        /** Status Net Twitter compatible API http://status.net/wiki/Twitter-compatible_API  */
        STATUSNET_TWITTER,
        /** https://github.com/e14n/pump.io/blob/master/API.md */
        PUMPIO
    }
    
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
        GET_FRIENDS_IDS,
        GET_USER,
        POST_DIRECT_MESSAGE,
        POST_REBLOG,
        REGISTER_CLIENT,
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
        OAUTH_REQUEST_TOKEN,
        /** For the "OAuth Dynamic Client Registration", 
         * is the link proper?: http://hdknr.github.io/docs/identity/oauth_reg.html  */
        OAUTH_REGISTER_CLIENT,
        
        /**
         * Simply ignore this API call
         */
        DUMMY
    }

    protected HttpConnection httpConnection;
    
    public Connection(OriginConnectionData connectionData) {
        httpConnection = HttpConnection.fromConnectionData(connectionData);
    }

    /**
     * @return API of this Connection
     */
    public ApiEnum getApi() {
        return httpConnection.connectionData.api;
    }

    /**
     * @return an empty string in case the API routine is not supported
     */
    protected abstract String getApiPath1(ApiRoutineEnum routine);

    /**
     * Full path of the API. Logged
     * @param routine
     * @return URL or an empty string in case the API routine is not supported
     */
    protected final String getApiPath(ApiRoutineEnum routine) {
        String path = this.getApiPath1(routine);
        if (TextUtils.isEmpty(path)) {
            Log.e(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");
        } else {
            if (MyLog.isLoggable(null, Log.VERBOSE )) {
                Log.v(this.getClass().getSimpleName(), "API '" + routine + "' Path=" + path);  
            }
        }
        return path;
    }
    
    /**
     * Use this method to check the connection's (Account's) capability before attempting to use it
     * and even before presenting corresponding action to the User. 
     * @param routine
     * @return true if supported
     */
    public boolean isApiSupported(ApiRoutineEnum routine) {
        boolean is = !TextUtils.isEmpty(this.getApiPath1(routine));
        if (!is && MyLog.isLoggable(null, Log.VERBOSE )) {
          Log.v(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");  
        }
        return (is);
    }
    
    /**
     * @return Does this connection use OAuth?
     */
    public boolean isOAuth() {
        return httpConnection.isOAuth();
    }
    
    public static String getScreenName(JSONObject credentials) {
        return credentials.optString("screen_name");
    }
    
    /**
     * Check API requests status.
     * TODO: Formalize this for different microblogging systems.
     */
    public abstract JSONObject rateLimitStatus() throws ConnectionException;

    /**
     * Do we need password to be set?
     * By default password is not needed and is ignored
     */
    public final boolean isPasswordNeeded() {
        return httpConnection.isPasswordNeeded();
    }
    
    /**
     * Set User's password if the Connection object needs it
     */
    public final void setPassword(String password) { 
        httpConnection.setPassword(password);
    }

    public final String getPassword() {
        return httpConnection.getPassword();
    }
    
    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    public boolean save(AccountDataWriter dw) {
        return httpConnection.save(dw);
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public final boolean getCredentialsPresent(AccountDataReader dr) {
        return httpConnection.getCredentialsPresent(dr);
    }
    
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
     * Returns an array of numeric IDs for every user the specified user is following.
     * @throws ConnectionException
     */
    public abstract JSONArray getFriendsIds(String userId) throws ConnectionException;
    
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
     * Get information about the specified User
     * @param userId
     * @return User object
     * @throws ConnectionException
     */
    public abstract JSONObject getUser(String userId) throws ConnectionException;
    
    protected final JSONObject postRequest(ApiRoutineEnum apiRoutine, List<NameValuePair> formParams) throws ConnectionException {
        return httpConnection.postRequest(getApiPath(apiRoutine), formParams);
    }
    
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
     */
    protected final int fixedLimit(int limit) {
        int out = 200;
        if (limit > 0 && limit < 200) {
            out = limit;
        }
        return out;
    }

    public static Connection fromConnectionData(OriginConnectionData connectionData) {
        Connection connection;
        switch (connectionData.api) {
            case PUMPIO:
                connection = ConnectionPumpio.fromConnectionDataProtected(connectionData);
                break;
            default:
                connection = ConnectionTwitter.fromConnectionDataProtected(connectionData);
        }
        return connection;
    }

    public void setAccountData(AccountDataReader dr) {
        httpConnection.setAccountData(dr);
    }

    public void clearAuthInformation() {
        httpConnection.clearAuthInformation();
    }

    public void setUserTokenWithSecret(String token, String secret) {
        httpConnection.setUserTokenWithSecret(token, secret);
    }

    public OAuthConsumerAndProvider getOAuthConsumerAndProvider() {
        OAuthConsumerAndProvider oa = null;
        if (isOAuth()) {
            oa = (OAuthConsumerAndProvider) httpConnection;
        }
        return oa;
    }

    public void registerClient() {}
}
