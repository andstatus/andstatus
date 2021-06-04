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

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.net.http.HttpConnection
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.notification.Notifier
import org.andstatus.app.origin.PersistentOrigins
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.service.ConnectionState
import org.andstatus.app.timeline.meta.PersistentTimelines
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.user.CachedUsersAndActors
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.IsEmpty
import java.util.function.Supplier

interface MyContext : IdentifiableInstance, IsEmpty {
    fun newInitialized(initializer: Any): MyContext
    fun initialized(): Boolean
    fun isReady(): Boolean
    fun state(): MyContextState
    val context: Context
    fun baseContext(): Context
    fun preferencesChangeTime(): Long
    fun getLastDatabaseError(): String
    fun getDatabase(): SQLiteDatabase?
    fun users(): CachedUsersAndActors
    fun accounts(): MyAccounts
    fun origins(): PersistentOrigins
    fun timelines(): PersistentTimelines
    fun queues(): CommandQueue
    fun putAssertionData(key: String, contentValues: ContentValues) {}
    fun save(reason: Supplier<String>)
    fun release(reason: Supplier<String>)
    val isExpired: Boolean
    fun setExpired(reason: Supplier<String>)
    fun getConnectionState(): ConnectionState

    /** Is our application in Foreground now?  */
    fun isInForeground(): Boolean
    fun setInForeground(inForeground: Boolean)
    fun getNotifier(): Notifier
    fun notify(data: NotificationData)
    fun clearNotifications(timeline: Timeline)
    fun isTestRun(): Boolean {
        return false
    }

    fun getHttpConnectionMock(): HttpConnection? {
        return null
    }

    override val isEmpty: Boolean get() = this === MyContextEmpty.EMPTY

    fun isEmptyOrExpired(): Boolean {
        return isEmpty || isExpired
    }

    fun isPreferencesChanged(): Boolean {
        return initialized() && preferencesChangeTime() != MyPreferences.getPreferencesChangeTime()
    }

}
