/*
 * Copyright (C) 2011-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

import android.net.Uri;
import android.support.annotation.NonNull;

import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.http.OAuthService;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

/**
 * Handles connection to the API of the Social Network (i.e. to the "Origin")
 * Authenticated User info (User account in the Social Network) and connection properties
 * are provided in the constructor.
 * 
 * @author yvolk@yurivolkov.com
 */
public abstract class Connection {
    public static final String KEY_PASSWORD = "password";

    /**
     * API routines (functions, "resources" in terms of Twitter)  enumerated
     */
    public enum ApiRoutineEnum {
        ACCOUNT_RATE_LIMIT_STATUS,
        ACCOUNT_VERIFY_CREDENTIALS,
        /** Returns most recent notes privately sent to the authenticating user */
        PRIVATE_NOTES(true),
        LIKE,
        UNDO_LIKE,
        FOLLOW,
        GET_CONFIG,
        GET_CONVERSATION,
        /** List of actors */
        GET_FRIENDS, 
        /** List of Actors' IDs */
        GET_FRIENDS_IDS,
        GET_FOLLOWERS,
        GET_FOLLOWERS_IDS,
        GET_OPEN_INSTANCES,
        GET_ACTOR,
        UPDATE_NOTE,
        UPDATE_NOTE_WITH_MEDIA,
        UPDATE_PRIVATE_NOTE,
        ANNOUNCE,
        UNDO_ANNOUNCE,
        DELETE_NOTE,
        REGISTER_CLIENT,
        /**
         * Get the Home timeline (whatever it is...).
         * This is the equivalent of /home on the Web.
         */
        HOME_TIMELINE,
        /** Notifications in a separate API */
        NOTIFICATIONS_TIMELINE,
        /**
         * Get the Actor timeline for an actor with the selectedActorId.
         * We use credentials of our Account, which may be not the same the actor.
         */
        ACTOR_TIMELINE,
        PUBLIC_TIMELINE,
        TAG_TIMELINE,
        LIKED_TIMELINE,
        SEARCH_NOTES,
        SEARCH_ACTORS,

        GET_NOTE,
        UNDO_FOLLOW,
        
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
        
        private final boolean isNotePrivate;

        ApiRoutineEnum() {
            this(false);
        }
        
        ApiRoutineEnum(boolean isNotePrivate) {
            this.isNotePrivate = isNotePrivate;
        }

        public boolean isNotePrivate() {
            return isNotePrivate;
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
        Uri fromActor = getPathFromActor(data.getAccountActor(), routine);
        String path = UriUtils.isEmpty(fromActor) ? this.getApiPath1(routine) : fromActor.toString();
        if (StringUtils.isEmpty(path)) {
            String detailMessage = "The API is not supported: '" + routine + "'";
            MyLog.e(this.getClass().getSimpleName(), detailMessage);
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, this.getClass().getSimpleName() + ": " + detailMessage);
        } else {
            MyLog.v(this.getClass().getSimpleName(), () -> "API '" + routine + "' Path=" + path +
                    (UriUtils.isEmpty(fromActor) ? "" : " from Actor"));
        }
        return path;
    }

    public static Uri getPathFromActor(Actor actor, ApiRoutineEnum routine) {
        switch (routine) {
            case GET_FOLLOWERS:
            case GET_FOLLOWERS_IDS:
                return actor.endpoints.getFirst(ActorEndpointType.API_FOLLOWERS);
            case GET_FRIENDS:
            case GET_FRIENDS_IDS:
                return actor.endpoints.getFirst(ActorEndpointType.API_FOLLOWING);
            case GET_ACTOR:
                return actor.endpoints.getFirst(ActorEndpointType.API_PROFILE);
            case HOME_TIMELINE:
                return actor.endpoints.getFirst(ActorEndpointType.API_INBOX);
            case LIKED_TIMELINE:
                return actor.endpoints.getFirst(ActorEndpointType.API_LIKED);
            case LIKE:
            case UNDO_LIKE:
            case FOLLOW:
            case UPDATE_PRIVATE_NOTE:
            case ANNOUNCE:
            case DELETE_NOTE:
            case UPDATE_NOTE:
            case ACTOR_TIMELINE:
                return actor.endpoints.getFirst(ActorEndpointType.API_OUTBOX);
            default:
                return Uri.EMPTY;
        }
    }

