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

import androidx.annotation.NonNull;

import org.andstatus.app.account.AccountConnectionData;
import org.andstatus.app.account.AccountDataWriter;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.OAuthService;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.service.ConnectionRequired;
import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.vavr.control.Try;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;
import static org.andstatus.app.util.UriUtils.isRealOid;

/**
 * Handles connection to the API of the Social Network (i.e. to the "Origin")
 * Authenticated User info (User account in the Social Network) and connection properties
 * are provided in the constructor.
 * 
 * @author yvolk@yurivolkov.com
 */
public abstract class Connection implements IsEmpty {
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
        UPDATE_PRIVATE_NOTE,
        UPLOAD_MEDIA,
        ANNOUNCE,
        UNDO_ANNOUNCE,
        DELETE_NOTE,
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
        DUMMY_API;
        
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
    protected AccountConnectionData data;

    public static Connection fromMyAccount(@NonNull MyAccount myAccount, TriState isOAuth) {
        if (!myAccount.getOrigin().isValid()) return ConnectionEmpty.EMPTY;

        AccountConnectionData connectionData = AccountConnectionData.fromMyAccount(myAccount, isOAuth);
        try {
            return myAccount.getOrigin().getOriginType().getConnectionClass().newInstance()
                    .setAccountConnectionData(connectionData);
        } catch (InstantiationException | IllegalAccessException e) {
            MyLog.e("Failed to instantiate connection for " + myAccount, e);
            return ConnectionEmpty.EMPTY;
        }
    }

    public static Connection fromOrigin(@NonNull Origin origin, TriState isOAuth) {
        if (!origin.isValid()) return ConnectionEmpty.EMPTY;

        AccountConnectionData connectionData = AccountConnectionData.fromOrigin(origin, isOAuth);
        try {
            return origin.getOriginType().getConnectionClass().newInstance()
                    .setAccountConnectionData(connectionData);
        } catch (InstantiationException | IllegalAccessException e) {
            MyLog.e("Failed to instantiate connection for " + origin, e);
            return ConnectionEmpty.EMPTY;
        }
    }

    protected Connection() {
    }

