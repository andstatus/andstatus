/*
 * Copyright (C) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.data.MyDatabase.User;
import org.andstatus.app.net.Connection;
import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.net.MbTimelineItem.ItemType;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Handles connection to the API of the Microblogging System (i.e. to the "Origin")
 * Authenticated User info (User account in the Microblogging system) and connection properties 
 * are provided in the constructor.
 * 
 * @author yvolk@yurivolkov.com, torgny.bjers
 */
public abstract class Connection {

    static final String  APPLICATION_ID = "http://andstatus.org/andstatus";
    public static final String KEY_PASSWORD = "password";
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
        /** GNU social (former: Status Net) Twitter compatible API http://status.net/wiki/Twitter-compatible_API  */
        GNUSOCIAL_TWITTER,
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
        CREATE_FAVORITE,
        DESTROY_FAVORITE,
        FOLLOW_USER,
        GET_CONFIG,
        GET_FRIENDS, // List of users
        GET_FRIENDS_IDS, // List of Users' IDs
        GET_USER,
        POST_MESSAGE,
        POST_WITH_MEDIA,
        POST_DIRECT_MESSAGE,
        POST_REBLOG,
        DESTROY_MESSAGE,
        REGISTER_CLIENT,
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
        STATUSES_USER_TIMELINE(true),
        PUBLIC_TIMELINE(true),
        SEARCH_MESSAGES(true),

        GET_MESSAGE,
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
        DUMMY;
        
        private final boolean msgPublic;

        private ApiRoutineEnum() {
            this(false);
        }
        
        private ApiRoutineEnum(boolean msgPublic) {
            this.msgPublic = msgPublic;
        }

