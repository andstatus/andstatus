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
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Twitter API implementations
 * https://dev.twitter.com/rest/public
 * @author yvolk@yurivolkov.com
 */
public abstract class ConnectionTwitter extends Connection {
    private static final String TAG = ConnectionTwitter.class.getSimpleName();

    /**
     * URL of the API. Not logged
     * @param routine
     * @return URL or an empty string in a case the API routine is not supported
     */
    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = "account/rate_limit_status" + EXTENSION;
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "account/verify_credentials" + EXTENSION;
                break;
            case DIRECT_MESSAGES:
                url = "direct_messages" + EXTENSION;
                break;
            case CREATE_FAVORITE:
                url = "favorites/create/";
                break;
            case DESTROY_FAVORITE:
                url = "favorites/destroy/";
                break;
            case GET_FOLLOWERS_IDS:
                url = "followers/ids" + EXTENSION;
                break;
            case FOLLOW_USER:
                url = "friendships/create" + EXTENSION;
                break;
            case GET_FRIENDS_IDS:
                url = "friends/ids" + EXTENSION;
                break;
            case GET_USER:
                url = "users/show" + EXTENSION;
                break;
            case POST_DIRECT_MESSAGE:
                url = "direct_messages/new" + EXTENSION;
                break;
            case POST_REBLOG:
                url = "statuses/retweet/";
                break;
            case DESTROY_MESSAGE:
                url = "statuses/destroy/";
                break;
            case STATUSES_HOME_TIMELINE:
                url = "statuses/home_timeline" + EXTENSION;
                break;
            case STATUSES_MENTIONS_TIMELINE:
                url = "statuses/mentions" + EXTENSION;
                break;
            case STATUSES_USER_TIMELINE:
                url = "statuses/user_timeline" + EXTENSION;
                break;
            case GET_MESSAGE:
                url = "statuses/show" + EXTENSION;
                break;
            case POST_MESSAGE:
                url = "statuses/update" + EXTENSION;
                break;
            case STOP_FOLLOWING_USER:
                url = "friendships/destroy" + EXTENSION;
                break;
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    public boolean destroyStatus(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPath(ApiRoutineEnum.DESTROY_MESSAGE) + statusId + EXTENSION);
        if (jso != null && MyLog.isVerboseEnabled()) {
            try {
                MyLog.v(TAG, "destroyStatus response: " + jso.toString(2));
            } catch (JSONException e) {
                MyLog.e(this, e);
                jso = null;
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
    public MbUser followUser(String userId, Boolean follow) throws ConnectionException {
        JSONObject out = new JSONObject();
        try {
            out.put("user_id", userId);
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject user = postRequest(follow ? ApiRoutineEnum.FOLLOW_USER : ApiRoutineEnum.STOP_FOLLOWING_USER, out);
        return userFromJson(user);
    } 

    /**
     * Returns an array of numeric IDs for every user the specified user is following.
     * Current implementation is restricted to 5000 IDs (no paged cursors are used...)
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/get/friends/ids">GET friends/ids</a>
     * @throws ConnectionException
     */
    @Override
    public List<String> getFriendsIds(String userId) throws ConnectionException {
        String method = "getFriendsIds";
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
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
     * Returns a cursored collection of user IDs for every user following the specified user.
     * @see <a
     *      href="https://dev.twitter.com/rest/reference/get/followers/ids">GET followers/ids</a>
     */
    @Override
    public List<String> getFollowersIds(String userId) throws ConnectionException {
        String method = "getFollowersIds";
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FOLLOWERS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
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
     * 
     * @throws ConnectionException
     */
    @Override
    public MbMessage getMessage1(String messageId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_MESSAGE));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("id", messageId);
        JSONObject message = http.getRequest(builder.build().toString());
        return messageFromJson(message);
    }

    @Override
    public List<MbTimelineItem> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                            TimelinePosition oldestPosition, int limit, String userId)
            throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        if (fixedDownloadLimitForApiRoutine(limit, apiRoutine) > 0) {
            builder.appendQueryParameter("count", String.valueOf(fixedDownloadLimitForApiRoutine(limit, apiRoutine)));
        }
        if (!TextUtils.isEmpty(userId)) {
            builder.appendQueryParameter("user_id", userId);
        }
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, url);
    }

    private MbTimelineItem timelineItemFromJson(JSONObject jso) throws ConnectionException {
        MbTimelineItem item = new MbTimelineItem();
        item.mbMessage = messageFromJson(jso);
        item.timelineItemDate = item.mbMessage.sentDate; 
        item.timelineItemPosition = new TimelinePosition(item.mbMessage.oid);
        return item;
    }

    @NonNull
    final MbMessage messageFromJson(JSONObject jso) throws ConnectionException {
        return jso == null ? MbMessage.EMPTY : messageFromJson2(jso);
    }

