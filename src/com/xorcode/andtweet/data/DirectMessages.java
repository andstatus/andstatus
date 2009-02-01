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
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import com.xorcode.andtweet.net.Connection;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;

/**
 * Handles loading data from JSON into database.
 * 
 * @author torgny.bjers
 */
public class DirectMessages {

	private static final String TAG = "DirectMessages";

	private static final String DATE_FORMAT = "EEE MMM dd HH:mm:ss Z yyyy";

	private ContentResolver mContentResolver;
	private String mUsername, mPassword;
	private int mNewMessages;

	public DirectMessages(ContentResolver contentResolver, String username, String password) {
		mContentResolver = contentResolver;
		mUsername = username;
		mPassword = password;
	}

	/**
	 * Load the user and friends timeline.
	 * 
	 * @throws ConnectionException 
	 * @return int
	 */
	public int loadMessages() throws ConnectionException, JSONException, SQLiteConstraintException, ConnectionAuthenticationException {
		long aLastRunTime = 0;
		mNewMessages = 0;
		if (mUsername != null && mUsername.length() > 0) {
			Log.i(TAG, "Loading direct messages");
			// Try to load the last record
			Cursor c = mContentResolver.query(AndTweetDatabase.DirectMessages.CONTENT_URI, new String[] {
				AndTweetDatabase.DirectMessages._ID, AndTweetDatabase.DirectMessages.SENT_DATE
			}, null, null, AndTweetDatabase.DirectMessages.DEFAULT_SORT_ORDER);
			try {
				c.moveToFirst();
				// If a record is available, get the last run time
				if (c.getCount() > 0) {
					DateFormat f = new SimpleDateFormat(DATE_FORMAT);
					Calendar cal = Calendar.getInstance();
					cal.setTimeInMillis(c.getLong(1));
					aLastRunTime = cal.getTimeInMillis();
					Log.d(TAG, "Last direct message: " + f.format(cal.getTime()));
				}
			} catch (Exception e) {
				String msg = e.getMessage();
				if (msg == null) {
					msg = "An unknown error has ocurred: " + e.toString();
				}
				Log.e(TAG, msg);
			} finally {
				if (c != null && !c.isClosed()) c.close();
			}
			Connection aConn = new Connection(mUsername, mPassword, aLastRunTime);
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

		DateFormat f = new SimpleDateFormat(DATE_FORMAT);
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
}
