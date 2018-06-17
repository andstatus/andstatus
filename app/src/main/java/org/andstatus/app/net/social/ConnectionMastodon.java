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
import android.support.annotation.NonNull;

import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.note.KeywordsFilter;
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

public class ConnectionMastodon extends ConnectionTwitterLike {
    private static final String ATTACHMENTS_FIELD_NAME = "media_attachments";
    private static final String NAME_PROPERTY = "spoiler_text";
    private static final String CONTENT_PROPERTY_UPDATE = "status";
    private static final String CONTENT_PROPERTY = "content";

    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        // See https://github.com/tootsuite/documentation/blob/master/Using-the-API/API.md
        switch (routine) {
            case REGISTER_CLIENT:
                url = "apps";
                break;
            case HOME_TIMELINE:
                url = "timelines/home";
                break;
            case NOTIFICATIONS_TIMELINE:
                url = "notifications";
                break;
            case FAVORITES_TIMELINE:
                url = "favourites";
                break;
            case PUBLIC_TIMELINE:
                url = "timelines/public";
                break;
            case TAG_TIMELINE:
                url = "timelines/tag/%tag%";
                break;
            case ACTOR_TIMELINE:
                url = "accounts/%actorId%/statuses";
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "accounts/verify_credentials";
                break;
            case UPDATE_NOTE:
                url = "statuses";
                break;
            case UPDATE_NOTE_WITH_MEDIA:
                url = "media";
                break;
            case GET_NOTE:
                url = "statuses/%noteId%";
                break;
            case SEARCH_NOTES:
                url = "search"; /* actually, this is a complex search "for content" */
                break;
            case SEARCH_ACTORS:
                url = "accounts/search";
                break;
            case GET_CONVERSATION:
                url = "statuses/%noteId%/context";
                break;
            case LIKE:
                url = "statuses/%noteId%/favourite";
                break;
            case UNDO_LIKE:
                url = "statuses/%noteId%/unfavourite";
                break;
            case FOLLOW:
                url = "accounts/%actorId%/follow";
                break;
            case UNDO_FOLLOW:
                url = "accounts/%actorId%/unfollow";
                break;
            case GET_FOLLOWERS:
                url = "accounts/%actorId%/followers";
                break;
            case GET_FRIENDS:
                url = "accounts/%actorId%/following";
                break;
            case GET_ACTOR:
                url = "accounts/%actorId%";
                break;
            case ANNOUNCE:
                url = "statuses/%noteId%/reblog";
                break;
            case UNDO_ANNOUNCE:
                url = "statuses/%noteId%/unreblog";
                break;
            default:
                url = "";
                break;
        }

