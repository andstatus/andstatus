/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social.activitypub;

import android.net.Uri;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.ObjectOrId;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public class ConnectionActivityPub extends Connection {
    private static final String TAG = ConnectionActivityPub.class.getSimpleName();
    static final String PUBLIC_COLLECTION_ID = "https://www.w3.org/ns/activitystreams#Public";
    static final String APPLICATION_ID = "http://andstatus.org/andstatus";
    static final String NAME_PROPERTY = "name";
    static final String CONTENT_PROPERTY = "content";
    static final String VIDEO_OBJECT = "stream";
    static final String IMAGE_OBJECT = "image";
    public static final String FULL_IMAGE_OBJECT = "fullImage";

    @Override
    public void enrichConnectionData(OriginConnectionData connectionData) {
        super.enrichConnectionData(connectionData);
        final String host = connectionData.getAccountActor().getHost();
        if (StringUtils.nonEmpty(host)) {
            connectionData.setOriginUrl(UrlUtils.buildUrl(host, connectionData.isSsl()));
        }
    }

    @NonNull
    @Override
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "ap/whoami";
                break;
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    @NonNull
    public Actor verifyCredentials() throws ConnectionException {
        JSONObject actor = getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return actorFromJson(actor);
    }

    @NonNull
    protected Actor actorFromJson(JSONObject jso) {
        if (!ApObjectType.PERSON.isTypeOf(jso)) {
            return Actor.EMPTY;
        }
        Actor actor = actorFromOid(jso.optString("id"));
        actor.setUsername(jso.optString("preferredUsername"));
        actor.setRealName(jso.optString(NAME_PROPERTY));
        actor.setAvatarUrl(JsonUtils.optStringInside(jso, "icon", "url"));
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY);
        actor.setSummary(jso.optString("summary"));
        actor.setHomepage(jso.optString("url"));
        actor.setProfileUrl(jso.optString("url"));
        actor.setUpdatedDate(dateFromJson(jso, "updated"));
        actor.endpoints
            .add(ActorEndpointType.API_PROFILE, jso.optString("id"))
            .add(ActorEndpointType.API_INBOX, jso.optString("inbox"))
            .add(ActorEndpointType.API_OUTBOX, jso.optString("outbox"))
            .add(ActorEndpointType.API_FOLLOWING, jso.optString("following"))
            .add(ActorEndpointType.API_FOLLOWERS, jso.optString("followers"))
            .add(ActorEndpointType.BANNER, JsonUtils.optStringInside(jso, "image", "url"))
            .add(ActorEndpointType.API_SHARED_INBOX, JsonUtils.optStringInside(jso, "endpoints", "sharedInbox"));
        return actor;
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }

    @Override
    public AActivity undoLike(String noteOid) throws ConnectionException {
        return actOnNote(ActivityType.UNDO_LIKE, noteOid);
    }

    @Override
    public AActivity like(String noteOid) throws ConnectionException {
        return actOnNote(ActivityType.LIKE, noteOid);
    }

    @Override
    public boolean deleteNote(String noteOid) throws ConnectionException {
        return !actOnNote(ActivityType.DELETE, noteOid).isEmpty();
    }

    private AActivity actOnNote(ActivityType activityType, String noteId) throws ConnectionException {
        return ActivitySender.fromId(this, noteId).send(activityType);
    }

    @Override
    public List<Actor> getFollowers(Actor actor) throws ConnectionException {
        return getActors(actor, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public List<Actor> getFriends(Actor actor) throws ConnectionException {
        return getActors(actor, ApiRoutineEnum.GET_FRIENDS);
    }

    @NonNull
    private List<Actor> getActors(Actor actor, ApiRoutineEnum apiRoutine) throws ConnectionException {
        int limit = 200;
        ConnectionAndUrl conu = ConnectionAndUrl.getConnectionAndUrl(this, apiRoutine, actor);
        Uri.Builder builder = conu.uri.buildUpon();
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = conu.httpConnection.getRequestAsArray(builder.build());
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
        MyLog.v(TAG, () -> apiRoutine + " '" + builder.build() + "' " + actors.size() + " actors");
        return actors;
    }

    @Override
    protected AActivity getNote1(String noteOid) throws ConnectionException {
        return activityFromJson(getRequest(UriUtils.fromString(noteOid)));
    }

    @Override
    public AActivity updateNote(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        ActivitySender sender = ActivitySender.fromContent(this, noteOid, audience, name, content);
        sender.setInReplyTo(inReplyToOid);
        sender.setMediaUri(mediaUri);
        return sender.send(ActivityType.CREATE);
    }

    String oidToObjectType(String oid) {
        String objectType = "";
        if (oid.contains("/comment/")) {
            objectType = "comment";
        } else if (oid.contains("/users/")) {
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
        if (StringUtils.isEmpty(objectType)) {
            objectType = "unknown object type: " + oid;
            MyLog.e(this, objectType);
        }
        return objectType;
    }

    @Override
    public AActivity announce(String rebloggedNoteOid) throws ConnectionException {
        return actOnNote(ActivityType.ANNOUNCE, rebloggedNoteOid);
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, Actor actor)
            throws ConnectionException {
        ConnectionAndUrl conu = ConnectionAndUrl.getConnectionAndUrl(this, apiRoutine, actor);
        Uri.Builder builder = conu.uri.buildUpon();
        if (youngestPosition.nonEmpty()) {
            // The "since" should point to the "Activity" on the timeline, not to the note
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("min_id", youngestPosition.getPosition());
        } else if (oldestPosition.nonEmpty()) {
            builder.appendQueryParameter("max_id", oldestPosition.getPosition());
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        JSONObject root = conu.httpConnection.getRequest(builder.build());
        List<AActivity> activities = new ArrayList<>();
        if (root != null) {
            JSONObject page = root.optJSONObject("first");
            if (page != null) {
                JSONArray jArr = page.optJSONArray("orderedItems");
                if (jArr != null) {
                    // Read the activities in the chronological order
                    for (int index = jArr.length() - 1; index >= 0; index--) {
                        AActivity item = activityFromJson(ObjectOrId.of(jArr, index));
                        if (item != AActivity.EMPTY) {
                            activities.add(item);
                        }
                    }
                }
            }
        }
        MyLog.d(TAG, "getTimeline '" + builder.build() + "' " + activities.size() + " activities");
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

        final ActivityType activityType = ActivityType.from(jsoActivity.optString("type"));
        AActivity activity = AActivity.from(data.getAccountActor(),
                activityType == ActivityType.EMPTY ? ActivityType.UPDATE : activityType);
        try {
            if (ApObjectType.ACTIVITY.isTypeOf(jsoActivity)) {
                return parseActivity(activity, jsoActivity);
            } else {
                return parseObjectOfActivity(activity, jsoActivity);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing activity", e, jsoActivity);
        }
    }

    @NonNull
    private AActivity activityFromJson(ObjectOrId objectOrId) throws ConnectionException {
        if (objectOrId.id.isPresent()) {
            return AActivity.newPartialNote(data.getAccountActor(), Actor.EMPTY, objectOrId.id.get());
        } else if (objectOrId.object.isPresent()) {
            return activityFromJson(objectOrId.object.get());
        } else {
            return AActivity.EMPTY;
        }
    }

    private AActivity parseActivity(AActivity activity, JSONObject jsoActivity) throws JSONException, ConnectionException {
        String oid = jsoActivity.optString("id");
        if (StringUtils.isEmpty(oid)) {
            MyLog.d(this, "Activity has no id:" + jsoActivity.toString(2));
            return AActivity.EMPTY;
        }
        activity.setTimelinePosition(oid);
        activity.setUpdatedDate(updatedOrCreatedDate(jsoActivity));

        ObjectOrId.of(jsoActivity, "actor")
            .ifObject(o -> activity.setActor(actorFromJson(o)))
            .ifId(id -> activity.setActor(actorFromOid(id)));

        ObjectOrId object = ObjectOrId.of(jsoActivity, "object")
            .ifId(id -> {
           switch (ApObjectType.fromId(activity.type, id)) {
               case PERSON:
                   activity.setObjActor(actorFromOid(id));
                   break;
               case NOTE:
                   activity.setNote(Note.fromOriginAndOid(data.getOrigin(), id, DownloadStatus.UNKNOWN));
                   break;
               default:
                   MyLog.w(this, "Unknown type of id:" + id);
                   break;
           }
        }).ifObject(objectOfActivity -> {
            if (ApObjectType.ACTIVITY.isTypeOf(objectOfActivity)) {
                // Simplified dealing with nested activities
                AActivity innerActivity = activityFromJson(objectOfActivity);
                activity.setObjActor(innerActivity.getObjActor());
                activity.setNote(innerActivity.getNote());
            } else {
                parseObjectOfActivity(activity, objectOfActivity);
            }
        });
        if (object.error.isPresent()) throw object.error.get();

        if (activity.getObjectType().equals(AObjectType.NOTE)) {
            ObjectOrId.of(jsoActivity, "to")
                .ifObject(o -> activity.getNote().audience().add(actorFromJson(o)))
                .ifArray(arrayOfTo -> {
                    for (int ind = 0; ind < arrayOfTo.length(); ind++) {
                        ObjectOrId.of(arrayOfTo, ind)
                            .ifObject(o -> addRecipient(activity, actorFromJson(o)))
                            .ifId(id -> addRecipient(activity, actorFromOid(id)));
                    }
                });
            setVia(activity.getNote(), jsoActivity);
            if(activity.getAuthor().isEmpty()) {
                activity.setAuthor(activity.getActor());
            }
        }
        return activity;
    }

    private void addRecipient(AActivity activity, Actor recipient) {
        activity.getNote().audience().add(
                PUBLIC_COLLECTION_ID.equals(recipient.oid)
                        ? Actor.PUBLIC
                        : recipient);
    }

    private Actor actorFromOid(String id) {
        return Actor.fromOid(data.getOrigin(), id);
    }

    public long updatedOrCreatedDate(JSONObject jso) {
        long dateUpdated = dateFromJson(jso, "updated");
        if (dateUpdated > SOME_TIME_AGO) return dateUpdated;

        return dateFromJson(jso, "published");
    }

    private AActivity parseObjectOfActivity(AActivity activity, JSONObject objectOfActivity) throws ConnectionException {
        if (ApObjectType.PERSON.isTypeOf(objectOfActivity)) {
            activity.setObjActor(actorFromJson(objectOfActivity));
        } else if (ApObjectType.compatibleWith(objectOfActivity) == ApObjectType.COMMENT) {
            noteFromJsonComment(activity, objectOfActivity);
        }
        return activity;
    }

    private void setVia(Note note, JSONObject activity) throws JSONException {
        if (StringUtils.isEmpty(note.via) && activity.has("generator")) {
            JSONObject generator = activity.getJSONObject("generator");
            if (generator.has(NAME_PROPERTY)) {
                note.via = generator.getString(NAME_PROPERTY);
            }
        }
    }

    private void noteFromJsonComment(AActivity parentActivity, JSONObject jso) throws ConnectionException {
        try {
            String oid = jso.optString("id");
            if (StringUtils.isEmpty(oid)) {
                MyLog.d(TAG, "ActivityPub object has no id:" + jso.toString(2));
                return;
            }
            long updatedDate = updatedOrCreatedDate(jso);
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
            note.setContentPosted(jso.optString(CONTENT_PROPERTY));

            setVia(note, jso);
            note.url = jso.optString("url");

            if (jso.has(VIDEO_OBJECT)) {
                Uri uri = UriUtils.fromJson(jso, VIDEO_OBJECT + "/url");
                Attachment mbAttachment =  Attachment.fromUriAndMimeType(uri, MyContentType.VIDEO.generalMimeType);
                if (mbAttachment.isValid()) {
                    note.attachments.add(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid video attachment; " + jso.toString());
                }
            }
            if (jso.has(FULL_IMAGE_OBJECT) || jso.has(IMAGE_OBJECT)) {
                Uri uri = UriUtils.fromAlternativeTags(jso, FULL_IMAGE_OBJECT + "/url", IMAGE_OBJECT + "/url");
                Attachment mbAttachment =  Attachment.fromUriAndMimeType(uri, MyContentType.IMAGE.generalMimeType);
                if (mbAttachment.isValid()) {
                    note.attachments.add(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid image attachment; " + jso.toString());
                }
            }

            // If the Msg is a Reply to other note
            if (jso.has("inReplyTo")) {
                note.setInReplyTo(activityFromJson(ObjectOrId.of(jso, "inReplyTo")));
            }

            if (jso.has("replies")) {
                JSONObject replies = jso.getJSONObject("replies");
                if (replies.has("items")) {
                    JSONArray jArr = replies.getJSONArray("items");
                    for (int index = 0; index < jArr.length(); index++) {
                        AActivity item = activityFromJson(ObjectOrId.of(jArr, index));
                        if (item != AActivity.EMPTY) {
                            note.replies.add(item);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing comment", e, jso);
        }
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
        return actOnActor(follow ? ActivityType.FOLLOW : ActivityType.UNDO_FOLLOW, actorOid);
    }

    private AActivity actOnActor(ActivityType activityType, String actorId) throws ConnectionException {
        return ActivitySender.fromId(this, actorId).send(activityType);
    }

    @Override
    public Actor getActor2(Actor actorIn) throws ConnectionException {
        ConnectionAndUrl conu = ConnectionAndUrl.getConnectionAndUrlForActor(this, ApiRoutineEnum.GET_ACTOR, actorIn);
        JSONObject jso = conu.httpConnection.getRequest(conu.uri);
        return actorFromJson(jso);
    }

}
