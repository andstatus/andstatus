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
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;

import org.andstatus.app.net.ConnectionException.StatusCode;
import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Implementation of pump.io API: <a href="https://github.com/e14n/pump.io/blob/master/API.md">https://github.com/e14n/pump.io/blob/master/API.md</a>  
 * @author yvolk@yurivolkov.com
 */
public class ConnectionPumpio extends Connection {
    private static final String TAG = ConnectionPumpio.class.getSimpleName();

    @Override
    public void enrichConnectionData(OriginConnectionData connectionData) {
        super.enrichConnectionData(connectionData);
        if (!TextUtils.isEmpty(connectionData.accountUsername)) {
            connectionData.host = usernameToHost(connectionData.accountUsername);
        }
        if (connectionData.isOAuth) {
            connectionData.httpConnectionClass = HttpConnectionOAuthJavaNet.class;
        } else {
            throw new IllegalArgumentException(TAG + " basic OAuth is not supported");
        }
    }
    
    @Override
    protected String getApiPath1(ApiRoutineEnum routine) {
        String url;
        switch(routine) {
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = "whoami";
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
            case STATUSES_HOME_TIMELINE:
                url = "user/%nickname%/inbox";
                break;
            case CREATE_FAVORITE:
            case DESTROY_FAVORITE:
            case FOLLOW_USER:
            case POST_DIRECT_MESSAGE:
            case POST_REBLOG:
            case STATUSES_DESTROY:
            case STATUSES_UPDATE:
            case STATUSES_USER_TIMELINE:
                url = "user/%nickname%/feed";
                break;
            default:
                url = "";
        }
        if (!TextUtils.isEmpty(url)) {
            url = http.data.basicPath + "/" + url;
        }
        return url;
    }

    @Override
    public MbRateLimitStatus rateLimitStatus() throws ConnectionException {
        // TODO Method stub
        MbRateLimitStatus status = new MbRateLimitStatus();
        return status;
    }

    @Override
    public MbUser verifyCredentials() throws ConnectionException {
        JSONObject user = http.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        return userFromJson(user);
    }

    private MbUser userFromJson(JSONObject jso) throws ConnectionException {
        if (!PumpioObjectType.PERSON.isMyType(jso)) {
            return MbUser.getEmpty();
        }
        String oid = jso.optString("id");
        MbUser user = MbUser.fromOriginAndUserOid(data.originId, oid);
        user.actor = MbUser.fromOriginAndUserOid(data.originId, data.accountUserOid);
        user.userName = userOidToUsername(oid);
        user.oid = oid;
        user.realName = jso.optString("displayName");
        if (jso.has("image")) {
            JSONObject image = jso.optJSONObject("image");
            if (image != null) {
                user.avatarUrl = image.optString("url");
            }
        }
        user.description = jso.optString("summary");
        user.homepage = jso.optString("url");
        user.url = jso.optString("url");
        user.updatedDate = dateFromJson(jso, "updated");
        return user;
    }
    
    private long dateFromJson(JSONObject jso, String fieldName) {
        long date = 0;
        if (jso != null && jso.has(fieldName)) {
            String updated = jso.optString(fieldName);
            if (updated.length() > 0) {
                date = parseDate(updated);
            }
        }
        return date;
    }
    
