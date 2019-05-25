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

import org.andstatus.app.account.AccountConnectionData;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AJsonCollection;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
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
import java.util.Optional;

import androidx.annotation.NonNull;
import io.vavr.control.Try;

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
    public Connection setAccountConnectionData(AccountConnectionData connectionData) {
        final String host = connectionData.getAccountActor().getConnectionHost();
        if (StringUtils.nonEmpty(host)) {
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
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    @NonNull
    public Actor verifyCredentials(Optional<Uri> whoAmI) throws ConnectionException {
        JSONObject actor = getRequest(
                whoAmI.filter(UriUtils::isDownloadable).orElse(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS))
        );
        return actorFromJson(actor);
    }

    @NonNull
    private Actor actorFromJson(JSONObject jso) {
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
        return actor.build();
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
        ConnectionAndUrl conu = ConnectionAndUrl.fromActor(this, apiRoutine, actor);
        JSONObject root = conu.httpConnection.getRequest(conu.uri);
        List<Actor> actors = AJsonCollection.of(root).mapAll(this::actorFromJson, this::actorFromOid);
        MyLog.v(TAG, () -> apiRoutine + " '" + conu.uri + "' " + actors.size() + " actors");
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

    @Override
    public AActivity announce(String rebloggedNoteOid) throws ConnectionException {
        return actOnNote(ActivityType.ANNOUNCE, rebloggedNoteOid);
    }

    @Override
    public List<AActivity> getConversation(String conversationOid) throws ConnectionException {
        Uri uri = UriUtils.fromString(conversationOid);
        if (UriUtils.isDownloadable(uri)) {
            return getActivities(ApiRoutineEnum.GET_CONVERSATION, ConnectionAndUrl
                    .fromUriActor(uri, this, ApiRoutineEnum.GET_CONVERSATION, data.getAccountActor()));
        } else {
            return super.getConversation(conversationOid);
        }
    }

    @NonNull
    @Override
    public List<AActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, Actor actor)
            throws ConnectionException {
        ConnectionAndUrl conu = ConnectionAndUrl.fromActor(this, apiRoutine, actor);
        Uri.Builder builder = conu.uri.buildUpon();
        // TODO: See https://github.com/andstatus/andstatus/issues/499#issuecomment-475881413
//        if (youngestPosition.nonEmpty()) {
//            // The "since" should point to the "Activity" on the timeline, not to the note
//            // Otherwise we will always get "not found"
//            builder.appendQueryParameter("min_id", youngestPosition.getPosition());
//        } else if (oldestPosition.nonEmpty()) {
//            builder.appendQueryParameter("max_id", oldestPosition.getPosition());
//        }
//        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        return getActivities(apiRoutine, conu.withUri(builder.build()));
    }

    private List<AActivity> getActivities(ApiRoutineEnum apiRoutine, ConnectionAndUrl conu) throws ConnectionException {
        JSONObject root = conu.httpConnection.getRequest(conu.uri);
        List<AActivity> activities = AJsonCollection.of(root)
                .mapObjects(item -> activityFromJson(ObjectOrId.of(item)));
        MyLog.d(TAG, "getTimeline " + apiRoutine + " '" + conu.uri + "' " + activities.size() + " activities");
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
    AActivity activityFromOid(String oid) {
        if (StringUtils.isEmptyOrTemp(oid)) return AActivity.EMPTY;
        return AActivity.from(data.getAccountActor(), ActivityType.UPDATE).setTimelinePosition(oid);
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

        actorFromProperty(jsoActivity, "actor").onSuccess(activity::setActor);

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
            addRecipients(activity, jsoActivity);
            if(activity.getAuthor().isEmpty()) {
                activity.setAuthor(activity.getActor());
            }
        }
        return activity;
    }

    private Try<Actor> actorFromProperty(JSONObject parentObject, String propertyName) {
        return ObjectOrId.of(parentObject, propertyName).mapOne(this::actorFromJson, this::actorFromOid);
    }

    private void addRecipients(AActivity activity, JSONObject jso) {
        ObjectOrId.of(jso, "to")
                .mapAll(this::actorFromJson, this::actorFromOid)
                .forEach(o -> addRecipient(activity, o));
        ObjectOrId.of(jso, "cc")
                .mapAll(this::actorFromJson, this::actorFromOid)
                .forEach(o -> addRecipient(activity, o));
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
            noteFromJson(activity, objectOfActivity);
        }
        return activity;
    }

    private void noteFromJson(AActivity parentActivity, JSONObject jso) throws ConnectionException {
        try {
            String oid = jso.optString("id");
            if (StringUtils.isEmpty(oid)) {
                MyLog.d(TAG, "ActivityPub object has no id:" + jso.toString(2));
                return;
            }
            Actor author = actorFromProperty(jso, "attributedTo")
                    .orElse(() -> actorFromProperty(jso, "author")).getOrElse(Actor.EMPTY);

            final AActivity noteActivity = AActivity.newPartialNote(
                    data.getAccountActor(),
                    author,
                    oid,
                    updatedOrCreatedDate(jso),
                    DownloadStatus.LOADED);

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

            note.setConversationOid(StringUtils.optNotEmpty(jso.optString("conversation"))
                    .orElseGet(() -> jso.optString("context")));

            addRecipients(activity, jso);

            ObjectOrId.of(jso, "attachment")
                .mapAll(this::attachmentFromJson, Attachment::fromUri)
                .forEach(note.attachments::add);

            // If the Note is a Reply to the other note
            ObjectOrId.of(jso, "inReplyTo")
                    .mapOne(this::activityFromJson, this::activityFromOid)
                    .onSuccess(note::setInReplyTo);

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
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso);
        }
    }

    @NonNull
    private Attachment attachmentFromJson(JSONObject jso) {
        return Attachment.fromUriAndMimeType(UriUtils.fromJson(jso, "url"), jso.optString("mediaType"));
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
        ConnectionAndUrl conu = ConnectionAndUrl.fromActor(this, ApiRoutineEnum.GET_ACTOR, actorIn);
        JSONObject jso = conu.httpConnection.getRequest(conu.uri);
        return actorFromJson(jso);
    }

}
