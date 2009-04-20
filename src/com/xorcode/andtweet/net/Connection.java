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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

	private static final String PUBLIC_TIMELINE_URL = BASE_URL + "/statuses/public_timeline" + EXTENSION;
	private static final String FRIENDS_TIMELINE_URL = BASE_URL + "/statuses/friends_timeline" + EXTENSION;
	private static final String REPLIES_TIMELINE_URL = BASE_URL + "/statuses/replies" + EXTENSION;
	private static final String DIRECT_MESSAGES_URL = BASE_URL + "/direct_messages" + EXTENSION;
	private static final String DIRECT_MESSAGES_SENT_URL = BASE_URL + "/direct_messages/sent" + EXTENSION;
	private static final String FRIENDS_URL = BASE_URL + "/statuses/friends" + EXTENSION;
	private static final String UPDATE_STATUS_URL = BASE_URL + "/statuses/update" + EXTENSION;
	private static final String VERIFY_CREDENTIALS_URL = BASE_URL + "/account/verify_credentials" + EXTENSION;
	private static final String RATE_LIMIT_STATUS_URL = BASE_URL + "/account/rate_limit_status" + EXTENSION;
	private static final String USER_AGENT = "Mozilla/4.5";
	private static final String SOURCE_PARAMETER = "andtweet";
	private static final String TAG = "AndTweetConnection";

	private String mUsername;
	private String mPassword;
	private long mSinceId;
	private int mLimit = 200;

	private final DateFormat mDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);

	/**
	 * Creates a new Connection instance.
	 * 
	 * Requires a user name and password.
	 * 
	 * @param username
	 * @param password
	 */
	public Connection(String username, String password) {
		mUsername = username;
		mPassword = password;
	}

	/**
	 * Creates a new Connection instance, specifying a last ID.
	 * 
	 * Requires a user name and password as well as a last run time.
	 * 
	 * @param username
	 * @param password
	 * @param lastId
	 */
	public Connection(String username, String password, long sinceId) {
		mUsername = username;
		mPassword = password;
		mSinceId = sinceId;
	}

	/**
	 * Creates a new Connection instance, specifying a last ID.
	 * 
	 * Requires a user name and password as well as a last run time.
	 * 
	 * @param username
	 * @param password
	 * @param lastId
	 */
	public Connection(String username, String password, long sinceId, int limit) {
		mUsername = username;
		mPassword = password;
		mSinceId = sinceId;
		mLimit = limit;
		if (mLimit < 1) mLimit = 1;
		if (mLimit > 200) mLimit = 200;
	}

	/**
	 * Get the user's public timeline.
	 * 
	 * Returns the 20 most recent statuses from non-protected users who have set
	 * a custom user icon. Does not require authentication. Note that the public
	 * timeline is cached for 60 seconds so requesting it more often than that
	 * is a waste of resources.
	 * 
	 * @return JSONArray
	 * @throws JSONException
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 */
	public JSONArray getPublicTimeline() throws JSONException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		return new JSONArray(getRequest(PUBLIC_TIMELINE_URL));
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
	 */
	public JSONArray getFriendsTimeline() throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String url = FRIENDS_TIMELINE_URL;
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
	 */
	public JSONArray getRepliesTimeline() throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String url = REPLIES_TIMELINE_URL;
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
	 * Get the user's most recent friends from the Twitter REST API.
	 * 
	 * Returns up to 100 of the authenticating user's friends who have most
	 * recently updated, each with current status in-line.
	 * 
	 * @return
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 * @throws ConnectionUnavailableException 
	 */
	public JSONArray getFriends() throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String url = FRIENDS_URL;
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
	 */
	public JSONArray getDirectMessages() throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
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
	 * Get the user's own and friends timeline.
	 * 
	 * Returns the 100 most recent sent messages for the authenticating user.
	 * 
	 * @return JSONArray
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 * @throws ConnectionUnavailableException 
	 */
	public JSONArray getSentDirectMessages() throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String url = DIRECT_MESSAGES_SENT_URL;
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
	 */
	public JSONObject updateStatus(String message, long inReplyToId)
			throws UnsupportedEncodingException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String url = UPDATE_STATUS_URL;
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
	 * Verify the user's credentials.
	 * 
	 * Returns an HTTP 200 OK response code and a representation of the
	 * requesting user if authentication was successful; returns a 401 status
	 * code and an error message if not.
	 * 
	 * @return JSONObject
	 * @throws JSONException
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 */
	public JSONObject verifyCredentials() throws JSONException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		return new JSONObject(getRequest(VERIFY_CREDENTIALS_URL, new DefaultHttpClient(new BasicHttpParams())));
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
	 * 
	 * @return JSONObject
	 * @throws JSONException
	 * @throws ConnectionException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 */
	public JSONObject rateLimitStatus() throws JSONException, ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		return new JSONObject(getRequest(RATE_LIMIT_STATUS_URL, new DefaultHttpClient(new BasicHttpParams())));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 * @throws ConnectionUnavailableException 
	 * @throws ConnectionAuthenticationException 
	 */
	protected String getRequest(String url) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
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
	 */
	protected String getRequest(String url, HttpClient client) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String result = null;
		int statusCode = 0;
		HttpGet getMethod = new HttpGet(url);
		try {
			getMethod.setHeader("User-Agent", USER_AGENT);
			getMethod.addHeader("Authorization", "Basic " + getCredentials());
			HttpResponse httpResponse = client.execute(getMethod);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
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
	 */
	protected String postRequest(String url) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
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
	 */
	protected String postRequest(String url, UrlEncodedFormEntity formParams) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
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
	protected String postRequest(String url, HttpClient client, UrlEncodedFormEntity formParams)
			throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
		String result = null;
		int statusCode = 0;
		HttpPost postMethod = new HttpPost(url);
		try {
			postMethod.setHeader("User-Agent", USER_AGENT);
			postMethod.addHeader("Authorization", "Basic " + getCredentials());
			if (formParams != null) {
				postMethod.setEntity(formParams);
			}
			HttpResponse httpResponse = client.execute(postMethod);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
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
	protected String retrieveInputStream(HttpEntity httpEntity) {
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
			Log.e(TAG, e.getMessage());
		} catch (IllegalStateException e) {
			Log.e(TAG, e.getMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		return stringBuffer.toString();
	}

	/**
	 * Get the HTTP digest authentication. Uses Base64 to encode credentials.
	 * 
	 * @return String
	 */
	protected String getCredentials() {
		return new String(Base64.encodeBytes((mUsername + ":" + mPassword).getBytes()));
	}

	protected void parseStatusCode(int code, String path) throws ConnectionException, ConnectionAuthenticationException, ConnectionUnavailableException {
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

	protected String getTwitterDate(long time) {
		synchronized (mDateFormat) {
			return mDateFormat.format(new Date(time));
		}
	}
}
