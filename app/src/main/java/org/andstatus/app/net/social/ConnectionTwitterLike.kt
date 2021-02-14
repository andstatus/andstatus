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

import androidx.annotation.NonNull;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.HttpRequest;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import static org.andstatus.app.util.UriUtils.nonRealOid;

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
        return partialPathToApiPath(url);
    }

    @Override
    public Try<Boolean> deleteNote(String noteOid) {
        return postNoteAction(ApiRoutineEnum.DELETE_NOTE, noteOid)
        .map(jso -> {
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(TAG, "deleteNote response: " + jso.toString());
            }
            return true;
        });
    }
    
    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/create">POST friendships/create</a>
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/destroy">POST friendships/destroy</a>
     * @return
     */
    @Override
    public Try<AActivity> follow(String actorOid, Boolean follow) {
        JSONObject out = new JSONObject();
        try {
            out.put("user_id", actorOid);
        } catch (JSONException e) {
            MyLog.w(this, "follow " + actorOid, e);
        }
        return postRequest(follow ? ApiRoutineEnum.FOLLOW : ApiRoutineEnum.UNDO_FOLLOW, out)
            .flatMap(HttpReadResult::getJsonObject)
            .map(this::actorFromJson)
            .map(friend -> data.getAccountActor().act(
                        data.getAccountActor(),
                        follow ? ActivityType.FOLLOW : ActivityType.UNDO_FOLLOW,
                        friend)
            );
    }

    /**
     * Returns an array of numeric IDs for every actor the specified actor is following.
     * Current implementation is restricted to 5000 IDs (no paged cursors are used...)
     * @see <a href="https://dev.twitter.com/docs/api/1.1/get/friends/ids">GET friends/ids</a>

     * Returns a cursored collection of actor IDs for every actor following the specified actor.
     * @see <a
     *      href="https://dev.twitter.com/rest/reference/get/followers/ids">GET followers/ids</a>
     */
    @Override
    public Try<List<String>> getFriendsOrFollowersIds(ApiRoutineEnum apiRoutine, String actorOid) {
        return getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(builder -> builder.appendQueryParameter("user_id", actorOid))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(result -> result.getJsonArrayInObject("ids"))
        .flatMap(jsonArray -> {
            List<String> list = new ArrayList<>();
            try {
                for (int index = 0; jsonArray != null && index < jsonArray.length(); index++) {
                    list.add(jsonArray.getString(index));
                }
            } catch (JSONException e) {
                return Try.failure(ConnectionException.loggedJsonException(this, apiRoutine.name(), e, jsonArray));
            }
            return Try.success(list);
        });
    }

    /**
     * Returns a single status, specified by the id parameter below.
     * The status's author will be returned inline.
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1/get/statuses/show/%3Aid">Twitter
     *      REST API Method: statuses/destroy</a>
     */
    @Override
    public Try<AActivity> getNote1(String noteOid) {
        return noteAction(ApiRoutineEnum.GET_NOTE, noteOid)
        .map(this::activityFromJson);
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> getTimeline(boolean syncYounger, ApiRoutineEnum apiRoutine,
                  TimelinePosition youngestPosition, TimelinePosition oldestPosition, int limit, Actor actor) {
        return getTimelineUriBuilder(apiRoutine, limit, actor)
        .map(builder -> appendPositionParameters(builder, youngestPosition, oldestPosition))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(result -> result.getJsonArray()
            .flatMap(jsonArray -> jArrToTimeline(jsonArray, apiRoutine)))
        .map(InputTimelinePage::of);
    }

    @NonNull
    protected Try<Uri.Builder> getTimelineUriBuilder(ApiRoutineEnum apiRoutine, int limit, Actor actor) {
        return this.getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(b -> b.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)))
        .map(b -> StringUtil.isEmpty(actor.oid) ? b : b.appendQueryParameter("user_id", actor.oid));
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
        reblog.setOid(mainActivity.getNote().oid);
        reblog.setUpdatedDate(mainActivity.getUpdatedDate());
        reblog.setActor(mainActivity.getActor());
        reblog.setActivity(rebloggedActivity);
        return reblog;
    }

    @NonNull
    AActivity newLoadedUpdateActivity(String oid, long updatedDate) {
        return AActivity.newPartialNote(data.getAccountActor(), Actor.EMPTY, oid, updatedDate,
                DownloadStatus.LOADED).setOid(oid);
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
            String oid = JsonUtils.optString(jso, "id_str");
            if (StringUtil.isEmpty(oid)) {
                // This is for the Status.net
                oid = JsonUtils.optString(jso, "id");
            }
            activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"));

            activity.setActor(authorFromJson(jso));

            Note note = activity.getNote();
            setNoteBodyFromJson(note, jso);

            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                note.audience().add(actorFromJson(recipient));
            }
            // Tweets are public by default, see https://help.twitter.com/en/safety-and-security/public-and-protected-tweets
            note.audience().setVisibility(Visibility.PUBLIC_AND_TO_FOLLOWERS);

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
            activity.getNote().audience().addActorsFromContent(activity.getNote().getContent(),
                    activity.getAuthor(), activity.getNote().getInReplyTo().getActor());

            if (!jso.isNull("favorited")) {
                note.addFavoriteBy(data.getAccountActor(),
                        TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favorited"))));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso);
        } catch (Exception e) {
            MyLog.w(this, "activityFromJson2", e);
            return AActivity.EMPTY;
        }
        return activity;
    }

    private Actor authorFromJson(JSONObject jso) throws ConnectionException, JSONException {
        Actor author = Actor.EMPTY;
        if (jso.has("sender")) {
            author = actorFromJson(jso.getJSONObject("sender"));
        } else if (jso.has("user")) {
            author = actorFromJson(jso.getJSONObject("user"));
        } else if (jso.has("from_user")) {
            // This is in the search results,
            // see https://dev.twitter.com/docs/api/1/get/search
            String senderName = jso.getString("from_user");
            String senderOid = JsonUtils.optString(jso, "from_user_id_str");
            if (SharedPreferencesUtil.isEmpty(senderOid)) {
                senderOid = JsonUtils.optString(jso, "from_user_id");
            }
            if (!SharedPreferencesUtil.isEmpty(senderOid)) {
                author = Actor.fromOid(data.getOrigin(), senderOid);
                author.setUsername(senderName);
                author.build();
            }
        }
        return author;
    }

    protected void setNoteBodyFromJson(Note note, JSONObject jso) throws JSONException {
        if (jso.has("text")) {
            note.setContentPosted(jso.getString("text"));
        }
    }

    @NonNull
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        Actor actor = actorBuilderFromJson(jso).build();
        if (jso != null && !jso.isNull("status")) {
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
    Actor actorBuilderFromJson(JSONObject jso) {
        if (jso == null) return Actor.EMPTY;

        String oid = "";
        if (jso.has("id_str")) {
            oid = JsonUtils.optString(jso, "id_str");
        } else if (jso.has("id")) {
            oid = JsonUtils.optString(jso, "id");
        }
        if (SharedPreferencesUtil.isEmpty(oid)) {
            oid = "";
        }
        String username = "";
        if (jso.has("screen_name")) {
            username = JsonUtils.optString(jso, "screen_name");
            if (SharedPreferencesUtil.isEmpty(username)) {
                username = "";
            }
        }
        Actor actor = Actor.fromOid(data.getOrigin(), oid);
        actor.setUsername(username);
        actor.setRealName(JsonUtils.optString(jso, "name"));
        if (!SharedPreferencesUtil.isEmpty(actor.getRealName())) {
            actor.setProfileUrlToOriginUrl(data.getOriginUrl());
        }
        actor.location = JsonUtils.optString(jso, "location");
        actor.setAvatarUri(UriUtils.fromAlternativeTags(jso,"profile_image_url_https","profile_image_url"));
        actor.endpoints.add(ActorEndpointType.BANNER, UriUtils.fromJson(jso, "profile_banner_url"));
        actor.setSummary(JsonUtils.optString(jso, "description"));
        actor.setHomepage(JsonUtils.optString(jso, "url"));
        // Hack for twitter.com
        actor.setProfileUrl(http.pathToUrlString("/").replace("/api.", "/") + username);
        actor.notesCount = jso.optLong("statuses_count");
        actor.favoritesCount = jso.optLong("favourites_count");
        actor.followingCount = jso.optLong("friends_count");
        actor.followersCount = jso.optLong("followers_count");
        actor.setCreatedDate(dateFromJson(jso, "created_at"));
        if (!jso.isNull("following")) {
            actor.isMyFriend = TriState.fromBoolean(jso.optBoolean("following"));
        }
        return actor;
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> searchNotes(boolean syncYounger, TimelinePosition youngestPosition,
                                              TimelinePosition oldestPosition, int limit, String searchQuery) {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_NOTES;
        return getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(b -> StringUtil.isEmpty(searchQuery) ? b : b.appendQueryParameter("q", searchQuery))
        .map(builder -> appendPositionParameters(builder, youngestPosition, oldestPosition))
        .map(builder -> builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine)))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(result -> result.getJsonArray()
            .flatMap(jsonArray -> jArrToTimeline(jsonArray, apiRoutine)))
        .map(InputTimelinePage::of);
    }

    Uri.Builder appendPositionParameters(Uri.Builder builder, TimelinePosition youngest, TimelinePosition oldest) {
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
        return builder;
    }

    Try<List<AActivity>> jArrToTimeline(JSONArray jArr, ApiRoutineEnum apiRoutine) {
        List<AActivity> timeline = new ArrayList<>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    AActivity item = activityFromTwitterLikeJson(jArr.getJSONObject(index));
                    timeline.add(item);
                } catch (JSONException e) {
                    return Try.failure(ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null));
                } catch (Exception e) {
                    return Try.failure(e);
                }
            }
        }
        if (apiRoutine.isNotePrivate()) {
            setNotesPrivate(timeline);
        }
        return Try.success(timeline);
    }

    private void setNotesPrivate(List<AActivity> timeline) {
        for (AActivity item : timeline) {
            if (item.getObjectType() == AObjectType.NOTE) {
                item.getNote().audience().setVisibility(Visibility.PRIVATE);
            }
        }
    }

    Try<List<Actor>> jArrToActors(JSONArray jArr, ApiRoutineEnum apiRoutine, Uri uri) {
        List<Actor> actors = new ArrayList<>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    Actor item = actorFromJson(jso);
                    actors.add(item);
                } catch (JSONException e) {
                    return Try.failure(ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null));
                } catch (Exception e) {
                    return Try.failure(e);
                }
            }
        }
        MyLog.d(this, apiRoutine + " '" + uri + "' " + actors.size() + " items");
        return Try.success(actors);
    }

    /**
     * @see <a href="https://dev.twitter.com/docs/api/1.1/get/users/show">GET users/show</a>
     * @return
     */
    @Override
    public Try<Actor> getActor2(Actor actorIn) {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.GET_ACTOR;
        return getApiPath(apiRoutine)
        .map(Uri::buildUpon)
        .map(builder -> UriUtils.isRealOid(actorIn.oid)
                ? builder.appendQueryParameter("user_id", actorIn.oid)
                : builder.appendQueryParameter("screen_name", actorIn.getUsername()))
        .map(Uri.Builder::build)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .map(this::actorFromJson);
    }
    
    @Override
    public Try<AActivity> announce(String rebloggedNoteOid) {
        return postNoteAction(ApiRoutineEnum.ANNOUNCE, rebloggedNoteOid)
                .map(this::activityFromJson);
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
    public Try<RateLimitStatus> rateLimitStatus() {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS;
        return getApiPath(apiRoutine)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .flatMap(result -> {
            RateLimitStatus status = new RateLimitStatus();
            if (result != null) {
                JSONObject resources = null;
                try {
                    resources = result.getJSONObject("resources");
                    JSONObject limitObject = resources.getJSONObject("statuses").getJSONObject("/statuses/home_timeline");
                    status.remaining = limitObject.optInt("remaining");
                    status.limit = limitObject.optInt("limit");
                } catch (JSONException e) {
                    return Try.failure(ConnectionException.loggedJsonException(this, "getting rate limits", e, resources));
                }
            }
            return Try.success(status);
        });
    }
    
    @Override
    public Try<AActivity> updateNote(Note note) {
        if (note.audience().hasNonSpecial() && note.audience().getVisibility().isPrivate()) {
            return updatePrivateNote(note, note.audience().getFirstNonSpecial().oid);
        }
        return updateNote2(note);
    }

    abstract Try<AActivity> updateNote2(Note note);

    void updateNoteSetFields(Note note, JSONObject formParams) throws JSONException {
        if (StringUtil.nonEmpty(note.getContentToPost())) {
            formParams.put("status", note.getContentToPost());
        }
        if (StringUtil.nonEmptyNonTemp(note.getInReplyTo().getOid())) {
            formParams.put("in_reply_to_status_id", note.getInReplyTo().getOid());
        }
    }

    private Try<AActivity> updatePrivateNote(Note note, String recipientOid) {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("text", note.getContentToPost());
            if ( !StringUtil.isEmpty(recipientOid)) {
                formParams.put("user_id", recipientOid);
            }
        } catch (JSONException e) {
            return Try.failure(e);
        }
        return postRequest(ApiRoutineEnum.UPDATE_PRIVATE_NOTE, formParams)
        .flatMap(HttpReadResult::getJsonObject)
        .map(this::activityFromJson);
    }

    /**
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
     *      REST API Method: account verify_credentials</a>
     */
    @Override
    @NonNull
    public Try<Actor> verifyCredentials(Optional<Uri> whoAmI) {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS;
        return getApiPath(apiRoutine)
        .map(uri -> HttpRequest.of(apiRoutine, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .map(this::actorFromJson);
    }

    protected final Try<HttpReadResult> postRequest(ApiRoutineEnum apiRoutine, JSONObject formParams) {
        return tryApiPath(data.getAccountActor(), apiRoutine)
                .map(uri -> HttpRequest.of(apiRoutine, uri).withPostParams(formParams))
                .flatMap(this::execute);
    }

    Try<Uri> getApiPathWithNoteId(ApiRoutineEnum routineEnum, String noteId) {
        return getApiPath(routineEnum).map(uri -> UriUtils.map(uri, s -> s.replace("%noteId%", noteId)));
    }

    Try<Uri> getApiPathWithActorId(ApiRoutineEnum routineEnum, String actorId) {
        return getApiPath(routineEnum).map(uri -> UriUtils.map(uri, s -> s.replace("%actorId%", actorId)));
    }

    @Override
    public Try<List<Actor>> getFollowers(Actor actor) {
        return getActors(actor, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public Try<List<Actor>> getFriends(Actor actor) {
        return getActors(actor, ApiRoutineEnum.GET_FRIENDS);
    }

    Try<List<Actor>> getActors(Actor actor, ApiRoutineEnum apiRoutine) {
        return TryUtils.emptyList();
    }

    @Override
    public Try<AActivity> like(String noteOid) {
        return postNoteAction(ApiRoutineEnum.LIKE, noteOid)
                .map(this::activityFromJson);
    }

    @Override
    public Try<AActivity> undoLike(String noteOid) {
        return postNoteAction(ApiRoutineEnum.UNDO_LIKE, noteOid)
                .map(this::activityFromJson);
    }

    Try<JSONObject> noteAction(ApiRoutineEnum apiRoutine, String noteOid) {
        return noteAction(apiRoutine, noteOid, false);
    }

    Try<JSONObject> postNoteAction(ApiRoutineEnum apiRoutine, String noteOid) {
        return noteAction(apiRoutine, noteOid, true);
    }

    private Try<JSONObject> noteAction(ApiRoutineEnum apiRoutine, String noteOid, boolean asPost) {
        if (nonRealOid(noteOid)) return Try.success(JsonUtils.EMPTY);

        return getApiPathWithNoteId(apiRoutine, noteOid)
                .map(uri -> HttpRequest.of(apiRoutine, uri).asPost(asPost))
                .flatMap(this::execute)
                .flatMap(HttpReadResult::getJsonObject);
    }
}
