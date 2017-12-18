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
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.notification.Notifier;
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

    private final MyContext mContext;
    private int mAppWidgetId;

    private String prefsFileName;

    private boolean isLoaded = false;

    String nothingPref = "";

    final Notifier notifier;

    /**  Value of {@link #dateLastChecked} before counters were cleared */
    long dateSince = 0;
    /**
     *  Date and time, when a server was successfully checked for new messages/tweets.
     *  If there was some new messages on the server, they were loaded at that time.
     */
    long dateLastChecked = 0;
    
    boolean changed = false;
    
    private MyAppWidgetData(MyContext myContext, int appWidgetId) {
        mContext = myContext;
        this.mAppWidgetId = appWidgetId;
        prefsFileName = PREFS_FILE_NAME + this.mAppWidgetId;
        notifier = myContext.getNotifier();
    }

    public static MyAppWidgetData newInstance(Context context, int appWidgetId) {
        MyAppWidgetData data = new MyAppWidgetData(MyContextHolder.initialize(context, context), appWidgetId);
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
                nothingPref = mContext.context().getString(R.string.appwidget_nothingnew_default);
                if (MyPreferences.isShowDebuggingInfoInUi()) {
                    nothingPref += " (" + mAppWidgetId + ")";
                }
            }
            dateLastChecked = prefs.getLong(PREF_DATECHECKED_KEY, 0);
            if (dateLastChecked == 0) {
                clearCounters();
            } else {
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
        dateSince = dateLastChecked;
        changed = true;
    }

    private void onDataCheckedOnTheServer() {
        dateLastChecked = System.currentTimeMillis();
        if (dateSince == 0) {
            clearCounters();
        }
        changed = true;
    }
    
    public boolean save() {
        if (!isLoaded) {
            MyLog.d(this, "Save without load is not possible");
            return false;
        }
        final SharedPreferences prefs = SharedPreferencesUtil.getSharedPreferences(prefsFileName);
        if (prefs == null) return false;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_NOTHING_KEY, nothingPref);
        editor.putLong(PREF_DATECHECKED_KEY, dateLastChecked);
        editor.putLong(PREF_DATESINCE_KEY, dateSince);
        editor.apply();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Saved " + toString());
        }
        return true;
    }

    /**
     * Delete the preferences file!
     * */
    public boolean delete() {
        MyLog.v(this, "Deleting data for widgetId=" + mAppWidgetId );
        return SharedPreferencesUtil.delete(mContext.context(), prefsFileName);
    }

    @Override
    public String toString() {
        return "MyAppWidgetData:{id:" + mAppWidgetId +
                ", notifications:" + notifier.events +
                (dateLastChecked > 0 ? ", checked:" + dateLastChecked : "") +
                (dateSince > 0 ? ", since:" + dateSince : "") +
                (TextUtils.isEmpty(nothingPref) ? "" : ", nothing:" + nothingPref) +
                "}";
    }

    public void update() {
        onDataCheckedOnTheServer();
        save();
    }

    int getId() {
        return mAppWidgetId;
    }
}
