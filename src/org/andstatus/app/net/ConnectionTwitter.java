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

package org.andstatus.app.net;

import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.TriState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Twitter API implementations
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
            case STATUSES_DESTROY:
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
            case STATUSES_UPDATE:
                url = "statuses/update" + EXTENSION;
                break;
            case STOP_FOLLOWING_USER:
                url = "friendships/destroy" + EXTENSION;
                break;
            default:
                url = "";
                break;
        }
        if (!TextUtils.isEmpty(url)) {
            url = http.data.basicPath + "/" + url;
        }
        return url;
    }

    @Override
    public boolean destroyStatus(String statusId) throws ConnectionException {
        JSONObject jso = http.postRequest(getApiPath(ApiRoutineEnum.STATUSES_DESTROY) + statusId + EXTENSION);
        if (jso != null && MyLog.isLoggable(null, MyLog.VERBOSE)) {
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
    public List<String> getIdsOfUsersFollowedBy(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        List<String> list = new ArrayList<String>();
        JSONObject jso = http.getRequest(builder.build().toString());
        if (jso != null) {
            try {
                JSONArray jArr = jso.getJSONArray("ids");
                for (int index = 0; index < jArr.length(); index++) {
                    list.add(jArr.getString(index));
                }
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, e, jso, "Parsing friendsIds");
            }
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
    public List<MbTimelineItem> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition sinceId, int limit, String userId)
            throws ConnectionException {
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!sinceId.isEmpty()) {
            builder.appendQueryParameter("since_id", sinceId.getPosition());
        }
        if (fixedDownloadLimitForApiRoutine(limit, apiRoutine) > 0) {
            builder.appendQueryParameter("count", String.valueOf(fixedDownloadLimitForApiRoutine(limit, apiRoutine)));
        }
        if (!TextUtils.isEmpty(userId)) {
            builder.appendQueryParameter("user_id", userId);
        }
        JSONArray jArr = http.getRequestAsArray(builder.build().toString());
        List<MbTimelineItem> timeline = new ArrayList<MbTimelineItem>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbTimelineItem item = timelineItemFromJson(jso);
                    timeline.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, e, null, "Parsing timeline");
                }
            }
        }
        if (apiRoutine.isMsgPublic()) {
            setMessagesPublic(timeline);
        }
        MyLog.d(TAG, "getTimeline '" + url + "' " + timeline.size() + " items");
        return timeline;
    }

    private MbTimelineItem timelineItemFromJson(JSONObject jso) throws ConnectionException {
        MbTimelineItem item = new MbTimelineItem();
        item.mbMessage = messageFromJson(jso);
        item.timelineItemDate = item.mbMessage.sentDate; 
        item.timelineItemPosition = new TimelinePosition(item.mbMessage.oid);
        return item;
    }

    protected MbMessage messageFromJson(JSONObject jso) throws ConnectionException {
        if (jso == null) {
            return MbMessage.getEmpty();
        }
        String oid = jso.optString("id_str");
        if (TextUtils.isEmpty(oid)) {
            // This is for the Status.net
            oid = jso.optString("id");
        } 
        MbMessage message =  MbMessage.fromOriginAndOid(data.originId, oid);
        message.actor = MbUser.fromOriginAndUserOid(data.originId, data.accountUserOid);
        try {
            message.sentDate = dateFromJson(jso, "created_at");

            JSONObject sender;
            if (jso.has("sender")) {
                sender = jso.getJSONObject("sender");
                message.sender = userFromJson(sender);
            } else if (jso.has("user")) {
                sender = jso.getJSONObject("user");
                message.sender = userFromJson(sender);
            }
            
            // Is this a reblog?
            if (jso.has("retweeted_status")) {
                JSONObject rebloggedMessage = jso.getJSONObject("retweeted_status");
                message.rebloggedMessage = messageFromJson(rebloggedMessage);
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
                message.favoritedByActor = TriState.fromBoolean(SharedPreferencesUtil.isTrue(jso.getString("favorited")));
            }

            // If the Msg is a Reply to other message
            String inReplyToUserOid = "";
            String inReplyToUserName = "";
            String inReplyToMessageOid = "";
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
                if (jso.has("in_reply_to_screen_name")) {
                    inReplyToUserName = jso.getString("in_reply_to_screen_name");
                }
                // Construct "User" from available info
                JSONObject inReplyToUser = new JSONObject();
                inReplyToUser.put("id_str", inReplyToUserOid);
                inReplyToUser.put("screen_name", inReplyToUserName);
                if (jso.has("in_reply_to_status_id_str")) {
                    inReplyToMessageOid = jso.getString("in_reply_to_status_id_str");
                } else if (jso.has("in_reply_to_status_id")) {
                    // This is for identi.ca
                    inReplyToMessageOid = jso.getString("in_reply_to_status_id");
                }
                if (SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                    inReplyToUserOid = "";
                }
                if (!SharedPreferencesUtil.isEmpty(inReplyToMessageOid)) {
                    // Construct Related "Msg" from available info
                    // and add it recursively
                    JSONObject inReplyToMessage = new JSONObject();
                    inReplyToMessage.put("id_str", inReplyToMessageOid);
                    inReplyToMessage.put("user", inReplyToUser);
                    message.inReplyToMessage = messageFromJson(inReplyToMessage);
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, e, jso, "Parsing message");
        } catch (Exception e) {
            MyLog.e(this, "messageFromJson", e);
            return MbMessage.getEmpty();
        }
        return message;
    }

    protected void setMessageBodyFromJson(MbMessage message, JSONObject jso) throws JSONException {
        if (jso.has("text")) {
            message.setBody(jso.getString("text"));
        }
    }
    
    private MbUser userFromJson(JSONObject jso) throws ConnectionException {
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
        MbUser user = MbUser.fromOriginAndUserOid(data.originId, oid);
        user.actor = MbUser.fromOriginAndUserOid(data.originId, data.accountUserOid);
        user.userName = userName;
        user.realName = jso.optString("name");
        user.avatarUrl = jso.optString("profile_image_url");
        user.description = jso.optString("description");
        user.homepage = jso.optString("url");
        user.createdDate = dateFromJson(jso, "created_at");
        if (!jso.isNull("following")) {
            user.followedByActor = TriState.fromBoolean(jso.optBoolean("following"));
        }
        if (jso.has("status")) {
            JSONObject latestMessage;
            try {
                latestMessage = jso.getJSONObject("status");
                // This message doesn't have a sender!
                user.latestMessage = messageFromJson(latestMessage);
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(this, e, jso, "getting status from user");
            }
        }
        return user;
    }
    
    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/get/users/show">GET users/show</a>
     */
    @Override
    public MbUser getUser(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_USER));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        JSONObject jso = http.getRequest(builder.build().toString());
        return userFromJson(jso);
    }
    
    @Override
    public MbMessage postDirectMessage(String message, String userId) throws ConnectionException {
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
            switch (data.api) {
                case TWITTER1P0:
                case STATUSNET_TWITTER:
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
                        throw ConnectionException.loggedJsonException(this, e, resources, "getting rate limits");
                    }
                    break;
            }
        }
        return status;
    }
    
    @Override
    public MbMessage updateStatus(String message, String inReplyToId) throws ConnectionException {
        JSONObject formParams = new JSONObject();
        try {
            formParams.put("status", message);
            
            if ( !TextUtils.isEmpty(inReplyToId)) {
                formParams.put("in_reply_to_status_id", inReplyToId);
            }
        } catch (JSONException e) {
            MyLog.e(this, e);
        }
        JSONObject jso = postRequest(ApiRoutineEnum.STATUSES_UPDATE, formParams);
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
        return http.postRequest(getApiPath(apiRoutine), formParams);
    }
    
    @Override
    public boolean userObjectHasMessage() {
        return true;
    }
}
