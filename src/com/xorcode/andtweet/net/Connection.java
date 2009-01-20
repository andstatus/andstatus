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
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
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
	private static final String PUBLIC_TIMELINE_URL = "http://twitter.com/statuses/public_timeline.json";
	private static final String FRIENDS_TIMELINE_URL = "http://twitter.com/statuses/friends_timeline.json";
	private static final String FRIENDS_URL = "http://twitter.com/statuses/friends.json";
	private static final String UPDATE_STATUS_URL = "http://twitter.com/statuses/update.json";
	private static final String VERIFY_CREDENTIALS_URL = "http://twitter.com/statuses/verify_credentials.json";
	private static final String USER_AGENT = "Mozilla/4.5";
	private static final String SOURCE_PARAMETER = "andtweet";
	private static final String TAG = "AndTweet";

	private String mUsername;
	private String mPassword;
	private long mLastRunTime;

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
	public Connection(String username, String password, long lastRunTime) {
		mUsername = username;
		mPassword = password;
		mLastRunTime = lastRunTime;
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
	 */
	public JSONArray getPublicTimeline() throws JSONException, ConnectionException {
		return new JSONArray(getRequest(PUBLIC_TIMELINE_URL));
	}

	/**
	 * Get the user's own and friends timeline.
	 * 
	 * Returns the 20 most recent statuses posted by the authenticating user and
	 * that user's friends. This is the equivalent of /home on the Web.
	 * 
	 * @return JSONArray
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 */
	public JSONArray getFriendsTimeline() throws ConnectionException, ConnectionAuthenticationException {
		String url = FRIENDS_TIMELINE_URL;
		url += "?count=100";
		if (mLastRunTime > 0) {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(mLastRunTime);
			DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
			url += "&since=" + URLEncoder.encode(df.format(cal.getTime()));
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
	 */
	public JSONArray getFriends() throws ConnectionException, ConnectionAuthenticationException {
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
	 * Update user status by posting to the Twitter REST API.
	 * 
	 * Updates the authenticating user's status. Requires the status parameter
	 * specified. Request must be a POST. A status update with text identical to
	 * the authenticating user's current status will be ignored.
	 * 
	 * @param message
	 * @return boolean
	 * @throws UnsupportedEncodingException
	 * @throws ConnectionException 
	 * @throws ConnectionAuthenticationException 
	 */
	public JSONObject updateStatus(String message, long inReplyToId)
			throws UnsupportedEncodingException, ConnectionException, ConnectionAuthenticationException {
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
	 * @return
	 * @throws JSONException
	 * @throws ConnectionException 
	 */
	public JSONObject verifyCredentials() throws JSONException, ConnectionException {
		return new JSONObject(getRequest(VERIFY_CREDENTIALS_URL, new DefaultHttpClient(new BasicHttpParams())));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 */
	protected String getRequest(String url) throws ConnectionException {
		return getRequest(url, new DefaultHttpClient(new BasicHttpParams()));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 * @throws ConnectionException
	 */
	protected String getRequest(String url, HttpClient client) throws ConnectionException {
		String result = null;
		HttpGet getMethod = new HttpGet(url);
		try {
			getMethod.setHeader("User-Agent", USER_AGENT);
			getMethod.addHeader("Authorization", "Basic " + getCredentials());
			HttpResponse httpResponse = client.execute(getMethod);
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (Exception e) {
			Log.d(TAG, e.getMessage());
			throw new ConnectionException(e);
		} finally {
			getMethod.abort();
		}
		return result;
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 */
	protected String postRequest(String url) throws ConnectionException {
		return postRequest(url, new DefaultHttpClient(new BasicHttpParams()), null);
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 * @throws ConnectionException 
	 */
	protected String postRequest(String url, UrlEncodedFormEntity formParams) throws ConnectionException {
		return postRequest(url, new DefaultHttpClient(new BasicHttpParams()), formParams);
	}

	/**
	 * Execute a POST request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 * @throws ConnectionException
	 */
	protected String postRequest(String url, HttpClient client, UrlEncodedFormEntity formParams)
			throws ConnectionException {
		String result = null;
		HttpPost postMethod = new HttpPost(url);
		try {
			postMethod.setHeader("User-Agent", USER_AGENT);
			postMethod.addHeader("Authorization", "Basic " + getCredentials());
			if (formParams != null) {
				postMethod.setEntity(formParams);
			}
			HttpResponse httpResponse = client.execute(postMethod);
			result = EntityUtils.toString(httpResponse.getEntity());
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
			throw new ConnectionException(e);
		} finally {
			postMethod.abort();
		}
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
			InputStreamReader inputStreamReader = new InputStreamReader(httpEntity.getContent(),
					HTTP.UTF_8);
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
}
