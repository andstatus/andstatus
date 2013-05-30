package org.andstatus.app.net;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import org.andstatus.app.account.AccountDataReader;
import org.andstatus.app.util.MyLog;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Twitter API implementations
 * @author yvolk
 */
public abstract class ConnectionTwitter extends Connection {
    private static final String TAG = Connection.class.getSimpleName();

    public ConnectionTwitter(AccountDataReader dr, ApiEnum api, String apiBaseUrl) {
        super(dr, api, apiBaseUrl);
    }

    /**
     * URL of the API. Not logged
     * @param routine
     * @return URL or an empty string in case the API routine is not supported
     */
    @Override
    protected String getApiUrl1(ApiRoutineEnum routine) {
        String url = "";
        switch(routine) {
            case ACCOUNT_RATE_LIMIT_STATUS:
                url = getBaseUrl() + "/account/rate_limit_status" + EXTENSION;
                break;
            case ACCOUNT_VERIFY_CREDENTIALS:
                url = getBaseUrl() + "/account/verify_credentials" + EXTENSION;
                break;
            case DIRECT_MESSAGES:
                url = getBaseUrl() + "/direct_messages" + EXTENSION;
                break;
            case FAVORITES_CREATE_BASE:
                url = getBaseUrl() + "/favorites/create/";
                break;
            case FAVORITES_DESTROY_BASE:
                url = getBaseUrl() + "/favorites/destroy/";
                break;
            case FOLLOW_USER:
                url = getBaseUrl() + "/friendships/create" + EXTENSION;
                break;
            case GET_FRIENDS_IDS:
                url = getBaseUrl() + "/friends/ids" + EXTENSION;
                break;
            case GET_USER:
                url = getBaseUrl() + "/users/show" + EXTENSION;
                break;
            case POST_DIRECT_MESSAGE:
                url = getBaseUrl() + "/direct_messages/new" + EXTENSION;
                break;
            case POST_REBLOG:
                url = getBaseUrl() + "/statuses/retweet/";
                break;
            case STATUSES_DESTROY:
                url = getBaseUrl() + "/statuses/destroy/";
                break;
            case STATUSES_HOME_TIMELINE:
                url = getBaseUrl() + "/statuses/home_timeline" + EXTENSION;
                break;
            case STATUSES_MENTIONS_TIMELINE:
                url = getBaseUrl()  + "/statuses/mentions" + EXTENSION;
                break;
            case STATUSES_USER_TIMELINE:
                url = getBaseUrl() + "/statuses/user_timeline" + EXTENSION;
                break;
            case STATUSES_SHOW:
                url = getBaseUrl() + "/statuses/show" + EXTENSION;
                break;
            case STATUSES_UPDATE:
                url = getBaseUrl() + "/statuses/update" + EXTENSION;
                break;
            case STOP_FOLLOWING_USER:
                url = getBaseUrl() + "/friendships/destroy" + EXTENSION;
                break;
        }
        return url;
    }

    @Override
    public JSONObject destroyStatus(String statusId) throws ConnectionException {
        return postRequest(getApiUrl(ApiRoutineEnum.STATUSES_DESTROY) + statusId + EXTENSION);
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
        Uri sUri = Uri.parse(getApiUrl(ApiRoutineEnum.GET_FRIENDS_IDS));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        JSONObject jso = getRequest(builder.build().toString());
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
        Uri sUri = Uri.parse(getApiUrl(ApiRoutineEnum.STATUSES_SHOW));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("id", statusId);
        return getRequest(builder.build().toString());
    }

    @Override
    public JSONArray getTimeline(ApiRoutineEnum apiRoutine, String sinceId, int limit, String userId)
            throws ConnectionException {
        boolean ok = false;
        String url = this.getApiUrl(apiRoutine);
        Uri sUri = Uri.parse(url);
        Uri.Builder builder = sUri.buildUpon();
        if (!TextUtils.isEmpty(fixSinceId(sinceId))) {
            builder.appendQueryParameter("since_id", fixSinceId(sinceId));
        }
        if (fixLimit(limit) > 0) {
            builder.appendQueryParameter("count", String.valueOf(fixLimit(limit)));
        }
        if (!TextUtils.isEmpty(userId)) {
            builder.appendQueryParameter("user_id", userId);
        }
        HttpGet get = new HttpGet(builder.build().toString());
        JSONArray jArr = getRequestAsArray(get);
        
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
        Uri sUri = Uri.parse(getApiUrl(ApiRoutineEnum.GET_USER));
        Uri.Builder builder = sUri.buildUpon();
        builder.appendQueryParameter("user_id", userId);
        return getRequest(builder.build().toString());
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
        return postRequest(getApiUrl(ApiRoutineEnum.POST_REBLOG) + rebloggedId + EXTENSION);
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
        return getRequest(getApiUrl(ApiRoutineEnum.ACCOUNT_RATE_LIMIT_STATUS));
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
}
