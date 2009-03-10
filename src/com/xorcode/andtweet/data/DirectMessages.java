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

package com.xorcode.andtweet.data;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;

/**
 * Handles loading data from JSON into database.
 * 
 * @author torgny.bjers
 */
public class DirectMessages {

	private static final String TAG = "DirectMessages";

	private ContentResolver mContentResolver;
	private String mUsername, mPassword;
	private int mNewMessages;
	private long mLastRunTime = 0;

	public DirectMessages(ContentResolver contentResolver, String username, String password, long lastRunTime) {
		mContentResolver = contentResolver;
		mUsername = username;
		mPassword = password;
		mLastRunTime = lastRunTime;
	}

	/**
	 * Load the user and friends timeline.
	 * 
	 * @throws ConnectionException 
	 * @return int
	 * @throws ConnectionUnavailableException 
	 */
	public int loadMessages() throws ConnectionException, JSONException, SQLiteConstraintException, ConnectionAuthenticationException, ConnectionUnavailableException {
		mNewMessages = 0;
		if (mUsername != null && mUsername.length() > 0) {
			Log.i(TAG, "Loading direct messages");
			DateFormat f = new SimpleDateFormat(AndTweetDatabase.TWITTER_DATE_FORMAT);
			Calendar cal = Calendar.getInstance();
			cal.setTimeInMillis(mLastRunTime);
			Log.d(TAG, "Last direct message: " + f.format(cal.getTime()));
			Connection aConn;
			if (mLastRunTime > 0) {
				aConn = new Connection(mUsername, mPassword, mLastRunTime);
			} else {
				aConn = new Connection(mUsername, mPassword);
			}
			JSONArray jArr = aConn.getDirectMessages();
			for (int index = 0; index < jArr.length(); index++) {
				JSONObject jo = jArr.getJSONObject(index);
				insertFromJSONObject(jo);
			}
			if (mNewMessages > 0) {
				mContentResolver.notifyChange(AndTweetDatabase.DirectMessages.CONTENT_URI, null);
			}
		}
		return mNewMessages;
	}

	public Uri insertFromJSONObject(JSONObject jo) throws JSONException, SQLiteConstraintException {
		ContentValues values = new ContentValues();

		// Construct the Uri to existing record
		Long lMessageId = Long.parseLong(jo.getString("id"));
		Uri aMessageUri = ContentUris.withAppendedId(AndTweetDatabase.DirectMessages.CONTENT_URI, lMessageId);
		Log.d(TAG, aMessageUri.toString());

		values.put(AndTweetDatabase.DirectMessages._ID, lMessageId.toString());
		values.put(AndTweetDatabase.DirectMessages.AUTHOR_ID, jo.getString("sender_screen_name"));

		values.put(AndTweetDatabase.DirectMessages.MESSAGE, Html.fromHtml(jo.getString("text")).toString());

		DateFormat f = new SimpleDateFormat(AndTweetDatabase.TWITTER_DATE_FORMAT);
		Calendar cal = Calendar.getInstance();
		try {
			cal.setTime(f.parse(jo.getString("created_at")));
			values.put(AndTweetDatabase.DirectMessages.SENT_DATE, cal.getTimeInMillis());
		} catch (java.text.ParseException e) {
			Log.e(TAG, e.getMessage());
		}

		if ((mContentResolver.update(aMessageUri, values, null, null)) == 0) {
			mContentResolver.insert(AndTweetDatabase.DirectMessages.CONTENT_URI, values);
			mNewMessages++;
		}
		return aMessageUri;
	}

	public Uri insertFromJSONObject(JSONObject jo, boolean notify) throws JSONException, SQLiteConstraintException {
		Uri aMessageUri = insertFromJSONObject(jo);
		if (notify) mContentResolver.notifyChange(aMessageUri, null);
		return aMessageUri;
	}

	public int pruneOldRecords(long sinceTimestamp) {
		if (sinceTimestamp == 0) {
			sinceTimestamp = System.currentTimeMillis();
		}
		return mContentResolver.delete(AndTweetDatabase.DirectMessages.CONTENT_URI, AndTweetDatabase.DirectMessages.CREATED_DATE + " < " + sinceTimestamp, null);
	}
}
