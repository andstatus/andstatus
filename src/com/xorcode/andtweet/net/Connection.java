/* 
 * Copyright (C) 2008 Torgny Bjers
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

package com.xorcode.andtweet.net;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.xorcode.andtweet.util.Base64;

/**
 * Handles connection to the Twitter REST API.
 * 
 * @author torgny.bjers
 */
public class Connection {

	private static final String BASE_URL = "http://twitter.com";
	private static final String EXTENSION = ".json";

	private static final String STATUSES_FRIENDS_TIMELINE_URL = BASE_URL + "/statuses/friends_timeline" + EXTENSION;
	private static final String STATUSES_MENTIONS_TIMELINE_URL = BASE_URL + "/statuses/mentions" + EXTENSION;
	private static final String STATUSES_UPDATE_URL = BASE_URL + "/statuses/update" + EXTENSION;
	private static final String STATUSES_DESTROY_URL = BASE_URL + "/statuses/destroy/";
	private static final String DIRECT_MESSAGES_URL = BASE_URL + "/direct_messages" + EXTENSION;
	private static final String ACCOUNT_VERIFY_CREDENTIALS_URL = BASE_URL + "/account/verify_credentials" + EXTENSION;
	private static final String ACCOUNT_RATE_LIMIT_STATUS_URL = BASE_URL + "/account/rate_limit_status" + EXTENSION;
	private static final String FAVORITES_CREATE_BASE_URL = BASE_URL + "/favorites/create/";
	private static final String FAVORITES_DESTROY_BASE_URL = BASE_URL + "/favorites/destroy/";
	private static final String USER_AGENT = "AndTweet/1.0";
	private static final String SOURCE_PARAMETER = "andtweet";
	private static final String TAG = "AndTweetConnection";
	
	/* TODO: Not implemented (yet?)
    private static final String STATUSES_PUBLIC_TIMELINE_URL = BASE_URL + "/statuses/public_timeline" + EXTENSION;
    private static final String STATUSES_FOLLOWERS_URL = BASE_URL + "/statuses/followers" + EXTENSION;
    private static final String DIRECT_MESSAGES_SENT_URL = BASE_URL + "/direct_messages/sent" + EXTENSION;
	 */

	private static final Integer DEFAULT_GET_REQUEST_TIMEOUT = 15000;
	private static final Integer DEFAULT_POST_REQUEST_TIMEOUT = 20000;

	private String mUsername;
	private String mPassword;
	private long mSinceId;
	private int mLimit = 200;

	/**
	 * Creates a new Connection instance.
	 * 
	 */
	public Connection(Context context) {
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

	    mUsername = prefs.getString("twitter_username", null);
        mPassword = prefs.getString("twitter_password", null);
	}

	protected long getSinceId() {
        return mSinceId;
	}
	protected long setSinceId(long sinceId) {
        if (sinceId>0) {
            mSinceId = sinceId;
        }
        return mSinceId;
    }

    protected int getLimit() {
        return mLimit;
    }
    protected int setLimit(int limit) {
        if (limit>0) {
            mLimit = limit;
            if (mLimit > 200) mLimit = 200;
        }
        return mLimit;
    }
	
	/**
	 * Get the user's own and friends timeline.
	 * 
	 * Returns the 100 most recent statuses posted by the authenticating user and
	 * that user's friends. This is the equivalent of /home on the Web.
	 * 
	 * @return JSONArray
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 * @throws ConnectionUnavailableException 
	 * @throws SocketTimeoutException 
	 */
	public JSONArray getFriendsTimeline(long sinceId, int limit) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
	    setSinceId(sinceId);
	    setLimit(limit);
	    
