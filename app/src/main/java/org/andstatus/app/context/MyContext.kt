/*
 * Copyright (C) 2013-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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
import org.andstatus.app.database.DatabaseHolder
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
import java.util.function.Supplier

interface MyContext : IdentifiableInstance {
    open fun newInitialized(initializer: Any?): MyContext?
    open fun newCreator(context: Context?, initializer: Any?): MyContext?
    open fun initialized(): Boolean
    open fun isReady(): Boolean
    open fun state(): MyContextState?
    open fun context(): Context?
    open fun baseContext(): Context?
    open fun preferencesChangeTime(): Long
    open fun getMyDatabase(): DatabaseHolder?
    open fun getLastDatabaseError(): String?
    open fun getDatabase(): SQLiteDatabase?
    open fun users(): CachedUsersAndActors
    open fun accounts(): MyAccounts
    open fun origins(): PersistentOrigins
    open fun timelines(): PersistentTimelines
    open fun queues(): CommandQueue
    fun putAssertionData(key: String, contentValues: ContentValues) {}
    open fun save(reason: Supplier<String?>?)
    open fun release(reason: Supplier<String?>?)
    open fun isExpired(): Boolean
    open fun setExpired(reason: Supplier<String?>?)
    open fun getConnectionState(): ConnectionState?

    /** Is our application in Foreground now?  */
    open fun isInForeground(): Boolean
    open fun setInForeground(inForeground: Boolean)
    open fun getNotifier(): Notifier?
    open fun notify(data: NotificationData?)
    open fun clearNotifications(timeline: Timeline)
    fun isTestRun(): Boolean {
        return false
    }

    fun getHttpConnectionMock(): HttpConnection? {
        return null
    }

    fun isEmpty(): Boolean {
        return context() == null
    }

    fun isEmptyOrExpired(): Boolean {
        return isEmpty() || isExpired()
    }

    fun isPreferencesChanged(): Boolean {
        return initialized() && preferencesChangeTime() != MyPreferences.getPreferencesChangeTime()
    }

    companion object {
        val EMPTY: MyContext? = MyContextImpl(null, null, "static")
    }
}