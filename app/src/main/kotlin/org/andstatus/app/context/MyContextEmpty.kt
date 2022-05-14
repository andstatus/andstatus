/*
 * Copyright (C) 2021 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.notification.Notifier
import org.andstatus.app.origin.PersistentOrigins
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.service.ConnectionState
import org.andstatus.app.timeline.meta.PersistentTimelines
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.user.CachedUsersAndActors
import java.util.function.Supplier

class MyContextEmpty: MyContext {
    override fun newInitialized(initializer: Any): MyContext = throwException()

    override val initialized: Boolean = false

    override val isReady: Boolean = false

    override val state: MyContextState = MyContextState.EMPTY

    override val context: Context
        get() = throwException()

    override val baseContext: Context get() = throwException()

    override val preferencesChangeTime: Long = 0

    override val lastDatabaseError: String = ""

    override val database: SQLiteDatabase? = null

    override val users: CachedUsersAndActors get() = throwException()

    override val accounts: MyAccounts get() = throwException()

    override val origins: PersistentOrigins get() = throwException()

    override val timelines: PersistentTimelines get() = throwException()

    override val queues: CommandQueue get() = throwException()

    override fun save(reason: Supplier<String>) {}

    override fun release(reason: Supplier<String>) {}

    override val isExpired: Boolean get() = false

    override fun setExpired(reason: Supplier<String>) {}

    override val connectionState: ConnectionState = ConnectionState.UNKNOWN

    override var isInForeground: Boolean
        get() = false
        set(_) {}

    override val notifier: Notifier get() = throwException()

    private fun throwException(): Nothing {
        throw IllegalStateException("This is empty implementation")
    }

    override fun notify(data: NotificationData) {}

    override fun clearNotifications(timeline: Timeline) {}

    override val instanceId: Long = 0L

    companion object {
        val EMPTY: MyContext by lazy {
            MyContextEmpty()
        }
    }
}
