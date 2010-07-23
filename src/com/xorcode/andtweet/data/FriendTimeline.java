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

import java.net.SocketTimeoutException;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import com.xorcode.andtweet.TwitterUser;
import com.xorcode.andtweet.TwitterUser.CredentialsVerified;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.net.ConnectionAuthenticationException;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.net.ConnectionUnavailableException;

/**
 * Handles loading data from JSON into database.
 * 
 * @author torgny.bjers
 */
public class FriendTimeline {

	private static final String TAG = "FriendTimeline";

	private ContentResolver mContentResolver;
    private Context mContext;
	private long mLastStatusId = 0;
	private int mNewTweets;
	private int mReplies;
	private TwitterUser mTu;
	private int mTimelineType;

	public FriendTimeline(Context context, int timelineType) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mTu = TwitterUser.getTwitterUser(mContext, false);
        mTimelineType = timelineType;
		mLastStatusId = mTu.getSharedPreferences().getLong("last_timeline_id" + timelineType, 0);
	}

	/**
	 * Load the user and friends timeline.
	 * 
	 * @throws ConnectionException
	 * @throws JSONException
	 * @throws SQLiteConstraintException
	 * @throws ConnectionAuthenticationException
	 * @throws ConnectionUnavailableException
	 * @throws SocketTimeoutException 
	 */
	public void loadTimeline() throws ConnectionException, JSONException, SQLiteConstraintException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		loadTimeline(false);
	}

    /**
     * Load the user and friends timeline.
     * 
     * @param firstRun
     * @throws ConnectionException
     * @throws JSONException
     * @throws SQLiteConstraintException
     * @throws ConnectionAuthenticationException
     * @throws ConnectionUnavailableException
     * @throws SocketTimeoutException
     */
    public void loadTimeline(boolean firstRun) throws ConnectionException,
            JSONException, SQLiteConstraintException, ConnectionAuthenticationException,
            ConnectionUnavailableException, SocketTimeoutException {
        mNewTweets = 0;
        mReplies = 0;
        long lastId = mLastStatusId;
        int limit = 200;
        if (firstRun) {
            limit = 20;
        }
        if (mTu.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
            JSONArray jArr = null;
            switch (mTimelineType) {
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS:
                    jArr = mTu.getConnection().getFriendsTimeline(lastId, limit);
                    break;
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS:
                    jArr = mTu.getConnection().getMentionsTimeline(lastId, limit);
                    break;
                default:
                    Log.e(TAG, "Got unhandled tweet type: " + mTimelineType);
                    break;
            }
            if (jArr != null) {
                for (int index = 0; index < jArr.length(); index++) {
                    JSONObject jo = jArr.getJSONObject(index);
                    long lId = jo.getLong("id");
                    if (lId > lastId) {
                        lastId = lId;
                    }
                    insertFromJSONObject(jo);
                }
            }
            if (mNewTweets > 0) {
                mContentResolver.notifyChange(AndTweetDatabase.Tweets.CONTENT_URI, null);
            }
            if (lastId > mLastStatusId) {
                mLastStatusId = lastId;
                mTu.getSharedPreferences().edit().putLong("last_timeline_id" + mTimelineType, mLastStatusId).commit();
            }
        }
    }

	/**
	 * Insert a row from a JSONObject.
	 * 
	 * @param jo
	 * @return
	 * @throws JSONException
	 * @throws SQLiteConstraintException
	 */
	public Uri insertFromJSONObject(JSONObject jo) throws JSONException, SQLiteConstraintException {
		JSONObject user;
		user = jo.getJSONObject("user");

		ContentValues values = new ContentValues();

		// Construct the Uri to existing record
		Long lTweetId = Long.parseLong(jo.getString("id"));
		Uri aTweetUri = ContentUris.withAppendedId(AndTweetDatabase.Tweets.CONTENT_URI, lTweetId);

		values.put(AndTweetDatabase.Tweets._ID, lTweetId.toString());
		values.put(AndTweetDatabase.Tweets.AUTHOR_ID, user.getString("screen_name"));

		String message = Html.fromHtml(jo.getString("text")).toString();
		values.put(AndTweetDatabase.Tweets.MESSAGE, message);
		values.put(AndTweetDatabase.Tweets.SOURCE, jo.getString("source"));
		values.put(AndTweetDatabase.Tweets.TWEET_TYPE, mTimelineType);
		values.put(AndTweetDatabase.Tweets.IN_REPLY_TO_STATUS_ID, jo.getString("in_reply_to_status_id"));
		values.put(AndTweetDatabase.Tweets.IN_REPLY_TO_AUTHOR_ID, jo.getString("in_reply_to_screen_name"));
		values.put(AndTweetDatabase.Tweets.FAVORITED, jo.getBoolean("favorited") ? 1 : 0);

		try {
			Long created = Date.parse(jo.getString("created_at"));
			values.put(Tweets.SENT_DATE, created);
		} catch (Exception e) {
		    Log.e(TAG, "insertFromJSONObject: " + e.toString());
		}

		if ((mContentResolver.update(aTweetUri, values, null, null)) == 0) {
		    // There was no such row so add new one
			mContentResolver.insert(AndTweetDatabase.Tweets.CONTENT_URI, values);
			mNewTweets++;
			if (mTu.getUsername().equals(jo.getString("in_reply_to_screen_name")) || message.contains("@" + mTu.getUsername())) {
				mReplies++;
			}
		}
		return aTweetUri;
	}

	/**
	 * Insert a row from a JSONObject.
	 * Takes an optional parameter to notify listeners of the change.
	 * 
	 * @param jo
	 * @param notify
	 * @return Uri
	 * @throws JSONException
	 * @throws SQLiteConstraintException
	 */
	public Uri insertFromJSONObject(JSONObject jo, boolean notify) throws JSONException, SQLiteConstraintException {
		Uri aTweetUri = insertFromJSONObject(jo);
		if (notify) mContentResolver.notifyChange(aTweetUri, null);
		return aTweetUri;
	}

	/**
	 * Remove old records to ensure that the database does not grow too large.
	 * Maximum number of records is configured in "history_size" preference
	 * 
	 * @return Number of deleted records
	 */
	public int pruneOldRecords() {
	    int maxSize = mTu.getSharedPreferences().getInt("history_size", 0);
	    if (maxSize < 1) {
	        maxSize = 2000;
	    }
	    // TODO:
		//return mContentResolver.delete(AndTweetDatabase.Tweets.CONTENT_URI, AndTweetDatabase.Tweets.CREATED_DATE + " < " + sinceTimestamp, null);
	    return 0;
	}

	/**
	 * Return the number of new statuses.
	 * 
	 * @return integer
	 */
	public int newCount() {
		return mNewTweets;
	}

	/**
	 * Return the number of new replies.
	 * 
	 * @return integer
	 */
	public int replyCount() {
		return mReplies;
	}

	/**
	 * Destroy the status specified by ID.
	 * 
	 * @param statusId
	 * @return Number of deleted records
	 */
	public int destroyStatus(long statusId) {
		return mContentResolver.delete(AndTweetDatabase.Tweets.CONTENT_URI, AndTweetDatabase.Tweets._ID + " = " + statusId, null);
	}
}
