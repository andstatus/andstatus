/*
 * Copyright (C) 2013-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.appwidget.MyAppWidgetProvider
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.notification.Notifier
import org.andstatus.app.origin.PersistentOrigins
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.service.ConnectionState
import org.andstatus.app.timeline.meta.PersistentTimelines
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.user.CachedUsersAndActors
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.IsEmpty
import java.util.function.Supplier

interface MyContext : Identifiable, IsEmpty {
    fun newInitialized(initializer: Any): MyContext
    val initialized: Boolean
    val isReady: Boolean
    val state: MyContextState
    val context: Context
    val baseContext: Context
    val preferencesChangeTime: Long
    val lastDatabaseError: String
    val database: SQLiteDatabase?
    val users: CachedUsersAndActors
    val accounts: MyAccounts
    val origins: PersistentOrigins
    val timelines: PersistentTimelines
    val queues: CommandQueue
    fun putAssertionData(key: String, contentValues: ContentValues) {}
    fun save(reason: Supplier<String>)
    fun release(reason: Supplier<String>)
    val isExpired: Boolean
    fun setExpired(reason: Supplier<String>)
    val connectionState: ConnectionState

    /** Is our application in Foreground now?  */
    var isInForeground: Boolean
    val notifier: Notifier
    fun notify(data: NotificationData)
    fun clearNotifications(timeline: Timeline)
    val isTestRun: Boolean get() = false

    val httpConnectionStub: HttpConnection? get() = null

    override val isEmpty: Boolean get() = this === MyContextEmpty.EMPTY

    val isEmptyOrExpired: Boolean get() = isEmpty || isExpired

    val isPreferencesChanged: Boolean
        get() = initialized && preferencesChangeTime != MyPreferences.getPreferencesChangeTime()

    val appWidgetIds: List<Int>
        get() = if (isEmptyOrExpired) emptyList()
        else AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, MyAppWidgetProvider::class.java))
            .asList()
}
