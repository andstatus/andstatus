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
package org.andstatus.app.appwidget

import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.SharedPreferencesUtil

/**
 * Maintains the appWidget instance (defined by [.appWidgetId]): - state
 * (that changes when new tweets etc. arrive); - preferences (that are set once
 * in the appWidget configuration activity).
 *
 * @author yvolk@yurivolkov.com
 */
class MyAppWidgetData private constructor(val events: NotificationEvents, private val appWidgetId: Int) {
    private val prefsFileName: String = TAG + appWidgetId
    private var isLoaded = false
    var nothingPref: String? = ""

    /**  Value of [.dateLastChecked] before counters were cleared  */
    var dateSince: Long = 0

    /**
     * Date and time, when a server was successfully checked for new notes/tweets.
     * If there was some new notes on the server, they were loaded at that time.
     */
    var dateLastChecked: Long = 0
    private fun load() {
        val prefs = SharedPreferencesUtil.getSharedPreferences(prefsFileName)
        if (prefs == null) {
            MyLog.w(this, "The prefs file '$prefsFileName' was not loaded")
        } else {
            nothingPref = prefs.getString(PREF_NOTHING_KEY, null)
            if (nothingPref == null) {
                nothingPref = events.myContext.context.getString(R.string.appwidget_nothingnew_default)
                if (MyPreferences.isShowDebuggingInfoInUi()) {
                    nothingPref += " ($appWidgetId)"
                }
            }
            dateLastChecked = prefs.getLong(PREF_DATECHECKED_KEY, 0)
            if (dateLastChecked == 0L) {
                clearCounters()
            } else {
                dateSince = prefs.getLong(PREF_DATESINCE_KEY, 0)
            }
            MyLog.v(this) { "Prefs for appWidgetId=$appWidgetId were loaded" }
            isLoaded = true
        }
    }

    fun clearCounters() {
        dateSince = dateLastChecked
    }

    private fun onDataCheckedOnTheServer() {
        dateLastChecked = System.currentTimeMillis()
        if (dateSince == 0L) clearCounters()
    }

    fun save(): Boolean {
        if (!isLoaded) {
            MyLog.d(this, "Save without load is not possible")
            return false
        }
        val prefs = SharedPreferencesUtil.getSharedPreferences(prefsFileName) ?: return false
        val editor = prefs.edit()
        editor.putString(PREF_NOTHING_KEY, nothingPref)
        editor.putLong(PREF_DATECHECKED_KEY, dateLastChecked)
        editor.putLong(PREF_DATESINCE_KEY, dateSince)
        editor.apply()
        MyLog.v(this) { "Saved " + toString() }
        return true
    }

    /** Delete the preferences file!  */
    fun delete(): Boolean {
        MyLog.v(this) { "Deleting data for widgetId=$appWidgetId" }
        return SharedPreferencesUtil.delete(events.myContext.context, prefsFileName)
    }

    override fun toString(): String {
        return "MyAppWidgetData:{" +
                "id:" + appWidgetId +
                (if (events.isEmpty()) "" else ", notifications:$events") +
                (if (dateLastChecked > 0) ", checked:$dateLastChecked" else "") +
                (if (dateSince > 0) ", since:$dateSince" else "") +
                (if (nothingPref.isNullOrEmpty()) "" else ", nothing:$nothingPref") +
                "}"
    }

    fun update() {
        onDataCheckedOnTheServer()
        save()
    }

    fun getId(): Int {
        return appWidgetId
    }

    companion object {
        private val TAG: String = MyAppWidgetData::class.java.simpleName

        /** Words shown in a case there is nothing new  */
        private val PREF_NOTHING_KEY: String = "nothing"

        /** Date and time when counters where cleared  */
        private val PREF_DATESINCE_KEY: String = "datecleared"

        /** Date and time when data was checked on the server last time  */
        private val PREF_DATECHECKED_KEY: String = "datechecked"
        fun newInstance(events: NotificationEvents, appWidgetId: Int): MyAppWidgetData {
            val data = MyAppWidgetData(events, appWidgetId)
            if ( MyContextHolder.myContextHolder.getNow().isReady()) {
                data.load()
            }
            return data
        }
    }

}
