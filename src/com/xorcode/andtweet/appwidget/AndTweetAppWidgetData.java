/*
 * Copyright (C) 2010 yvolk (Yuri Volkov), http://yurivolkov.com
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
package com.xorcode.andtweet.appwidget;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import static android.content.Context.MODE_PRIVATE;

import com.xorcode.andtweet.AndTweetService;
import com.xorcode.andtweet.R;
import com.xorcode.andtweet.util.SharedPreferencesUtil;

/**
 * The class maintains the appWidget instance (defined by mappWidgetId): - state
 * (that changes when new tweets etc. arrive); - preferences (that are set once
 * in the appWidget configuration activity.
 * 
 * @author yvolk (Yuri Volkov), http://yurivolkov.com
 */
public class AndTweetAppWidgetData {
	private static final String TAG = AndTweetAppWidgetData.class
			.getSimpleName();

	/**
	 * Name of the preferences file (with appWidgetId appended to it, so every
	 * appWidget instance would have its own preferences file) We don't need
	 * qualified name here because the file is in the "qualified" directory:
	 * "/data/data/com.xorcode.andtweet/shared_prefs/"
	 * */
	public static final String PREFS_FILE_NAME = TAG;

	/**
	 * Key to store number of new tweets received
	 */
	public static final String PREF_NUM_TWEETS_KEY = "num_tweets";
	public static final String PREF_NUM_MENTIONS_KEY = "num_mentions";
	public static final String PREF_NUM_MESSAGES_KEY = "num_messages";
	/**
	 * Words shown in a case there is nothing new
	 */
	public static final String PREF_NOTHING_KEY = "nothing";
	/**
	 *	Date and time when counters where cleared
	 */
	public static final String PREF_DATECLEARED_KEY = "datecleared";
	/**
	 *	Date and time when data was checked on the server last time
	 */
	public static final String PREF_DATECHECKED_KEY = "datechecked";

	private Context mContext;
	private int mappWidgetId;
	private String prefsFileName;

	private boolean isLoaded = false;

	public String nothingPref = "";

	// Numbers of new Tweets accumulated
	// TODO: GETTER AND SETTER METHODS, REMEMBER "OLD VALUE"...
	public int numTweets = 0;
	public int numMentions = 0;
	public int numMessages = 0;

    /**
     *  When server was successfully checked for new tweets
     *  If there was some new data on the server, it was loaded that time.
     */
    public long dateChecked = 0;
	/**
	 *  dateChecked before counters were cleared.
	 */
	public long dateCleared = 0;
	
 	public boolean changed = false;
	
	public AndTweetAppWidgetData(Context context, int appWidgetId) {
		mContext = context;
		mappWidgetId = appWidgetId;
		prefsFileName = PREFS_FILE_NAME + mappWidgetId;
	}

	/**
	 * Clear counters
	 */
	public void clearCounters() {
		numMentions = 0;
		numMessages = 0;
		numTweets = 0;
		// New Tweets etc. will be since dateChecked ! 
		dateCleared = dateChecked;
		changed = true;
	}

    /**
     * Data on the server was successfully checked now
     */
    public void checked() {
        dateChecked = Long.valueOf(System.currentTimeMillis());
        if (dateCleared == 0) {
            clearCounters();
        }
        changed = true;
    }
	
	public boolean load() {
		boolean Ok = false;
		SharedPreferences prefs = mContext.getSharedPreferences(prefsFileName,
				MODE_PRIVATE);
		if (prefs == null) {
			Log.e(TAG, "The prefs file '" + prefsFileName + "' was not loaded");
		} else {
			nothingPref = prefs.getString(PREF_NOTHING_KEY, null);
			if (nothingPref == null) {
				nothingPref = mContext
						.getString(R.string.appwidget_nothingnew_default);
				// TODO Add AndTweet Debug option...
//				if (debug) {
//					nothingPref += " (" + mappWidgetId + ")";
//				}
			}
            dateChecked = prefs.getLong(PREF_DATECHECKED_KEY, 0);
			if (dateChecked == 0) {
				clearCounters();
			} else {
				numTweets = prefs.getInt(PREF_NUM_TWEETS_KEY, 0);
				numMentions = prefs.getInt(PREF_NUM_MENTIONS_KEY, 0);
				numMessages = prefs.getInt(PREF_NUM_MESSAGES_KEY, 0);
	            dateCleared = prefs.getLong(PREF_DATECLEARED_KEY, 0);
			}

	        if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
	            Log.v(TAG, "Prefs for appWidgetId=" + mappWidgetId
	                    + " were loaded");
	        }
			Ok = true;
			isLoaded = Ok;
		}
		return Ok;
	}

	public boolean save() {
		boolean Ok = false;
		if (!isLoaded) {
			Log.e(TAG, "Save without load is not possible");
		} else {
			SharedPreferences.Editor prefs = mContext.getSharedPreferences(
					prefsFileName, MODE_PRIVATE).edit();
			if (prefs == null) {
				Log.e(TAG, "Prefs Editor was not loaded");
			} else {
				prefs.putString(PREF_NOTHING_KEY, nothingPref);
				prefs.putInt(PREF_NUM_TWEETS_KEY, numTweets);
				prefs.putInt(PREF_NUM_MENTIONS_KEY, numMentions);
				prefs.putInt(PREF_NUM_MESSAGES_KEY, numMessages);
				
                prefs.putLong(PREF_DATECHECKED_KEY, dateChecked);
				prefs.putLong(PREF_DATECLEARED_KEY, dateCleared);
				prefs.commit();
	            if (Log.isLoggable(AndTweetService.APPTAG, Log.VERBOSE)) {
    				Log.v(TAG, "Prefs for appWidgetId=" + mappWidgetId
    				        + " were saved, nothing='" + nothingPref + "'");
	            }
				Ok = true;
			}
		}
		return Ok;
	}

	/**
	 * Delete the preferences file!
	 * */
	public boolean delete() {
	    return SharedPreferencesUtil.delete(mContext, prefsFileName); 
	}
}
