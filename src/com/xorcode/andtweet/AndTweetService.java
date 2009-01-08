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

package com.xorcode.andtweet;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.util.Linkify;
import android.util.Log;

import com.xorcode.andtweet.data.AndTweet;
import com.xorcode.andtweet.data.AndTweet.Tweets;
import com.xorcode.andtweet.net.Connection;

/**
 * This is an application service that serves as a connection between Android
 * and Twitter. Other applications can interact with it via IPC.
 */
public class AndTweetService extends Service {

	private static final String TAG = "AndTweetService";

	/**
	 * This is a list of callbacks that have been registered with the service.
	 */
	final RemoteCallbackList<IAndTweetServiceCallback> mCallbacks = new RemoteCallbackList<IAndTweetServiceCallback>();

	int mTimelineValue = 0;
	int mReportValue = 0;
	long mLastRunTime = 0;

	private static final int MILLISECONDS = 1000;
	private static final int MSG_UPDATE_TIMELINE = 1;
	private static final int MSG_UPDATE_FRIENDS = 2;
	private static final int MSG_UPDATE_DIRECTMESSAGES = 3;

	private String mUsername;
	private String mPassword;
	private SharedPreferences mPreferences;
	private int mFrequency = 180;

	@Override
	public void onCreate() {
		// Start the time line updater.
		mHandler.sendEmptyMessage(MSG_UPDATE_TIMELINE);
		mPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
	}

	@Override
	public void onDestroy() {
		// Unregister all callbacks.
		mCallbacks.kill();

		// Remove the next pending message to increment the counter, stopping
		// the increment loop.
		mHandler.removeMessages(MSG_UPDATE_TIMELINE);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Select the interface to return. If your service only implements
		// a single interface, you can just return it here without checking
		// the Intent.
		if (IAndTweetService.class.getName().equals(intent.getAction())) {
			return mBinder;
		}
		return null;
	}

	/**
	 * The IAndTweetService is defined through IDL
	 */
	private final IAndTweetService.Stub mBinder = new IAndTweetService.Stub() {
		public void registerCallback(IAndTweetServiceCallback cb) {
			if (cb != null)
				mCallbacks.register(cb);
		}

		public void unregisterCallback(IAndTweetServiceCallback cb) {
			if (cb != null)
				mCallbacks.unregister(cb);
		}
	};

	/**
	 * Our Handler used to execute operations on the main thread. This is used
	 * to schedule updates of timeline data from Twitter.
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			final int N = mCallbacks.beginBroadcast();
			switch (msg.what) {

			case MSG_UPDATE_TIMELINE:
				int aNewTweets = loadTimeline();
				mFrequency = Integer.parseInt(mPreferences.getString("fetch_frequency", "180"));
				mLastRunTime = Long.valueOf(System.currentTimeMillis());
				// Broadcast new value to all clients
				for (int i = 0; i < N; i++) {
					try {
						mCallbacks.getBroadcastItem(i).tweetsChanged(aNewTweets);
					} catch (RemoteException e) {
						Log.e(TAG, e.getMessage());
					}
				}
				mCallbacks.finishBroadcast();
				// Repeat every 180 seconds (3 minutes)
				sendMessageDelayed(obtainMessage(MSG_UPDATE_TIMELINE),
						mFrequency * MILLISECONDS);
				break;

			default:
				super.handleMessage(msg);
			}
		}
	};

	/**
	 * Load the friend timeline from Twitter.
	 * 
	 * @return int
	 */
	protected int loadTimeline() {
		long aLastRunTime = 0;
		int aNewTweets = 0;
		mUsername = mPreferences.getString("twitter_username", null);
		mPassword = mPreferences.getString("twitter_password", null);
		if (mUsername != null && mUsername.length() > 0) {
			String mDateFormat = (String) getText(R.string.twitter_dateformat);
			Cursor c = getContentResolver().query(
					AndTweet.Tweets.CONTENT_URI,
					new String[] { AndTweet.Tweets._ID,
							AndTweet.Tweets.SENT_DATE }, null, null,
					AndTweet.Tweets.DEFAULT_SORT_ORDER);
			try {
				c.moveToFirst();
				if (c.getCount() > 0) {
					try {
						DateFormat f = new SimpleDateFormat(mDateFormat);
						Calendar cal = Calendar.getInstance();
						cal.setTime(f.parse(c.getString(1)));
						aLastRunTime = cal.getTimeInMillis();
					} catch (java.text.ParseException e) {
						throw new RuntimeException(e);
					}
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			} finally {
				c.close();
			}
			Connection aConn = new Connection(mUsername, mPassword, aLastRunTime);
			try {
				JSONArray jArr = aConn.getFriendsTimeline();
				for (int index = 0; index < jArr.length(); index++) {
					JSONObject jo = jArr.getJSONObject(index);
					JSONObject user;
					user = jo.getJSONObject("user");

					ContentValues values = new ContentValues();

					// Construct the Uri to existing record
					Long lTweetId = Long.parseLong(jo.getString("id"));
					Uri aTweetUri = ContentUris.withAppendedId(
							AndTweet.Tweets.CONTENT_URI, lTweetId);

					values.put(AndTweet.Tweets._ID, lTweetId.toString());
					values.put(AndTweet.Tweets.AUTHOR_ID, user
							.getString("screen_name"));

					Spannable sText = new SpannableString(jo
							.getString("text"));
					Linkify.addLinks(sText, Linkify.ALL);
					values.put(AndTweet.Tweets.MESSAGE, sText.toString());

					DateFormat f = new SimpleDateFormat(mDateFormat);
					Calendar cal = Calendar.getInstance();
					try {
						cal.setTime(f.parse(jo.getString("created_at")));
					} catch (java.text.ParseException e) {
						Log.e(TAG, e.getMessage());
					}
					Spannable sDate = new SpannableString(f.format(cal
							.getTime()));
					values.put(Tweets.SENT_DATE, sDate.toString());

					if ((getContentResolver().update(aTweetUri, values,
							null, null)) == 0) {
						getContentResolver().insert(
								AndTweet.Tweets.CONTENT_URI, values);
						aNewTweets++;
					}
				}
			} catch (JSONException e) {
				Log.e(TAG, e.getMessage());
			} catch (SQLiteConstraintException e) {
				Log.e(TAG, e.getMessage());
			}
		}
		return aNewTweets;
	}
}