    MbMessage messageFromJson2(@NonNull JSONObject jso) throws ConnectionException {
        String oid = jso.optString("id_str");
        if (TextUtils.isEmpty(oid)) {
            // This is for the Status.net
            oid = jso.optString("id");
        } 
        MbMessage message =  MbMessage.fromOriginAndOid(data.getOriginId(), oid, DownloadStatus.LOADED);
        message.actor = MbUser.fromOriginAndUserOid(data.getOriginId(), data.getAccountUserOid());
        try {
            message.sentDate = dateFromJson(jso, "created_at");

            JSONObject sender;
            if (jso.has("sender")) {
                sender = jso.getJSONObject("sender");
                message.sender = userFromJson(sender);
            } else if (jso.has("user")) {
                sender = jso.getJSONObject("user");
                message.sender = userFromJson(sender);
            } else if (jso.has("from_user")) {
                // This is in the search results, 
                // see https://dev.twitter.com/docs/api/1/get/search
                String senderName = jso.getString("from_user");
                String senderOid = jso.optString("from_user_id_str");
                if (SharedPreferencesUtil.isEmpty(senderOid)) {
                    senderOid = jso.optString("from_user_id");
                }
                if (!SharedPreferencesUtil.isEmpty(senderOid)) {
                    message.sender = MbUser.fromOriginAndUserOid(data.getOriginId(), senderOid);
                    message.sender.setUserName(senderName);
                }
            }
            
            // Is this a reblog?
            if (!jso.isNull("retweeted_status")) {
                message.setReblogged(messageFromJson(jso.getJSONObject("retweeted_status")));
            }
            setMessageBodyFromJson(message, jso);
            if (jso.has("recipient")) {
                JSONObject recipient = jso.getJSONObject("recipient");
                message.recipient = userFromJson(recipient);
            }
            if (jso.has("source")) {
                message.via = jso.getString("source");
            }
            if (jso.has("favorited")) {
                message.setFavoritedByActor(TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favorited"))));
            }

            // If the Msg is a Reply to other message
            String inReplyToUserOid = "";
            if (jso.has("in_reply_to_user_id_str")) {
                inReplyToUserOid = jso.getString("in_reply_to_user_id_str");
            } else if (jso.has("in_reply_to_user_id")) {
                // This is for Status.net
                inReplyToUserOid = jso.getString("in_reply_to_user_id");
            }
            if (SharedPreferencesUtil.isEmpty(inReplyToUserOid)) {
                inReplyToUserOid = "";
            }
            if (!SharedPreferencesUtil.isEmpty(inReplyToUserOid)) {
                String inReplyToMessageOid = "";
                if (jso.has("in_reply_to_status_id_str")) {
                    inReplyToMessageOid = jso.getString("in_reply_to_status_id_str");
                } else if (jso.has("in_reply_to_status_id")) {
                    // This is for StatusNet
                    inReplyToMessageOid = jso.getString("in_reply_to_status_id");
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                    // Construct Related message from available info
                    MbMessage inReplyToMessage = MbMessage.fromOriginAndOid(data.getOriginId(),
                            inReplyToMessageOid, DownloadStatus.UNKNOWN);
                    MbUser inReplyToUser = MbUser.fromOriginAndUserOid(data.getOriginId(),
                            inReplyToUserOid);
                    if (jso.has("in_reply_to_screen_name")) {
                        inReplyToUser.setUserName(jso.getString("in_reply_to_screen_name"));
                    }
                    inReplyToMessage.sender = inReplyToUser;
                    inReplyToMessage.actor = message.actor;
                    message.setInReplyTo(inReplyToMessage);
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing message", e, jso);
        } catch (Exception e) {
            MyLog.e(this, "messageFromJson", e);
            return MbMessage.EMPTY;
        }
        return message;
    }

    protected void setMessageBodyFromJson(MbMessage message, JSONObject jso) throws JSONException {
        if (jso.has("text")) {
            message.setBody(jso.getString("text"));
        }
    }
    
    protected MbUser userFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return MbUser.getEmpty();
        }
        String oid = "";
        if (jso.has("id_str")) {
            oid = jso.optString("id_str");
        } else if (jso.has("id")) {
            oid = jso.optString("id");
        } 
        if (SharedPreferencesUtil.isEmpty(oid)) {
            oid = "";
        }
        String userName = "";
        if (jso.has("screen_name")) {
            userName = jso.optString("screen_name");
            if (SharedPreferencesUtil.isEmpty(userName)) {
                userName = "";
            }
        }
        MbUser user = MbUser.fromOriginAndUserOid(data.getOriginId(), oid);
        user.actor = MbUser.fromOriginAndUserOid(data.getOriginId(), data.getAccountUserOid());
        user.setUserName(userName);
        user.setRealName(jso.optString("name"));
        if (!SharedPreferencesUtil.isEmpty(user.getRealName())) {
            user.setProfileUrl(data.getOriginUrl());
        }
        user.location = jso.optString("location");
        user.avatarUrl = UriUtils.fromAlternativeTags(jso,
                "profile_image_url_https", "profile_image_url").toString();
        user.bannerUrl = UriUtils.fromJson(jso, "profile_banner_url").toString();
        user.setDescription(jso.optString("description"));
        user.setHomepage(jso.optString("url"));
        // Hack for twitter.com
        user.setProfileUrl(http.pathToUrlString("/").replace("/api.", "/") + userName);
        user.msgCount = jso.optLong("statuses_count");
        user.favoritesCount = jso.optLong("favourites_count");
        user.followingCount = jso.optLong("friends_count");
        user.followersCount = jso.optLong("followers_count");
        user.setCreatedDate(dateFromJson(jso, "created_at"));
        if (!jso.isNull("following")) {
            user.followedByActor = TriState.fromBoolean(jso.optBoolean("following"));
        }
        if (!jso.isNull("status")) {
            JSONObject latestMessage;
            try {
                latestMessage = jso.getJSONObject("status");
                user.setLatestMessage(messageFromJson(latestMessage));
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, "getting status from user", e, jso);
            }
        }
        return user;
    }

