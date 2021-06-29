/*
 * Copyright (C) 2014-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.database.sqlite.SQLiteException
import android.os.Environment
import org.andstatus.app.ClassInApplicationPackage
import org.andstatus.app.FirstActivity
import org.andstatus.app.account.MyAccounts
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.database.DatabaseHolder
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.notification.Notifier
import org.andstatus.app.origin.PersistentOrigins
import org.andstatus.app.service.CommandData
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.service.ConnectionState
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.timeline.meta.PersistentTimelines
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.user.CachedUsersAndActors
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.Permissions
import org.andstatus.app.util.Permissions.PermissionType
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.SharedPreferencesUtil
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.UriUtils
import java.util.function.Supplier

/**
 * Contains global state of the application
 * The objects are effectively immutable
 * @author yvolk@yurivolkov.com
 */
open class MyContextImpl internal constructor(parent: MyContext, context: Context, initializer: Any?) : MyContext {
    override val instanceId = InstanceId.next()

    @Volatile
    final override var state: MyContextState = MyContextState.EMPTY
        private set

    private val initializedBy: String = MyStringBuilder.objToTag(initializer)
    override val baseContext: Context = calcBaseContextToUse(parent, context)
    override val context: Context = MyLocale.onAttachBaseContext(baseContext)

    /**
     * When preferences, loaded into this class, were changed
     */
    @Volatile
    final override var preferencesChangeTime: Long = 0
        private set

    @Volatile
    private var db: DatabaseHolder? = null

    @Volatile
    final override var lastDatabaseError: String = if (parent.nonEmpty) parent.lastDatabaseError else ""
        private set
    override val users: CachedUsersAndActors = CachedUsersAndActors.newEmpty(this)
    override val accounts: MyAccounts = MyAccounts.newEmpty(this)
    override val origins: PersistentOrigins = PersistentOrigins.newEmpty(this)
    override val timelines: PersistentTimelines = PersistentTimelines.newEmpty(this)
    override val queues: CommandQueue = CommandQueue(this)

    @Volatile
    public final override var isExpired = false
        private set

    override val notifier: Notifier = Notifier(this)

    private fun calcBaseContextToUse(parent: MyContext, contextIn: Context): Context {
        val contextToUse: Context = contextIn.applicationContext
        // Maybe we need to determine if the context is compatible, using some Interface...
        // ...but we don't have any yet.
        if (!contextToUse.javaClass.name.contains(ClassInApplicationPackage.PACKAGE_NAME)) {
            throw IllegalArgumentException("parent:$parent, Incompatible context: " + contextToUse.javaClass.name)
        }
        return contextToUse
    }

    override fun newInitialized(initializer: Any): MyContext {
        return MyContextImpl(this, context, initializer).initialize()
    }

    fun initialize(): MyContext {
        val stopWatch: StopWatch = StopWatch.createStarted()
        MyLog.i(this, "Starting initialization by $initializedBy")
        val myContext: MyContext = initializeInternal(initializedBy)
        MyLog.i(this, "myContextInitializedMs:" + stopWatch.time + "; "
                + state + " by " + initializedBy)
        return myContext
    }

    private fun initializeInternal(initializer: Any?): MyContextImpl {
        val method = "initialize"
        if (!Permissions.checkPermission(context, PermissionType.GET_ACCOUNTS)) {
            state = MyContextState.NO_PERMISSIONS
            return this
        }
        val createApplicationData = MyStorage.isApplicationDataCreated().untrue
        if (createApplicationData) {
            val context2 = if (initializer is Context) initializer else context
            if (!FirstActivity.setDefaultValues(context2)) {
                setExpired { "No default values yet" }
                return this
            }
            MyLog.i(this, "$method; Creating application data")
            tryToSetExternalStorageOnDataCreation()
        }
        preferencesChangeTime = MyPreferences.getPreferencesChangeTime()
        initializeDatabase(createApplicationData)
        when (state) {
            MyContextState.DATABASE_READY -> state = if (!origins.initialize()) {
                MyContextState.DATABASE_UNAVAILABLE
            } else if ( MyContextHolder.myContextHolder.isOnRestore()) {
                MyContextState.RESTORING
            } else {
                users.initialize()
                accounts.initialize()
                timelines.initialize()
                ImageCaches.initialize(context)
                this.queues.load()
                MyContextState.READY
            }
            else -> {
            }
        }
        if (state == MyContextState.READY) {
            notifier.initialize()
            MyServiceManager.sendCommand(CommandData.EMPTY)
        }
        return this
    }

