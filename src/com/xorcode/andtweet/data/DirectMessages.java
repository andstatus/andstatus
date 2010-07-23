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
    private Context mContext;
	private int mNewMessages;
    private TwitterUser mTu;
	private long mLastMessageId = 0;

	/**
	 * Class constructor.
	 * 
	 * @param contentResolver
	 * @param Context
	 * @param long lastMessageId
	 */
	public DirectMessages(Context context) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mTu = TwitterUser.getTwitterUser(mContext, false);
        mLastMessageId = mTu.getSharedPreferences().getLong("last_message_id", 0);
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
	public void loadMessages() throws ConnectionException, JSONException, SQLiteConstraintException, ConnectionAuthenticationException, ConnectionUnavailableException, SocketTimeoutException {
		mNewMessages = 0;
		
        long lastId = mLastMessageId;
		if (mTu.getCredentialsVerified() == CredentialsVerified.SUCCEEDED) {
    		JSONArray jArr = mTu.getConnection().getDirectMessages(mLastMessageId, 0);
            if (jArr != null) {
        		for (int index = 0; index < jArr.length(); index++) {
        			JSONObject jo = jArr.getJSONObject(index);
        			long lId = jo.getLong("id");
        			if (lId > lastId) {
        			    lastId = lId;
        			}
        			insertFromJSONObject(jo);
        		}
        		if (mNewMessages > 0) {
        			mContentResolver.notifyChange(AndTweetDatabase.DirectMessages.CONTENT_URI, null);
        		}
                if (lastId > mLastMessageId) {
                    mLastMessageId = lastId;
                    mTu.getSharedPreferences().edit().putLong("last_message_id", mLastMessageId).commit();
                }
            }
        }
	}

	/**
	 * Insert a record from a JSON object.
	 * 
	 * @param jo
	 * @return Uri
	 * @throws JSONException
	 * @throws SQLiteConstraintException
	 */
	public Uri insertFromJSONObject(JSONObject jo) throws JSONException, SQLiteConstraintException {
		ContentValues values = new ContentValues();

		// Construct the Uri to existing record
		Long lMessageId = Long.parseLong(jo.getString("id"));
		Uri aMessageUri = ContentUris.withAppendedId(AndTweetDatabase.DirectMessages.CONTENT_URI, lMessageId);

		values.put(AndTweetDatabase.DirectMessages._ID, lMessageId.toString());
		values.put(AndTweetDatabase.DirectMessages.AUTHOR_ID, jo.getString("sender_screen_name"));
		values.put(AndTweetDatabase.DirectMessages.MESSAGE, Html.fromHtml(jo.getString("text")).toString());

		try {
			Long created = Date.parse(jo.getString("created_at"));
			values.put(AndTweetDatabase.DirectMessages.SENT_DATE, created);
		} catch (Exception e) {
		    /* 2010-0326 yvolk
		     * Here and in other places got rid of e.getMessage()
             * in exception handling, e.g.:
             *   Log.e(TAG, e.getMessage());
             * because sometimes it produces uncaught exception!!!
		     */
			Log.e(TAG, "insertFromJSONObject: " + e.toString());
		}

		if ((mContentResolver.update(aMessageUri, values, null, null)) == 0) {
			mContentResolver.insert(AndTweetDatabase.DirectMessages.CONTENT_URI, values);
			mNewMessages++;
		}
		return aMessageUri;
	}

	/**
	 * Insert a record from a JSON object.
	 * 
	 * @param jo
	 * @param notify
	 * @return Uri
	 * @throws JSONException
	 * @throws SQLiteConstraintException
	 */
	public Uri insertFromJSONObject(JSONObject jo, boolean notify) throws JSONException, SQLiteConstraintException {
		Uri aMessageUri = insertFromJSONObject(jo);
		if (notify) mContentResolver.notifyChange(aMessageUri, null);
		return aMessageUri;
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
        //return mContentResolver.delete(AndTweetDatabase.DirectMessages.CONTENT_URI, AndTweetDatabase.DirectMessages.CREATED_DATE + " < " + sinceTimestamp, null);
        return 0;
    }
	
	public int newCount() {
		return mNewMessages;
	}
}