    @Override
    public List<MbTimelineItem> search(TimelinePosition youngestPosition,
                                       TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.SEARCH_MESSAGES;
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!TextUtils.isEmpty(searchQuery)) {
            builder.appendQueryParameter("q", searchQuery);
        }
        appendPositionParameters(builder, youngestPosition, oldestPosition);
        if (fixedDownloadLimitForApiRoutine(limit, apiRoutine) > 0) {
            builder.appendQueryParameter("count", String.valueOf(fixedDownloadLimitForApiRoutine(limit, apiRoutine)));
        }
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        return jArrToTimeline(jArr, apiRoutine, url);
    }

    protected void appendPositionParameters(Uri.Builder builder, TimelinePosition youngest, TimelinePosition oldest) {
        if (youngest.isPresent()) {
            builder.appendQueryParameter("since_id", youngest.getPosition());
        } else if (oldest.isPresent()) {
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

    List<MbTimelineItem> jArrToTimeline(JSONArray jArr, ApiRoutineEnum apiRoutine, String url) throws ConnectionException {
        List<MbTimelineItem> timeline = new ArrayList<>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbTimelineItem item = timelineItemFromJson(jso);
                    timeline.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null);
                }
            }
        }
        if (apiRoutine.isMsgPublic()) {
            setMessagesPublic(timeline);
        }
        MyLog.d(this, apiRoutine + " '" + url + "' " + timeline.size() + " items");
        return timeline;
    }

    List<MbUser> jArrToUsers(JSONArray jArr, ApiRoutineEnum apiRoutine, String url) throws ConnectionException {
        List<MbUser> users = new ArrayList<>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbUser item = userFromJson(jso);
                    users.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing " + apiRoutine, e, null);
                }
            }
        }
        if (apiRoutine.isMsgPublic()) {
            setUserMessagesPublic(users);
        }
        MyLog.d(this, apiRoutine + " '" + url + "' " + users.size() + " items");
        return users;
    }

    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/get/users/show">GET users/show</a>
     */
    @Override
    public MbUser getUser(String userId, String userName) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_USER));
        Uri.Builder builder = sUri.buildUpon();
        if (MbUser.isOidReal(userId)) {
            builder.appendQueryParameter("user_id", userId);
        } else {
            builder.appendQueryParameter("screen_name", userName);
        }
        JSONObject jso = http.getRequest(builder.build().toString());
        MbUser mbUser = userFromJson(jso);
        MyLog.v(this, "getUser oid='" + userId + "', userName='" + userName + "' -> " + mbUser.getRealName());
        return mbUser;
    }
    
    @Override
    public MbMessage postDirectMessage(String message, String statusId, String userId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("text", message);
            if ( !TextUtils.isEmpty(userId)) {
                formParams.put("user_id", userId);
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_DIRECT_MESSAGE, formParams);
        return messageFromJson(jso);
    }
    
    @Override
    public MbMessage postReblog(String rebloggedId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPath(ApiRoutineEnum.POST_REBLOG) + rebloggedId + EXTENSION);
        return messageFromJson(jso);
    }

    /**
     * Check API requests status.
     * 
     * Returns the remaining number of API requests available to the requesting 
     * user before the API limit is reached for the current hour. Calls to 
     * rate_limit_status do not count against the rate limit.  If authentication 
     * credentials are provided, the rate limit status for the authenticating 
     * user is returned.  Otherwise, the rate limit status for the requester's 
     * IP address is returned.
     * @see <a
           href="https://dev.twitter.com/docs/api/1/get/account/rate_limit_status">GET 
           account/rate_limit_status</a>
     * 
     * @throws ConnectionException
     */
    @Override
    public MbRateLimitStatus rateLimitStatus() throws ConnectionException {
        JSONObject result = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS));
        MbRateLimitStatus status = new MbRateLimitStatus();
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
    public MbMessage updateStatus(String message, String statusId, String inReplyToId, Uri mediaUri) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", message);
            if ( !TextUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_status_id", inReplyToId);
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.POST_MESSAGE, formParams);
        return messageFromJson(jso);
    }

    /**
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
     *      REST API Method: account verify_credentials</a>
     */
    @Override
    public MbUser verifyCredentials() throws ConnectionException {
        JSONObject user = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return userFromJson(user);
    }

    protected final JSONObject postRequest(ApiRoutineEnum apiRoutine, JSONObject formParams) throws ConnectionException {
        return postRequest(getApiPath(apiRoutine), formParams);
    }

}