    /**
     * Simple solution based on:
     * http://stackoverflow.com/questions/2201925/converting-iso8601-compliant-string-to-java-util-date
     */
    private static long parseDate(String date) {
        if(date == null)
            return new Date().getTime();
        String datePrepared;        
        if (date.lastIndexOf("Z") == date.length()-1) {
            datePrepared = date.substring(0, date.length()-1) + "+0000";
        } else {
            datePrepared = date.replaceAll("\\+0([0-9]){1}\\:00", "+0$100");
        }
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.GERMANY);
        try {
            long unixTime = df.parse(datePrepared).getTime();
            return unixTime;
        } catch (ParseException e) {
            Log.e(TAG, "Failed to parse the date: '" + date +"'");
            return new Date().getTime();
        }
    }
    
    @Override
    public MbMessage destroyFavorite(String messageId) throws ConnectionException {
        return verbWithMessage("unfavorite", messageId);
    }

    @Override
    public MbMessage createFavorite(String messageId) throws ConnectionException {
        return verbWithMessage("favorite", messageId);
    }

    @Override
    public boolean destroyStatus(String messageId) throws ConnectionException {
        MbMessage message = verbWithMessage("delete", messageId);
        return !message.isEmpty();
    }

    private MbMessage verbWithMessage(String verb, String messageId) throws ConnectionException {
        return ActivitySender.fromId(this, messageId).sendMessage(verb);
    }

    @Override
    public List<MbUser> getUsersFollowedBy(String userId) throws ConnectionException {
        int limit = 200;
        ApiRoutineEnum apiRoutine = ApiRoutineEnum.GET_FRIENDS;
        ConnectionAndUrl conu = getConnectionAndUrl(apiRoutine, userId);
        Uri sUri = Uri.parse(conu.url);
        Uri.Builder builder = sUri.buildUpon();
        if (fixedDownloadLimitForApiRoutine(limit, apiRoutine) > 0) {
            builder.appendQueryParameter("count",String.valueOf(fixedDownloadLimitForApiRoutine(limit, apiRoutine)));
        }
        String url = builder.build().toString();
        JSONArray jArr = conu.httpConnection.getRequestAsArray(url);
        List<MbUser> followedUsers = new ArrayList<MbUser>();
        if (jArr != null) {
            for (int index = 0; index < jArr.length(); index++) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbUser item = userFromJson(jso);
                    followedUsers.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(TAG, e, null, "Parsing list of users");
                }
            }
        }
        MyLog.d(TAG, "getUsersFollowedBy '" + url + "' " + followedUsers.size() + " users");
        return followedUsers;
    }

    @Override
    public MbMessage getMessage(String messageId) throws ConnectionException {
        JSONObject message = http.getRequest(messageId);
        return messageFromJson(message);
    }

    @Override
    public MbMessage updateStatus(String message, String inReplyToId) throws ConnectionException {
        ActivitySender sender = ActivitySender.fromContent(this, message);
        if (!TextUtils.isEmpty(inReplyToId)) {
            sender.setInReplyTo(inReplyToId);
        }
        return messageFromJson(sender.sendMe("post"));
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
            Log.e(TAG, objectType);
        }
        return objectType;
    }

    ConnectionAndUrl getConnectionAndUrl(ApiRoutineEnum apiRoutine, String userId) throws ConnectionException {
        ConnectionAndUrl conu = new ConnectionAndUrl();
        conu.url = this.getApiPath(apiRoutine);
        if (TextUtils.isEmpty(conu.url)) {
            throw new ConnectionException(StatusCode.UNSUPPORTED_API, "The API is not supported yet: " + apiRoutine);
        }
        if (TextUtils.isEmpty(userId)) {
            throw new IllegalArgumentException(apiRoutine + ": userId is required");
        }
        String nickname = userOidToNickname(userId);
        if (TextUtils.isEmpty(nickname)) {
            throw new IllegalArgumentException(apiRoutine + ": wrong userId=" + userId);
        }
        String host = usernameToHost(userOidToUsername(userId));
        conu.httpConnection = http;
        if (TextUtils.isEmpty(host)) {
            throw new IllegalArgumentException(apiRoutine + ": host is empty for the userId=" + userId);
        } else if (host.compareToIgnoreCase(http.data.host) != 0) {
            MyLog.v(TAG, "Requesting data from the host: " + host);
            HttpConnectionData connectionData1 = http.data.newCopy();
            connectionData1.oauthClientKeys = null;
            connectionData1.host = host;
            try {
                conu.httpConnection = http.getClass().newInstance();
                conu.httpConnection.setConnectionData(connectionData1);
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        if (!conu.httpConnection.data.areOAuthClientKeysPresent()) {
            conu.httpConnection.registerClient(getApiPath(ApiRoutineEnum.REGISTER_CLIENT));
            if (!conu.httpConnection.getCredentialsPresent()) {
                throw ConnectionException.fromStatusCodeAndHost(StatusCode.NO_CREDENTIALS_FOR_HOST, conu.httpConnection.data.host, "No credentials");
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
    public MbMessage postDirectMessage(String message, String recipientId) throws ConnectionException {
        ActivitySender sender = ActivitySender.fromContent(this, message);
        if (!TextUtils.isEmpty(recipientId)) {
            sender.setRecipient(recipientId);
        }
        return messageFromJson(sender.sendMe("post"));
    }

    @Override
    public MbMessage postReblog(String rebloggedId) throws ConnectionException {
        return verbWithMessage("share", rebloggedId);
    }

    @Override
    public List<MbTimelineItem> getTimeline(ApiRoutineEnum apiRoutine, TimelinePosition sinceId, int limit, String userId)
            throws ConnectionException {
        ConnectionAndUrl conu = getConnectionAndUrl(apiRoutine, userId);
        Uri sUri = Uri.parse(conu.url);
        Uri.Builder builder = sUri.buildUpon();
        if (!sinceId.isEmpty()) {
            // The "since" should point to the "Activity" on the timeline, not to the message
            // Otherwise we will always get "not found"
            builder.appendQueryParameter("since", sinceId.getPosition());
        }
        if (fixedDownloadLimitForApiRoutine(limit, apiRoutine) > 0) {
            builder.appendQueryParameter("count",String.valueOf(fixedDownloadLimitForApiRoutine(limit, apiRoutine)));
        }
        String url = builder.build().toString();
        JSONArray jArr = conu.httpConnection.getRequestAsArray(url);
        List<MbTimelineItem> timeline = new ArrayList<MbTimelineItem>();
        if (jArr != null) {
            // Read the activities in chronological order
            for (int index = jArr.length() - 1; index >= 0; index--) {
                try {
                    JSONObject jso = jArr.getJSONObject(index);
                    MbTimelineItem item = timelineItemFromJson(jso);
                    timeline.add(item);
                } catch (JSONException e) {
                    throw ConnectionException.loggedJsonException(TAG, e, null, "Parsing timeline");
                }
            }
        }
        MyLog.d(TAG, "getTimeline '" + url + "' " + timeline.size() + " messages");
        return timeline;
    }

    @Override
    public int fixedDownloadLimitForApiRoutine(int limit, ApiRoutineEnum apiRoutine) {
        final int maxLimit = (apiRoutine == ApiRoutineEnum.GET_FRIENDS ? 200 : 20);
        int out = super.fixedDownloadLimitForApiRoutine(limit, apiRoutine);
        if (out > maxLimit) {
            out = maxLimit;
        }
        return out;
    }

    private MbTimelineItem timelineItemFromJson(JSONObject activity) throws ConnectionException {
        MbTimelineItem item = new MbTimelineItem();
        if (PumpioObjectType.ACTIVITY.isMyType(activity)) {
            try {
                item.timelineItemPosition = new TimelinePosition(activity.optString("id"));
                item.timelineItemDate = dateFromJson(activity, "updated");
                
                if (PumpioObjectType.PERSON.isMyType(activity.getJSONObject("object"))) {
                    item.mbUser = userFromJsonActivity(activity);
                } else {
                    item.mbMessage = messageFromJsonActivity(activity);
                }
            } catch (JSONException e) {
                throw ConnectionException.loggedJsonException(TAG, e, activity, "Parsing timeline item");
            }
        } else {
            Log.e(TAG, "Not an Activity in the timeline:" + activity.toString() );
            item.mbMessage = messageFromJson(activity);
        }
        return item;
    }
    
    public MbUser userFromJsonActivity(JSONObject activity) throws ConnectionException {
        MbUser mbUser;
        try {
            String verb = activity.getString("verb");
            String oid = activity.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio activity has no id:" + activity.toString(2));
                return MbUser.getEmpty();
            }
            mbUser = userFromJson(activity.getJSONObject("object"));
            if (activity.has("actor")) {
                mbUser.actor = userFromJson(activity.getJSONObject("actor"));
            }
            
            if (verb.equalsIgnoreCase("follow")) {
                mbUser.followedByActor = TriState.TRUE;
            } else if (verb.equalsIgnoreCase("unfollow") 
                    || verb.equalsIgnoreCase("stop-following")) {
                mbUser.followedByActor = TriState.FALSE;
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, activity, "Parsing activity");
        }
        return mbUser;
    }

    MbMessage messageFromJson(JSONObject jso) throws ConnectionException {
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            try {
                MyLog.v(TAG, "messageFromJson: " + jso.toString(2));
            } catch (JSONException e) {
                ConnectionException.loggedJsonException(TAG, e, jso, "messageFromJson");
            }
        }
        if (PumpioObjectType.ACTIVITY.isMyType(jso)) {
            return messageFromJsonActivity(jso);
        } else if (PumpioObjectType.compatibleWith(jso) == PumpioObjectType.COMMENT) { 
            return messageFromJsonComment(jso);
        } else {
            return MbMessage.getEmpty();
        }
    }
    
    private MbMessage messageFromJsonActivity(JSONObject activity) throws ConnectionException {
        MbMessage message;
        try {
            String verb = activity.getString("verb");
            String oid = activity.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio activity has no id:" + activity.toString(2));
                return MbMessage.getEmpty();
            } 
            message =  MbMessage.fromOriginAndOid(data.originId, oid);
            message.actor = MbUser.fromOriginAndUserOid(data.originId, data.accountUserOid);
            message.sentDate = dateFromJson(activity, "updated");

            if (activity.has("actor")) {
                message.sender = userFromJson(activity.getJSONObject("actor"));
                if (!message.sender.isEmpty()) {
                    message.actor = message.sender;
                }
            }
            if (activity.has("to")) {
                JSONObject to = activity.optJSONObject("to");
                if ( to != null) {
                    message.recipient = userFromJson(to);
                } else {
                    JSONArray arrayOfTo = activity.optJSONArray("to");
                    if (arrayOfTo != null && arrayOfTo.length() > 0) {
                        // TODO: handle multiple recipients
                        to = arrayOfTo.optJSONObject(0);
                        MbUser recipient = userFromJson(to);
                        if (!recipient.isEmpty()) {
                            message.recipient = recipient;
                        }
                    }
                }
            }
            if (activity.has("generator")) {
                JSONObject generator = activity.getJSONObject("generator");
                if (generator.has("displayName")) {
                    message.via = generator.getString("displayName");
                }
            }
            
            JSONObject jso = activity.getJSONObject("object");
            // Is this a reblog ("Share" in terms of Activity streams)?
            if (verb.equalsIgnoreCase("share")) {
                message.rebloggedMessage = messageFromJson(jso);
                if (message.rebloggedMessage.isEmpty()) {
                    MyLog.d(TAG, "No reblogged message " + jso.toString(2));
                    return message.markAsEmpty();
                }
            } else {
                if (verb.equalsIgnoreCase("favorite")) {
                    message.favoritedByActor = TriState.TRUE;
                } else if (verb.equalsIgnoreCase("unfavorite") || verb.equalsIgnoreCase("unlike")) {
                    message.favoritedByActor = TriState.FALSE;
                }
                
                if (PumpioObjectType.compatibleWith(jso) == PumpioObjectType.COMMENT) {
                    parseComment(message, jso);
                } else {
                    return message.markAsEmpty();
                }
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, activity, "Parsing activity");
        }
        return message;
    }

    private void parseComment(MbMessage message, JSONObject jso) throws ConnectionException {
        try {
            String oid = jso.optString("id");
            if (!TextUtils.isEmpty(oid)) {
                if (!message.oid.equalsIgnoreCase(oid)) {
                    message.oid = oid;
                }
            } 
            if (jso.has("author")) {
                MbUser author = userFromJson(jso.getJSONObject("author"));
                if (!author.isEmpty()) {
                    message.sender = author;
                }
            }
            if (jso.has("content")) {
                message.body = Html.fromHtml(jso.getString("content")).toString().trim();
            }
            message.sentDate = dateFromJson(jso, "published");

            if (jso.has("generator")) {
                JSONObject generator = jso.getJSONObject("generator");
                if (generator.has("displayName")) {
                    message.via = generator.getString("displayName");
                }
            }
            message.url = jso.optString("url");

            // If the Msg is a Reply to other message
            if (jso.has("inReplyTo")) {
                JSONObject inReplyToObject = jso.getJSONObject("inReplyTo");
                message.inReplyToMessage = messageFromJson(inReplyToObject);
            }
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, jso, "Parsing comment/note");
        }
    }
    
    private MbMessage messageFromJsonComment(JSONObject jso) throws ConnectionException {
        MbMessage message;
        try {
            String oid = jso.optString("id");
            if (TextUtils.isEmpty(oid)) {
                MyLog.d(TAG, "Pumpio object has no id:" + jso.toString(2));
                return MbMessage.getEmpty();
            } 
            message =  MbMessage.fromOriginAndOid(data.originId, oid);
            message.actor = MbUser.fromOriginAndUserOid(data.originId, data.accountUserOid);

            parseComment(message, jso);
        } catch (JSONException e) {
            throw ConnectionException.loggedJsonException(TAG, e, jso, "Parsing comment");
        }
        return message;
    }
    
    private String userOidToUsername(String userId) {
        String username = "";
        if (!TextUtils.isEmpty(userId)) {
            int indexOfColon = userId.indexOf(":");
            if (indexOfColon > 0) {
                username = userId.substring(indexOfColon+1);
            }
        }
        return username;
    }
    
    private String userOidToNickname(String userId) {
        String nickname = "";
        if (!TextUtils.isEmpty(userId)) {
            int indexOfColon = userId.indexOf(":");
            int indexOfAt = userId.indexOf("@");
            if (indexOfColon > 0 && indexOfAt > indexOfColon) {
                nickname = userId.substring(indexOfColon+1, indexOfAt);
            }
        }
        return nickname;
    }

    String usernameToHost(String username) {
        String host = "";
        if (!TextUtils.isEmpty(username)) {
            int indexOfAt = username.indexOf("@");
            if (indexOfAt >= 0) {
                host = username.substring(indexOfAt + 1);
            }
        }
        return host;
    }
    
    @Override
    public MbUser followUser(String userId, Boolean follow) throws ConnectionException {
        return verbWithUser(follow ? "follow" : "unfollow", userId);
    }

    private MbUser verbWithUser(String verb, String userId) throws ConnectionException {
        return ActivitySender.fromId(this, userId).sendUser(verb);
    }
    
    @Override
    public MbUser getUser(String userId) throws ConnectionException {
        ConnectionAndUrl conu = getConnectionAndUrl(ApiRoutineEnum.GET_USER, userId);
        JSONObject jso = conu.httpConnection.getRequest(conu.url);
        MbUser mbUser = userFromJson(jso);
        MyLog.v(TAG, "getUser '" + userId + "' " + mbUser.realName);
        return mbUser;
    }
}
