/*
 * Copyright (C) 2011-2012 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.Connection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles connection to the Twitter API
 * 
 * @author yvolk
 * @author torgny.bjers
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
     * API enumerated
     */
    protected enum apiEnum {
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
        STATUSES_SHOW,
        STATUSES_UPDATE,
        
        /**
         * OAuth APIs
         */
        OAUTH_ACCESS_TOKEN,
        OAUTH_AUTHORIZE,
        OAUTH_REQUEST_TOKEN
    }
    
    /*
     * TODO: Not implemented (yet?) protected static final String
     * STATUSES_PUBLIC_TIMELINE_URL = BASE_URL_OLD + "/statuses/public_timeline" +
     * EXTENSION; protected static final String STATUSES_FOLLOWERS_URL =
     * BASE_URL_OLD + "/statuses/followers" + EXTENSION; protected static final
     * String DIRECT_MESSAGES_SENT_URL = BASE_URL_OLD + "/direct_messages/sent" +
     * EXTENSION;
     */

    protected String mSinceId = "";
    protected int mLimit = 200;

    protected String mUsername;

    protected String mPassword;

    protected Connection(MyAccount ma) {
        mUsername = ma.getDataString(MyAccount.KEY_USERNAME, "");
        mPassword = ma.getDataString(MyAccount.KEY_PASSWORD, "");
        mBaseUrl = ma.getBaseUrl();
    }

    protected String getApiUrl(apiEnum api) {
        String url = "";
        switch(api) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = mBaseUrl + "/account/rate_limit_status" + EXTENSION;
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = mBaseUrl + "/account/verify_credentials" + EXTENSION;
                break;
            case DIRECT_MESSAGES:
                url = mBaseUrl + "/direct_messages" + EXTENSION;
                break;
            case FAVORITES_CREATE_BASE:
                url = mBaseUrl + "/favorites/create/";
                break;
            case FAVORITES_DESTROY_BASE:
                url = mBaseUrl + "/favorites/destroy/";
                break;
            case POST_DIRECT_MESSAGE:
                url = mBaseUrl + "/direct_messages/new" + EXTENSION;
                break;
            case POST_REBLOG:
                url = mBaseUrl + "/statuses/retweet/";
                break;
            case STATUSES_DESTROY:
                url = mBaseUrl + "/statuses/destroy/";
                break;
            case STATUSES_HOME_TIMELINE:
                url = mBaseUrl + "/statuses/home_timeline" + EXTENSION;
                break;
            case STATUSES_MENTIONS_TIMELINE:
                url = mBaseUrl  + "/statuses/mentions" + EXTENSION;
                break;
            case STATUSES_SHOW:
                url = mBaseUrl + "/statuses/show" + EXTENSION;
                break;
            case STATUSES_UPDATE:
                url = mBaseUrl + "/statuses/update" + EXTENSION;
                break;
        }
        return url;
    }
    
    /**
     * @return Does this connection use OAuth?
     */
    public abstract boolean isOAuth();
    
    public String getUsername() {
        return mUsername;
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
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0rate_limit_status">Twitter
     *      REST API Method: account rate_limit_status</a>
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
    public void setPassword(String password) {
        if (password == null) {
            password = "";
        }
        if (password.compareTo(mPassword) != 0) {
            mPassword = password;
        }
    }
    public String getPassword() {
        return mPassword;
    }
    
    /**
     * Persist the connection data
     * @param editor
     * @return true if something changed
     */
    public boolean save(MyAccount ma) {
        boolean changed = false;
        
        if (mPassword.compareTo(ma.getDataString(MyAccount.KEY_PASSWORD, "")) != 0) {
            ma.setDataString(MyAccount.KEY_PASSWORD, mPassword);
            changed = true;
        }
        
        return changed;
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public abstract boolean getCredentialsPresent(MyAccount ma);  
    
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
     * @param screenName {@link User#USERNAME} - The screen name of the user who should receive the direct message
     * @return The sent message in the requested format if successful.
     * @throws ConnectionException
     */
    public abstract JSONObject postDirectMessage(String userId, String screenName, String message)
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
     * Get Direct messages
     * 
     * @throws ConnectionException 
     */
    public abstract JSONArray getDirectMessages(String sinceId, int limit)
            throws ConnectionException;
    
    /**
     * Get the user's replies.
     * 
     * Returns the 20 most recent @replies (status updates prefixed with @username) 
     * for the authenticating user.
     * 
     * @return JSONArray
     * @throws ConnectionException 
     */
    public abstract JSONArray getMentionsTimeline(String sinceId, int limit)
            throws ConnectionException;

    /**
     * Get the Home timeline (whatever it is...).
     * 
     * This is the equivalent of /home on the Web.
     * 
     * @return JSONArray
     * @throws ConnectionException 
     */
    public abstract JSONArray getHomeTimeline(String sinceId, int limit)
            throws ConnectionException;

    protected String getSinceId() {
        return mSinceId;
    }

    protected String setSinceId(String sinceId) {
        if (sinceId.length() > 0) {
            mSinceId = sinceId;
        }
        return mSinceId;
    }

    protected int getLimit() {
        return mLimit;
    }

    protected int setLimit(int limit) {
        if (limit > 0) {
            mLimit = limit;
            if (mLimit > 200)
                mLimit = 200;
        }
        return mLimit;
    }

}