    /**
     * @return an empty string in case the API routine is not supported
     */
    @NonNull
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        return "";
    }

    /**
     * Full path of the API. Logged
     * Use {@link #tryApiPath(Actor, ApiRoutineEnum)}
     * @return URL or throws a ConnectionException in case the API routine is not supported
     */
    @NonNull
    protected final Try<Uri> getApiPath(ApiRoutineEnum routine) {
        return tryApiPath(data.getAccountActor(), routine);
    }

    /**
     * Full path of the API. Logged
     */
    @NonNull
    public final Try<Uri> tryApiPath(Actor endpointActor, ApiRoutineEnum routine) {
        return TryUtils.fromOptional(getApiUri(endpointActor, routine), () ->
                new ConnectionException(StatusCode.UNSUPPORTED_API, this.getClass().getSimpleName() +
                    ": " + ("The API is not supported: '" + routine + "'")))
            .onSuccess(uri -> MyLog.v(this.getClass().getSimpleName(), () -> "API '" + routine + "' URI=" + uri));
    }

    /**
     * Use this method to check the connection's (Account's) capability before attempting to use it
     * and even before presenting corresponding action to the User.
     * @return true if supported
     */
    public boolean hasApiEndpoint(ApiRoutineEnum routine) {
        return getApiUri(data.getAccountActor(), routine).isPresent();
    }

    private Optional<Uri> getApiUri(Actor endpointActor, ApiRoutineEnum routine) {
        if (routine == null || routine == ApiRoutineEnum.DUMMY_API) {
            return Optional.empty();
        }
        Optional<Uri> fromActor = endpointActor.getEndpoint(ActorEndpointType.from(routine));
        return fromActor.isPresent()
                ? fromActor
                : Optional.of(getApiPathFromOrigin(routine)).flatMap(this::pathToUri);
    }

    Optional<Uri> pathToUri(String path) {
        return Optional.ofNullable(path)
                .filter(StringUtil::nonEmpty)
                .flatMap(path2 -> UrlUtils.pathToUrl(data.getOriginUrl(), path2))
                .map(URL::toExternalForm)
                .flatMap(UriUtils::toDownloadableOptional);
    }

    /**
     * Check API requests status.
     */
    public Try<RateLimitStatus> rateLimitStatus() {
        return Try.success(new RateLimitStatus());
    }

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
    public boolean saveTo(AccountDataWriter dw) {
        return http.saveTo(dw);
    }
    
    /**
     * Do we have enough credentials to verify them?
     * @return true == yes
     */
    public final boolean getCredentialsPresent() {
        return http.getCredentialsPresent();
    }
    
    @NonNull
    public abstract Try<Actor> verifyCredentials(Optional<Uri> whoAmI);

    public abstract Try<AActivity> undoLike(String noteOid);

    /**
     * Favorites the status specified in the ID parameter as the authenticating account.
     * Returns the favorite status when successful.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-favorites%C2%A0create">Twitter
     *      REST API Method: favorites create</a>
     */
    public abstract Try<AActivity> like(String noteOid);

    public Try<Boolean> undoAnnounce(String noteOid) {
        return deleteNote(noteOid);
    }

    /**
     * Destroys the status specified by the required ID parameter.
     * The authenticating account's actor must be the author of the specified status.
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-statuses%C2%A0destroy">Twitter
     *      REST API Method: statuses/destroy</a>
     */
    public abstract Try<Boolean> deleteNote(String noteOid);

    public Try<List<Actor>> getFriendsOrFollowers(Connection.ApiRoutineEnum routineEnum, Actor actor) {
        return routineEnum == ApiRoutineEnum.GET_FRIENDS
                ? getFriends(actor)
                : getFollowers(actor);
    }

    public Try<List<String>> getFriendsOrFollowersIds(Connection.ApiRoutineEnum routineEnum, String actorOid) {
        return routineEnum == ApiRoutineEnum.GET_FRIENDS_IDS
                ? getFriendsIds(actorOid)
                : getFollowersIds(actorOid);
    }

    /**
     * Returns a list of actors the specified actor is following.
     */
    public Try<List<Actor>> getFriends(Actor actor) {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFriends for actor:" + actor.getUniqueNameWithOrigin()));
    }
    
    /**
     * Returns a list of IDs for every actor the specified actor is following.
     */
    public Try<List<String>> getFriendsIds(String actorOid) {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFriendsIds for actorOid=" + actorOid));
    }

    @NonNull
    public Try<List<String>> getFollowersIds(String actorOid) {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFollowersIds for actorOid=" + actorOid));
    }

    public Try<List<Actor>> getFollowers(Actor actor) {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getFollowers for actor:" + actor.getUniqueNameWithOrigin()));
    }

    /**
     * Requests a single note (status), specified by the id parameter.
     * More than one activity may be returned (as replies) to reflect Favoriting and Reblogging of the "status"
     */
    @NonNull
    public final Try<AActivity> getNote(String noteOid) {
        return getNote1(noteOid);
    }

    /** See {@link #getNote(String)} */
    protected abstract Try<AActivity> getNote1(String noteOid);

    public boolean canGetConversation(String conversationOid) {
        return isRealOid(conversationOid) && hasApiEndpoint(Connection.ApiRoutineEnum.GET_CONVERSATION);
    }

    public Try<List<AActivity>> getConversation(String conversationOid) {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                "getConversation oid=" + conversationOid));
    }

    /**
     * Update Actor's status by posting to the Server's API
     * Updates the authenticating actor's status, also known as tweeting/blogging.
     *
     * @param note
     * @param inReplyToOid   The ID of an existing Note that the update is in reply to.
     * @param attachments
     *
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/post/statuses/update">Twitter
     *      POST statuses/update</a>
     */
    public abstract Try<AActivity> updateNote(Note note, String inReplyToOid, Attachments attachments);

    /**
     * Post Reblog ("retweet")
     * @see <a href="https://dev.twitter.com/docs/api/1/post/statuses/retweet/%3Aid">POST statuses/retweet/:id</a>
     * 
     * @param rebloggedNoteOid id of the Reblogged note
     */
    public abstract Try<AActivity> announce(String rebloggedNoteOid);

    /**
     * Universal method for several Timeline Types...
     * @param syncYounger
     * @param actor For the {@link ApiRoutineEnum#ACTOR_TIMELINE}, null for the other timelines
     */
    @NonNull
    public abstract Try<InputTimelinePage> getTimeline(boolean syncYounger, ApiRoutineEnum apiRoutine,
               TimelinePosition youngestPosition, TimelinePosition oldestPosition, int limit, Actor actor);

    @NonNull
    public Try<InputTimelinePage> searchNotes(boolean syncYounger, TimelinePosition youngestPosition,
                                         TimelinePosition oldestPosition, int limit, String searchQuery) {
        return InputTimelinePage.TRY_EMPTY;
    }

    @NonNull
    public Try<List<Actor>> searchActors(int limit, String searchQuery) {
        return TryUtils.emptyList();
    }

    /**
     * Allows this Account to follow (or stop following) an actor specified in the actorOid parameter
     * @param follow true - Follow, false - Stop following
     */
    public abstract Try<AActivity> follow(String actorOid, Boolean follow);

    /** Get information about the specified Actor */
    public Try<Actor> getActor(Actor actorIn) {
        return getActor2(actorIn).map(actor -> {
            if (!actor.isPartiallyDefined() && (actor.getUpdatedDate() <= SOME_TIME_AGO)) {
                actor.setUpdatedDate(MyLog.uniqueCurrentTimeMS());
            }
            MyLog.v(this, () -> "getActor oid='" + actorIn.oid
                    + "', username='" + actorIn.getUsername() + "' -> " + actor.getRealName());
            return actor;
        });
    }

    protected abstract Try<Actor> getActor2(Actor actorIn);
    
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

    public void clearAuthInformation() {
        http.clearAuthInformation();
    }

    public void setUserTokenWithSecret(String token, String secret) {
        http.setUserTokenWithSecret(token, secret);
    }

    public OAuthService getOAuthService() {
        return (http instanceof OAuthService) ? (OAuthService) http : null;
    }

    public Try<Void> registerClientForAccount() {
        return http.registerClient();
    }

    public void clearClientKeys() {
        http.clearClientKeys();
    }

    public boolean areOAuthClientKeysPresent() {
        return http.data.areOAuthClientKeysPresent();
    }

    public Connection setAccountConnectionData(AccountConnectionData connectionData) {
        data = connectionData;
        http = connectionData.newHttpConnection();
        http.setHttpConnectionData(HttpConnectionData.fromConnectionData(connectionData));
        return this;
    }

    public final Try<HttpReadResult> postRequest(Uri apiUri, JSONObject formParams) {
        return http.postRequest(apiUri, formParams);
    }

    public Try<OriginConfig> getConfig() {
        return Try.success(OriginConfig.getEmpty());
    }

    public Try<List<Server>> getOpenInstances() {
        return Try.failure(ConnectionException.fromStatusCode(StatusCode.UNSUPPORTED_API,
                MyStringBuilder.objToTag(this)));
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
        if(StringUtil.isEmpty(stringDate)) {
            return 0;
        }
        long unixDate = 0;
        String[] formats = {"", "E MMM d HH:mm:ss Z yyyy", "E, d MMM yyyy HH:mm:ss Z"};
        for (String format : formats) {
            if (StringUtil.isEmpty(format)) {
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
            String formatString = stringDate.contains(".")
                    ? (stringDate.length() - stringDate.lastIndexOf(".") > 4
                        ? "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ"
                        : "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                    : "yyyy-MM-dd'T'HH:mm:ssZ";
            DateFormat iso8601DateFormatSec = new SimpleDateFormat(formatString, Locale.GERMANY);
            try {
                unixDate = iso8601DateFormatSec.parse(datePrepared).getTime();
            } catch (ParseException e) {
                MyLog.e(this, "Failed to parse the date: '" + stringDate +"' using '" + formatString + "'", e);
            }
        }
        return unixDate;
    }

    final Try<HttpReadResult> tryGetRequest(Uri uri) {
        return http.getRequestCommon(uri, true);
    }

    public final Try<JSONObject> getRequest(Uri uri) {
        return http.getRequest(uri);
    }

    Try<JSONArray> getRequestArrayInObject(Uri uri, String arrayName) {
        String method = "getRequestArrayInObject";
        return getRequest(uri).flatMap(jso -> {
            JSONArray jArr = null;
            if (jso != null) {
                try {
                    jArr = jso.getJSONArray(arrayName);
                } catch (JSONException e) {
                    return Try.failure(ConnectionException.loggedJsonException(this, method + ", arrayName=" + arrayName, e, jso));
                }
            }
            return Try.success(jArr);
        });
    }

    public Try<HttpReadResult> downloadFile(ConnectionRequired connectionRequired, Uri uri, File file) {
        return http.downloadFile(connectionRequired, uri, file);
    }

    public HttpConnection getHttp() {
        return http;
    }

    public AccountConnectionData getData() {
        return data;
    }

    @NonNull
    public String partialPathToApiPath(String partialPath) {
        if (!StringUtil.isEmpty(partialPath) && !partialPath.contains("://")) {
            partialPath = http.data.getAccountName().getOrigin().getOriginType().getBasicPath() + "/" + partialPath;
        }
        return partialPath;
    }

    @NonNull
    @Override
    public String toString() {
        return data == null ? "(empty)" : data.toString();
    }

    @Override
    public boolean isEmpty() {
        return this == ConnectionEmpty.EMPTY || data == null || http == null;
    }

}
