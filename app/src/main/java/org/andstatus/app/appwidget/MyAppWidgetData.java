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

import android.content.SharedPreferences;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.notification.NotificationEvents;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtil;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * Maintains the appWidget instance (defined by {@link #appWidgetId}): - state
 * (that changes when new tweets etc. arrive); - preferences (that are set once
 * in the appWidget configuration activity).
 * 
 * @author yvolk@yurivolkov.com
 */
public class MyAppWidgetData {
    private static final String TAG = MyAppWidgetData.class.getSimpleName();

    /** Words shown in a case there is nothing new */
    private static final String PREF_NOTHING_KEY = "nothing";
    /** Date and time when counters where cleared */
    private static final String PREF_DATESINCE_KEY = "datecleared";
    /** Date and time when data was checked on the server last time */
    private static final String PREF_DATECHECKED_KEY = "datechecked";

    public final NotificationEvents events;
    private int appWidgetId;

    private final String prefsFileName;

    private boolean isLoaded = false;

    String nothingPref = "";

    /**  Value of {@link #dateLastChecked} before counters were cleared */
    long dateSince = 0;
    /**
     *  Date and time, when a server was successfully checked for new notes/tweets.
     *  If there was some new notes on the server, they were loaded at that time.
     */
    long dateLastChecked = 0;

    private MyAppWidgetData(NotificationEvents events, int appWidgetId) {
        this.events = events;
        this.appWidgetId = appWidgetId;
        prefsFileName = TAG + this.appWidgetId;
    }

    public static MyAppWidgetData newInstance(NotificationEvents events, int appWidgetId) {
        MyAppWidgetData data = new MyAppWidgetData(events, appWidgetId);
        if (myContextHolder.getNow().isReady()) {
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
                nothingPref = events.myContext.context().getString(R.string.appwidget_nothingnew_default);
                if (MyPreferences.isShowDebuggingInfoInUi()) {
                    nothingPref += " (" + appWidgetId + ")";
                }
            }
            dateLastChecked = prefs.getLong(PREF_DATECHECKED_KEY, 0);
            if (dateLastChecked == 0) {
                clearCounters();
            } else {
                dateSince = prefs.getLong(PREF_DATESINCE_KEY, 0);
            }

            MyLog.v(this, () -> "Prefs for appWidgetId=" + appWidgetId + " were loaded");
            isLoaded = true;
        }
    }

    public void clearCounters() {
        dateSince = dateLastChecked;
    }

    private void onDataCheckedOnTheServer() {
        dateLastChecked = System.currentTimeMillis();
        if (dateSince == 0) clearCounters();
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
        MyLog.v(this, () -> "Saved " + toString());
        return true;
    }

    /** Delete the preferences file! */
    public boolean delete() {
        MyLog.v(this, () -> "Deleting data for widgetId=" + appWidgetId);
        return SharedPreferencesUtil.delete(events.myContext.context(), prefsFileName);
    }

    @Override
    public String toString() {
        return "MyAppWidgetData:{" +
                "id:" + appWidgetId +
                (events.isEmpty() ? "" : ", notifications:" + events) +
                (dateLastChecked > 0 ? ", checked:" + dateLastChecked : "") +
                (dateSince > 0 ? ", since:" + dateSince : "") +
                (StringUtil.isEmpty(nothingPref) ? "" : ", nothing:" + nothingPref) +
            "}";
    }

    public void update() {
        onDataCheckedOnTheServer();
        save();
    }

    int getId() {
        return appWidgetId;
    }
}