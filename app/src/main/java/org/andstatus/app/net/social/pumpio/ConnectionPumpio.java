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

import androidx.annotation.NonNull;

import org.andstatus.app.account.AccountConnectionData;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.InputTimelinePage;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.origin.OriginPumpio;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.ObjectOrId;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import io.vavr.control.Try;

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
    static final String FULL_IMAGE_OBJECT = "fullImage";

    @Override
    public Connection setAccountConnectionData(AccountConnectionData connectionData) {
        final String host = connectionData.getAccountActor().getConnectionHost();
        if (StringUtil.nonEmpty(host)) {
            connectionData.setOriginUrl(UrlUtils.buildUrl(host, connectionData.isSsl()));
        }
        return super.setAccountConnectionData(connectionData);
    }

    @NonNull
    @Override
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "whoami";
                break;
            case GET_FOLLOWERS:
            case GET_FOLLOWERS_IDS:
                url = "user/%username%/followers";
                break;
            case GET_FRIENDS:
            case GET_FRIENDS_IDS:
                url = "user/%username%/following";
                break;
            case GET_ACTOR:
                url = "user/%username%/profile";
                break;
            case HOME_TIMELINE:
                url = "user/%username%/inbox";
                break;
            case LIKED_TIMELINE:
                url = "user/%username%/favorites";
                break;
            case UPLOAD_MEDIA:
                url = "user/%username%/uploads";
                break;
            case LIKE:
            case UNDO_LIKE:
            case FOLLOW:
            case UPDATE_PRIVATE_NOTE:
            case ANNOUNCE:
            case DELETE_NOTE:
            case UPDATE_NOTE:
            case ACTOR_TIMELINE:
                url = "user/%username%/feed";
                break;
            default:
                url = "";
                break;
        }
        return partialPathToApiPath(url);
    }

    @Override
    @NonNull
    public Try<Actor> verifyCredentials(Optional<Uri> whoAmI) {
        return TryUtils.fromOptional(whoAmI)
            .filter(UriUtils::isDownloadable)
            .orElse(() -> getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS))
            .flatMap(this::getRequest)
            .map(this::actorFromJson);
    }

    @NonNull
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        GroupType groupType;
        switch (PObjectType.fromJson(jso)) {
            case PERSON:
                groupType = GroupType.NOT_A_GROUP;
                break;
            case COLLECTION:
                groupType = GroupType.GENERIC;
                break;
            default:
                return Actor.EMPTY;
        }
        String oid = JsonUtils.optString(jso, "id");
        Actor actor = Actor.fromTwoIds(data.getOrigin(), groupType, 0, oid);
        String username = JsonUtils.optString(jso, "preferredUsername");
        actor.setUsername(StringUtil.isEmpty(username) ? actorOidToUsername(oid) : username);
        actor.setRealName(JsonUtils.optString(jso, NAME_PROPERTY));
        actor.setAvatarUrl(JsonUtils.optStringInside(jso, "image", "url"));
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY);
        actor.setSummary(JsonUtils.optString(jso, "summary"));
        actor.setHomepage(JsonUtils.optString(jso, "url"));
        actor.setProfileUrl(JsonUtils.optString(jso, "url"));
        actor.setUpdatedDate(dateFromJson(jso, "updated"));
        JSONObject pumpIo = jso.optJSONObject("pump_io");
        if (pumpIo != null && !pumpIo.isNull("followed")) {
            actor.isMyFriend = TriState.fromBoolean(pumpIo.optBoolean("followed"));
        }
        JSONObject links = jso.optJSONObject("links");
        if (links != null) {
            actor.endpoints.add(ActorEndpointType.API_PROFILE, JsonUtils.optStringInside(links, "self", "href"))
            .add(ActorEndpointType.API_INBOX, JsonUtils.optStringInside(links, "activity-inbox", "href"))
            .add(ActorEndpointType.API_OUTBOX, JsonUtils.optStringInside(links, "activity-outbox", "href"));
        }
        actor.endpoints.add(ActorEndpointType.API_FOLLOWING, JsonUtils.optStringInside(jso, "following", "url"))
            .add(ActorEndpointType.API_FOLLOWERS, JsonUtils.optStringInside(jso, "followers", "url"))
            .add(ActorEndpointType.API_LIKED, JsonUtils.optStringInside(jso, "favorites", "url"));
        return actor.build();
    }

    private Actor actorFromOid(String id) {
        return Actor.fromOid(data.getOrigin(), id);
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }
    
    @Override
    public Try<AActivity> undoLike(String noteOid) {
        return actOnNote(PActivityType.UNFAVORITE, noteOid);
    }

    @Override
    public Try<AActivity> like(String noteOid) {
        return actOnNote(PActivityType.FAVORITE, noteOid);
    }

    @Override
    public Try<Boolean> deleteNote(String noteOid) {
        return actOnNote(PActivityType.DELETE, noteOid).map(AActivity::nonEmpty);
    }

    private Try<AActivity> actOnNote(PActivityType activityType, String noteId) {
        return ActivitySender.fromId(this, noteId).send(activityType);
    }

    @Override
    public Try<List<Actor>> getFollowers(Actor actor) {
        return getActors(actor, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public Try<List<Actor>> getFriends(Actor actor) {
        return getActors(actor, ApiRoutineEnum.GET_FRIENDS);
    }

    @NonNull
    private Try<List<Actor>> getActors(Actor actor, ApiRoutineEnum apiRoutine) {
        int limit = 200;
        return ConnectionAndUrl.fromActor(this, apiRoutine, actor)
                .flatMap(conu -> {
                    Uri.Builder builder = conu.uri.buildUpon();
                    builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
                    Uri uri = builder.build();
                    return conu.httpConnection.getRequestAsArray(uri)
                            .map(jArr -> jsonArrayToActors(apiRoutine, uri, jArr));
                });
    }

    private List<Actor> jsonArrayToActors(ApiRoutineEnum apiRoutine, Uri uri, JSONArray jArr) throws ConnectionException {
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
        MyLog.d(TAG, apiRoutine + " '" + uri + "' " + actors.size() + " actors");
        return actors;
    }

    @Override
    protected Try<AActivity> getNote1(String noteOid) {
        return getRequest(UriUtils.fromString(noteOid)).map(this::activityFromJson);
    }

    @Override
    public Try<AActivity> updateNote(Note note, String inReplyToOid, Attachments attachments) {
        ActivitySender sender = ActivitySender.fromContent(this, note);
        sender.setInReplyTo(inReplyToOid);
        sender.setAttachments(attachments);
        return sender.send(PActivityType.POST);
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
        if (StringUtil.isEmpty(objectType)) {
            objectType = "unknown object type: " + oid;
            MyLog.e(this, objectType);
        }
        return objectType;
    }

    @Override
    public Try<AActivity> announce(String rebloggedNoteOid) {
        return actOnNote(PActivityType.SHARE, rebloggedNoteOid);
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> getTimeline(boolean syncYounger, ApiRoutineEnum apiRoutine,
                  TimelinePosition youngestPosition, TimelinePosition oldestPosition, int limit, Actor actor) {
        Try<ConnectionAndUrl> tryConu = ConnectionAndUrl.fromActor(this, apiRoutine, actor);
        if (tryConu.isFailure()) return tryConu.map(any -> InputTimelinePage.EMPTY);

        ConnectionAndUrl conu = tryConu.get();
        Uri.Builder builder = conu.uri.buildUpon();
        if (youngestPosition.nonEmpty()) {
            // The "since" should point to the "Activity" on the timeline, not to the note
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("since", youngestPosition.getPosition());
        } else if (oldestPosition.nonEmpty()) {
            builder.appendQueryParameter("before", oldestPosition.getPosition());
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        return conu.httpConnection.getRequestAsArray(builder.build()).map(jArr -> {
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
            MyLog.d(TAG, "getTimeline '" + builder.build() + "' " + activities.size() + " activities");
            return InputTimelinePage.of(activities);
        });
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

        final PActivityType verb = PActivityType.load(JsonUtils.optString(jsoActivity, "verb"));
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
        String oid = JsonUtils.optString(jsoActivity, "id");
        if (StringUtil.isEmpty(oid)) {
            MyLog.d(this, "Pumpio activity has no id:" + jsoActivity.toString(2));
            return AActivity.EMPTY;
        }
        activity.setOid(oid);
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
            setAudience(activity, jsoActivity);
            setVia(activity.getNote(), jsoActivity);
            if(activity.getAuthor().isEmpty()) {
                activity.setAuthor(activity.getActor());
            }
        }
        return activity;
    }

    private void setAudience(AActivity activity, JSONObject jso) {
        Audience audience = activity.getNote().audience();
        audience.setPublic(TriState.FALSE);
        ObjectOrId.of(jso, "to")
                .mapAll(this::actorFromJson, this::actorFromOid)
                .forEach(o -> addRecipient(o, audience));
        ObjectOrId.of(jso, "cc")
                .mapAll(this::actorFromJson, this::actorFromOid)
                .forEach(o -> addRecipient(o, audience));
    }

    private void addRecipient(Actor recipient, Audience audience) {
        audience.add(
                PUBLIC_COLLECTION_ID.equals(recipient.oid)
                        ? Actor.PUBLIC
                        : recipient);
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
        if (StringUtil.isEmpty(note.via) && activity.has(Properties.GENERATOR.code)) {
            JSONObject generator = activity.getJSONObject(Properties.GENERATOR.code);
            if (generator.has(NAME_PROPERTY)) {
                note.via = generator.getString(NAME_PROPERTY);
            }
        }
    }

    private void noteFromJsonComment(AActivity parentActivity, JSONObject jso) throws ConnectionException {
        try {
            String oid = JsonUtils.optString(jso, "id");
            if (StringUtil.isEmpty(oid)) {
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
            note.setName(JsonUtils.optString(jso, NAME_PROPERTY));
            note.setContentPosted(JsonUtils.optString(jso, CONTENT_PROPERTY));

            setVia(note, jso);
            note.url = JsonUtils.optString(jso, "url");

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

            if (jso.has(VIDEO_OBJECT)) {
                Uri uri = UriUtils.fromJson(jso, VIDEO_OBJECT + "/url");
                Attachment mbAttachment =  Attachment.fromUriAndMimeType(uri, MyContentType.VIDEO.generalMimeType);
                if (mbAttachment.isValid()) {
                    activity.addAttachment(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid video attachment; " + jso.toString());
                }
            }
            if (jso.has(FULL_IMAGE_OBJECT) || jso.has(IMAGE_OBJECT)) {
                Uri uri = UriUtils.fromAlternativeTags(jso, FULL_IMAGE_OBJECT + "/url", IMAGE_OBJECT + "/url");
                Attachment mbAttachment =  Attachment.fromUriAndMimeType(uri, MyContentType.IMAGE.generalMimeType);
                if (mbAttachment.isValid()) {
                    activity.addAttachment(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid image attachment; " + jso.toString());
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
        if (StringUtil.isEmpty(actorId)) return "";

        return UriUtils.toOptional(actorId)
                .map(Uri::getPath)
                .map(stripBefore("/api"))
                .map(stripBefore("/"))
            .orElse(Optional.of(actorId)
                .map(stripBefore(":"))
                .map(stripAfter("@"))
                .orElse("")
            );
    }

    @NonNull
    public static UnaryOperator<String> stripBefore(String prefixEnd) {
        return value -> {
            if (StringUtil.isEmpty(value)) return "";

            int index = value.indexOf(prefixEnd);
            return (index >= 0)
                    ? value.substring(index + prefixEnd.length())
                    : value;
        };
    }

    @NonNull
    public static UnaryOperator<String> stripAfter(String suffixStart) {
        return value -> {
            if (StringUtil.isEmpty(value)) return "";

            int index = value.indexOf(suffixStart);
            return (index >= 0)
                    ? value.substring(0, index)
                    : value;
        };
    }

    public String actorOidToHost(String actorId) {
        if (StringUtil.isEmpty(actorId)) return "";

        int indexOfAt = actorId.indexOf('@');
        return (indexOfAt < 0) ? "" : actorId.substring(indexOfAt + 1);
    }

    @Override
    public Try<AActivity> follow(String actorOid, Boolean follow) {
        return actOnActor(follow ? PActivityType.FOLLOW : PActivityType.STOP_FOLLOWING, actorOid);
    }

    private Try<AActivity> actOnActor(PActivityType activityType, String actorId) {
        return ActivitySender.fromId(this, actorId).send(activityType);
    }
    
    @Override
    public Try<Actor> getActor2(Actor actorIn) {
        return ConnectionAndUrl
        .fromActor(this, ApiRoutineEnum.GET_ACTOR, actorIn)
        .flatMap(ConnectionAndUrl::getRequest)
        .map(this::actorFromJson);
    }

}
