/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpReadResult;
import org.andstatus.app.note.KeywordsFilter;
import org.andstatus.app.origin.OriginConfig;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.ObjectOrId;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.TryUtils;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.vavr.control.CheckedFunction;
import io.vavr.control.Try;

import static org.andstatus.app.context.MyPreferences.BYTES_IN_MB;
import static org.andstatus.app.util.UriUtils.nonRealOid;

/**
 * See <a href="https://source.joinmastodon.org/mastodon/docs">API</a>
 */
public class ConnectionMastodon extends ConnectionTwitterLike {
    private static final String ATTACHMENTS_FIELD_NAME = "media_attachments";

    private static final String VISIBILITY_PROPERTY = "visibility";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String VISIBILITY_UNLISTED = "unlisted";
    private static final String VISIBILITY_PRIVATE = "private";
    private static final String VISIBILITY_DIRECT = "direct";

    private static final String SENSITIVE_PROPERTY = "sensitive";
    private static final String SUMMARY_PROPERTY = "spoiler_text";
    private static final String CONTENT_PROPERTY_UPDATE = "status";
    private static final String CONTENT_PROPERTY = "content";
    /** Only Pleroma has this, see https://github.com/tootsuite/mastodon/issues/4915 */
    private final static String TEXT_LIMIT_KEY = "max_toot_chars";

    @NonNull
    @Override
    protected String getApiPathFromOrigin(ApiRoutineEnum routine) {
        String url;
        switch (routine) {
            case GET_CONFIG:
                url = "v1/instance";  // https://docs.joinmastodon.org/api/rest/instances/
                break;
            case HOME_TIMELINE:
                url = "v1/timelines/home";
                break;
            case NOTIFICATIONS_TIMELINE:
                url = "v1/notifications";
                break;
            case LIKED_TIMELINE:
                url = "v1/favourites";
                break;
            case PUBLIC_TIMELINE:
                url = "v1/timelines/public";
                break;
            case TAG_TIMELINE:
                url = "v1/timelines/tag/%tag%";
                break;
            case ACTOR_TIMELINE:
                url = "v1/accounts/%actorId%/statuses";
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "v1/accounts/verify_credentials";
                break;
            case UPDATE_NOTE:
            case UPDATE_PRIVATE_NOTE:
                url = "v1/statuses";
                break;
            case UPLOAD_MEDIA:
                url = "v1/media";
                break;
            case GET_NOTE:
                url = "v1/statuses/%noteId%";
                break;
            case SEARCH_NOTES:
                url = "v1/search"; /* actually, this is a complex search "for content" */
                break;
            case SEARCH_ACTORS:
                url = "v1/accounts/search";
                break;
            case GET_CONVERSATION:
                url = "v1/statuses/%noteId%/context";
                break;
            case LIKE:
                url = "v1/statuses/%noteId%/favourite";
                break;
            case UNDO_LIKE:
                url = "v1/statuses/%noteId%/unfavourite";
                break;
            case FOLLOW:
                url = "v1/accounts/%actorId%/follow";
                break;
            case UNDO_FOLLOW:
                url = "v1/accounts/%actorId%/unfollow";
                break;
            case GET_FOLLOWERS:
                url = "v1/accounts/%actorId%/followers";
                break;
            case GET_FRIENDS:
                url = "v1/accounts/%actorId%/following";
                break;
            case GET_ACTOR:
                url = "v1/accounts/%actorId%";
                break;
            case ANNOUNCE:
                url = "v1/statuses/%noteId%/reblog";
                break;
            case UNDO_ANNOUNCE:
                url = "v1/statuses/%noteId%/unreblog";
                break;
            default:
                url = "";
                break;
        }

        return partialPathToApiPath(url);
    }

