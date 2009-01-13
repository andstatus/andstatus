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
import android.util.Log;

import com.xorcode.andtweet.data.AndTweet.Tweets;
import com.xorcode.andtweet.net.Connection;

public class FriendTimeline {

	private static final String TAG = "FriendTimeline";

	private static final String DATE_FORMAT = "EEE MMM dd HH:mm:ss Z yyyy";

	private ContentResolver mContentResolver;
	private String mUsername, mPassword;

	public FriendTimeline(ContentResolver contentResolver, String username, String password) {
		mContentResolver = contentResolver;
		mUsername = username;
		mPassword = password;
	}

	public int loadTimeline() {
		long aLastRunTime = 0;
		int aNewTweets = 0;
		if (mUsername != null && mUsername.length() > 0) {
			Log.i(TAG, "Loading friends timeline");
			// Try to load the last record
			Cursor c = mContentResolver.query(AndTweet.Tweets.CONTENT_URI, new String[] {
				AndTweet.Tweets._ID, AndTweet.Tweets.SENT_DATE
			}, null, null, AndTweet.Tweets.DEFAULT_SORT_ORDER);
			try {
				c.moveToFirst();
				// If a record is available, get the last run time
				if (c.getCount() > 0) {
					DateFormat f = new SimpleDateFormat(DATE_FORMAT);
					Calendar cal = Calendar.getInstance();
					cal.setTimeInMillis(c.getLong(1));
					aLastRunTime = cal.getTimeInMillis();
					Log.d(TAG, "Last tweet: " + f.format(cal.getTime()));
				}
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
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
					Uri aTweetUri = ContentUris.withAppendedId(AndTweet.Tweets.CONTENT_URI, lTweetId);

					values.put(AndTweet.Tweets._ID, lTweetId.toString());
					values.put(AndTweet.Tweets.AUTHOR_ID, user.getString("screen_name"));

					values.put(AndTweet.Tweets.MESSAGE, jo.getString("text"));

					DateFormat f = new SimpleDateFormat(DATE_FORMAT);
					Calendar cal = Calendar.getInstance();
					try {
						cal.setTime(f.parse(jo.getString("created_at")));
						values.put(Tweets.SENT_DATE, cal.getTimeInMillis());
					} catch (java.text.ParseException e) {
						Log.e(TAG, e.getMessage());
					}

					if ((mContentResolver.update(aTweetUri, values, null, null)) == 0) {
						mContentResolver.insert(AndTweet.Tweets.CONTENT_URI, values);
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