    private fun initializeDatabase(createApplicationData: Boolean) {
        val stopWatch: StopWatch = StopWatch.createStarted()
        val method = "initializeDatabase"
        val newDb = DatabaseHolder(baseContext, createApplicationData)
        try {
            state = newDb.checkState()
            if (state == MyContextState.DATABASE_READY && MyStorage.isApplicationDataCreated().untrue) {
                state = MyContextState.ERROR
            }
        } catch (e: SQLiteException) {
            logDatabaseError(method, e)
            state = MyContextState.ERROR
            newDb.close()
            db = null
        } catch (e: IllegalStateException) {
            logDatabaseError(method, e)
            state = MyContextState.ERROR
            newDb.close()
            db = null
        }
        if (state == MyContextState.DATABASE_READY) {
            db = newDb
        }
        MyLog.i(this, "databaseInitializedMs: " + stopWatch.time + "; " + state)
    }

    private fun logDatabaseError(method: String, e: Exception) {
        MyLog.w(this, "$method; Error", e)
        e.message?.let { lastDatabaseError = it }
    }

    private fun tryToSetExternalStorageOnDataCreation() {
        val useExternalStorage = (!Environment.isExternalStorageEmulated()
                && MyStorage.isWritableExternalStorageAvailable(null))
        MyLog.i(this, "External storage is " + (if (useExternalStorage) "" else "not") + " used")
        SharedPreferencesUtil.putBoolean(MyPreferences.KEY_USE_EXTERNAL_STORAGE, useExternalStorage)
    }

    override fun toString(): String {
        return instanceTag() + " by " + initializedBy + "; state=" + state +
                (if (this.isExpired) "; expired" else "") +
                "; " + accounts.size() + " accounts, " +
                ("context=" + context.javaClass.name)
    }

    override val initialized: Boolean get() = state != MyContextState.EMPTY

    override val isReady: Boolean get() = state == MyContextState.READY && !DatabaseConverterController.isUpgrading()

    override val database: SQLiteDatabase?
        get() {
            if (db == null || this.isExpired) {
                return null
            }
            try {
                return db?.getWritableDatabase()
            } catch (e: Exception) {
                MyLog.e(this, "getDatabase", e)
            }
            return null
        }

    override fun save(reason: Supplier<String>) {
        queues.save()
    }

    /**
     * 2019-01-16 After getting not only (usual previously) errors "A SQLiteConnection object for database ... was leaked!"
     * but also "SQLiteException: no such table" and "Failed to open database" in Android 9
     * and reading https://stackoverflow.com/questions/50476782/android-p-sqlite-no-such-table-error-after-copying-database-from-assets
     * and https://stackoverflow.com/questions/4557154/android-sqlite-db-when-to-close?noredirect=1&lq=1
     * I decided to db.close on every context release in order to have new instance for each MyContext  */
    override fun release(reason: Supplier<String>) {
        setExpired { "Release " + reason.get() }
        try {
            db?.close()
        } catch (e: Exception) {
            MyLog.d(this, "db.close()", e)
        }
    }

    override fun setExpired(reason: Supplier<String>) {
        MyLog.v(this) { "setExpired " + reason.get() }
        isExpired = true
        state = MyContextState.EXPIRED
    }

    override val connectionState: ConnectionState get() = UriUtils.getConnectionState(context)

    override var isInForeground: Boolean
      get() = inForeground
      set(value) {
          inForeground = value
      }

    override fun notify(data: NotificationData) {
        notifier.notifyAndroid(data)
    }

    override fun clearNotifications(timeline: Timeline) {
        notifier.clear(timeline)
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = MyContextImpl::class.java.simpleName

        @Volatile
        private var inForeground = false
            get() = if (!field &&
                !RelativeTime.moreSecondsAgoThan(inForegroundChangedAt, CONSIDER_IN_BACKGROUND_AFTER_SECONDS)
            ) true
            else field
            private set(value) {
                if (field != value) {
                    inForegroundChangedAt = System.currentTimeMillis()
                    field = value
                }
            }

        @Volatile
        private var inForegroundChangedAt: Long = 0
        private const val CONSIDER_IN_BACKGROUND_AFTER_SECONDS: Long = 20
    }
}