        public boolean isMsgPublic() {
            return msgPublic;
        }
    }

    protected HttpConnection http;
    protected OriginConnectionData data;
    
    protected Connection() {
    }

    /**
     * @return an empty string in case the API routine is not supported
     */
    protected abstract String getApiPath1(ApiRoutineEnum routine);

    /**
     * Full path of the API. Logged
     * @return URL or throws a ConnectionException in case the API routine is not supported
     */
    protected final String getApiPath(ApiRoutineEnum routine) throws ConnectionException {
        String path = this.getApiPath1(routine);
        if (TextUtils.isEmpty(path)) {
            String detailMessage = "The API is not supported: '" + routine + "'";
            MyLog.e(this.getClass().getSimpleName(), detailMessage);
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, this.getClass().getSimpleName() + ": " + detailMessage);
        } else {
            if (MyLog.isLoggable(null, MyLog.VERBOSE )) {
                MyLog.v(this.getClass().getSimpleName(), "API '" + routine + "' Path=" + path);  
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
        if (!is && MyLog.isLoggable(null, MyLog.VERBOSE )) {
          MyLog.v(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");  
        }
        return is;
    }
    
    /**
     * Check API requests status.
     */
    public abstract MbRateLimitStatus rateLimitStatus() throws ConnectionException;

    /**
     * Do we need password to be set?
     * By default password is not needed and is ignored
     */
    public final boolean isPasswordNeeded() {
        return http.isPasswordNeeded();
    }
    
    /**
     * Set User's password if the Connection object needs it
     */
    public final void setPassword(String password) { 
        http.setPassword(password);
    }

    public final String getPassword() {
        return http.getPassword();
    }
    
    /**
     * Persist the connection data
     * @return true if something changed (so it needs to be rewritten to persistence...)
     */
    public boolean save(AccountDataWriter dw) {
        return http.save(dw);
    }
    

    public boolean save(JSONObject jso) throws JSONException {
        return http.save(jso);
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public final boolean getCredentialsPresent() {
        return http.getCredentialsPresent();
    }
    
    /**
     * Verify the user's credentials.
     */
    public abstract MbUser verifyCredentials() throws ConnectionException;

    public abstract MbMessage destroyFavorite(String statusId) throws ConnectionException;

    /**
     * Favorites the status specified in the ID parameter as the authenticating user.
     * Returns the favorite status when successful.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-favorites%C2%A0create">Twitter
     *      REST API Method: favorites create</a>
     */
    public abstract MbMessage createFavorite(String statusId) throws ConnectionException;

    /**
     * Destroys the status specified by the required ID parameter.
     * The authenticating user must be the author of the specified status.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-statuses%C2%A0destroy">Twitter
     *      REST API Method: statuses/destroy</a>
     */
    public abstract boolean destroyStatus(String statusId) throws ConnectionException;

    /**
     * Returns a list of users the specified user is following.
     */
    public List<MbUser> getUsersFollowedBy(String userId) throws ConnectionException {
        throw ConnectionException.fromStatusCodeAndHost(StatusCode.UNSUPPORTED_API, null, "getUsersFollowedBy for userOid=" + userId);
    }
    
    /**
     * Returns a list of IDs for every user the specified user is following.
     */
    public List<String> getIdsOfUsersFollowedBy(String userId) throws ConnectionException {
        throw ConnectionException.fromStatusCodeAndHost(StatusCode.UNSUPPORTED_API, null, "getIdsOfUsersFollowedBy for userOid=" + userId);
    }
    
    /**
     * Returns a single status, specified by the id parameter below.
     * The status's author will be returned inline.
     */
    public final MbMessage getMessage(String statusId) throws ConnectionException {
        MbMessage msg = getMessage1(statusId);
        if (msg != null) {
            msg.setPublic(true);
        }
        return msg;
    }

    /** See {@link #getMessage(String)} */
    protected abstract MbMessage getMessage1(String statusId) throws ConnectionException;
    
    /**
     * Update user status by posting to the Twitter REST API.
     * 
     * Updates the authenticating user's status, also known as tweeting/blogging.
     * To upload an image to accompany the tweet, use POST statuses/update_with_media.
     * 
     * @param message       Text of the "status"
     * @param inReplyToId   The ID of an existing status that the update is in reply to.
     * @param mediaUri 
     * @throws ConnectionException 
     *
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/statuses/update">Twitter
     *      POST statuses/update</a>
     */
    public abstract MbMessage updateStatus(String message, String inReplyToId, Uri mediaUri)
            throws ConnectionException;

    /**
     * Post Direct Message
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/direct_messages/new">POST direct_messages/new</a>
     * 
     * @param message
     * @param userId {@link User#USER_OID} - The ID of the user who should receive the direct message
     * @return The sent message if successful (empty message if not)
     * @throws ConnectionException
     */
    public abstract MbMessage postDirectMessage(String message, String userId)
            throws ConnectionException;

    /**
     * Post reblog ("Retweet")
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/statuses/retweet/%3Aid">POST statuses/retweet/:id</a>
     * 
     * @param rebloggedId id of the Reblogged message
     * @throws ConnectionException
     */
    public abstract MbMessage postReblog(String rebloggedId)
            throws ConnectionException;

    /**
     * Universal method for several Timeline Types...
     * @param userId For the {@link ApiRoutineEnum#STATUSES_USER_TIMELINE}, null for the other timelines
     */
    public abstract List<MbTimelineItem> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition sinceId, int limit, String userId)
            throws ConnectionException;

    public abstract List<MbTimelineItem> search(String searchQuery, int limit)
            throws ConnectionException;
    
    /**
     * Allows this User to follow the user specified in the userId parameter
     * Allows this User to stop following the user specified in the userId parameter
     * @param userId
     * @param follow true - Follow, false - Stop following
     * @return User object with 'following' flag set/reset
     * @throws ConnectionException
     */
    public abstract MbUser followUser(String userId, Boolean follow) throws ConnectionException;

    /**
     * Get information about the specified User
     * @param userId
     * @return User object
     * @throws ConnectionException
     */
    public abstract MbUser getUser(String userId) throws ConnectionException;
    
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
     * @param apiRoutine 
     */
    public int fixedDownloadLimitForApiRoutine(int limit, ApiRoutineEnum apiRoutine) {
        int out = 200;
        if (limit > 0 && limit < 200) {
            out = limit;
        }
        return out;
    }

    public final void setAccountData(OriginConnectionData connectionData) throws InstantiationException, IllegalAccessException {
        this.data = connectionData;
        http = connectionData.newHttpConnection();
        http.setConnectionData(HttpConnectionData.fromConnectionData(connectionData));
    }

    public void clearAuthInformation() {
        http.clearAuthInformation();
    }

    public void setUserTokenWithSecret(String token, String secret) {
        http.setUserTokenWithSecret(token, secret);
    }

    public OAuthConsumerAndProvider getOAuthConsumerAndProvider() {
        OAuthConsumerAndProvider oa = null;
        if (data.isOAuth()) {
            oa = (OAuthConsumerAndProvider) http;
        }
        return oa;
    }

    public void registerClientForAccount() throws ConnectionException {
        http.registerClient(getApiPath(ApiRoutineEnum.REGISTER_CLIENT));
    }

    public void clearClientKeys() {
        http.clearClientKeys();
    }

    public boolean areOAuthClientKeysPresent() {
        return http.data.areOAuthClientKeysPresent();
    }

    public void enrichConnectionData(OriginConnectionData connectionData2) {
        // Nothing to do
    }

    public boolean userObjectHasMessage() {
        return false;
    }

    public MbConfig getConfig() throws ConnectionException {
        return MbConfig.getEmpty();
    }
    
    /**
     * @return Unix time. Returns 0 in a case of an error or absence of such a field
     */
    public long dateFromJson(JSONObject jso, String fieldName) {
        long date = 0;
        if (jso != null && jso.has(fieldName)) {
            String updated = jso.optString(fieldName);
            if (updated.length() > 0) {
                date = parseDate(updated);
            }
        }
        return date;
    }
    
    /**
     * @return Unix time. Returns 0 in a case of an error
     */
    public long parseDate(String stringDate) {
        if(TextUtils.isEmpty(stringDate)) {
            return 0;
        }
        long unixDate = 0;
        String formats[] = {"", "E MMM d HH:mm:ss Z yyyy", "E, d MMM yyyy HH:mm:ss Z"};
        for (String format : formats) {
            if (TextUtils.isEmpty(format)) {
                try {
                    unixDate = Date.parse(stringDate);
                } catch (IllegalArgumentException e) {
                    MyLog.ignored(this, e);
                }
            } else {
                DateFormat dateFormat1 = new SimpleDateFormat(format, Locale.ENGLISH);
                try {
                    unixDate = dateFormat1.parse(stringDate).getTime();
                } catch (ParseException e) {
                    MyLog.ignored(this, e);
                }
            }
            if (unixDate != 0) {
                break;
            }
        }
        if (unixDate == 0) {
            MyLog.d(this, "Failed to parse the date: '" + stringDate +"'");
        }
        return unixDate;
    }

    protected void setMessagesPublic(List<MbTimelineItem> timeline) {
        for (MbTimelineItem item : timeline) {
            if (item.getType() == ItemType.MESSAGE) {
                item.mbMessage.setPublic(true);
            }
        }
    }

    public JSONArray getRequestArrayInObject(String path, String arrayName) throws ConnectionException {
        String method = "getRequestArrayInObject";
        JSONArray jArr = null;
        JSONObject jso = http.getRequest(path);
        if (jso != null) {
            try {
                jArr = jso.getJSONArray(arrayName);
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, e, jso, method + ", arrayName=" + arrayName);
            }
        }
        return jArr;
    }
}
