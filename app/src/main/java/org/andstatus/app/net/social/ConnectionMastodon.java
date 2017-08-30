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
import android.text.TextUtils;

import org.andstatus.app.data.MyContentType;
import org.andstatus.app.msg.KeywordsFilter;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ConnectionMastodon extends ConnectionTwitterLike {
    private static final String ATTACHMENTS_FIELD_NAME = "media_attachments";

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
            case MENTIONS_TIMELINE:
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
            case USER_TIMELINE:
                url = "accounts/%userId%/statuses";
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "accounts/verify_credentials";
                break;
            case POST_MESSAGE:
                url = "statuses";
                break;
            case POST_WITH_MEDIA:
                url = "media";
                break;
            case GET_MESSAGE:
                url = "statuses/%messageId%";
                break;
            case SEARCH_MESSAGES:
                url = "search"; /* actually, this is a complex search "for content" */
                break;
            case SEARCH_USERS:
                url = "accounts/search";
                break;
            case GET_CONVERSATION:
                url = "statuses/%messageId%/context";
                break;
            case CREATE_FAVORITE:
                url = "statuses/%messageId%/favourite";
                break;
            case DESTROY_FAVORITE:
                url = "statuses/%messageId%/unfavourite";
                break;
            case FOLLOW_USER:
                url = "accounts/%userId%/follow";
                break;
            case STOP_FOLLOWING_USER:
                url = "accounts/%userId%/unfollow";
                break;
            case GET_FOLLOWERS:
                url = "accounts/%userId%/followers";
                break;
            case GET_FRIENDS:
                url = "accounts/%userId%/following";
                break;
            case GET_USER:
                url = "accounts/%userId%";
                break;
            case POST_REBLOG:
                url = "statuses/%messageId%/reblog";
                break;
            case DESTROY_REBLOG:
                url = "statuses/%messageId%/unreblog";
                break;
            default:
                url = "";
                break;
        }

        return prependWithBasicPath(url);
    }

    @NonNull
    @Override
    protected Uri.Builder getTimelineUriBuilder(ApiRoutineEnum apiRoutine, int limit, String userId) throws ConnectionException {
        String url = this.getApiPathWithUserId(apiRoutine, userId);
        return Uri.parse(url).buildUpon().appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
    }

    @Override
    protected MbActivity activityFromTwitterLikeJson(JSONObject timelineItem) throws ConnectionException {
        if (isNotification(timelineItem)) {
            MbActivity activity = MbActivity.from(data.getPartialAccountUser(), getType(timelineItem));
            activity.setTimelinePosition(timelineItem.optString("id"));
            activity.setUpdatedDate(dateFromJson(timelineItem, "created_at"));
            activity.setActor(userFromJson(timelineItem.optJSONObject("account")));
            MbActivity messageActivity = activityFromJson2(timelineItem.optJSONObject("status"));

            switch (activity.type) {
                case LIKE:
                case UNDO_LIKE:
                case ANNOUNCE:
                case UNDO_ANNOUNCE:
                    activity.setActivity(messageActivity);
                    break;
                case FOLLOW:
                case UNDO_FOLLOW:
                    activity.setUser(data.getPartialAccountUser());
                    break;
                default:
                    activity.setMessage(messageActivity.getMessage());
                    break;
            }
            return activity;
        } else {
            return super.activityFromTwitterLikeJson(timelineItem);
        }

    }

    @NonNull
    protected MbActivityType getType(JSONObject timelineItem) throws ConnectionException {
        if (isNotification(timelineItem)) {
            switch (timelineItem.optString("type")) {
                case "favourite":
                    return MbActivityType.LIKE;
                case "reblog":
                    return MbActivityType.ANNOUNCE;
                case "follow":
                    return MbActivityType.FOLLOW;
                case "mention":
                    return MbActivityType.UPDATE;
                default:
                    return MbActivityType.EMPTY;
            }
        }
        return MbActivityType.UPDATE;
    }

    private boolean isNotification(JSONObject activity) {
        return activity != null && !activity.isNull("type");
    }

    @NonNull
    @Override
    public List<MbActivity> searchMessages(TimelinePosition youngestPosition,
                                           TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        String tag = new KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword();
        if (TextUtils.isEmpty(tag)) {
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
    public List<MbUser> searchUsers(int limit, String searchQuery) throws ConnectionException {
        String tag = new KeywordsFilter(searchQuery).getFirstTagOrFirstKeyword();
        if (TextUtils.isEmpty(tag)) {
            return new ArrayList<>();
        }
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_USERS;
        String url = getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("q", searchQuery);
        builder.appendQueryParameter("resolve", "true");
        builder.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToUsers(jArr, apiRoutine, url);
    }

    protected String getApiPathWithTag(ApiRoutineEnum routineEnum, String tag) throws ConnectionException {
        return getApiPath(routineEnum).replace("%tag%", tag);
    }

    @Override
    public List<MbActivity> getConversation(String conversationOid) throws ConnectionException {
        List<MbActivity> timeline = new ArrayList<>();
        if (TextUtils.isEmpty(conversationOid)) {
            return timeline;
        }
        String url = getApiPathWithMessageId(ApiRoutineEnum.GET_CONVERSATION, conversationOid);
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
    public MbActivity updateStatus(String message, String statusId, String inReplyToId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        JSONObject mediaObject = null;
        try {
            formParams.put("status", message);
            if ( !TextUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_id", inReplyToId);
            }
            if (!UriUtils.isEmpty(mediaUri)) {
                mediaObject = uploadMedia(mediaUri);
                if (mediaObject != null && mediaObject.has("id")) {
                    formParams.put("media_ids[]", mediaObject.get("id"));
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error posting message '" + mediaUri + "'", e, mediaObject);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_MESSAGE, formParams);
        return activityFromJson(jso);
    }

    private JSONObject uploadMedia(Uri mediaUri) throws ConnectionException {
        JSONObject jso = null;
        try {
            JSONObject formParams = new JSONObject();
            formParams.put(HttpConnection.KEY_MEDIA_PART_NAME, "file");
            formParams.put(HttpConnection.KEY_MEDIA_PART_URI, mediaUri.toString());
            jso = postRequest(ApiRoutineEnum.POST_WITH_MEDIA, formParams);
            if (jso != null) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, "uploaded '" + mediaUri.toString() + "' " + jso.toString(2));
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Error uploading '" + mediaUri + "'", e, jso);
        }
        return jso;
    }

    @Override
    protected MbUser userFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return MbUser.EMPTY;
        }
        String oid = jso.optString("id");
        String userName = jso.optString("username");
        if (TextUtils.isEmpty(oid) || TextUtils.isEmpty(userName)) {
            throw ConnectionException.loggedJsonException(this, "Id or username is empty", null, jso);
        }
        MbUser user = MbUser.fromOriginAndUserOid(data.getOriginId(), oid);
        user.setUserName(userName);
        user.setRealName(jso.optString("display_name"));
        user.setWebFingerId(jso.optString("acct"));
        if (!SharedPreferencesUtil.isEmpty(user.getRealName())) {
            user.setProfileUrl(data.getOriginUrl());
        }
        user.avatarUrl = UriUtils.fromJson(jso, "avatar").toString();
        user.bannerUrl = UriUtils.fromJson(jso, "header").toString();
        user.setDescription(jso.optString("note"));
        user.setProfileUrl(jso.optString("url"));
        user.msgCount = jso.optLong("statuses_count");
        user.followingCount = jso.optLong("following_count");
        user.followersCount = jso.optLong("followers_count");
        user.setCreatedDate(dateFromJson(jso, "created_at"));
        return user;
    }

    @Override
    @NonNull
    MbActivity activityFromJson2(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return MbActivity.EMPTY;
        }
        final String method = "activityFromJson2";
        String oid = jso.optString("id");
        MbActivity activity = newLoadedUpdateActivity(oid, dateFromJson(jso, "created_at"));
        try {
            JSONObject actor;
            if (jso.has("account")) {
                actor = jso.getJSONObject("account");
                activity.setActor(userFromJson(actor));
            }

            MbMessage message =  activity.getMessage();
            message.setBody(jso.optString("content"));
            message.url = jso.optString("url");
            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                message.addRecipient(userFromJson(recipient));
            }
            if (!jso.isNull("application")) {
                JSONObject application = jso.getJSONObject("application");
                message.via = application.optString("name");
            }
            if (!jso.isNull("favourited")) {
                message.setFavoritedByMe(TriState.fromBoolean(SharedPreferencesUtil.isTrue(
                        jso.getString("favourited"))));
            }

            // If the Msg is a Reply to other message
            String inReplyToUserOid = "";
            if (jso.has("in_reply_to_account_id")) {
                inReplyToUserOid = jso.getString("in_reply_to_account_id");
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToUserOid)) {
                inReplyToUserOid = "";
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToUserOid)) {
                String inReplyToMessageOid = "";
                if (jso.has("in_reply_to_id")) {
                    inReplyToMessageOid = jso.getString("in_reply_to_id");
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                    // Construct Related message from available info
                    MbActivity inReplyTo = MbActivity.newPartialMessage(data.getPartialAccountUser(),
                            inReplyToMessageOid);
                    inReplyTo.setActor(MbUser.fromOriginAndUserOid(data.getOriginId(), inReplyToUserOid));
                    message.setInReplyTo(inReplyTo);
                }
            }

            // TODO: Remove duplicated code of attachments parsing
            if (!jso.isNull(ATTACHMENTS_FIELD_NAME)) {
                try {
                    JSONArray jArr = jso.getJSONArray(ATTACHMENTS_FIELD_NAME);
                    for (int ind = 0; ind < jArr.length(); ind++) {
                        JSONObject attachment = (JSONObject) jArr.get(ind);
                        URL url = UrlUtils.fromJson(attachment, "url");
                        if (url == null) {
                            url = UrlUtils.fromJson(attachment, "preview_url");
                        }
                        MbAttachment mbAttachment =  MbAttachment.fromUrlAndContentType(url, MyContentType.fromUrl(url, attachment.optString("type")));
                        if (mbAttachment.isValid()) {
                            message.attachments.add(mbAttachment);
                        } else {
                            MyLog.d(this, method + "; invalid attachment #" + ind + "; " + jArr.toString());
                        }
                    }
                } catch (JSONException e) {
                    MyLog.d(this, method, e);
                }
            }

        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing message", e, jso);
        } catch (Exception e) {
            MyLog.e(this, "messageFromJson", e);
            return MbActivity.EMPTY;
        }
        return activity;
    }

    @Override
    MbActivity rebloggedMessageFromJson(@NonNull JSONObject jso) throws ConnectionException {
        return  activityFromJson2(jso.optJSONObject("reblog"));
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }

    @Override
    public MbUser getUser(String userId, String userName) throws ConnectionException {
        JSONObject jso = http.getRequest(getApiPathWithUserId(ApiRoutineEnum.GET_USER, userId));
        MbUser mbUser = userFromJson(jso);
        MyLog.v(this, "getUser oid='" + userId + "', userName='" + userName + "' -> " + mbUser.getRealName());
        return mbUser;
    }

    @Override
    public MbActivity followUser(String userId, Boolean follow) throws ConnectionException {
        JSONObject relationship = postRequest(getApiPathWithUserId(follow ? ApiRoutineEnum.FOLLOW_USER :
                ApiRoutineEnum.STOP_FOLLOWING_USER, userId), new JSONObject());
        MbUser user = MbUser.fromOriginAndUserOid(data.getOriginId(), userId);
        if (relationship == null || relationship.isNull("following")) {
            return MbActivity.EMPTY;
        }
        TriState following = TriState.fromBoolean(relationship.optBoolean("following"));
        return user.act(MbUser.EMPTY, data.getPartialAccountUser(), following.toBoolean(!follow) == follow
                ? (follow ? MbActivityType.FOLLOW : MbActivityType.UNDO_FOLLOW)
                : MbActivityType.UPDATE );
    }

    @Override
    public boolean destroyReblog(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPathWithMessageId(ApiRoutineEnum.DESTROY_REBLOG, statusId));
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

    List<MbUser> getMbUsers(String userId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        String url = this.getApiPathWithUserId(apiRoutine, userId);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        int limit = 400;
        builder.appendQueryParameter("limit", strFixedDownloadLimit(limit, apiRoutine));
        return jArrToUsers(http.getRequestAsArray(builder.build().toString()), apiRoutine, url);
    }

}
