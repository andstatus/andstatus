/*
 * Copyright (C) 2010-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.notification.NotificationEvents
import org.andstatus.app.util.MyLog
import java.util.*
import java.util.function.Consumer
import java.util.function.Predicate

/**
 * A widget provider. It uses [MyAppWidgetData] to store preferences and to
 * accumulate data (notifications...) received.
 *
 * @author yvolk@yurivolkov.com
 */
class MyAppWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context?, intent: Intent?) {
        MyLog.i(TAG, "onReceive; action=" + intent.getAction())
         MyContextHolder.myContextHolder.initialize(context, TAG).getBlocking()
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        MyLog.v(TAG) { "onUpdate; ids=" + Arrays.toString(appWidgetIds) }
        AppWidgets.Companion.of(NotificationEvents.Companion.newInstance()).updateViews(appWidgetManager, filterIds(appWidgetIds))
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        MyLog.v(TAG) { "onDeleted; ids=" + Arrays.toString(appWidgetIds) }
        AppWidgets.Companion.of(NotificationEvents.Companion.newInstance()).list()
                .stream().filter(filterIds(appWidgetIds)).forEach(Consumer { obj: MyAppWidgetData? -> obj.update() })
    }

    override fun onEnabled(context: Context?) {
        MyLog.v(TAG, "onEnabled")
    }

    override fun onDisabled(context: Context?) {
        MyLog.v(TAG, "onDisabled")
    }

    companion object {
        private val TAG: String? = MyAppWidgetProvider::class.java.simpleName
        private fun filterIds(appWidgetIds: IntArray?): Predicate<MyAppWidgetData?>? {
            return Predicate { data: MyAppWidgetData? -> Arrays.stream(appWidgetIds).boxed().anyMatch { id: Int? -> data.getId() == id } }
        }
    }
}