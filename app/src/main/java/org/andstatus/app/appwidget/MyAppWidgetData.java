/*
 * Copyright (C) 2010-2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

/**
 * The class maintains the appWidget instance (defined by mappWidgetId): - state
 * (that changes when new tweets etc. arrive); - preferences (that are set once
 * in the appWidget configuration activity).
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetData {
    private static final String TAG = MyAppWidgetData.class.getSimpleName();

    /**
     * Name of the preferences file (with appWidgetId appended to it, so every
     * appWidget instance would have its own preferences file) We don't need
     * qualified name here because the file is in the "qualified" directory:
     * "/data/data/org.andstatus.app/shared_prefs/"
     * */
    public static final String PREFS_FILE_NAME = TAG;

    /**
     * Key to store number of new messages/tweets received
     */
    private static final String PREF_NUM_HOME_TIMELINE_KEY = "num_messages";
    private static final String PREF_NUM_MENTIONS_KEY = "num_mentions";
    private static final String PREF_NUM_DIRECTMESSAGES_KEY = "num_directmessages";
    /**
     * Words shown in a case there is nothing new
     */
    public static final String PREF_NOTHING_KEY = "nothing";
    /**
     *  Date and time when counters where cleared
     */
    public static final String PREF_DATESINCE_KEY = "datecleared";
    /**
     *  Date and time when data was checked on the server last time
     */
    public static final String PREF_DATECHECKED_KEY = "datechecked";

    private Context mContext;
    private int mAppWidgetId;

    private String prefsFileName;

    private boolean isLoaded = false;

    String nothingPref = "";

    // Numbers of new Messages accumulated
    int numHomeTimeline = 0;
    int numMentions = 0;
    int numDirectMessages = 0;

    /**  Value of {@link #dateLastChecked} before counters were cleared */
    long dateSince = 0;
    /**
     *  Date and time, when a server was successfully checked for new messages/tweets.
     *  If there was some new messages on the server, they were loaded at that time.
     */
    long dateLastChecked = 0;
    
    boolean changed = false;
    
    private MyAppWidgetData(Context context, int appWidgetId) {
        mContext = context;
        this.mAppWidgetId = appWidgetId;
        prefsFileName = PREFS_FILE_NAME + this.mAppWidgetId;
    }

    public static MyAppWidgetData newInstance(Context context, int appWidgetId) {
        MyAppWidgetData data = new MyAppWidgetData(context, appWidgetId);
        MyContextHolder.initialize(context, data);
        if (MyContextHolder.get().isReady()) {
            data.load();
        }
        return data;
    }
    
    private void load() {
        SharedPreferences prefs = SharedPreferencesUtil.getSharedPreferences(prefsFileName);
        if (prefs == null) {
            MyLog.e(this, "The prefs file '" + prefsFileName + "' was not loaded");
        } else {
            nothingPref = prefs.getString(PREF_NOTHING_KEY, null);
            if (nothingPref == null) {
                nothingPref = mContext.getString(R.string.appwidget_nothingnew_default);
                if (MyPreferences.getShowDebuggingInfoInUi()) {
                    nothingPref += " (" + mAppWidgetId + ")";
                }
            }
            dateLastChecked = prefs.getLong(PREF_DATECHECKED_KEY, 0);
            if (dateLastChecked == 0) {
                clearCounters();
            } else {
                numHomeTimeline = prefs.getInt(PREF_NUM_HOME_TIMELINE_KEY, 0);
                numMentions = prefs.getInt(PREF_NUM_MENTIONS_KEY, 0);
                numDirectMessages = prefs.getInt(PREF_NUM_DIRECTMESSAGES_KEY, 0);
                dateSince = prefs.getLong(PREF_DATESINCE_KEY, 0);
            }

            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this, "Prefs for appWidgetId=" + mAppWidgetId
                        + " were loaded");
            }
            isLoaded = true;
        }
    }

    public void clearCounters() {
        numMentions = 0;
        numDirectMessages = 0;
        numHomeTimeline = 0;
        dateSince = dateLastChecked;
        changed = true;
    }

    public boolean areThereAnyNewMessagesInAnyTimeline() {
        return (numMentions >0) || (numDirectMessages > 0) || (numHomeTimeline > 0);
    }
    
    private void onDataCheckedOnTheServer() {
        dateLastChecked = System.currentTimeMillis();
        if (dateSince == 0) {
            clearCounters();
        }
        changed = true;
    }
    
    public boolean save() {
        boolean ok = false;
        if (!isLoaded) {
            MyLog.d(this, "Save without load is not possible");
        } else {
            SharedPreferences.Editor prefs = SharedPreferencesUtil.getSharedPreferences(
                    prefsFileName).edit();
            if (prefs == null) {
                MyLog.e(this, "Prefs Editor was not loaded");
            } else {
                prefs.putString(PREF_NOTHING_KEY, nothingPref);
                prefs.putInt(PREF_NUM_HOME_TIMELINE_KEY, numHomeTimeline);
                prefs.putInt(PREF_NUM_MENTIONS_KEY, numMentions);
                prefs.putInt(PREF_NUM_DIRECTMESSAGES_KEY, numDirectMessages);
                
                prefs.putLong(PREF_DATECHECKED_KEY, dateLastChecked);
                prefs.putLong(PREF_DATESINCE_KEY, dateSince);
                prefs.commit();
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, "Saved " + toString());
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
        MyLog.v(this, "Deleting data for widgetId=" + mAppWidgetId );
        return SharedPreferencesUtil.delete(mContext, prefsFileName); 
    }

    @Override
    public String toString() {
        return "MyAppWidgetData:{id:" + mAppWidgetId +
                (numHomeTimeline > 0 ? ", home:" + numHomeTimeline : "") +
                (numMentions > 0 ? ", mentions:" + numMentions : "") +
                (numDirectMessages > 0 ? ", direct:" + numDirectMessages : "") +
                (dateLastChecked > 0 ? ", checked:" + dateLastChecked : "") +
                (dateSince > 0 ? ", since:" + dateSince : "") +
                (TextUtils.isEmpty(nothingPref) ? "" : ", nothing:" + nothingPref) +
                "}";
    }

    public void update(CommandResult result) {
        if (result.hasError() && result.getDownloadedCount() == 0) {
            return;
        }
        numHomeTimeline += result.getMessagesAdded();
        numMentions += result.getMentionsAdded();
        numDirectMessages += result.getDirectedAdded();
        onDataCheckedOnTheServer();
        save();
    }

    int getId() {
        return mAppWidgetId;
    }
}
