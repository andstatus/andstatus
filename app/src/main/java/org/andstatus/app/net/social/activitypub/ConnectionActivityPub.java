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

import androidx.annotation.NonNull;

import org.andstatus.app.account.AccountConnectionData;
import org.andstatus.app.actor.GroupType;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.net.http.HttpRequest;
import org.andstatus.app.net.social.AActivity;
import org.andstatus.app.net.social.AJsonCollection;
import org.andstatus.app.net.social.AObjectType;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.net.social.ActorEndpointType;
import org.andstatus.app.net.social.ApiRoutineEnum;
import org.andstatus.app.net.social.Attachment;
import org.andstatus.app.net.social.Attachments;
import org.andstatus.app.net.social.Audience;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.ConnectionMastodon;
import org.andstatus.app.net.social.InputActorPage;
import org.andstatus.app.net.social.InputTimelinePage;
import org.andstatus.app.net.social.Note;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.net.social.Visibility;
import org.andstatus.app.origin.OriginType;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.ObjectOrId;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import static org.andstatus.app.util.RelativeTime.SOME_TIME_AGO;

public class ConnectionActivityPub extends Connection {
    private static final String TAG = ConnectionActivityPub.class.getSimpleName();
    static final String PUBLIC_COLLECTION_ID = "https://www.w3.org/ns/activitystreams#Public";
    static final String APPLICATION_ID = "http://andstatus.org/andstatus";
    static final String NAME_PROPERTY = "name";
    static final String SUMMARY_PROPERTY = "summary";
    static final String SENSITIVE_PROPERTY = "sensitive";
    static final String CONTENT_PROPERTY = "content";
    static final String VIDEO_OBJECT = "stream";
    static final String IMAGE_OBJECT = "image";
    public static final String FULL_IMAGE_OBJECT = "fullImage";

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
        .map(uri -> HttpRequest.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri))
        .flatMap(this::execute)
        .recoverWith(Exception.class, this::mastodonsHackToVerifyCredentials)
        .flatMap(HttpReadResult::getJsonObject)
        .map(this::actorFromJson);
    }

    /** @return  original error, if this Mastodon's hack didn't work */
    private Try<HttpReadResult> mastodonsHackToVerifyCredentials(Exception originalException) {
        // Get Username first by partially parsing Mastodon's non-ActivityPub response
        Try<String> userNameInMastodon = Try.success(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS)
        .map(ConnectionMastodon::partialPath)
        .map(OriginType.MASTODON::partialPathToApiPath)
        .flatMap(this::pathToUri)
        .map(uri -> HttpRequest.of(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS, uri))
        .flatMap(this::execute)
        .flatMap(HttpReadResult::getJsonObject)
        .map(jso -> JsonUtils.optString(jso, "username"))
        .filter(StringUtil::nonEmpty);

        // Now build the Actor's Uri artificially using Mastodon's known URL pattern
        return userNameInMastodon.map(username -> "users/" + username)
        .flatMap(this::pathToUri)
        .map(uri -> HttpRequest.of(ApiRoutineEnum.GET_ACTOR, uri))
        .flatMap(this::execute)
        .recoverWith(Exception.class, newException -> Try.failure(originalException));
    }

    @NonNull
    private Actor actorFromJson(JSONObject jso) {
        switch (ApObjectType.fromJson(jso)) {
            case PERSON:
                return actorFromPersonTypeJson(jso);
            case COLLECTION:
            case ORDERED_COLLECTION:
                return actorFromCollectionTypeJson(jso);
            case UNKNOWN:
                return Actor.EMPTY;
            default:
                MyLog.w(TAG, "Unexpected object type for Actor: " + ApObjectType.fromJson(jso) + ", JSON:\n" + jso);
                return Actor.EMPTY;
        }
    }

    @NonNull
    private Actor actorFromCollectionTypeJson(JSONObject jso) {
        Actor actor = Actor.fromTwoIds(data.getOrigin(), GroupType.COLLECTION, 0, JsonUtils.optString(jso, "id"));
        return actor.build();
    }

    @NonNull
    private Actor actorFromPersonTypeJson(JSONObject jso) {
        Actor actor = actorFromOid(JsonUtils.optString(jso, "id"));
        actor.setUsername(JsonUtils.optString(jso, "preferredUsername"));
        actor.setRealName(JsonUtils.optString(jso, NAME_PROPERTY));
        actor.setAvatarUrl(JsonUtils.optStringInside(jso, "icon", "url"));
        actor.location = JsonUtils.optStringInside(jso, "location", NAME_PROPERTY);
        actor.setSummary(JsonUtils.optString(jso, "summary"));
        actor.setHomepage(JsonUtils.optString(jso, "url"));
        actor.setProfileUrl(JsonUtils.optString(jso, "url"));
        actor.setUpdatedDate(dateFromJson(jso, "updated"));
        actor.endpoints
                .add(ActorEndpointType.API_PROFILE, JsonUtils.optString(jso, "id"))
                .add(ActorEndpointType.API_INBOX, JsonUtils.optString(jso, "inbox"))
                .add(ActorEndpointType.API_OUTBOX, JsonUtils.optString(jso, "outbox"))
                .add(ActorEndpointType.API_FOLLOWING, JsonUtils.optString(jso, "following"))
                .add(ActorEndpointType.API_FOLLOWERS, JsonUtils.optString(jso, "followers"))
                .add(ActorEndpointType.BANNER, JsonUtils.optStringInside(jso, "image", "url"))
                .add(ActorEndpointType.API_SHARED_INBOX, JsonUtils.optStringInside(jso, "endpoints", "sharedInbox"))
                .add(ActorEndpointType.API_UPLOAD_MEDIA, JsonUtils.optStringInside(jso, "endpoints", "uploadMedia"));
        return actor.build();
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }

    @Override
    public Try<AActivity> undoLike(String noteOid) {
        return actOnNote(ActivityType.UNDO_LIKE, noteOid);
    }

    @Override
    public Try<AActivity> like(String noteOid) {
        return actOnNote(ActivityType.LIKE, noteOid);
    }

    @Override
    public Try<Boolean> deleteNote(String noteOid) {
        return actOnNote(ActivityType.DELETE, noteOid).map(AActivity::isEmpty);
    }

    private Try<AActivity> actOnNote(ActivityType activityType, String noteId) {
        return ActivitySender.fromId(this, noteId).send(activityType);
    }

    @Override
    public Try<InputActorPage> getFriendsOrFollowers(ApiRoutineEnum routineEnum, TimelinePosition position, Actor actor) {
        return getActors(routineEnum, position, actor);
    }

    @NonNull
    private Try<InputActorPage> getActors(ApiRoutineEnum apiRoutine, TimelinePosition position, Actor actor) {
        return ConnectionAndUrl.fromActor(this, apiRoutine, position, actor)
        .flatMap(conu -> conu.execute(conu.newRequest())
            .flatMap(HttpReadResult::getJsonObject)
            .map(jsonObject -> {
                AJsonCollection jsonCollection = AJsonCollection.of(jsonObject);
                List<Actor> actors = jsonCollection.mapAll(this::actorFromJson, this::actorFromOid);
                MyLog.v(TAG, () -> apiRoutine + " '" + conu.uri + "' " + actors.size() + " actors");
                return InputActorPage.of(jsonCollection, actors);
            })
        );
    }

    @Override
    protected Try<AActivity> getNote1(String noteOid) {
        return execute(HttpRequest.of(ApiRoutineEnum.GET_NOTE, UriUtils.fromString(noteOid)))
        .flatMap(HttpReadResult::getJsonObject)
        .map(this::activityFromJson);
    }

    @Override
    public Try<AActivity> updateNote(Note note, String inReplyToOid, Attachments attachments) {
        ActivitySender sender = ActivitySender.fromContent(this, note);
        sender.setInReplyTo(inReplyToOid);
        sender.setAttachments(attachments);
        return sender.send(ActivityType.CREATE);
    }

    @Override
    public Try<AActivity> announce(String rebloggedNoteOid) {
        return actOnNote(ActivityType.ANNOUNCE, rebloggedNoteOid);
    }

    @Override
    public boolean canGetConversation(String conversationOid) {
        Uri uri = UriUtils.fromString(conversationOid);
        return UriUtils.isDownloadable(uri);
    }

    @Override
    public Try<List<AActivity>> getConversation(String conversationOid) {
        Uri uri = UriUtils.fromString(conversationOid);
        if (UriUtils.isDownloadable(uri)) {
            return ConnectionAndUrl
            .fromUriActor(uri, this, ApiRoutineEnum.GET_CONVERSATION, data.getAccountActor())
            .flatMap(this::getActivities)
            .map(p -> p.items);
        } else {
            return super.getConversation(conversationOid);
        }
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> getTimeline(boolean syncYounger, ApiRoutineEnum apiRoutine,
                  TimelinePosition youngestPosition, TimelinePosition oldestPosition, int limit, Actor actor) {
        TimelinePosition requestedPosition = syncYounger ? youngestPosition : oldestPosition;

        // TODO: See https://github.com/andstatus/andstatus/issues/499#issuecomment-475881413
        return ConnectionAndUrl.fromActor(this, apiRoutine, requestedPosition, actor)
        .flatMap(conu -> getActivities(conu));
    }

    private Try<InputTimelinePage> getActivities(ConnectionAndUrl conu) {
        return conu.execute(conu.newRequest())
        .flatMap(HttpReadResult::getJsonObject)
        .map(root -> {
            AJsonCollection jsonCollection = AJsonCollection.of(root);
            List<AActivity> activities = jsonCollection.mapObjects(item ->
                    activityFromJson(ObjectOrId.of(item)).setTimelinePosition(jsonCollection.getId()));
            MyLog.d(TAG, "getTimeline " + conu.apiRoutine + " '" + conu.uri + "' " + activities.size() + " activities");
            return InputTimelinePage.of(jsonCollection, activities);
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

        final ActivityType activityType = ActivityType.from(JsonUtils.optString(jsoActivity, "type"));
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
        if (StringUtil.isEmptyOrTemp(oid)) return AActivity.EMPTY;
        return AActivity.from(data.getAccountActor(), ActivityType.UPDATE);
    }

    @NonNull
    private AActivity activityFromJson(ObjectOrId objectOrId) throws ConnectionException {
        if (objectOrId.id.isPresent()) {
            return AActivity.newPartialNote(data.getAccountActor(), Actor.EMPTY, objectOrId.id.get())
                .setOid(objectOrId.id.get());
        } else if (objectOrId.object.isPresent()) {
            return activityFromJson(objectOrId.object.get());
        } else {
            return AActivity.EMPTY;
        }
    }

    private AActivity parseActivity(AActivity activity, JSONObject jsoActivity) throws JSONException, ConnectionException {
        String oid = JsonUtils.optString(jsoActivity, "id");
        if (StringUtil.isEmpty(oid)) {
            MyLog.d(this, "Activity has no id:" + jsoActivity.toString(2));
            return AActivity.EMPTY;
        }
        activity.setOid(oid)
            .setUpdatedDate(updatedOrCreatedDate(jsoActivity));

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
            setAudience(activity, jsoActivity);
            if(activity.getAuthor().isEmpty()) {
                activity.setAuthor(activity.getActor());
            }
        }
        return activity;
    }

    private Try<Actor> actorFromProperty(JSONObject parentObject, String propertyName) {
        return ObjectOrId.of(parentObject, propertyName).mapOne(this::actorFromJson, this::actorFromOid);
    }

    private void setAudience(AActivity activity, JSONObject jso) {
        Audience audience = new Audience(data.getOrigin());
        ObjectOrId.of(jso, "to")
                .mapAll(this::actorFromJson, this::actorFromOid)
                .forEach(o -> addRecipient(o, audience));
        ObjectOrId.of(jso, "cc")
                .mapAll(this::actorFromJson, this::actorFromOid)
                .forEach(o -> addRecipient(o, audience));
        if (audience.hasNonSpecial()) {
            audience.addVisibility(Visibility.PRIVATE);
        }
        activity.getNote().setAudience(audience);
    }

    private void addRecipient(Actor recipient, Audience audience) {
        audience.add(
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
            if (activity.getOid().isEmpty()) {
                activity.setOid(activity.getObjActor().oid);
            }
        } else if (ApObjectType.compatibleWith(objectOfActivity) == ApObjectType.NOTE) {
            noteFromJson(activity, objectOfActivity);
            if (activity.getOid().isEmpty()) {
                activity.setOid(activity.getNote().oid);
            }
        }
        return activity;
    }

    private void noteFromJson(AActivity parentActivity, JSONObject jso) throws ConnectionException {
        try {
            String oid = JsonUtils.optString(jso, "id");
            if (StringUtil.isEmpty(oid)) {
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
            note.setName(JsonUtils.optString(jso, NAME_PROPERTY));
            note.setSummary(JsonUtils.optString(jso, SUMMARY_PROPERTY));
            note.setContentPosted(JsonUtils.optString(jso, CONTENT_PROPERTY));

            note.setConversationOid(StringUtil.optNotEmpty(JsonUtils.optString(jso, "conversation"))
                    .orElseGet(() -> JsonUtils.optString(jso, "context")));

            setAudience(activity, jso);

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

            ObjectOrId.of(jso, "attachment")
                    .mapAll(ConnectionActivityPub::attachmentFromJson, Attachment::fromUri)
                    .forEach(activity::addAttachment);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso);
        }
    }

    @NonNull
    private static Attachment attachmentFromJson(JSONObject jso) {
        return ObjectOrId.of(jso, "url")
                .mapAll(ConnectionActivityPub::attachmentFromUrlObject,
                        url -> Attachment.fromUriAndMimeType(url, JsonUtils.optString(jso, "mediaType")))
                .stream().findFirst().orElse(Attachment.EMPTY);
    }

    @NonNull
    private static Attachment attachmentFromUrlObject(JSONObject jso) {
        return Attachment.fromUriAndMimeType(JsonUtils.optString(jso, "href"), JsonUtils.optString(jso, "mediaType"));
    }

    @Override
    public Try<AActivity> follow(String actorOid, Boolean follow) {
        return actOnActor(follow ? ActivityType.FOLLOW : ActivityType.UNDO_FOLLOW, actorOid);
    }

    private Try<AActivity> actOnActor(ActivityType activityType, String actorId) {
        return ActivitySender.fromId(this, actorId).send(activityType);
    }

    @Override
    public Try<Actor> getActor2(Actor actorIn) {
        return ConnectionAndUrl
        .fromActor(this, ApiRoutineEnum.GET_ACTOR, TimelinePosition.EMPTY, actorIn)
        .flatMap(connectionAndUrl -> connectionAndUrl.execute(connectionAndUrl.newRequest()))
        .flatMap(HttpReadResult::getJsonObject)
        .map(this::actorFromJson);
    }

}