    /**
     * Use this method to check the connection's (Account's) capability before attempting to use it
     * and even before presenting corresponding action to the User.
     * @return true if supported
     */
    public boolean isApiSupported(ApiRoutineEnum routine) {
        if (routine == null || routine == ApiRoutineEnum.DUMMY) {
            return true;
        }
        boolean is = !StringUtils.isEmpty(this.getApiPath1(routine));
        if (!is && MyLog.isVerboseEnabled()) {
          MyLog.v(this.getClass().getSimpleName(), "The API routine '" + routine + "' is not supported");  
        }
        return is;
    }
    
    /**
     * Check API requests status.
     */
    public abstract RateLimitStatus rateLimitStatus() throws ConnectionException;

    /**
     * Do we need password to be set?
     * By default password is not needed and is ignored
     */
    public final boolean isPasswordNeeded() {
        return http.isPasswordNeeded();
    }
    
    /**
     * Set Account's password if the Connection object needs it
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
    
    @NonNull
    public abstract Actor verifyCredentials() throws ConnectionException;

    public abstract AActivity undoLike(String noteOid) throws ConnectionException;

    /**
     * Favorites the status specified in the ID parameter as the authenticating account.
     * Returns the favorite status when successful.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-favorites%C2%A0create">Twitter
     *      REST API Method: favorites create</a>
     */
    public abstract AActivity like(String noteOid) throws ConnectionException;

    public boolean undoAnnounce(String noteOid) throws ConnectionException {
        return deleteNote(noteOid);
    }

    /**
     * Destroys the status specified by the required ID parameter.
     * The authenticating account's actor must be the author of the specified status.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-statuses%C2%A0destroy">Twitter
     *      REST API Method: statuses/destroy</a>
     */
    public abstract boolean deleteNote(String noteOid) throws ConnectionException;