    @NonNull
    @Override
    protected Try<Uri.Builder> getTimelineUriBuilder(ApiRoutineEnum apiRoutine, int limit, Actor actor) {
        return this.getApiPathWithActorId(apiRoutine, actor.oid)
            .map(Uri::buildUpon)
            .map(b -> b.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)));
    }

    @Override
    protected AActivity activityFromTwitterLikeJson(JSONObject timelineItem) throws ConnectionException {
        if (isNotification(timelineItem)) {
            AActivity activity = AActivity.from(data.getAccountActor(), getType(timelineItem));
            activity.setTimelinePosition(timelineItem.optString("id"));
            activity.setUpdatedDate(dateFromJson(timelineItem, "created_at"));
            activity.setActor(actorFromJson(timelineItem.optJSONObject("account")));
            AActivity noteActivity = activityFromJson2(timelineItem.optJSONObject("status"));

            switch (activity.type) {
                case LIKE:
                case UNDO_LIKE:
                case ANNOUNCE:
                case UNDO_ANNOUNCE:
                    activity.setActivity(noteActivity);
                    break;
                case FOLLOW:
                case UNDO_FOLLOW:
                    activity.setObjActor(data.getAccountActor());
                    break;
                default:
                    activity.setNote(noteActivity.getNote());
                    break;
            }
            return activity;
        } else {
            return super.activityFromTwitterLikeJson(timelineItem);
        }

    }

    @NonNull
    protected ActivityType getType(JSONObject timelineItem) {
        if (isNotification(timelineItem)) {
            switch (timelineItem.optString("type")) {
                case "favourite":
                    return ActivityType.LIKE;
                case "reblog":
                    return ActivityType.ANNOUNCE;
                case "follow":
                    return ActivityType.FOLLOW;
                case "mention":
                    return ActivityType.UPDATE;
                default:
                    return ActivityType.EMPTY;
            }
        }
        return ActivityType.UPDATE;
    }

    private boolean isNotification(JSONObject activity) {
        return activity != null && !activity.isNull("type");
    }

    @NonNull
    @Override
    public Try<InputTimelinePage> searchNotes(boolean syncYounger, TimelinePosition youngestPosition,
                                         TimelinePosition oldestPosition, int limit, String searchQuery) {
        String tag = new KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword();
        if (StringUtil.isEmpty(tag)) {
            return InputTimelinePage.TRY_EMPTY;
        }
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.TAG_TIMELINE;
        return getApiPath(apiRoutine).map(Uri::buildUpon)
                .map(b -> appendPositionParameters(b, youngestPosition, oldestPosition))
                .map(b -> b.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)))
                .map(Uri.Builder::build)
                .flatMap(uri -> http.getRequestAsArray(uri)
                    .flatMap(jsonArray -> jArrToTimeline("", jsonArray, apiRoutine, uri)))
                .map(InputTimelinePage::of);
    }

    @NonNull
    @Override
    public Try<List<Actor>> searchActors(int limit, String searchQuery) {
        String tag = new KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword();
        if (StringUtil.isEmpty(tag)) {
            return TryUtils.emptyList();
        }

        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_ACTORS;
        return getApiPath(apiRoutine).map(Uri::buildUpon)
        .map(b -> b.appendQueryParameter("q", searchQuery)
                .appendQueryParameter("resolve", "true")
                .appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)))
        .map(Uri.Builder::build)
        .flatMap(uri -> http.getRequestAsArray(uri).flatMap(jsonArray -> jArrToActors(jsonArray, apiRoutine, uri)));
    }

    // TODO: Delete ?
    protected Try<Uri> getApiPathWithTag(ApiRoutineEnum routineEnum, String tag) {
        return getApiPath(routineEnum).map(uri -> UriUtils.map(uri, s -> s.replace("%tag%", tag)));
    }

    @Override
    public Try<List<AActivity>> getConversation(String conversationOid) {
        if (nonRealOid(conversationOid)) {
            return TryUtils.emptyList();
        }
        return getApiPathWithNoteId(ApiRoutineEnum.GET_CONVERSATION, conversationOid)
            .flatMap(uri -> getRequest(uri)
                .map(mastodonContext -> getConversationActivities(uri, mastodonContext, conversationOid)));
    }

    private List<AActivity> getConversationActivities(Uri uri, JSONObject mastodonContext, String conversationOid)
            throws ConnectionException {
        List<AActivity> timeline = new ArrayList<>();
        try {
            String ancestors = "ancestors";
            if (mastodonContext.has(ancestors)) {
                jArrToTimeline(ancestors, mastodonContext.getJSONArray(ancestors), ApiRoutineEnum.GET_CONVERSATION, uri)
                    .onSuccess( timeline::addAll);
            }
            String descendants = "descendants";
            if (mastodonContext.has(descendants)) {
                jArrToTimeline(descendants, mastodonContext.getJSONArray(descendants), ApiRoutineEnum.GET_CONVERSATION, uri)
                        .onSuccess( timeline::addAll);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error getting conversation '" + conversationOid + "'",
                    e, mastodonContext);
        }
        return timeline;
    }

    @Override
    public Try<AActivity> updateNote(Note note, String inReplyToOid, Attachments attachments) {
        return updateNote2(note, inReplyToOid, attachments);
    }

    @Override
    protected Try<AActivity> updateNote2(Note note, String inReplyToOid, Attachments attachments) {
        JSONObject obj = new JSONObject();
        try {
            obj.put(SUMMARY_PROPERTY, note.getSummary());
            obj.put(VISIBILITY_PROPERTY, getVisibility(note));
            obj.put(SENSITIVE_PROPERTY, note.isSensitive());
            obj.put(CONTENT_PROPERTY_UPDATE, note.getContentToPost());
            if ( !StringUtil.isEmpty(inReplyToOid)) {
                obj.put("in_reply_to_id", inReplyToOid);
            }
            List<String> ids = new ArrayList<>();
            for (Attachment attachment : attachments.list) {
                if (UriUtils.isDownloadable(attachment.uri)) {
                    // TODO
                    MyLog.i(this, "Skipped downloadable " + attachment);
                } else {
                    Try<AActivity> uploaded = uploadMedia(attachment.uri)
                    .map( mediaObject -> {
                        if (mediaObject != null && mediaObject.has("id")) {
                            ids.add(mediaObject.get("id").toString());
                        }
                        return AActivity.EMPTY;
                    });
                    if (uploaded.isFailure()) return uploaded;
                }
            }
            if (!ids.isEmpty()) {
                obj.put("media_ids[]", ids);
            }
        } catch (JSONException e) {
            return Try.failure(ConnectionException.loggedJsonException(this, "Error updating note '" + attachments + "'", e, obj));
        }
        return postRequest(ApiRoutineEnum.UPDATE_NOTE, obj)
                .flatMap(HttpReadResult::getJsonObject)
                .map(this::activityFromJson);
    }

    private String getVisibility(Note note) {
        if (note.audience().getPublic().isTrue) {
            return VISIBILITY_PUBLIC;
        }
        return note.audience().isFollowers() ? VISIBILITY_PRIVATE : VISIBILITY_DIRECT;
    }

    private Try<JSONObject> uploadMedia(Uri mediaUri) {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "file");
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            return postRequest(ApiRoutineEnum.UPLOAD_MEDIA, formParams)
                .flatMap(HttpReadResult::getJsonObject)
                .filter(Objects::nonNull)
                .onSuccess(jso -> {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(this, "uploaded '" + mediaUri.toString() + "' " + jso.toString());
                    }
                });
        } catch (JSONException e) {
            return Try.failure(ConnectionException.loggedJsonException(this, "Error uploading '" + mediaUri + "'", e, formParams));
        }
    }

    @Override
    @NonNull
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return Actor.EMPTY;
        }
        String oid = jso.optString("id");
        String username = jso.optString("username");
        if (StringUtil.isEmpty(oid) || StringUtil.isEmpty(username)) {
            throw ConnectionException.loggedJsonException(this, "Id or username is empty", null, jso);
        }
        Actor actor = Actor.fromOid(data.getOrigin(), oid);
        actor.setUsername(username);
        actor.setRealName(jso.optString("display_name"));
        actor.setWebFingerId(jso.optString("acct"));
        if (!SharedPreferencesUtil.isEmpty(actor.getRealName())) {
            actor.setProfileUrlToOriginUrl(data.getOriginUrl());
        }
        actor.setAvatarUri(UriUtils.fromJson(jso, "avatar"));
        actor.endpoints.add(ActorEndpointType.BANNER, UriUtils.fromJson(jso, "header"));
        actor.setSummary(extractSummary(jso));
        actor.setProfileUrl(jso.optString("url"));
        actor.notesCount = jso.optLong("statuses_count");
        actor.followingCount = jso.optLong("following_count");
        actor.followersCount = jso.optLong("followers_count");
        actor.setCreatedDate(dateFromJson(jso, "created_at"));
        return actor.build();
    }

    private String extractSummary(JSONObject jso) {
        MyStringBuilder builder = new MyStringBuilder();
        builder.append(jso.optString("note"));
        JSONArray fields = jso.optJSONArray("fields");
        if (fields != null) {
            for (int ind=0; ind < fields.length(); ind++) {
                JSONObject field = fields.optJSONObject(ind);
                if (field != null) {
                    builder.append(field.optString("name"), field.optString("value"),
                            "\n<br>", false);
                }
            }
        }
        return builder.toString();
    }

    @Override
    @NonNull
    AActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return AActivity.EMPTY;
        }
        final String method = "activityFromJson2";
        String oid = jso.optString("id");
        AActivity activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"));
        try {
            JSONObject actor;
            if (jso.has("account")) {
                actor = jso.getJSONObject("account");
                activity.setActor(actorFromJson(actor));
            }

            Note note =  activity.getNote();
            note.setSummary(jso.optString(SUMMARY_PROPERTY));
            note.setSensitive(jso.optBoolean(SENSITIVE_PROPERTY));
            note.setContentPosted(jso.optString(CONTENT_PROPERTY));
            note.url = jso.optString("url");
            if (jso.has(VISIBILITY_PROPERTY)) {
                switch (jso.getString(VISIBILITY_PROPERTY)) {
                    case VISIBILITY_PUBLIC:
                    case VISIBILITY_UNLISTED:
                        note.audience().setPublic(TriState.TRUE);
                        note.audience().setFollowers(true);
                        break;
                    case VISIBILITY_PRIVATE:
                        note.audience().setPublic(TriState.FALSE);
                        note.audience().setFollowers(true);
                        break;
                    case VISIBILITY_DIRECT:
                        note.audience().setPublic(TriState.FALSE);
                        note.audience().setFollowers(false);
                        break;
                    default:
                        break;
                }
            }
            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                note.audience().add(actorFromJson(recipient));
            }
            ObjectOrId.of(jso, "mentions")
                    .mapAll(this::actorFromJson, oid1 -> Actor.EMPTY)
                    .forEach(o -> note.audience().add(o));

            if (!jso.isNull("application")) {
                JSONObject application = jso.getJSONObject("application");
                note.via = application.optString("name");
            }
            if (!jso.isNull("favourited")) {
                note.addFavoriteBy(data.getAccountActor(),
                        TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favourited"))));
            }

            // If the Msg is a Reply to other note
            String inReplyToActorOid = "";
            if (jso.has("in_reply_to_account_id")) {
                inReplyToActorOid = jso.getString("in_reply_to_account_id");
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                inReplyToActorOid = "";
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToActorOid)) {
                String inReplyToNoteOid = "";
                if (jso.has("in_reply_to_id")) {
                    inReplyToNoteOid = jso.getString("in_reply_to_id");
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToNoteOid)) {
                    // Construct Related note from available info
                    AActivity inReplyTo = AActivity.newPartialNote(data.getAccountActor(),
                            Actor.fromOid(data.getOrigin(), inReplyToActorOid), inReplyToNoteOid);
                    note.setInReplyTo(inReplyTo);
                }
            }
            ObjectOrId.of(jso, ATTACHMENTS_FIELD_NAME).mapObjects(jsonToAttachments(method))
                    .forEach(attachments -> attachments.forEach(activity::addAttachment));
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso);
        } catch (Exception e) {
            MyLog.e(this, method, e);
            return AActivity.EMPTY;
        }
        return activity;
    }

    private CheckedFunction<JSONObject, List<Attachment>> jsonToAttachments(String method) {
        return jsoAttachment -> {
            String type = StringUtil.notEmpty(jsoAttachment.optString("type"), "unknown");
            if ("unknown".equals(type)) {
                // When the type is "unknown", it is likely only remote_url is available and local url is missing
                Uri remoteUri = UriUtils.fromJson(jsoAttachment, "remote_url");
                Attachment attachment = Attachment.fromUri(remoteUri);
                if (attachment.isValid()) {
                    return Collections.singletonList(attachment);
                }
                type = "";
            }

            List<Attachment> attachments = new ArrayList<>();
            Uri uri = UriUtils.fromJson(jsoAttachment, "url");
            Attachment attachment = Attachment.fromUriAndMimeType(uri, type);
            if (attachment.isValid()) {
                attachments.add(attachment);
                Attachment preview =
                        Attachment.fromUriAndMimeType(
                                UriUtils.fromJson(jsoAttachment,"preview_url"),
                                MyContentType.IMAGE.generalMimeType)
                                .setPreviewOf(attachment);
                attachments.add(preview);
            } else {
                MyLog.d(this, method + "; invalid attachment " + jsoAttachment);
            }
            return attachments;
        };
    }

    @Override
    AActivity rebloggedNoteFromJson(@NonNull JSONObject jso) throws ConnectionException {
        return  activityFromJson2(jso.optJSONObject("reblog"));
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }

    @Override
    public Try<Actor> getActor2(Actor actorIn) {
        return getApiPathWithActorId(ApiRoutineEnum.GET_ACTOR,
                UriUtils.isRealOid(actorIn.oid) ? actorIn.oid : actorIn.getUsername())
                .flatMap(this::getRequest)
                .map(this::actorFromJson);
    }

    @Override
    public Try<AActivity> follow(String actorOid, Boolean follow) {
        Try<JSONObject> tryRelationship = getApiPathWithActorId(follow
                ? ApiRoutineEnum.FOLLOW : ApiRoutineEnum.UNDO_FOLLOW, actorOid)
            .flatMap(uri -> postRequest(uri, new JSONObject()))
            .flatMap(HttpReadResult::getJsonObject);

        return tryRelationship.map(relationship -> {
            if (relationship == null || relationship.isNull("following")) {
                return AActivity.EMPTY;
            }
            TriState following = TriState.fromBoolean(relationship.optBoolean("following"));
            return data.getAccountActor().act(
                    data.getAccountActor(),
                    following.toBoolean(!follow) == follow
                            ? (follow
                            ? ActivityType.FOLLOW
                            : ActivityType.UNDO_FOLLOW)
                            : ActivityType.UPDATE,
                    Actor.fromOid(data.getOrigin(), actorOid)
            );
        });
    }

    @Override
    public Try<Boolean> undoAnnounce(String noteOid) {
        return getApiPathWithNoteId(ApiRoutineEnum.UNDO_ANNOUNCE, noteOid)
            .flatMap(http::postRequest)
            .flatMap(HttpReadResult::getJsonObject)
            .filter(Objects::nonNull)
            .onSuccess(jso -> MyLog.v(this, "destroyReblog response: " + jso.toString()))
            .map(any -> true);
    }

    @Override
    Try<List<Actor>> getActors(Actor actor, ApiRoutineEnum apiRoutine) {
        int limit = 400;
        return getApiPathWithActorId(apiRoutine, actor.oid)
            .map(Uri::buildUpon)
            .map(b -> b.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine)))
            .map(Uri.Builder::build)
            .flatMap(uri -> http.getRequestAsArray(uri)
                    .flatMap(jsonArray -> jArrToActors(jsonArray, apiRoutine, uri)));
    }

    @Override
    public Try<OriginConfig> getConfig() {
        return getApiPath(ApiRoutineEnum.GET_CONFIG)
            .flatMap(this::getRequest)
            .map(result -> {
                // Hardcoded in https://github.com/tootsuite/mastodon/blob/master/spec/validators/status_length_validator_spec.rb
                int textLimit = result == null || result.optInt(TEXT_LIMIT_KEY) < 1
                        ? OriginConfig.MASTODON_TEXT_LIMIT_DEFAULT
                        : result.optInt(TEXT_LIMIT_KEY);
                // Hardcoded in https://github.com/tootsuite/mastodon/blob/master/app/models/media_attachment.rb
                return OriginConfig.fromTextLimit(textLimit, 10 * BYTES_IN_MB);
        });
    }
}
