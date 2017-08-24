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

import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.DownloadStatus;
import org.andstatus.app.data.MyContentType;
import org.andstatus.app.net.http.ConnectionException;
import org.andstatus.app.net.http.ConnectionException.StatusCode;
import org.andstatus.app.net.http.HttpConnection;
import org.andstatus.app.net.http.HttpConnectionData;
import org.andstatus.app.net.social.Connection;
import org.andstatus.app.net.social.MbActivity;
import org.andstatus.app.net.social.MbAttachment;
import org.andstatus.app.net.social.MbMessage;
import org.andstatus.app.net.social.MbObjectType;
import org.andstatus.app.net.social.MbRateLimitStatus;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.JsonUtils;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.andstatus.app.util.UrlUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of pump.io API: <a href="https://github.com/e14n/pump.io/blob/master/API.md">https://github.com/e14n/pump.io/blob/master/API.md</a>  
 * @author yvolk@yurivolkov.com
 */
public class ConnectionPumpio extends Connection {
    private static final String TAG = ConnectionPumpio.class.getSimpleName();
    static final String  APPLICATION_ID = "http://andstatus.org/andstatus";

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
            case GET_USER:
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
            case POST_WITH_MEDIA:
                url = "user/%nickname%/uploads";
                break;
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case FOLLOW_USER:
            case POST_DIRECT_MESSAGE:
            case POST_REBLOG:
            case DESTROY_MESSAGE:
            case POST_MESSAGE:
            case USER_TIMELINE:
                url = "user/%nickname%/feed";
                break;
            default:
                url = "";
                break;
        }
        return prependWithBasicPath(url);
    }

    @Override
    public MbRateLimitStatus rateLimitStatus() throws ConnectionException {
        // TODO Method stub
        return new MbRateLimitStatus();
    }

    @Override
    public MbUser verifyCredentials() throws ConnectionException {
        JSONObject user = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return userFromJson(user);
    }

    private MbUser userFromJson(JSONObject jso) throws ConnectionException {
        if (!ObjectType.PERSON.isTypeOf(jso)) {
            return MbUser.EMPTY;
        }
        String oid = jso.optString("id");
        MbUser user = MbUser.fromOriginAndUserOid(data.getOriginId(), oid);
        user.setUserName(userOidToUsername(oid));
        user.setRealName(jso.optString("displayName"));
        user.avatarUrl = JsonUtils.optStringInside(jso, "image", "url");
        user.location = JsonUtils.optStringInside(jso, "location", "displayName");
        user.setDescription(jso.optString("summary"));
        user.setHomepage(jso.optString("url"));
        user.setProfileUrl(jso.optString("url"));
        user.setUpdatedDate(dateFromJson(jso, "updated"));
        return user;
    }

    @Override
    public long parseDate(String stringDate) {
        return parseIso8601Date(stringDate);
    }
    
    @Override
    public MbActivity destroyFavorite(String messageId) throws ConnectionException {
        return actOnMessage(ActivityType.UNFAVORITE, messageId);
    }

    @Override
    public MbActivity createFavorite(String messageId) throws ConnectionException {
        return actOnMessage(ActivityType.FAVORITE, messageId);
    }

    @Override
    public boolean destroyStatus(String messageId) throws ConnectionException {
        return !actOnMessage(ActivityType.DELETE, messageId).isEmpty();
    }

    private MbActivity actOnMessage(ActivityType activityType, String messageId) throws ConnectionException {
        return ActivitySender.fromId(this, messageId).sendMessage(activityType);
    }

    @Override
    public List<MbUser> getFollowers(String userId) throws ConnectionException {
        return getUsers(userId, ApiRoutineEnum.GET_FOLLOWERS);
    }

    @Override
    public List<MbUser> getFriends(String userId) throws ConnectionException {
        return getUsers(userId, ApiRoutineEnum.GET_FRIENDS);
    }

    @NonNull
    private List<MbUser> getUsers(String userId, ApiRoutineEnum apiRoutine) throws ConnectionException {
        int limit = 200;
        ConnectionAndUrl conu = getConnectionAndUrl(apiRoutine, userId);
        Uri sUri = Uri.parse(conu.url);
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        String url = builder.build().toString();
        JSONArray jArr = conu.httpConnection.getRequestAsArray(url);
        List<MbUser> users = new ArrayList<>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbUser item = userFromJson(jso);
                    users.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(this, "Parsing list of users", e, null);
                }
            }
        }
        MyLog.d(TAG, apiRoutine + " '" + url + "' " + users.size() + " users");
        return users;
    }

    @Override
    protected MbActivity getMessage1(String messageId) throws ConnectionException {
        return activityFromJson(http.getRequest(messageId));
    }

    @Override
    public MbActivity updateStatus(String messageIn, String statusId, String inReplyToId, Uri mediaUri) throws ConnectionException {
        String message = toHtmlIfAllowed(messageIn);
        ActivitySender sender = ActivitySender.fromContent(this, statusId, message);
        sender.setInReplyTo(inReplyToId);
        sender.setMediaUri(mediaUri);
        return activityFromJson(sender.sendMe(ActivityType.POST));
    }
    
    private String toHtmlIfAllowed(String message) {
        return MyContextHolder.get().persistentOrigins().isHtmlContentAllowed(data.getOriginId()) ?
            MyHtml.htmlify(message) : message;
    }

    String oidToObjectType(String oid) {
        String objectType = "";
        if (oid.contains("/comment/")) {
            objectType = "comment";
        } else if (oid.startsWith("acct:")) {
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

    ConnectionAndUrl getConnectionAndUrl(ApiRoutineEnum apiRoutine, String userId) throws ConnectionException {
        if (TextUtils.isEmpty(userId)) {
            throw new ConnectionException(StatusCode.BAD_REQUEST, apiRoutine + ": userId is required");
        }
        return  getConnectionAndUrlForUsername(apiRoutine, userOidToUsername(userId));
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
            throw new ConnectionException(StatusCode.BAD_REQUEST, apiRoutine + ": host is empty for the userName='" + username + "'");
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
                throw ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST, "No credentials", conu.httpConnection.data.originUrl);
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
    public MbActivity postDirectMessage(String messageIn, String statusId, String recipientId, Uri mediaUri) throws ConnectionException {
        String message = toHtmlIfAllowed(messageIn);
        ActivitySender sender = ActivitySender.fromContent(this, statusId, message);
        sender.setRecipient(recipientId);
        sender.setMediaUri(mediaUri);
        return activityFromJson(sender.sendMe(ActivityType.POST));
    }

    @Override
    public MbActivity postReblog(String rebloggedId) throws ConnectionException {
        return actOnMessage(ActivityType.SHARE, rebloggedId);
    }

    @NonNull
    @Override
    public List<MbActivity> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition youngestPosition,
                                        TimelinePosition oldestPosition, int limit, String userId)
            throws ConnectionException {
        ConnectionAndUrl conu = getConnectionAndUrl(apiRoutine, userId);
        Uri sUri = Uri.parse(conu.url);
        Uri.Builder builder = sUri.buildUpon();
        if (youngestPosition.isPresent()) {
            // The "since" should point to the "Activity" on the timeline, not to the message
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("since", youngestPosition.getPosition());
        } else if (oldestPosition.isPresent()) {
            builder.appendQueryParameter("before", oldestPosition.getPosition());
        }
        builder.appendQueryParameter("count", strFixedDownloadLimit(limit, apiRoutine));
        String url = builder.build().toString();
        JSONArray jArr = conu.httpConnection.getRequestAsArray(url);
        List<MbActivity> activities = new ArrayList<>();
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
        MyLog.d(TAG, "getTimeline '" + url + "' " + activities.size() + " messages");
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
    MbActivity activityFromJson(JSONObject jsoActivity) throws ConnectionException {
        if (jsoActivity == null) {
            return MbActivity.EMPTY;
        }
        MbActivity activity = MbActivity.from(data.getPartialAccountUser(),
                ActivityType.load(jsoActivity.optString("verb")).mbActivityType);
        try {
            if (ObjectType.ACTIVITY.isTypeOf(jsoActivity)) {
                String oid = jsoActivity.optString("id");
                if (TextUtils.isEmpty(oid)) {
                    MyLog.d(this, "Pumpio activity has no id:" + jsoActivity.toString(2));
                    return MbActivity.EMPTY;
                }
                activity.setTimelinePosition(oid);
                activity.setUpdatedDate(dateFromJson(jsoActivity, "updated"));
                if (jsoActivity.has("actor")) {
                    activity.setActor(userFromJson(jsoActivity.getJSONObject("actor")));
                }

                JSONObject objectOfActivity = jsoActivity.getJSONObject("object");
                if (ObjectType.ACTIVITY.isTypeOf(objectOfActivity)) {
                    // Simplified dealing with nested activities
                    MbActivity innerActivity = activityFromJson(objectOfActivity);
                    activity.setUser(innerActivity.getUser());
                    activity.setMessage(innerActivity.getMessage());
                } else {
                    parseObjectOfActivity(activity, objectOfActivity);
                }
                if (activity.getObjectType().equals(MbObjectType.MESSAGE)) {
                    if (jsoActivity.has("to")) {
                        JSONObject to = jsoActivity.optJSONObject("to");
                        if ( to != null) {
                            activity.getMessage().addRecipient(userFromJson(to));
                        } else {
                            JSONArray arrayOfTo = jsoActivity.optJSONArray("to");
                            if (arrayOfTo != null && arrayOfTo.length() > 0) {
                                // TODO: handle multiple recipients
                                to = arrayOfTo.optJSONObject(0);
                                MbUser recipient = userFromJson(to);
                                if (!recipient.isEmpty()) {
                                    activity.getMessage().addRecipient(recipient);
                                }
                            }
                        }
                    }
                    setVia(activity.getMessage(), jsoActivity);
                    if(activity.getAuthor().isEmpty()) {
                        activity.setAuthor(activity.getActor());
                    }
                }
            } else {
                parseObjectOfActivity(activity, jsoActivity);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(this, "Parsing timeline item", e, jsoActivity);
        }
        return activity;
    }

    private void parseObjectOfActivity(MbActivity activity, JSONObject objectOfActivity) throws ConnectionException {
        if (ObjectType.PERSON.isTypeOf(objectOfActivity)) {
            activity.setUser(userFromJson(objectOfActivity));
        } else if (ObjectType.compatibleWith(objectOfActivity) == ObjectType.COMMENT) {
            messageFromJsonComment(activity, objectOfActivity);
            switch (activity.type) {
                case LIKE:
                    activity.getMessage().setFavorited(TriState.TRUE);
                    break;
                case UNDO_LIKE:
                    activity.getMessage().setFavorited(TriState.FALSE);
                    break;
                case ANNOUNCE:
                    activity.getMessage().setReblogOid(activity.getTimelinePosition().getPosition());
                    break;
                default:
                    break;
            }
        }
    }

    private void setVia(MbMessage message, JSONObject activity) throws JSONException {
        if (TextUtils.isEmpty(message.via) && activity.has(Properties.GENERATOR.code)) {
            JSONObject generator = activity.getJSONObject(Properties.GENERATOR.code);
            if (generator.has("displayName")) {
                message.via = generator.getString("displayName");
            }
        }
    }

    private URL getImageUrl(JSONObject jso, String imageTag) throws JSONException {
        if (jso.has(imageTag)) {
            JSONObject attachment = jso.getJSONObject(imageTag);
            return UrlUtils.fromJson(attachment, "url");
        } 
        return null;
    }
    
    private void messageFromJsonComment(MbActivity activity, JSONObject jso) throws ConnectionException {
        try {
            String oid = jso.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio object has no id:" + jso.toString(2));
                return;
            } 
            MbMessage message =  MbMessage.fromOriginAndOid(data.getOriginId(), oid, DownloadStatus.LOADED);
            activity.setMessage(message);
            if (jso.has("author")) {
                activity.setActor(userFromJson(jso.getJSONObject("author")));
            }
            if (jso.has("content")) {
                message.setBody(jso.getString("content"));
            }
            message.setUpdatedDate(dateFromJson(jso, "updated"));
            if (message.getUpdatedDate() == 0) {
                message.setUpdatedDate(dateFromJson(jso, "published"));
            }

            setVia(message, jso);
            message.url = jso.optString("url");

            if (jso.has("fullImage") || jso.has("image")) {
                URL url = getImageUrl(jso, "fullImage");
                if (url == null) {
                    url = getImageUrl(jso, "image");
                }
                MbAttachment mbAttachment =  MbAttachment.fromUrlAndContentType(url, MyContentType.IMAGE);
                if (mbAttachment.isValid()) {
                    message.attachments.add(mbAttachment);
                } else {
                    MyLog.d(this, "Invalid attachment; " + jso.toString());
                }
            }

            // If the Msg is a Reply to other message
            if (jso.has("inReplyTo")) {
                message.setInReplyTo(activityFromJson(jso.getJSONObject("inReplyTo")));
                message.getInReplyTo().getMessage().setSubscribedByMe(TriState.FALSE);
            }

            if (jso.has("replies")) {
                JSONObject replies = jso.getJSONObject("replies");
                if (replies.has("items")) {
                    JSONArray jArr = replies.getJSONArray("items");
                    for (int index = 0; index < jArr.length(); index++) {
                        try {
                            MbMessage item = activityFromJson(jArr.getJSONObject(index)).getMessage();
                            item.setSubscribedByMe(TriState.FALSE);
                            message.replies.add(item);
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
     * 2014-01-22 According to the crash reports, userId may not have "acct:" prefix
     */
    public String userOidToUsername(String userId) {
        String username = "";
        if (!TextUtils.isEmpty(userId)) {
            int indexOfColon = userId.indexOf(':');
            if (indexOfColon >= 0) {
                username = userId.substring(indexOfColon+1);
            } else {
                username = userId;
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
    public List<MbActivity> searchMessages(TimelinePosition youngestPosition,
                                           TimelinePosition oldestPosition, int limit, String searchQuery)
            throws ConnectionException {
        return new ArrayList<>();
    }

    @Override
    public MbActivity followUser(String userId, Boolean follow) throws ConnectionException {
        return actOnUser(follow ? ActivityType.FOLLOW : ActivityType.STOP_FOLLOWING, userId);
    }

    private MbActivity actOnUser(ActivityType activityType, String userId) throws ConnectionException {
        return ActivitySender.fromId(this, userId).sendUser(activityType);
    }
    
    @Override
    public MbUser getUser(String userId, String userName) throws ConnectionException {
        ConnectionAndUrl conu = getConnectionAndUrlForUsername(ApiRoutineEnum.GET_USER,
                MbUser.isOidReal(userId) ? userOidToUsername(userId) : userName);
        JSONObject jso = conu.httpConnection.getRequest(conu.url);
        MbUser mbUser = userFromJson(jso);
        MyLog.v(this, "getUser oid='" + userId + "', userName='" + userName + "' -> " + mbUser.getRealName());
        return mbUser;
    }

    protected OriginConnectionData getData() {
        return data;
    }

}