    /**
     * Returns a list of actors the specified actor is following.
     */
    public List<Actor> getFriends(String actorOid) throws ConnectionException {
        throw ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API, "getFriends for actorOid=" + actorOid);
    }
    
    /**
     * Returns a list of IDs for every actor the specified actor is following.
     */
    public List<String> getFriendsIds(String actorOid) throws ConnectionException {
        throw ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API, "getFriendsIds for actorOid=" + actorOid);
    }

    @NonNull
    public List<String> getFollowersIds(String actorOid) throws ConnectionException {
        throw ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API, "getFollowersIds actorOid=" + actorOid);
    }

    public List<Actor> getFollowers(String actorOid) throws ConnectionException {
        throw ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API, "getFollowers actorOid=" + actorOid);
    }

    /**
     * Requests a single note (status), specified by the id parameter.
     * More than one activity may be returned (as replies) to reflect Favoriting and Reblogging of the "status"
     */
    @NonNull
    public final AActivity getNote(String noteOid) throws ConnectionException {
        return getNote1(noteOid);
    }

    /** See {@link #getNote(String)} */
    protected abstract AActivity getNote1(String noteOid) throws ConnectionException;

    public List<AActivity> getConversation(String conversationOid) throws ConnectionException {
        throw ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API, "getConversation oid=" + conversationOid);
    }

    /**
     * Update Actor's status by posting to the Server's API
     * Updates the authenticating actor's status, also known as tweeting/blogging.
     *
     *
     * @param name Name (Title) of the Note
     * @param content      Text of the note
     * @param noteOid      id is not empty, if we are updating existing "status"
     * @param audience
     * @param inReplyToOid   The ID of an existing Note that the update is in reply to.
     * @param mediaUri
     *
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/statuses/update">Twitter
     *      POST statuses/update</a>
     */
    public abstract AActivity updateNote(String name, String content, String noteOid, Audience audience,
                                         String inReplyToOid, Uri mediaUri)
            throws ConnectionException;

    /**
     * Post Reblog ("retweet")
     * @see <a href="https://dev.twitter.com/docs/api/1/post/statuses/retweet/%3Aid">POST statuses/retweet/:id</a>
     * 
     * @param rebloggedNoteOid id of the Reblogged note
     */
    public abstract AActivity announce(String rebloggedNoteOid)
            throws ConnectionException;

    /**
     * Universal method for several Timeline Types...
     * @param actorOid For the {@link ApiRoutineEnum#ACTOR_TIMELINE}, null for the other timelines
     */
    @NonNull
    public abstract List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                                TimelinePosition oldestPosition, int limit, String actorOid)
            throws ConnectionException;

    @NonNull
    public List<AActivity> searchNotes(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery) throws ConnectionException {
        return new ArrayList<>();
    }

    @NonNull
    public List<Actor> searchActors(int limit, String searchQuery) throws ConnectionException {
        return new ArrayList<>();
    }

    /**
     * Allows this Account to follow (or stop following) an actor specified in the actorOid parameter
     * @param follow true - Follow, false - Stop following
     */
    public abstract AActivity follow(String actorOid, Boolean follow) throws ConnectionException;

    /** Get information about the specified Actor */
    public Actor getActor(String actorOid, String username) throws ConnectionException {
        long time = MyLog.uniqueCurrentTimeMS();
        Actor actor = getActor2(actorOid, username);
        if (!actor.isPartiallyDefined()) {
            if (actor.getUpdatedDate() <= SOME_TIME_AGO) actor.setUpdatedDate(time);
        }
        return actor;
    }

    protected abstract Actor getActor2(String actorOid, String username) throws ConnectionException;
    
    protected final String fixSinceId(String sinceId) {
        String out = "";
        if (!StringUtils.isEmpty(sinceId) && sinceId.length()>1) {
            // For some reason "0" results in "bad request"
            out = sinceId;
        }
        return out;
    }

    @NonNull
    protected String strFixedDownloadLimit(int limit, ApiRoutineEnum apiRoutine) {
        return String.valueOf(fixedDownloadLimit(limit, apiRoutine));
    }

    /**
     * Restrict the limit to 1 - 400
     */
    public int fixedDownloadLimit(int limit, ApiRoutineEnum apiRoutine) {
        int out = 400;
        if (limit > 0 && limit < 400) {
            out = limit;
        }
        return out;
    }

    public final void setAccountData(OriginConnectionData connectionData) throws ConnectionException {
        this.data = connectionData;
        http = connectionData.newHttpConnection("");
        http.setConnectionData(HttpConnectionData.fromConnectionData(connectionData));
    }

    public void clearAuthInformation() {
        http.clearAuthInformation();
    }

    public void setUserTokenWithSecret(String token, String secret) {
        http.setUserTokenWithSecret(token, secret);
    }

    public OAuthService getOAuthService() {
        if (data.isOAuth() && OAuthService.class.isAssignableFrom(http.getClass())) {
            return (OAuthService) http;
        }
        return null;
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

    public final JSONObject postRequest(String apiPath, JSONObject formParams) throws ConnectionException {
        return http.postRequest(apiPath, formParams);
    }

    public OriginConfig getConfig() throws ConnectionException {
        return OriginConfig.getEmpty();
    }

    public List<Server> getOpenInstances() throws ConnectionException {
        throw ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API, MyLog.objToTag(this));
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
        if(StringUtils.isEmpty(stringDate)) {
            return 0;
        }
        long unixDate = 0;
        String[] formats = {"", "E MMM d HH:mm:ss Z yyyy", "E, d MMM yyyy HH:mm:ss Z"};
        for (String format : formats) {
            if (StringUtils.isEmpty(format)) {
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

    /**
     * Simple solution based on:
     * http://stackoverflow.com/questions/2201925/converting-iso8601-compliant-string-to-java-util-date
     * @return Unix time. Returns 0 in a case of an error
     */
    protected long parseIso8601Date(String stringDate) {
        long unixDate = 0;
        if(stringDate != null) {
            String datePrepared;
            if (stringDate.lastIndexOf('Z') == stringDate.length()-1) {
                datePrepared = stringDate.substring(0, stringDate.length()-1) + "+0000";
            } else {
                datePrepared = stringDate.replaceAll("\\+0([0-9]):00", "+0$100");
            }
            String formatString = stringDate.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSSZ" : "yyyy-MM-dd'T'HH:mm:ssZ";
            DateFormat iso8601DateFormatSec = new SimpleDateFormat(formatString, Locale.GERMANY);
            try {
                unixDate = iso8601DateFormatSec.parse(datePrepared).getTime();
            } catch (ParseException e) {
                MyLog.e(this, "Failed to parse the date: '" + stringDate +"' using '" + formatString + "'", e);
            }
        }
        return unixDate;
    }

    JSONArray getRequestArrayInObject(String path, String arrayName) throws ConnectionException {
        String method = "getRequestArrayInObject";
        JSONArray jArr = null;
        JSONObject jso = http.getRequest(path);
        if (jso != null) {
            try {
                jArr = jso.getJSONArray(arrayName);
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, method + ", arrayName=" + arrayName, e, jso);
            }
        }
        return jArr;
    }

    public void downloadFile(String url, File file) throws ConnectionException {
        http.downloadFile(url, file);
    }

    public HttpConnection getHttp() {
        return http;
    }

    protected String prependWithBasicPath(String url) {
        if (!StringUtils.isEmpty(url) && !url.contains("://")) {
            url = http.data.getAccountName().getOrigin().getOriginType().getBasicPath() + "/" + url;
        }
        return url;
    }
}
