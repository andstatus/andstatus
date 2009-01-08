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
import java.util.Calendar;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;

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
	private static final String USER_AGENT = "Mozilla/4.5";
	private static final String TAG = Connection.class.getName();

	private String mUsername;
	private String mPassword;
	private long mLastRunTime;

	/**
	 * Creates a new Connection instance.
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
	 * @return JSONArray
	 * @throws JSONException
	 */
	public JSONArray getPublicTimeline() throws JSONException {
		return new JSONArray(getRequest(PUBLIC_TIMELINE_URL));
	}

	/**
	 * Get the user's own and friends timeline.
	 * 
	 * @return JSONArray
	 * @throws JSONException
	 */
	public JSONArray getFriendsTimeline() throws JSONException {
		return new JSONArray(getRequest(FRIENDS_TIMELINE_URL));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @return String
	 */
	private String getRequest(String url) {
		return getRequest(url, new DefaultHttpClient(new BasicHttpParams()));
	}

	/**
	 * Execute a GET request against the Twitter REST API.
	 * 
	 * @param url
	 * @param client
	 * @return String
	 */
	private String getRequest(String url, HttpClient client) {
		String result = null;
		url += "?count=200";
		if (mLastRunTime > 0) {
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(mLastRunTime);
			DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
			url += "&since=" + URLEncoder.encode(df.format(cal.getTime()));
		}
		HttpGet getMethod = new HttpGet(url);
		try {
			getMethod.setHeader("User-Agent", USER_AGENT);
			getMethod.addHeader("Authorization", "Basic " + getCredentials());
			HttpResponse httpResponse = client.execute(getMethod);
			result = retrieveInputStream(httpResponse.getEntity());
		} catch (Exception e) {
			Log.e(TAG, e.getMessage());
		} finally {
			getMethod.abort();
		}
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
			InputStreamReader inputStreamReader = new InputStreamReader(
					httpEntity.getContent(), HTTP.UTF_8);
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
	private String getCredentials() {
		return new String(Base64.encodeBytes((mUsername + ":" + mPassword)
				.getBytes()));
	}
}
