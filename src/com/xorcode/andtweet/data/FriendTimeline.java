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

import java.util.Date;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.text.Html;
import android.util.Log;

import com.xorcode.andtweet.TwitterUser;
import com.xorcode.andtweet.TwitterUser.CredentialsVerified;
import com.xorcode.andtweet.data.AndTweetDatabase.Tweets;
import com.xorcode.andtweet.net.ConnectionException;
import com.xorcode.andtweet.util.MyLog;
import com.xorcode.andtweet.util.SelectionAndArgs;

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

    private Uri mContentUri;

    private Uri mContentCountUri;

    public FriendTimeline(Context context, int timelineType) {
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mTu = TwitterUser.getTwitterUser();
        mTimelineType = timelineType;
        mLastStatusId = mTu.getSharedPreferences().getLong("last_timeline_id" + timelineType, 0);
        switch (mTimelineType) {
            case AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS:
            case AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS:
                mContentUri = AndTweetDatabase.Tweets.CONTENT_URI;
                mContentCountUri = AndTweetDatabase.Tweets.CONTENT_COUNT_URI;
                break;
            case AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES:
                mContentUri = AndTweetDatabase.DirectMessages.CONTENT_URI;
                mContentCountUri = AndTweetDatabase.DirectMessages.CONTENT_COUNT_URI;
                break;
        }

    }

    /**
     * Load the user and friends timeline.
     * 
     * @throws ConnectionException
     */
    public boolean loadTimeline() throws ConnectionException {
        return loadTimeline(false);
    }

    /**
     * Load the user and friends timeline from the Internet to the device
     * 
     * @param firstRun
     * @throws ConnectionException
     */
    public boolean loadTimeline(boolean firstRun) throws ConnectionException {
        boolean ok = false;
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
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES:
                    jArr = mTu.getConnection().getDirectMessages(lastId, limit);
                    break;
                default:
                    Log.e(TAG, "Got unhandled tweet type: " + mTimelineType);
                    break;
            }
            if (jArr != null) {
                ok = true;
                try {
                    for (int index = 0; index < jArr.length(); index++) {
                        JSONObject jo = jArr.getJSONObject(index);
                        long lId = jo.getLong("id");
                        if (lId > lastId) {
                            lastId = lId;
                        }
                        insertFromJSONObject(jo);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (mNewTweets > 0) {
                mContentResolver.notifyChange(mContentUri, null);
            }
            if (lastId > mLastStatusId) {
                mLastStatusId = lastId;
                mTu.getSharedPreferences().edit().putLong("last_timeline_id" + mTimelineType,
                        mLastStatusId).commit();
            }
        }
        return ok;
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
        ContentValues values = new ContentValues();

        // Construct the Uri to existing record
        Long lTweetId = Long.parseLong(jo.getString("id"));
        Uri aTweetUri = ContentUris.withAppendedId(mContentUri, lTweetId);

        String message = Html.fromHtml(jo.getString("text")).toString();

        try {
            // TODO: Unify databases!
            switch (mTimelineType) {
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS:
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS:
                    JSONObject user;
                    user = jo.getJSONObject("user");

                    values.put(AndTweetDatabase.Tweets._ID, lTweetId.toString());
                    values.put(AndTweetDatabase.Tweets.AUTHOR_ID, user.getString("screen_name"));

                    values.put(AndTweetDatabase.Tweets.MESSAGE, message);
                    values.put(AndTweetDatabase.Tweets.SOURCE, jo.getString("source"));
                    values.put(AndTweetDatabase.Tweets.TWEET_TYPE, mTimelineType);
                    values.put(AndTweetDatabase.Tweets.IN_REPLY_TO_STATUS_ID, jo
                            .getString("in_reply_to_status_id"));
                    values.put(AndTweetDatabase.Tweets.IN_REPLY_TO_AUTHOR_ID, jo
                            .getString("in_reply_to_screen_name"));
                    values.put(AndTweetDatabase.Tweets.FAVORITED, jo.getBoolean("favorited") ? 1 : 0);
                    break;
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES:
                    values.put(AndTweetDatabase.DirectMessages._ID, lTweetId.toString());
                    values.put(AndTweetDatabase.DirectMessages.AUTHOR_ID, jo
                            .getString("sender_screen_name"));
                    values.put(AndTweetDatabase.DirectMessages.MESSAGE, message);
                    break;
            }

            Long created = Date.parse(jo.getString("created_at"));
            values.put(Tweets.SENT_DATE, created);
        } catch (Exception e) {
            Log.e(TAG, "insertFromJSONObject: " + e.toString());
        }

        if ((mContentResolver.update(aTweetUri, values, null, null)) == 0) {
            // There was no such row so add new one
            mContentResolver.insert(mContentUri, values);
            mNewTweets++;
            switch (mTimelineType) {
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_FRIENDS:
                case AndTweetDatabase.Tweets.TIMELINE_TYPE_MENTIONS:
                    if (mTu.getUsername().equals(jo.getString("in_reply_to_screen_name"))
                            || message.contains("@" + mTu.getUsername())) {
                        mReplies++;
                    }
            }
        }
        return aTweetUri;
    }

    /**
     * Insert a row from a JSONObject. Takes an optional parameter to notify
     * listeners of the change.
     * 
     * @param jo
     * @param notify
     * @return Uri
     * @throws JSONException
     * @throws SQLiteConstraintException
     */
    public Uri insertFromJSONObject(JSONObject jo, boolean notify) throws JSONException,
            SQLiteConstraintException {
        Uri aTweetUri = insertFromJSONObject(jo);
        if (notify)
            mContentResolver.notifyChange(aTweetUri, null);
        return aTweetUri;
    }

    /**
     * Remove old records to ensure that the database does not grow too large.
     * Maximum number of records is configured in "history_size" preference
     * 
     * @return Number of deleted records
     */
    public int pruneOldRecords() {
        int nDeleted = 0;
        int nDeletedTime = 0;
        // We're using global preferences here
        SharedPreferences sp = MyPreferences
                .getDefaultSharedPreferences();
        int maxDays = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_TIME, "3"));
        long sinceTimestamp = 0;
        if (maxDays > 0) {
            sinceTimestamp = System.currentTimeMillis() - maxDays * (1000L * 60 * 60 * 24);

            SelectionAndArgs sa = new SelectionAndArgs();
            sa.addSelection(AndTweetDatabase.Tweets.SENT_DATE + " <  ?", new String[] {
                String.valueOf(sinceTimestamp)
            });

            if (mTimelineType != AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES) {
                // Don't delete Favorites!
                sa.addSelection(AndTweetDatabase.Tweets.FAVORITED + " = ?", new String[] {
                    "0"
                });
            }
            nDeletedTime = mContentResolver.delete(mContentUri, sa.selection, sa.selectionArgs);
        }

        int nTweets = 0;
        int nToDeleteSize = 0;
        int nDeletedSize = 0;
        int maxSize = Integer.parseInt(sp.getString(MyPreferences.KEY_HISTORY_SIZE, "2000"));
        long sinceTimestampSize = 0;
        if (maxSize > 0) {
            try {

                nDeletedSize = 0;
                Cursor cursor = mContentResolver.query(mContentCountUri, null, null, null, null);
                if (cursor.moveToFirst()) {
                    // Count is in the first column
                    nTweets = cursor.getInt(0);
                    nToDeleteSize = nTweets - maxSize;
                }
                cursor.close();
                if (nToDeleteSize > 0) {
                    // Find SENT_DATE of the most recent tweet to delete
                    cursor = mContentResolver.query(mContentUri, new String[] {
                        Tweets.SENT_DATE
                    }, null, null, "sent ASC LIMIT 0," + nToDeleteSize);
                    if (cursor.moveToLast()) {
                        sinceTimestampSize = cursor.getLong(0);
                    }
                    cursor.close();
                    if (sinceTimestampSize > 0) {
                        SelectionAndArgs sa = new SelectionAndArgs();
                        sa.addSelection(AndTweetDatabase.Tweets.SENT_DATE + " <=  ?", new String[] {
                            String.valueOf(sinceTimestampSize)
                        });
                        if (mTimelineType != AndTweetDatabase.Tweets.TIMELINE_TYPE_MESSAGES) {
                            sa.addSelection(AndTweetDatabase.Tweets.FAVORITED + " = ?",
                                    new String[] {
                                        "0"
                                    });
                        }
                        nDeletedSize = mContentResolver.delete(mContentUri, sa.selection,
                                sa.selectionArgs);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "pruneOldRecords failed");
                e.printStackTrace();
            }
        }
        nDeleted = nDeletedTime + nDeletedSize;
        if (MyLog.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG,
                    "pruneOldRecords; History time=" + maxDays + " days; deleted " + nDeletedTime
                            + " , since " + sinceTimestamp + ", now=" + System.currentTimeMillis());
            Log.v(TAG, "pruneOldRecords; History size=" + maxSize + " tweets; deleted "
                    + nDeletedSize + " of " + nTweets + " tweets, since " + sinceTimestampSize);
        }

        return nDeleted;
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
        return mContentResolver.delete(mContentUri, AndTweetDatabase.Tweets._ID + " = " + statusId,
                null);
    }
}
