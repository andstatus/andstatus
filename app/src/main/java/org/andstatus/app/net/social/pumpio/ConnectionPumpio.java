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

package org.andstatus.app.net.social.pumpio;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.RateLimitStatus;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of pump.io API: <a href="https://github.com/e14n/pump.io/blob/master/API.md">https://github.com/e14n/pump.io/blob/master/API.md</a>  
 * @author yvolk@yurivolkov.com
 */
public class ConnectionPumpio extends Connection {
    private static final String TAG = ConnectionPumpio.class.getSimpleName();
    static final String PUBLIC_COLLECTION_ID = "http://activityschema.org/collection/public";
    static final String APPLICATION_ID = "http://andstatus.org/andstatus";
    static final String NAME_PROPERTY = "displayName";
    static final String CONTENT_PROPERTY = "content";
    static final String VIDEO_OBJECT = "stream";
    static final String IMAGE_OBJECT = "image";
    public static final String FULL_IMAGE_OBJECT = "fullImage";

    @Override
    public void enrichConnectionData(OriginConnectionData connectionData) {
        super.enrichConnectionData(connectionData);
        if (!TextUtils.isEmpty(connectionData.getAccountName().getUsername())) {
            connectionData.setOriginUrl(UrlUtils.buildUrl(usernameToHost(
                    connectionData.getAccountName().getUsername()), connectionData.isSsl()));
        }
    }
    
    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "whoami";
                break;
            case GET_FOLLOWERS:
            case GET_FOLLOWERS_IDS:
                url = "user/%nickname%/followers";
                break;
            case GET_FRIENDS:
            case GET_FRIENDS_IDS:
                url = "user/%nickname%/following";
                break;
            case GET_ACTOR:
                url = "user/%nickname%/profile";
                break;
            case REGISTER_CLIENT:
                url = "client/register";
                break;
            case HOME_TIMELINE:
                url = "user/%nickname%/inbox";
                break;
            case FAVORITES_TIMELINE:
                url = "user/%nickname%/favorites";
                break;
            case UPDATE_NOTE_WITH_MEDIA:
                url = "user/%nickname%/uploads";
                break;
            case LIKE:
            case UNDO_LIKE:
            case FOLLOW:
            case UPDATE_PRIVATE_NOTE:
            case ANNOUNCE:
            case DELETE_NOTE:
            case UPDATE_NOTE:
            case ACTOR_TIMELINE:
                url = "user/%nickname%/feed";
                break;
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    public RateLimitStatus rateLimitStatus() throws ConnectionException {
        // TODO Method stub
        return new RateLimitStatus();
    }

    @Override
    @NonNull
    public Actor verifyCredentials() throws ConnectionException {
        JSONObject actor = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return actorFromJson(actor);
    }

