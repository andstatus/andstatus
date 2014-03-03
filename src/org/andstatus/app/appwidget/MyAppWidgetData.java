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

package org.andstatus.app.appwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * The class maintains the appWidget instance (defined by mappWidgetId): - state
 * (that changes when new tweets etc. arrive); - preferences (that are set once
 * in the appWidget configuration activity.
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetData {
	private static final String TAG = MyAppWidgetData.class
			.getSimpleName();

	/**
	 * Name of the preferences file (with appWidgetId appended to it, so every
	 * appWidget instance would have its own preferences file) We don't need
	 * qualified name here because the file is in the "qualified" directory:
	 * "/data/data/org.andstatus.app/shared_prefs/"
	 * */
	public static final String PREFS_FILE_NAME = TAG;

	/**
	 * Key to store number of new tweets received
	 */
	private static final String PREF_NUM_HOME_TIMELINE_KEY = "num_messages";
	private static final String PREF_NUM_MENTIONS_KEY = "num_mentions";
	private static final String PREF_NUM_DIRECTMESSAGES_KEY = "num_directmessages";
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

	// Numbers of new Messages accumulated
	// TODO: GETTER AND SETTER METHODS, REMEMBER "OLD VALUE"...
	public int numHomeTimeline = 0;
	public int numMentions = 0;
	public int numDirectMessages = 0;

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
	
	public MyAppWidgetData(Context context, int appWidgetId) {
		mContext = context;
		mappWidgetId = appWidgetId;
		prefsFileName = PREFS_FILE_NAME + mappWidgetId;
		MyContextHolder.initialize(context, this);
	}

	/**
	 * Clear counters
	 */
	public void clearCounters() {
		numMentions = 0;
		numDirectMessages = 0;
		numHomeTimeline = 0;
		// New Tweets etc. will be since dateChecked ! 
		dateCleared = dateChecked;
		changed = true;
	}

	/**
	 * Are there any new messages in any of timelines
	 * @return
	 */
	public boolean areNew() {
	    return (numMentions >0) || (numDirectMessages > 0) || (numHomeTimeline > 0);
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
		boolean ok = false;
		SharedPreferences prefs = MyPreferences.getSharedPreferences(prefsFileName);
		if (prefs == null) {
			MyLog.e(this, "The prefs file '" + prefsFileName + "' was not loaded");
		} else {
			nothingPref = prefs.getString(PREF_NOTHING_KEY, null);
			if (nothingPref == null) {
				nothingPref = mContext
						.getString(R.string.appwidget_nothingnew_default);
				if (MyLog.isLoggable(this, MyLog.DEBUG)) {
					nothingPref += " (" + mappWidgetId + ")";
				}
			}
            dateChecked = prefs.getLong(PREF_DATECHECKED_KEY, 0);
			if (dateChecked == 0) {
				clearCounters();
			} else {
				numHomeTimeline = prefs.getInt(PREF_NUM_HOME_TIMELINE_KEY, 0);
				numMentions = prefs.getInt(PREF_NUM_MENTIONS_KEY, 0);
				numDirectMessages = prefs.getInt(PREF_NUM_DIRECTMESSAGES_KEY, 0);
	            dateCleared = prefs.getLong(PREF_DATECLEARED_KEY, 0);
			}

	        if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
	            MyLog.v(TAG, "Prefs for appWidgetId=" + mappWidgetId
	                    + " were loaded");
	        }
			ok = true;
			isLoaded = ok;
		}
		return ok;
	}

	public boolean save() {
		boolean ok = false;
		if (!isLoaded) {
			MyLog.e(this, "Save without load is not possible");
		} else {
			SharedPreferences.Editor prefs = MyPreferences.getSharedPreferences(
					prefsFileName).edit();
			if (prefs == null) {
				MyLog.e(this, "Prefs Editor was not loaded");
			} else {
				prefs.putString(PREF_NOTHING_KEY, nothingPref);
				prefs.putInt(PREF_NUM_HOME_TIMELINE_KEY, numHomeTimeline);
				prefs.putInt(PREF_NUM_MENTIONS_KEY, numMentions);
				prefs.putInt(PREF_NUM_DIRECTMESSAGES_KEY, numDirectMessages);
				
                prefs.putLong(PREF_DATECHECKED_KEY, dateChecked);
				prefs.putLong(PREF_DATECLEARED_KEY, dateCleared);
				prefs.commit();
	            if (MyLog.isLoggable(TAG, MyLog.VERBOSE)) {
    				MyLog.v(TAG, "Prefs for appWidgetId=" + mappWidgetId
    				        + " were saved, nothing='" + nothingPref + "'");
	            }
				ok = true;
			}
		}
		return ok;
	}

	/**
	 * Delete the preferences file!
	 * */
	public boolean delete() {
	    return SharedPreferencesUtil.delete(mContext, prefsFileName); 
	}
}