		String url = STATUSES_FRIENDS_TIMELINE_URL;
		url += "?count=" + mLimit;
		if (mSinceId > 0) {
			url += "&since_id=" + mSinceId;
		}
		JSONArray jArr = null;
		String request = getRequest(url);
		try {
			jArr = new JSONArray(request);
		} catch (JSONException e) {
			try {
				JSONObject jObj = new JSONObject(request);
				String error = jObj.optString("error");
				if ("Could not authenticate you.".equals(error)) {
					throw new ConnectionAuthenticationException(error);
				}
			} catch (JSONException e1) {
				throw new ConnectionException(e);
			}
		}
		return jArr;
	}

	/**
	 * Get the user's replies.
	 * 
	 * Returns the 20 most recent @replies (status updates prefixed with @username) 
	 * for the authenticating user.
	 * 
	 * @return JSONArray
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 * @throws ConnectionUnavailableException 
	 * @throws SocketTimeoutException 
	 */
	public JSONArray getMentionsTimeline(long sinceId, int limit) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
        setSinceId(sinceId);
        setLimit(limit);

        String url = STATUSES_MENTIONS_TIMELINE_URL;
		url += "?count=" + mLimit;
		if (mSinceId > 0) {
			url += "&since_id=" + mSinceId;
		}
		JSONArray jArr = null;
		String request = getRequest(url);
		try {
			jArr = new JSONArray(request);
		} catch (JSONException e) {
			try {
				JSONObject jObj = new JSONObject(request);
				String error = jObj.optString("error");
				if ("Could not authenticate you.".equals(error)) {
					throw new ConnectionAuthenticationException(error);
				}
			} catch (JSONException e1) {
				throw new ConnectionException(e);
			}
		}
		return jArr;
	}

	/**
	 * Get the user's own and friends timeline.
	 * 
	 * Returns the 100 most recent direct messages for the authenticating user.
	 * 
	 * @return JSONArray
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 * @throws ConnectionUnavailableException 
	 * @throws SocketTimeoutException 
	 */
	public JSONArray getDirectMessages(long sinceId, int limit) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
        setSinceId(sinceId);
        setLimit(limit);

		String url = DIRECT_MESSAGES_URL;
		url += "?count=" + mLimit;
		if (mSinceId > 0) {
			url += "&since_id=" + mSinceId;
		}
		JSONArray jArr = null;
		String request = getRequest(url);
		try {
			jArr = new JSONArray(request);
		} catch (JSONException e) {
			try {
				JSONObject jObj = new JSONObject(request);
				String error = jObj.optString("error");
				if ("Could not authenticate you.".equals(error)) {
					throw new ConnectionAuthenticationException(error);
				}
			} catch (JSONException e1) {
				throw new ConnectionException(e);
			}
		}
		return jArr;
	}

	/**
	 * Update user status by posting to the Twitter REST API.
	 * 
	 * Updates the authenticating user's status. Requires the status parameter
	 * specified. Request must be a POST. A status update with text identical to
	 * the authenticating user's current status will be ignored.
	 * 
	 * @param message
	 * @return JSONObject
	 * @throws UnsupportedEncodingException
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 * @throws ConnectionUnavailableException 
	 * @throws SocketTimeoutException 
	 */
	public JSONObject updateStatus(String message, long inReplyToId) throws UnsupportedEncodingException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		String url = STATUSES_UPDATE_URL;
		List<NameValuePair> formParams = new ArrayList<NameValuePair>();
		formParams.add(new BasicNameValuePair("status", message));
		formParams.add(new BasicNameValuePair("source", SOURCE_PARAMETER));
		if (inReplyToId > 0) {
			formParams.add(new BasicNameValuePair("in_reply_to_status_id", String.valueOf(inReplyToId)));
		}
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url, new UrlEncodedFormEntity(formParams, HTTP.UTF_8)));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionAuthenticationException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

	/**
	 * Destroys the status specified by the required ID parameter.
	 * The authenticating user must be the author of the specified status.
	 * 
	 * @param statusId
	 * @return JSONObject
	 * @throws UnsupportedEncodingException
	 * @throws ConnectionException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 * @throws SocketTimeoutException
	 */
	public JSONObject destroyStatus(long statusId) throws UnsupportedEncodingException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		StringBuilder url = new StringBuilder(STATUSES_DESTROY_URL);
		url.append(String.valueOf(statusId));
		url.append(EXTENSION);
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url.toString()));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionAuthenticationException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

	/**
	 * Favorites the status specified in the ID parameter as the authenticating user.
	 * Returns the favorite status when successful.
	 * 
	 * @param statusId
	 * @return JSONObject
	 * @throws UnsupportedEncodingException
	 * @throws ConnectionException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 * @throws SocketTimeoutException
	 */
	public JSONObject createFavorite(long statusId) throws UnsupportedEncodingException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		StringBuilder url = new StringBuilder(FAVORITES_CREATE_BASE_URL);
		url.append(String.valueOf(statusId));
		url.append(EXTENSION);
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url.toString()));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionAuthenticationException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

	/**
	 * 
	 * @param statusId
	 * @return JSONObject
	 * @throws UnsupportedEncodingException
	 * @throws ConnectionException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 * @throws SocketTimeoutException
	 */
	public JSONObject destroyFavorite(long statusId) throws UnsupportedEncodingException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		StringBuilder url = new StringBuilder(FAVORITES_DESTROY_BASE_URL);
		url.append(String.valueOf(statusId));
		url.append(EXTENSION);
		JSONObject jObj = null;
		try {
			jObj = new JSONObject(postRequest(url.toString()));
			String error = jObj.optString("error");
			if ("Could not authenticate you.".equals(error)) {
				throw new ConnectionAuthenticationException(error);
			}
		} catch (JSONException e) {
			throw new ConnectionException(e);
		}
		return jObj;
	}

	/**
	 * Verify the user's credentials.
	 * 
	 * Returns true if authentication was successful
	 * 
	 * @return boolean
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 * @throws SocketTimeoutException 
	 */
	public boolean verifyCredentials() throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
	    boolean isOk = false;
	    /**
         * Returns an HTTP 200 OK response code and a representation of the
         * requesting user if authentication was successful; returns a 401 status
         * code and an error message if not.
         * @see <a
         *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0verify_credentials">Twitter
         *      REST API Method: account verify_credentials</a>
	     */

        try {
            JSONObject jo = new JSONObject(getRequest(ACCOUNT_VERIFY_CREDENTIALS_URL, new DefaultHttpClient(new BasicHttpParams())));
            if ( jo.optInt("id") > 0) {
                isOk = true;
            }
        } catch (JSONException e) {
            Log.e(TAG, "verifyCredentials: " + e.toString());
        }
	    
		return isOk;
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
     *      href="http://apiwiki.twitter.com/Twitter-REST-API-Method%3A-account%C2%A0rate_limit_status">Twitter
     *      REST API Method: account rate_limit_status</a>
	 * 
	 * @return JSONObject
	 * @throws JSONException
	 * @throws ConnectionException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 * @throws SocketTimeoutException 
	 */
	public JSONObject rateLimitStatus() throws JSONException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		return new JSONObject(getRequest(ACCOUNT_RATE_LIMIT_STATUS_URL, new DefaultHttpClient(new BasicHttpParams())));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 * @throws SocketTimeoutException 
	 */
	private String getRequest(String url) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		return getRequest(url, new DefaultHttpClient(new BasicHttpParams()));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 * @throws ConnectionException
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 * @throws SocketTimeoutException 
	 */
	private String getRequest(String url, HttpClient client) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		String result = null;
		int statusCode = 0;
		HttpGet getMethod = new HttpGet(url);
		try {
			getMethod.setHeader("User-Agent", USER_AGENT);
			getMethod.addHeader("Authorization", "Basic " + getCredentials());
			client.getParams().setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, DEFAULT_GET_REQUEST_TIMEOUT);
			client.getParams().setIntParameter(HttpConnectionParams.SO_TIMEOUT, DEFAULT_GET_REQUEST_TIMEOUT);
			HttpResponse httpResponse = client.execute(getMethod);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (Exception e) {
            Log.e(TAG, "getRequest: " + e.toString());
			throw new ConnectionException(e);
		} finally {
			getMethod.abort();
		}
		parseStatusCode(statusCode, url);
		return result;
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 * @throws SocketTimeoutException 
	 */
	private String postRequest(String url) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		return postRequest(url, new DefaultHttpClient(new BasicHttpParams()), null);
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 * @throws SocketTimeoutException 
	 */
	private String postRequest(String url, UrlEncodedFormEntity formParams) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		return postRequest(url, new DefaultHttpClient(new BasicHttpParams()), formParams);
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 * @throws ConnectionException
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 */
	private String postRequest(String url, HttpClient client, UrlEncodedFormEntity formParams) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		String result = null;
		int statusCode = 0;
		HttpPost postMethod = new HttpPost(url);
		try {
			postMethod.setHeader("User-Agent", USER_AGENT);
			postMethod.addHeader("Authorization", "Basic " + getCredentials());
			if (formParams != null) {
				postMethod.setEntity(formParams);
			}
			client.getParams().setIntParameter(HttpConnectionParams.CONNECTION_TIMEOUT, DEFAULT_POST_REQUEST_TIMEOUT);
			client.getParams().setIntParameter(HttpConnectionParams.SO_TIMEOUT, DEFAULT_POST_REQUEST_TIMEOUT);
			HttpResponse httpResponse = client.execute(postMethod);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (SocketTimeoutException e) {
			throw e;
		} catch (Exception e) {
            Log.e(TAG, "postRequest: " + e.toString());
			throw new ConnectionException(e);
		} finally {
			postMethod.abort();
		}
		parseStatusCode(statusCode, url);
		return result;
	}

	/**
	 * Retrieve the input stream from the HTTP connection.
	 * 
	 * @param httpEntity
	 * @return String
	 */
	private String retrieveInputStream(HttpEntity httpEntity) {
		int length = (int) httpEntity.getContentLength();
		StringBuffer stringBuffer = new StringBuffer(length);
		try {
			InputStreamReader inputStreamReader = new InputStreamReader(httpEntity.getContent(), HTTP.UTF_8);
			char buffer[] = new char[length];
			int count;
			while ((count = inputStreamReader.read(buffer, 0, length - 1)) > 0) {
				stringBuffer.append(buffer, 0, count);
			}
		} catch (UnsupportedEncodingException e) {
			Log.e(TAG, e.toString());
		} catch (IllegalStateException e) {
			Log.e(TAG, e.toString());
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		}
		return stringBuffer.toString();
	}

	/**
	 * Get the HTTP digest authentication. Uses Base64 to encode credentials.
	 * 
	 * @return String
	 */
	private String getCredentials() {
		return new String(Base64.encodeBytes((mUsername + ":" + mPassword).getBytes()));
	}

	/**
	 * Parse the status code and throw appropriate exceptions when necessary.
	 * 
	 * @param code
	 * @param path
	 * @throws ConnectionException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 */
	private void parseStatusCode(int code, String path) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		switch (code) {
		case 200:
		case 304:
			break;
		case 401:
			throw new ConnectionAuthenticationException(String.valueOf(code));
		case 400:
		case 403:
		case 404:
			throw new ConnectionException(String.valueOf(code));
		case 500:
		case 502:
		case 503:
			throw new ConnectionUnavailableException(String.valueOf(code));
		}
	}
}
