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

package org.andstatus.app.net.social;

import android.net.Uri;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * Twitter API implementations
 * https://dev.twitter.com/rest/public
 * @author yvolk@yurivolkov.com
 */
public abstract class ConnectionTwitterLike extends Connection {
    private static final String TAG = ConnectionTwitterLike.class.getSimpleName();

    /**
     * URL of the API. Not logged
     * @return URL or an empty string in a case the API routine is not supported
     */
    @NonNull
    @Override
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "account/rate_limit_status.json";
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "account/verify_credentials.json";
                break;
            case LIKE:
                url = "favorites/create/%noteId%.json";
                break;
            case UNDO_LIKE:
                url = "favorites/destroy/%noteId%.json";
                break;
            case DELETE_NOTE:
                url = "statuses/destroy/%noteId%.json";
                break;
            case PRIVATE_NOTES:
                url = "direct_messages.json";
                break;
            case LIKED_TIMELINE:
                url = "favorites.json";
                break;
            case FOLLOW:
                url = "friendships/create.json";
                break;
            case GET_FOLLOWERS_IDS:
                url = "followers/ids.json";
                break;
            case GET_FRIENDS_IDS:
                url = "friends/ids.json";
                break;
            case GET_NOTE:
                url = "statuses/show.json" + "?id=%noteId%";
                break;
            case GET_ACTOR:
                url = "users/show.json";
                break;
            case HOME_TIMELINE:
                url = "statuses/home_timeline.json";
                break;
            case NOTIFICATIONS_TIMELINE:
                url = "statuses/mentions.json";
                break;
            case UPDATE_PRIVATE_NOTE:
                url = "direct_messages/new.json";
                break;
            case UPDATE_NOTE:
                url = "statuses/update.json";
                break;
            case ANNOUNCE:
                url = "statuses/retweet/%noteId%.json";
                break;
            case UNDO_FOLLOW:
                url = "friendships/destroy.json";
                break;
            case ACTOR_TIMELINE:
                url = "statuses/user_timeline.json";
                break;
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    public boolean deleteNote(String noteOid) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithNoteId(ApiRoutineEnum.DELETE_NOTE, noteOid));
        if (jso != null && MyLog.isVerboseEnabled()) {
            try {
                MyLog.v(TAG, "deleteNote response: " + jso.toString(2));
            } catch (JSONException e) {
                MyLog.e(this, e);
                return false;
            }
        }
        return jso != null;
    }
    
    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/create">POST friendships/create</a>
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/destroy">POST friendships/destroy</a>
     */
    @Override
    public AActivity follow(String actorOid, Boolean follow) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("user_id", actorOid);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jsoActor = postRequest(follow ? ApiRoutineEnum.FOLLOW : ApiRoutineEnum.UNDO_FOLLOW, out);
        final Actor friend = actorFromJson(jsoActor);
        return data.getAccountActor().act(
                data.getAccountActor(),
                follow
                    ? ActivityType.FOLLOW
                    : ActivityType.UNDO_FOLLOW,
                friend);
    } 

    /**
     * Returns an array of numeric IDs for every actor the specified actor is following.
     * Current implementation is restricted to 5000 IDs (no paged cursors are used...)
     * @see <a href="https://dev.twitter.com/docs/api/1.1/get/friends/ids">GET friends/ids</a>
     */
    @Override
    public List<String> getFriendsIds(String actorOid) throws ConnectionException {
        String method = "getFriendsIds";
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", actorOid);
        List<String> list = new ArrayList<>();
        JSONArray jArr = getRequestArrayInObject(builder.build().toString(), "ids");
        try {
            for (int index = 0; jArr != null && index < jArr.length(); index++) {
                list.add(jArr.getString(index));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, method, e, jArr);
        }
        return list;
    }

    /**
     * Returns a cursored collection of actor IDs for every actor following the specified actor.
     * @see <a
     *      href="https://dev.twitter.com/rest/reference/get/followers/ids">GET followers/ids</a>
     */
    @NonNull
    @Override
    public List<String> getFollowersIds(String actorOid) throws ConnectionException {
        String method = "getFollowersIds";
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FOLLOWERS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", actorOid);
        List<String> list = new ArrayList<>();
        JSONArray jArr = getRequestArrayInObject(builder.build().toString(), "ids");
        try {
            for (int index = 0; jArr != null && index < jArr.length(); index++) {
                list.add(jArr.getString(index));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, method, e, jArr);
        }
        return list;
    }

    /**
     * Returns a single status, specified by the id parameter below.
     * The status's author will be returned inline.
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/get/statuses/show/%3Aid">Twitter
     *      REST API Method: statuses/destroy</a>
     */
    @Override
    public AActivity getNote1(String noteOid) throws ConnectionException {
        JSONObject note = getRequest(getApiPathWithNoteId(ApiRoutineEnum.GET_NOTE, noteOid));
        return activityFromJson(note);
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, Actor actor)
            throws ConnectionException {
        Uri.Builder builder = getTimelineUriBuilder(apiRoutine, limit, actor);
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, builder.build().toString());
    }

    @NonNull
    protected Uri.Builder getTimelineUriBuilder(ApiRoutineEnum apiRoutine, int limit, Actor actor) throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        if (!StringUtils.isEmpty(actor.oid)) {
            builder.appendQueryParameter("user_id", actor.oid);
        }
        return builder;
    }

    protected AActivity activityFromTwitterLikeJson(JSONObject jso) throws ConnectionException {
        return activityFromJson(jso);
    }

    @NonNull
    final AActivity activityFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        final AActivity mainActivity = activityFromJson2(jso);
        final AActivity rebloggedActivity = rebloggedNoteFromJson(jso);
        if (rebloggedActivity.isEmpty()) {
            return mainActivity;
        } else {
            return makeReblog(data.getAccountActor(), mainActivity, rebloggedActivity);
        }
    }

    @NonNull
    private AActivity makeReblog(Actor accountActor, @NonNull AActivity mainActivity,
                                 AActivity rebloggedActivity) {
        AActivity reblog = AActivity.from(accountActor, ActivityType.ANNOUNCE);
        reblog.setTimelinePosition(mainActivity.getNote().oid);
        reblog.setUpdatedDate(mainActivity.getUpdatedDate());
        reblog.setActor(mainActivity.getActor());
        reblog.setActivity(rebloggedActivity);
        return reblog;
    }

    @NonNull
    AActivity newLoadedUpdateActivity(String oid, long updatedDate) throws ConnectionException {
        return AActivity.newPartialNote(data.getAccountActor(), Actor.EMPTY, oid, updatedDate,
                DownloadStatus.LOADED).setTimelinePosition(oid);
    }

    AActivity rebloggedNoteFromJson(@NonNull JSONObject jso) throws ConnectionException {
        return activityFromJson2(jso.optJSONObject("retweeted_status"));
    }

    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        AActivity activity;
        try {
            String oid = jso.optString("id_str");
            if (StringUtils.isEmpty(oid)) {
                // This is for the Status.net
                oid = jso.optString("id");
            }
            activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"));

            Actor author = Actor.EMPTY;
            if (jso.has("sender")) {
                author = actorFromJson(jso.getJSONObject("sender"));
            } else if (jso.has("user")) {
                author = actorFromJson(jso.getJSONObject("user"));
            } else if (jso.has("from_user")) {
                // This is in the search results,
                // see https://dev.twitter.com/docs/api/1/get/search
                String senderName = jso.getString("from_user");
                String senderOid = jso.optString("from_user_id_str");
                if (SharedPreferencesUtil.isEmpty(senderOid)) {
                    senderOid = jso.optString("from_user_id");
                }
                if (!SharedPreferencesUtil.isEmpty(senderOid)) {
                    author = Actor.fromOid(data.getOrigin(), senderOid);
                    author.setUsername(senderName);
                }
            }
            activity.setActor(author);

            Note note = activity.getNote();
            setNoteBodyFromJson(note, jso);
            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                note.audience().add(actorFromJson(recipient));
            }
            if (jso.has("source")) {
                note.via = jso.getString("source");
            }

            // If the Msg is a Reply to other note
            String inReplyToActorOid = "";
            if (jso.has("in_reply_to_user_id_str")) {
                inReplyToActorOid = jso.getString("in_reply_to_user_id_str");
            } else if (jso.has("in_reply_to_user_id")) {
                // This is for Status.net
                inReplyToActorOid = jso.getString("in_reply_to_user_id");
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                inReplyToActorOid = "";
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                String inReplyToNoteOid = "";
                if (jso.has("in_reply_to_status_id_str")) {
                    inReplyToNoteOid = jso.getString("in_reply_to_status_id_str");
                } else if (jso.has("in_reply_to_status_id")) {
                    // This is for StatusNet
                    inReplyToNoteOid = jso.getString("in_reply_to_status_id");
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToNoteOid)) {
                    // Construct Related note from available info
                    Actor inReplyToActor = Actor.fromOid(data.getOrigin(), inReplyToActorOid);
                    if (jso.has("in_reply_to_screen_name")) {
                        inReplyToActor.setUsername(jso.getString("in_reply_to_screen_name"));
                    }
                    AActivity inReplyTo = AActivity.newPartialNote(data.getAccountActor(), inReplyToActor,
                            inReplyToNoteOid);
                    note.setInReplyTo(inReplyTo);
                }
            }

            if (!jso.isNull("favorited")) {
                note.addFavoriteBy(data.getAccountActor(),
                        TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favorited"))));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso);
        } catch (Exception e) {
            MyLog.e(this, "activityFromJson2", e);
            return AActivity.EMPTY;
        }
        return activity;
    }

    protected void setNoteBodyFromJson(Note note, JSONObject jso) throws JSONException {
        if (jso.has("text")) {
            note.setContentPosted(jso.getString("text"));
        }
    }

    @NonNull
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) return Actor.EMPTY;

        String oid = "";
        if (jso.has("id_str")) {
            oid = jso.optString("id_str");
        } else if (jso.has("id")) {
            oid = jso.optString("id");
        } 
        if (SharedPreferencesUtil.isEmpty(oid)) {
            oid = "";
        }
        String username = "";
        if (jso.has("screen_name")) {
            username = jso.optString("screen_name");
            if (SharedPreferencesUtil.isEmpty(username)) {
                username = "";
            }
        }
        Actor actor = Actor.fromOid(data.getOrigin(), oid);
        actor.setUsername(username);
        actor.setRealName(jso.optString("name"));
        if (!SharedPreferencesUtil.isEmpty(actor.getRealName())) {
            actor.setProfileUrlToOriginUrl(data.getOriginUrl());
        }
        actor.location = jso.optString("location");
        actor.setAvatarUri(UriUtils.fromAlternativeTags(jso,"profile_image_url_https","profile_image_url"));
        actor.endpoints.add(ActorEndpointType.BANNER, UriUtils.fromJson(jso, "profile_banner_url"));
        actor.setSummary(jso.optString("description"));
        actor.setHomepage(jso.optString("url"));
        // Hack for twitter.com
        actor.setProfileUrl(http.pathToUrlString("/").replace("/api.", "/") + username);
        actor.notesCount = jso.optLong("statuses_count");
        actor.favoritesCount = jso.optLong("favourites_count");
        actor.followingCount = jso.optLong("friends_count");
        actor.followersCount = jso.optLong("followers_count");
        actor.setCreatedDate(dateFromJson(jso, "created_at"));
        if (!jso.isNull("following")) {
            actor.followedByMe = TriState.fromBoolean(jso.optBoolean("following"));
        }
        if (!jso.isNull("status")) {
            try {
                final AActivity activity = activityFromJson(jso.getJSONObject("status"));
                activity.setActor(actor);
                actor.setLatestActivity(activity);
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, "getting status from actor", e, jso);
            }
        }
        return actor;
    }

    @NonNull
    @Override
    public List<AActivity> searchNotes(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_NOTES;
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!StringUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, url);
    }

    void appendPositionParameters(Uri.Builder builder, TimelinePosition youngest, TimelinePosition oldest) {
        if (youngest.nonEmpty()) {
            builder.appendQueryParameter("since_id", youngest.getPosition());
        } else if (oldest.nonEmpty()) {
            String maxIdString = oldest.getPosition();
            try {
                // Subtract 1, as advised at https://dev.twitter.com/rest/public/timelines
                long maxId = Long.parseLong(maxIdString);
                maxIdString = Long.toString(maxId - 1);
            } catch (NumberFormatException e) {
                MyLog.i(this, "Is not long number: '" + maxIdString + "'");
            }
            builder.appendQueryParameter("max_id", maxIdString);
        }
    }

    List<AActivity> jArrToTimeline(JSONArray jArr, ApiRoutineEnum apiRoutine, String url) throws ConnectionException {
        List<AActivity> timeline = new ArrayList<>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    AActivity item = activityFromTwitterLikeJson(jArr.getJSONObject(index));
                    timeline.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null);
                }
            }
        }
        if (apiRoutine.isNotePrivate()) {
            setNotesPrivate(timeline);
        }
        MyLog.d(this, apiRoutine + " '" + url + "' " + timeline.size() + " items");
        return timeline;
    }

    void setNotesPrivate(List<AActivity> timeline) {
        for (AActivity item : timeline) {
            if (item.getObjectType() == AObjectType.NOTE) {
                item.getNote().setPublic(TriState.FALSE);
            }
        }
    }

    List<Actor> jArrToActors(JSONArray jArr, ApiRoutineEnum apiRoutine, Uri uri) throws ConnectionException {
        List<Actor> actors = new ArrayList<>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    Actor item = actorFromJson(jso);
                    actors.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null);
                }
            }
        }
        MyLog.d(this, apiRoutine + " '" + uri + "' " + actors.size() + " items");
        return actors;
    }

    /**
     * @see <a href="https://dev.twitter.com/docs/api/1.1/get/users/show">GET users/show</a>
     */
    @Override
    public Actor getActor2(Actor actorIn, String username) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_ACTOR));
        Uri.Builder builder = sUri.buildUpon();
        if (UriUtils.isRealOid(actorIn.oid)) {
            builder.appendQueryParameter("user_id", actorIn.oid);
        } else {
            builder.appendQueryParameter("screen_name", username);
        }
        JSONObject jso = getRequest(builder.build().toString());
        Actor actor = actorFromJson(jso);
        MyLog.v(this, () -> "getActor oid='" + actor.oid
                + "', username='" + username + "' -> " + actor.getRealName());
        return actor;
    }
    
    @Override
    public AActivity announce(String rebloggedNoteOid) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithNoteId(ApiRoutineEnum.ANNOUNCE, rebloggedNoteOid));
        return activityFromJson(jso);
    }

    /**
     * Check API requests status.
     * 
     * Returns the remaining number of API requests available to the requesting 
     * account before the API limit is reached for the current hour. Calls to
     * rate_limit_status do not count against the rate limit.  If authentication 
     * credentials are provided, the rate limit status for the authenticating 
     * account is returned.  Otherwise, the rate limit status for the requester's
     * IP address is returned.
     * @see <a
           href="https://dev.twitter.com/docs/api/1/get/account/rate_limit_status">GET 
           account/rate_limit_status</a>
     */
    @Override
    public RateLimitStatus rateLimitStatus() throws ConnectionException {
        JSONObject result = getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS));
        RateLimitStatus status = new RateLimitStatus();
        if (result != null) {
            switch (data.getOriginType()) {
                case GNUSOCIAL:
                    status.remaining = result.optInt("remaining_hits");
                    status.limit = result.optInt("hourly_limit");
                    break;
                default:
                    JSONObject resources = null;
                    try {
                        resources = result.getJSONObject("resources");
                        JSONObject limitObject = resources.getJSONObject("statuses").getJSONObject("/statuses/home_timeline");
                        status.remaining = limitObject.optInt("remaining");
                        status.limit = limitObject.optInt("limit");
                    } catch (JSONException e) {
                        throw ConnectionException.loggedJsonException(this, "getting rate limits", e, resources);
                    }
                    break;
            }
        }
        return status;
    }
    
    @Override
    public AActivity updateNote(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        if (StringUtils.isEmpty(inReplyToOid) && audience.hasNonPublic() && audience.getPublic().isFalse) {
            return updatePrivateNote(name, content, noteOid, audience.getFirstNonPublic().oid, mediaUri);
        }
        return  updateNote2(name, content, noteOid, audience, inReplyToOid, mediaUri);
    }

    protected AActivity updateNote2(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            if (StringUtils.nonEmpty(content)) formParams.put("status", content);
            if (StringUtils.nonEmpty(inReplyToOid)) formParams.put("in_reply_to_status_id", inReplyToOid);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.UPDATE_NOTE, formParams);
        return activityFromJson(jso);
    }

    private AActivity updatePrivateNote(String name, String content, String noteOid, String recipientOid, Uri mediaUri)
            throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("text", content);
            if ( !StringUtils.isEmpty(recipientOid)) {
                formParams.put("user_id", recipientOid);
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.UPDATE_PRIVATE_NOTE, formParams);
        return activityFromJson(jso);
    }

    /**
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
     *      REST API Method: account verify_credentials</a>
     */
    @Override
    @NonNull
    public Actor verifyCredentials() throws ConnectionException {
        JSONObject actor = getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return actorFromJson(actor);
    }

    protected final JSONObject postRequest(ApiRoutineEnum apiRoutine, JSONObject formParams) throws ConnectionException {
        return postRequest(getApiPath(apiRoutine), formParams);
    }

    String getApiPathWithNoteId(ApiRoutineEnum routineEnum, String noteId) throws ConnectionException {
        return getApiPath(routineEnum).replace("%noteId%", noteId);
    }

    String getApiPathWithActorId(ApiRoutineEnum routineEnum, String actorId) throws ConnectionException {
        return getApiPath(routineEnum).replace("%actorId%", actorId);
    }

    @Override
    public List<Actor> getFollowers(Actor actor) throws ConnectionException {
        return getActors(actor, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public List<Actor> getFriends(Actor actor) throws ConnectionException {
        return getActors(actor, ApiRoutineEnum.GET_FRIENDS);
    }

    List<Actor> getActors(Actor actor, ApiRoutineEnum apiRoutine) throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public AActivity like(String noteOid) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithNoteId(ApiRoutineEnum.LIKE, noteOid));
        return activityFromJson(jso);
    }

    @Override
    public AActivity undoLike(String noteOid) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithNoteId(ApiRoutineEnum.UNDO_LIKE, noteOid));
        return activityFromJson(jso);
    }

}