        return prependWithBasicPath(url);
    }

    @NonNull
    @Override
    protected Uri.Builder getTimelineUriBuilder(ApiRoutineEnum apiRoutine, int limit, String actorId) throws ConnectionException {
        String url = this.getApiPathWithActorId(apiRoutine, actorId);
        return Uri.parse(url).buildUpon().appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
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
    protected ActivityType getType(JSONObject timelineItem) throws ConnectionException {
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
    public List<AActivity> searchNotes(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        String tag = new KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword();
        if (StringUtils.isEmpty(tag)) {
            return new ArrayList<>();
        }
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.TAG_TIMELINE;
        String url = getApiPathWithTag(apiRoutine, tag);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        builder.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, url);
    }

    @NonNull
    @Override
    public List<Actor> searchActors(int limit, String searchQuery) throws ConnectionException {
        String tag = new KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword();
        if (StringUtils.isEmpty(tag)) {
            return new ArrayList<>();
        }
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_ACTORS;
        String url = getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("q", searchQuery);
        builder.appendQueryParameter("resolve", "true");
        builder.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToActors(jArr, apiRoutine, url);
    }

    protected String getApiPathWithTag(ApiRoutineEnum routineEnum, String tag) throws ConnectionException {
        return getApiPath(routineEnum).replace("%tag%", tag);
    }

    @Override
    public List<AActivity> getConversation(String conversationOid) throws ConnectionException {
        List<AActivity> timeline = new ArrayList<>();
        if (StringUtils.isEmpty(conversationOid)) {
            return timeline;
        }
        String url = getApiPathWithNoteId(ApiRoutineEnum.GET_CONVERSATION, conversationOid);
        JSONObject mastodonContext = http.getRequest(url);
        try {
            if (mastodonContext.has("ancestors")) {
                timeline.addAll(jArrToTimeline(mastodonContext.getJSONArray("ancestors"),
                        ApiRoutineEnum.GET_CONVERSATION, url));
            }
            if (mastodonContext.has("descendants")) {
                timeline.addAll(jArrToTimeline(mastodonContext.getJSONArray("descendants"),
                        ApiRoutineEnum.GET_CONVERSATION, url));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error getting conversation '" + conversationOid + "'",
                    e, mastodonContext);
        }
        return timeline;
    }


    @Override
    protected AActivity updateNote2(String name, String content, String noteOid, Audience audience, String inReplyToOid,
                                Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        JSONObject mediaObject = null;
        try {
            formParams.put(NAME_PROPERTY, name);
            formParams.put(CONTENT_PROPERTY_UPDATE, content);
            if ( !StringUtils.isEmpty(inReplyToOid)) {
                formParams.put("in_reply_to_id", inReplyToOid);
            }
            if (!UriUtils.isEmpty(mediaUri)) {
                mediaObject = uploadMedia(mediaUri);
                if (mediaObject != null && mediaObject.has("id")) {
                    formParams.put("media_ids[]", mediaObject.get("id"));
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error updating note '" + mediaUri + "'", e, mediaObject);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.UPDATE_NOTE, formParams);
        return activityFromJson(jso);
    }

    private JSONObject uploadMedia(Uri mediaUri) throws ConnectionException {
        JSONObject jso = null;
        try {
            JSONObject formParams = new JSONObject();
            formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "file");
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            jso = postRequest(ApiRoutineEnum.UPDATE_NOTE_WITH_MEDIA, formParams);
            if (jso != null && MyLog.isVerboseEnabled()) {
                MyLog.v(this, "uploaded '" + mediaUri.toString() + "' " + jso.toString(2));
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error uploading '" + mediaUri + "'", e, jso);
        }
        return jso;
    }

    @Override
    @NonNull
    protected Actor actorFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return Actor.EMPTY;
        }
        String oid = jso.optString("id");
        String username = jso.optString("username");
        if (StringUtils.isEmpty(oid) || StringUtils.isEmpty(username)) {
            throw ConnectionException.loggedJsonException(this, "Id or username is empty", null, jso);
        }
        Actor actor = Actor.fromOriginAndActorOid(data.getOrigin(), oid);
        actor.setUsername(username);
        actor.setRealName(jso.optString("display_name"));
        actor.setWebFingerId(jso.optString("acct"));
        if (!SharedPreferencesUtil.isEmpty(actor.getRealName())) {
            actor.setProfileUrl(data.getOriginUrl());
        }
        actor.avatarUrl = UriUtils.fromJson(jso, "avatar").toString();
        actor.bannerUrl = UriUtils.fromJson(jso, "header").toString();
        actor.setDescription(jso.optString("note"));
        actor.setProfileUrl(jso.optString("url"));
        actor.notesCount = jso.optLong("statuses_count");
        actor.followingCount = jso.optLong("following_count");
        actor.followersCount = jso.optLong("followers_count");
        actor.setCreatedDate(dateFromJson(jso, "created_at"));
        return actor;
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
            note.setName(jso.optString(NAME_PROPERTY));
            note.setContent(jso.optString(CONTENT_PROPERTY));
            note.url = jso.optString("url");
            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                note.addRecipient(actorFromJson(recipient));
            }
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
                            Actor.fromOriginAndActorOid(data.getOrigin(), inReplyToActorOid), inReplyToNoteOid);
                    note.setInReplyTo(inReplyTo);
                }
            }

            // TODO: Remove duplicated code of attachments parsing
            if (!jso.isNull(ATTACHMENTS_FIELD_NAME)) {
                try {
                    JSONArray jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME);
                    for (int ind = 0; ind < jArr.length(); ind++) {
                        JSONObject attachment = (JSONObject) jArr.get(ind);
                        Uri uri = UriUtils.fromAlternativeTags(attachment, "url", "preview_url");
                        Attachment mbAttachment =  Attachment.fromUriAndContentType(uri, attachment.optString("type"));
                        if (mbAttachment.isValid()) {
                            note.attachments.add(mbAttachment);
                        } else {
                            MyLog.d(this, method + "; invalid attachment #" + ind + "; " + jArr.toString());
                        }
                    }
                } catch (JSONException e) {
                    MyLog.d(this, method, e);
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing note", e, jso);
        } catch (Exception e) {
            MyLog.e(this, method, e);
            return AActivity.EMPTY;
        }
        return activity;
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
    public Actor getActor(String actorOid, String username) throws ConnectionException {
        JSONObject jso = http.getRequest(
                getApiPathWithActorId(ApiRoutineEnum.GET_ACTOR, UriUtils.isRealOid(actorOid) ? actorOid : username)
        );
        Actor actor = actorFromJson(jso);
        MyLog.v(this, () -> "getActor oid='" + actorOid
                + "', username='" + username + "' -> " + actor.getRealName());
        return actor;
    }

    @Override
    public AActivity follow(String actorOid, Boolean follow) throws ConnectionException {
        JSONObject relationship = postRequest(getApiPathWithActorId(follow ? ApiRoutineEnum.FOLLOW :
                ApiRoutineEnum.UNDO_FOLLOW, actorOid), new JSONObject());
        Actor friend = Actor.fromOriginAndActorOid(data.getOrigin(), actorOid);
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
                friend
        );
    }

    @Override
    public boolean undoAnnounce(String noteOid) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithNoteId(ApiRoutineEnum.UNDO_ANNOUNCE, noteOid));
        if (jso != null && MyLog.isVerboseEnabled()) {
            try {
                MyLog.v(this, "destroyReblog response: " + jso.toString(2));
            } catch (JSONException e) {
                MyLog.e(this, e);
                jso = null;
            }
        }
        return jso != null;
    }

    List<Actor> getActors(String actorId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        String url = this.getApiPathWithActorId(apiRoutine, actorId);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        int limit = 400;
        builder.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
        return jArrToActors(http.getRequestAsArray(builder.build().toString()), apiRoutine, url);
    }

}
