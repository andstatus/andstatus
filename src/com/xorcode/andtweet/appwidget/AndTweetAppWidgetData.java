package com.xorcode.andtweet.appwidget;

import static android.content.Context.MODE_PRIVATE;

import java.io.IOException;

import com.xorcode.andtweet.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

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
	 *	Date and time when data was updated last time
	 */
	public static final String PREF_DATEUPDATED_KEY = "dateupdated";

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

	// When counters where cleared
	public long dateCleared = 0;
	// When data was updated  last time
	public long dateUpdated = 0;
 	public boolean changed = false;
	
	public AndTweetAppWidgetData(Context context, int appWidgetId) {
		mContext = context;
		mappWidgetId = appWidgetId;
		prefsFileName = PREFS_FILE_NAME + mappWidgetId;
	}

	public void clear() {
		numMentions = 0;
		numMessages = 0;
		numTweets = 0;
		dateCleared = Long.valueOf(System.currentTimeMillis());
		dateUpdated = dateCleared;
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
						.getString(R.string.appwidget_nothing_default);
				// TODO Add AndTweet Debug option...
//				if (debug) {
//					nothingPref += " (" + mappWidgetId + ")";
//				}
			}
			dateCleared = prefs.getLong(PREF_DATECLEARED_KEY, 0);
			if (dateCleared == 0) {
				clear();
			} else {
				numTweets = prefs.getInt(PREF_NUM_TWEETS_KEY, 0);
				numMentions = prefs.getInt(PREF_NUM_MENTIONS_KEY, 0);
				numMessages = prefs.getInt(PREF_NUM_MESSAGES_KEY, 0);
				dateUpdated = prefs.getLong(PREF_DATEUPDATED_KEY, 0);
			}

			Log.d(TAG, "Prefs for appWidgetId=" + mappWidgetId
				+ " were loaded");
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
				
				prefs.putLong(PREF_DATECLEARED_KEY, dateCleared);
				if (changed) {
					dateUpdated = Long.valueOf(System.currentTimeMillis());
					prefs.putLong(PREF_DATEUPDATED_KEY, dateUpdated);
				}
				prefs.commit();
				Log.d(TAG, "Prefs for appWidgetId=" + mappWidgetId
				+ " were saved, nothing='" + nothingPref + "'");
				Ok = true;
			}
		}
		return Ok;
	}

	/**
	 * Delete the preferences file!
	 * */
	public boolean delete() {
		boolean isDeleted = false;

		// Delete preferences file
		java.io.File prefFile = new java.io.File("/data/data/"
				+ mContext.getPackageName() + "/shared_prefs/" + prefsFileName
				+ ".xml");
		if (prefFile.exists()) {
			// Commit any changes left
			SharedPreferences.Editor prefs = mContext.getSharedPreferences(
					prefsFileName, MODE_PRIVATE).edit();
			if (prefs != null) {
				prefs.commit();
				prefs = null;
			}

			isDeleted = prefFile.delete();
			try {
				Log.d(TAG, "The prefs file '" + prefFile.getCanonicalPath()
						+ "' was " + (isDeleted ? "" : "not ") + " deleted");
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
		} else {
			try {
				Log.d(TAG, "The prefs file '" + prefFile.getCanonicalPath()
						+ "' was not found");
			} catch (IOException e) {
				Log.e(TAG, e.toString());
			}
		}
		return isDeleted;
	}
}