    @NonNull
    private Actor actorFromJson(JSONObject jso) throws ConnectionException {
        if (!PObjectType.PERSON.isTypeOf(jso)) {
            return Actor.EMPTY;
        }
        String oid = jso.optString("id");
        Actor actor = Actor.fromOriginAndActorOid(data.getOrigin(), oid);
        actor.setUsername(actorOidToUsername(oid));
        actor.setRealName(jso.optString(NAME_PROPERTY));
        actor.avatarUrl = JsonUtils.optStringInside(jso, "image", "url");
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY);
        actor.setDescription(jso.optString("summary"));
        actor.setHomepage(jso.optString("url"));
        actor.setProfileUrl(jso.optString("url"));
        actor.setUpdatedDate(dateFromJson(jso, "updated"));
        JSONObject pumpIo = jso.optJSONObject("pump_io");
        if (pumpIo != null && !pumpIo.isNull("followed")) {
            actor.followedByMe = TriState.fromBoolean(pumpIo.optBoolean("followed"));
        }
        return actor;
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }
    
    @Override
    public AActivity undoLike(String noteOid) throws ConnectionException {
        return actOnNote(PActivityType.UNFAVORITE, noteOid);
    }

    @Override
    public AActivity like(String noteOid) throws ConnectionException {
        return actOnNote(PActivityType.FAVORITE, noteOid);
    }

    @Override
    public boolean deleteNote(String noteOid) throws ConnectionException {
        return !actOnNote(PActivityType.DELETE, noteOid).isEmpty();
    }

    private AActivity actOnNote(PActivityType activityType, String noteId) throws ConnectionException {
        return ActivitySender.fromId(this, noteId).send(activityType);
    }

    @Override
    public List<Actor> getFollowers(String actorOid) throws ConnectionException {
        return getActors(actorOid, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public List<Actor> getFriends(String actorOid) throws ConnectionException {
        return getActors(actorOid, ApiRoutineEnum.GET_FRIENDS);
    }

    @NonNull
    private List<Actor> getActors(String actorId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        int limit = 200;
        ConnectionAndUrl conu = getConnectionAndUrl(apiRoutine, actorId);
        Uri sUri = Uri.parse(conu.url);
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        String url = builder.build().toString();
        JSONArray jArr = conu.httpConnection.getRequestAsArray(url);
        List<Actor> actors = new ArrayList<>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    Actor item = actorFromJson(jso);
                    actors.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing list of actors", e, null);
                }
            }
        }
        MyLog.d(TAG, apiRoutine + " '" + url + "' " + actors.size() + " actors");
        return actors;
    }

    @Override
    protected AActivity getNote1(String noteOid) throws ConnectionException {
        return activityFromJson(http.getRequest(noteOid));
    }

    @Override
    public AActivity updateNote(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        String content2 = toHtmlIfAllowed(content);
        ActivitySender sender = ActivitySender.fromContent(this, noteOid, audience, name, content2);
        sender.setInReplyTo(inReplyToOid);
        sender.setMediaUri(mediaUri);
        return sender.send(PActivityType.POST);
    }

    private String toHtmlIfAllowed(String body) {
        return data.getOrigin().isHtmlContentAllowed() ? MyHtml.htmlify(body) : body;
    }

    String oidToObjectType(String oid) {
        String objectType = "";
        if (oid.contains("/comment/")) {
            objectType = "comment";
        } else if (oid.startsWith(OriginPumpio.ACCOUNT_PREFIX)) {
            objectType = "person";
        } else if (oid.contains("/note/")) {
            objectType = "note";
        } else if (oid.contains("/notice/")) {
            objectType = "note";
        } else if (oid.contains("/person/")) {
            objectType = "person";
        } else if (oid.contains("/collection/") || oid.endsWith("/followers")) {
            objectType = "collection";
        } else if (oid.contains("/user/")) {
            objectType = "person";
        } else {
            String pattern = "/api/";
            int indStart = oid.indexOf(pattern);
            if (indStart >= 0) {
                int indEnd = oid.indexOf("/", indStart+pattern.length());
                if (indEnd > indStart) {
                    objectType = oid.substring(indStart+pattern.length(), indEnd);
                }
            }
        }
        if (TextUtils.isEmpty(objectType)) {
            objectType = "unknown object type: " + oid;
            MyLog.e(this, objectType);
        }
        return objectType;
    }

    ConnectionAndUrl getConnectionAndUrl(ApiRoutineEnum apiRoutine, String actorId) throws ConnectionException {
        if (TextUtils.isEmpty(actorId)) {
            throw new ConnectionException(StatusCode.BAD_REQUEST, apiRoutine + ": actorId is required");
        }
        return  getConnectionAndUrlForUsername(apiRoutine, actorOidToUsername(actorId));
    }

    private ConnectionAndUrl getConnectionAndUrlForUsername(ApiRoutineEnum apiRoutine, String username) throws ConnectionException {
        ConnectionAndUrl conu = new ConnectionAndUrl();
        conu.url = this.getApiPath(apiRoutine);
        if (TextUtils.isEmpty(conu.url)) {
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, "The API is not supported yet: " + apiRoutine);
        }
        if (TextUtils.isEmpty(username)) {
            throw new ConnectionException(StatusCode.BAD_REQUEST, apiRoutine + ": userName is required");
        }
        String nickname = usernameToNickname(username);
        if (TextUtils.isEmpty(nickname)) {
            throw new ConnectionException(StatusCode.BAD_REQUEST, apiRoutine + ": wrong userName='" + username + "'");
        }
        String host = usernameToHost(username);
        conu.httpConnection = http;
        if (TextUtils.isEmpty(host)) {
            throw new ConnectionException(StatusCode.BAD_REQUEST, apiRoutine + ": host is empty for the username='"
                    + username + "'");
        } else if (http.data.originUrl == null || host.compareToIgnoreCase(http.data.originUrl.getHost()) != 0) {
            MyLog.v(this, "Requesting data from the host: " + host);
            HttpConnectionData connectionData1 = http.data.copy();
            connectionData1.oauthClientKeys = null;
            connectionData1.originUrl = UrlUtils.buildUrl(host, connectionData1.isSsl());
            conu.httpConnection = http.getNewInstance();
            conu.httpConnection.setConnectionData(connectionData1);
        }
        if (!conu.httpConnection.data.areOAuthClientKeysPresent()) {
            conu.httpConnection.registerClient(getApiPath(ApiRoutineEnum.REGISTER_CLIENT));
            if (!conu.httpConnection.getCredentialsPresent()) {
                throw ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST,
                        "No credentials", conu.httpConnection.data.originUrl);
            }
        }
        conu.url = conu.url.replace("%nickname%", nickname);
        return conu;
    }

    static class ConnectionAndUrl {
        String url;
        HttpConnection httpConnection;
    }

    @Override
    public AActivity announce(String rebloggedNoteOid) throws ConnectionException {
        return actOnNote(PActivityType.SHARE, rebloggedNoteOid);
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String actorOid)
            throws ConnectionException {
        ConnectionAndUrl conu = getConnectionAndUrl(apiRoutine, actorOid);
        Uri sUri = Uri.parse(conu.url);
        Uri.Builder builder = sUri.buildUpon();
        if (youngestPosition.nonEmpty()) {
            // The "since" should point to the "Activity" on the timeline, not to the note
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("since", youngestPosition.getPosition());
        } else if (oldestPosition.nonEmpty()) {
            builder.appendQueryParameter("before", oldestPosition.getPosition());
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        String url = builder.build().toString();
        JSONArray jArr = conu.httpConnection.getRequestAsArray(url);
        List<AActivity> activities = new ArrayList<>();
        if (jArr != null) {
            // Read the activities in the chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    activities.add(activityFromJson(jso));
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing timeline", e, null);
                }
            }
        }
        MyLog.d(TAG, "getTimeline '" + url + "' " + activities.size() + " notes");
        return activities;
    }

    @Override
    public int fixedDownloadLimit(int limit, ApiRoutineEnum apiRoutine) {
        final int maxLimit = apiRoutine == ApiRoutineEnum.GET_FRIENDS ? 200 : 20;
        int out = super.fixedDownloadLimit(limit, apiRoutine);
        if (out > maxLimit) {
            out = maxLimit;
        }
        return out;
    }

    @NonNull
    AActivity activityFromJson(JSONObject jsoActivity) throws ConnectionException {
        if (jsoActivity == null) return AActivity.EMPTY;

        final PActivityType verb = PActivityType.load(jsoActivity.optString("verb"));
        AActivity activity = AActivity.from(data.getAccountActor(),
                verb == PActivityType.UNKNOWN ? ActivityType.UPDATE : verb.activityType);
        try {
            if (PObjectType.ACTIVITY.isTypeOf(jsoActivity)) {
                return parseActivity(activity, jsoActivity);
            } else {
                return parseObjectOfActivity(activity, jsoActivity);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing activity", e, jsoActivity);
        }
    }

    private AActivity parseActivity(AActivity activity, JSONObject jsoActivity) throws JSONException, ConnectionException {
        String oid = jsoActivity.optString("id");
        if (TextUtils.isEmpty(oid)) {
            MyLog.d(this, "Pumpio activity has no id:" + jsoActivity.toString(2));
            return AActivity.EMPTY;
        }
        activity.setTimelinePosition(oid);
        activity.setUpdatedDate(dateFromJson(jsoActivity, "updated"));
        if (jsoActivity.has("actor")) {
            activity.setActor(actorFromJson(jsoActivity.getJSONObject("actor")));
        }

        JSONObject objectOfActivity = jsoActivity.getJSONObject("object");
        if (PObjectType.ACTIVITY.isTypeOf(objectOfActivity)) {
            // Simplified dealing with nested activities
            AActivity innerActivity = activityFromJson(objectOfActivity);
            activity.setObjActor(innerActivity.getObjActor());
            activity.setNote(innerActivity.getNote());
        } else {
            parseObjectOfActivity(activity, objectOfActivity);
        }
        if (activity.getObjectType().equals(AObjectType.NOTE)) {
            if (jsoActivity.has("to")) {
                JSONObject to = jsoActivity.optJSONObject("to");
                if ( to != null) {
                    activity.getNote().addRecipient(actorFromJson(to));
                } else {
                    JSONArray arrayOfTo = jsoActivity.optJSONArray("to");
                    if (arrayOfTo != null && arrayOfTo.length() > 0) {
                        for (int ind = 0; ind < arrayOfTo.length(); ind++) {
                            Actor recipient = actorFromJson(arrayOfTo.optJSONObject(ind));
                            activity.getNote().addRecipient(
                                    ConnectionPumpio.PUBLIC_COLLECTION_ID.equals(recipient.oid)
                                            ? Actor.PUBLIC
                                            : recipient
                            );
                        }
                    }
                }
            }
            setVia(activity.getNote(), jsoActivity);
            if(activity.getAuthor().isEmpty()) {
                activity.setAuthor(activity.getActor());
            }
        }
        return activity;
    }

    private AActivity parseObjectOfActivity(AActivity activity, JSONObject objectOfActivity) throws ConnectionException {
        if (PObjectType.PERSON.isTypeOf(objectOfActivity)) {
            activity.setObjActor(actorFromJson(objectOfActivity));
        } else if (PObjectType.compatibleWith(objectOfActivity) == PObjectType.COMMENT) {
            noteFromJsonComment(activity, objectOfActivity);
        }
        return activity;
    }

    private void setVia(Note note, JSONObject activity) throws JSONException {
        if (TextUtils.isEmpty(note.via) && activity.has(Properties.GENERATOR.code)) {
            JSONObject generator = activity.getJSONObject(Properties.GENERATOR.code);
            if (generator.has(NAME_PROPERTY)) {
                note.via = generator.getString(NAME_PROPERTY);
            }
        }
    }

    private void noteFromJsonComment(AActivity parentActivity, JSONObject jso) throws ConnectionException {
        try {
            String oid = jso.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio object has no id:" + jso.toString(2));
                return;
            }
            long updatedDate = dateFromJson(jso, "updated");
            if (updatedDate == 0) {
                updatedDate = dateFromJson(jso, "published");
            }
            final AActivity noteActivity = AActivity.newPartialNote(data.getAccountActor(),
                    jso.has("author") ? actorFromJson(jso.getJSONObject("author")) : Actor.EMPTY,
                    oid,
                    updatedDate, DownloadStatus.LOADED);

            final AActivity activity;
            switch (parentActivity.type) {
                case UPDATE:
                case CREATE:
                case DELETE:
                    activity = parentActivity;
                    activity.setNote(noteActivity.getNote());
                    if (activity.getActor().isEmpty()) {
                        MyLog.d(this, "No Actor in outer activity " + activity);
                        activity.setActor(noteActivity.getActor());
                    }
                    break;
                default:
                    activity = noteActivity;
                    parentActivity.setActivity(noteActivity);
                    break;
            }

            Note note =  activity.getNote();
            note.setName(jso.optString(NAME_PROPERTY));
            note.setContent(jso.optString(CONTENT_PROPERTY));

            setVia(note, jso);
            note.url = jso.optString("url");

            if (jso.has(VIDEO_OBJECT)) {
                Uri uri = UriUtils.fromJson(jso, VIDEO_OBJECT + "/url");
                Attachment mbAttachment =  Attachment.fromUriAndContentType(uri, MyContentType.VIDEO.generalMimeType);
                if (mbAttachment.isValid()) {
                    note.attachments.add(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid video attachment; " + jso.toString());
                }
            }
            if (jso.has(FULL_IMAGE_OBJECT) || jso.has(IMAGE_OBJECT)) {
                Uri uri = UriUtils.fromAlternativeTags(jso, FULL_IMAGE_OBJECT + "/url", IMAGE_OBJECT + "/url");
                Attachment mbAttachment =  Attachment.fromUriAndContentType(uri, MyContentType.IMAGE.generalMimeType);
                if (mbAttachment.isValid()) {
                    note.attachments.add(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid image attachment; " + jso.toString());
                }
            }

            // If the Msg is a Reply to other note
            if (jso.has("inReplyTo")) {
                note.setInReplyTo(activityFromJson(jso.getJSONObject("inReplyTo")));
            }

            if (jso.has("replies")) {
                JSONObject replies = jso.getJSONObject("replies");
                if (replies.has("items")) {
                    JSONArray jArr = replies.getJSONArray("items");
                    for (int index = 0; index < jArr.length(); index++) {
                        try {
                            AActivity item = activityFromJson(jArr.getJSONObject(index));
                            note.replies.add(item);
                        } catch (JSONException e) {
                            throw ConnectionException.loggedJsonException(this,
                                    "Parsing list of replies", e, null);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing comment", e, jso);
        }
    }
    
    /**
     * 2014-01-22 According to the crash reports, actorId may not have "acct:" prefix
     */
    public String actorOidToUsername(String actorId) {
        String username = "";
        if (!TextUtils.isEmpty(actorId)) {
            int indexOfColon = actorId.indexOf(':');
            if (indexOfColon >= 0) {
                username = actorId.substring(indexOfColon+1);
            } else {
                username = actorId;
            }
        }
        return username;
    }
    
    public String usernameToNickname(String username) {
        String nickname = "";
        if (!TextUtils.isEmpty(username)) {
            int indexOfAt = username.indexOf('@');
            if (indexOfAt > 0) {
                nickname = username.substring(0, indexOfAt);
            }
        }
        return nickname;
    }

    public String usernameToHost(String username) {
        String host = "";
        if (!TextUtils.isEmpty(username)) {
            int indexOfAt = username.indexOf('@');
            if (indexOfAt >= 0) {
                host = username.substring(indexOfAt + 1);
            }
        }
        return host;
    }
    
    @NonNull
    @Override
    public List<AActivity> searchNotes(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public AActivity follow(String actorOid, Boolean follow) throws ConnectionException {
        return actOnActor(follow ? PActivityType.FOLLOW : PActivityType.STOP_FOLLOWING, actorOid);
    }

    private AActivity actOnActor(PActivityType activityType, String actorId) throws ConnectionException {
        return ActivitySender.fromId(this, actorId).send(activityType);
    }
    
    @Override
    public Actor getActor(String actorOid, String username) throws ConnectionException {
        ConnectionAndUrl conu = getConnectionAndUrlForUsername(ApiRoutineEnum.GET_ACTOR,
                UriUtils.isRealOid(actorOid) ? actorOidToUsername(actorOid) : username);
        JSONObject jso = conu.httpConnection.getRequest(conu.url);
        Actor actor = actorFromJson(jso);
        MyLog.v(this, "getActor oid='" + actorOid + "', username='" + username + "' -> " + actor.getRealName());
        return actor;
    }

    protected OriginConnectionData getData() {
        return data;
    }

}
