/*
 * Copyright (C) 2010-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.util.MyLog

/**
 * A widget provider. It uses [MyAppWidgetData] to store preferences and to
 * accumulate data (notifications...) received.
 *
 * @author yvolk@yurivolkov.com
 */
class MyAppWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        MyLog.i(TAG, "onReceive; action=" + intent.action)
        myContextHolder.initialize(context, TAG)
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MyLog.v(TAG) { "onUpdate; ids=" + appWidgetIds.contentToString() }
        AppWidgets.of(NotificationEvents.newInstance()).updateViews(appWidgetManager, createFilter(appWidgetIds))
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray) {
        MyLog.v(TAG) { "onDeleted; ids=" + appWidgetIds.contentToString() }
        AppWidgets.of(NotificationEvents.newInstance()).listOfData
            .filter(createFilter(appWidgetIds)).forEach { obj: MyAppWidgetData -> obj.update() }
    }

    override fun onEnabled(context: Context?) {
        MyLog.v(TAG, "onEnabled")
    }

    override fun onDisabled(context: Context?) {
        MyLog.v(TAG, "onDisabled")
    }

    companion object {
        private val TAG: String = MyAppWidgetProvider::class.simpleName!!

        private fun createFilter(appWidgetIds: IntArray): (MyAppWidgetData) -> Boolean = { data ->
            appWidgetIds.contains(data.appWidgetId)
        }
    }
}
