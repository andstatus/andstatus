package org.andstatus.app.net;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.andstatus.app.origin.OriginConnectionData;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Twitter API implementations
 * @author yvolk
 */
public abstract class ConnectionTwitter extends Connection {
    private static final String TAG = ConnectionTwitter.class.getSimpleName();

    protected static Connection fromConnectionDataProtected(OriginConnectionData connectionData) {
        Connection connection;
        switch (connectionData.api) {
            case STATUSNET_TWITTER:
                connection = new ConnectionTwitterStatusNet(connectionData);
                break;
            case TWITTER1P0:
                connection = new ConnectionTwitter1p0(connectionData);
                break;
            default:
                connection = new ConnectionTwitter1p1(connectionData);
        }
        return connection;
    }

    public ConnectionTwitter(OriginConnectionData connectionData) {
        super(connectionData);
    }

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
            case FAVORITES_CREATE_BASE:
                url = "favorites/create/";
                break;
            case FAVORITES_DESTROY_BASE:
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
            case STATUSES_SHOW:
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
        }
        if (!TextUtils.isEmpty(url)) {
            url = httpConnection.connectionData.basicPath + "/" + url;
        }
        return url;
    }

    @Override
    public JSONObject destroyStatus(String statusId) throws ConnectionException {
        return httpConnection.postRequest(getApiPath(ApiRoutineEnum.STATUSES_DESTROY) + statusId + EXTENSION);
    }
    
    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/create">POST friendships/create</a>
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/post/friendships/destroy">POST friendships/destroy</a>
     */
    @Override
    public JSONObject followUser(String userId, Boolean follow) throws ConnectionException {
        List<NameValuePair> out = new LinkedList<NameValuePair>();
        out.add(new BasicNameValuePair("user_id", userId));
        return postRequest((follow ? ApiRoutineEnum.FOLLOW_USER : ApiRoutineEnum.STOP_FOLLOWING_USER), out);
    } 

    /**
     * Returns an array of numeric IDs for every user the specified user is following.
     * Current implementation is restricted to 5000 IDs (no paged cursors are used...)
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/get/friends/ids">GET friends/ids</a>
     * @throws ConnectionException
     */
    @Override
    public JSONArray getFriendsIds(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        JSONObject jso = httpConnection.getRequest(builder.build().toString());
        JSONArray jArr = null;
        if (jso != null) {
            try {
                jArr = jso.getJSONArray("ids");
            } catch (JSONException e) {
                Log.w(TAG, "getFriendsIds, response=" + (jso == null ? "(null)" : jso.toString()));
                throw new ConnectionException(e.getLocalizedMessage());
            }
        }
        return jArr;
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
    public JSONObject getStatus(String statusId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.STATUSES_SHOW));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("id", statusId);
        return httpConnection.getRequest(builder.build().toString());
    }

    @Override
    public JSONArray getTimeline(ApiRoutineEnum apiRoutine, String sinceId, int limit, String userId)
            throws ConnectionException {
        boolean ok = false;
        String url = this.getApiPath(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!TextUtils.isEmpty(fixSinceId(sinceId))) {
            builder.appendQueryParameter("since_id", fixSinceId(sinceId));
        }
        if (fixedLimit(limit) > 0) {
            builder.appendQueryParameter("count", String.valueOf(fixedLimit(limit)));
        }
        if (!TextUtils.isEmpty(userId)) {
            builder.appendQueryParameter("user_id", userId);
        }
        JSONArray jArr = httpConnection.getRequestAsArray(builder.build().toString());
        
        ok = (jArr != null);
        if (MyLog.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getTimeline '" + url + "' "
                    + (ok ? "OK, " + jArr.length() + " statuses" : "FAILED"));
        }
        return jArr;
    }


    /**
     * @see <a
     *      href="https://dev.twitter.com/docs/api/1.1/get/users/show">GET users/show</a>
     */
    @Override
    public JSONObject getUser(String userId) throws ConnectionException {
        Uri sUri = Uri.parse(getApiPath(ApiRoutineEnum.GET_USER));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        return httpConnection.getRequest(builder.build().toString());
    }
    
    @Override
    public JSONObject postDirectMessage(String message, String userId) throws ConnectionException {
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        formParams.add(new BasicNameValuePair("text", message));
        if ( !TextUtils.isEmpty(userId)) {
            formParams.add(new BasicNameValuePair("user_id", userId));
        }
        return postRequest(ApiRoutineEnum.POST_DIRECT_MESSAGE, formParams);
    }
    
    @Override
    public JSONObject postReblog(String rebloggedId) throws ConnectionException {
        return httpConnection.postRequest(getApiPath(ApiRoutineEnum.POST_REBLOG) + rebloggedId + EXTENSION);
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
     * @return JSONObject
     * @throws ConnectionException
     */
    @Override
    public JSONObject rateLimitStatus() throws ConnectionException {
        return httpConnection.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS));
    }
    
    @Override
    public JSONObject updateStatus(String message, String inReplyToId) throws ConnectionException {
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        formParams.add(new BasicNameValuePair("status", message));
        
        // This parameter was removed from API:
        // formParams.add(new BasicNameValuePair("source", SOURCE_PARAMETER));
        
        if ( !TextUtils.isEmpty(inReplyToId)) {
            formParams.add(new BasicNameValuePair("in_reply_to_status_id", inReplyToId));
        }
        return postRequest(ApiRoutineEnum.STATUSES_UPDATE, formParams);
    }

    /**
     * @see <a
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
     *      REST API Method: account verify_credentials</a>
     */
    @Override
    public MbUser verifyCredentials() throws ConnectionException {
        JSONObject user = httpConnection.getRequest(getApiPath(ApiRoutineEnum.ACCOUNT_VERIFY_CREDENTIALS));
        MbUser mbUser = new MbUser();
        if (user.has("screen_name")) {
            mbUser.userName = user.optString("screen_name");
            if (SharedPreferencesUtil.isEmpty(mbUser.userName)) {
                mbUser.userName = "";
            }
        }
        if (user.has("id_str")) {
            mbUser.oid = user.optString("id_str");
        } else if (user.has("id")) {
            mbUser.oid = user.optString("id");
        } 
        if (SharedPreferencesUtil.isEmpty(mbUser.oid)) {
            mbUser.oid = "";
        }
        mbUser.originId = httpConnection.connectionData.originId;
        mbUser.realName = user.optString("name");
        mbUser.avatarUrl = user.optString("profile_image_url");
        mbUser.description = user.optString("description");
        mbUser.homepage = user.optString("url");
        if (user.has("created_at")) {
            String createdAt = user.optString("created_at");
            if (createdAt.length() > 0) {
                mbUser.createdDate = Date.parse(createdAt);
            }
        }
        if (!user.isNull("following")) {
            try {
                mbUser.isFollowed = user.getBoolean("following");
            } catch (JSONException e) {
                Log.e(TAG, "error; following='" + user.optString("following") +"'. " + e.toString());
            }
        }
        return mbUser;
    }
}
