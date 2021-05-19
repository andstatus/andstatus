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
import org.andstatus.app.service.CommandQueue
import org.andstatus.app.service.ConnectionState
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
    private var state: MyContextState = MyContextState.EMPTY
    private val baseContext: Context
    private val context: Context
    private val initializedBy: String

    /**
     * When preferences, loaded into this class, were changed
     */
    @Volatile
    private var preferencesChangeTime: Long = 0

    @Volatile
    private var db: DatabaseHolder? = null

    @Volatile
    private var lastDatabaseError: String = ""
    private val users: CachedUsersAndActors = CachedUsersAndActors.newEmpty(this)
    private val accounts: MyAccounts = MyAccounts.newEmpty(this)
    private val origins: PersistentOrigins = PersistentOrigins.newEmpty(this)
    private val timelines: PersistentTimelines = PersistentTimelines.newEmpty(this)
    private val commandQueue: CommandQueue = CommandQueue(this)

    @Volatile
    public final override var isExpired = false
        private set

    private val notifier: Notifier = Notifier(this)

    private fun calcBaseContextToUse(parent: MyContext, contextIn: Context?): Context {
        val context: Context = contextIn ?: parent.let { if (it.nonEmpty) it.context() else null }
                ?: throw IllegalArgumentException("parent:$parent, contextIn:$contextIn")
        val contextToUse: Context = context.applicationContext
        // TODO: Maybe we need to determine if the context is compatible, using some Interface...
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
                ImageCaches.initialize(context())
                commandQueue.load()
                MyContextState.READY
            }
            else -> {
            }
        }
        if (state == MyContextState.READY) {
            notifier.initialize()
        }
        return this
    }

    private fun initializeDatabase(createApplicationData: Boolean) {
        requireNotNull(baseContext)

        val stopWatch: StopWatch = StopWatch.createStarted()
        val method = "initializeDatabase"
        val newDb = DatabaseHolder(baseContext, createApplicationData)
        try {
            state = newDb.checkState()
            if (state() == MyContextState.DATABASE_READY && MyStorage.isApplicationDataCreated().untrue) {
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
        if (state() == MyContextState.DATABASE_READY) {
            db = newDb
        }
        MyLog.i(this, "databaseInitializedMs: " + stopWatch.time + "; " + state)
    }

    private fun logDatabaseError(method: String, e: Exception) {
        MyLog.w(this, "$method; Error", e)
        e.message?.let { lastDatabaseError = it }
    }

    override fun getLastDatabaseError(): String {
        return lastDatabaseError
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
                "; " + accounts().size() + " accounts, " +
                ("context=" + context.javaClass.name)
    }

    override fun newCreator(context: Context, initializer: Any?): MyContext {
        return MyContextImpl(MyContextEmpty.EMPTY, context, initializer)
    }

    override fun initialized(): Boolean {
        return state != MyContextState.EMPTY
    }

    override fun isReady(): Boolean {
        return state == MyContextState.READY && !DatabaseConverterController.isUpgrading()
    }

    override fun state(): MyContextState {
        return state
    }

    override fun context(): Context {
        return context
    }

    override fun baseContext(): Context {
        return baseContext
    }

    override fun preferencesChangeTime(): Long {
        return preferencesChangeTime
    }

    override fun getDatabase(): SQLiteDatabase? {
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
        commandQueue.save()
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

    override fun users(): CachedUsersAndActors {
        return users
    }

    override fun accounts(): MyAccounts {
        return accounts
    }

    override fun setExpired(reason: Supplier<String>) {
        MyLog.v(this) { "setExpired " + reason.get() }
        isExpired = true
        state = MyContextState.EXPIRED
    }

    override fun origins(): PersistentOrigins {
        return origins
    }

    override fun timelines(): PersistentTimelines {
        return timelines
    }

    override fun queues(): CommandQueue {
        return commandQueue
    }

    override fun getConnectionState(): ConnectionState {
        return UriUtils.getConnectionState(context)
    }

    override fun isInForeground(): Boolean {
        return if (!inForeground
                && !RelativeTime.moreSecondsAgoThan(inForegroundChangedAt,
                        CONSIDER_IN_BACKGROUND_AFTER_SECONDS)) {
            true
        } else inForeground
    }

    override fun setInForeground(inForeground: Boolean) {
        setInForegroundStatic(inForeground)
    }

    override fun getNotifier(): Notifier {
        return notifier
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

        @Volatile
        private var inForegroundChangedAt: Long = 0
        private const val CONSIDER_IN_BACKGROUND_AFTER_SECONDS: Long = 20

        /** To avoid "Write to static field" warning
         * On static members in interfaces: http://stackoverflow.com/questions/512877/why-cant-i-define-a-static-method-in-a-java-interface
         */
        private fun setInForegroundStatic(inForeground: Boolean) {
            if (Companion.inForeground != inForeground) {
                inForegroundChangedAt = System.currentTimeMillis()
            }
            Companion.inForeground = inForeground
        }
    }

    init {
        initializedBy = MyStringBuilder.objToTag(initializer)
        baseContext = calcBaseContextToUse(parent, context)
        this.context = MyLocale.onAttachBaseContext(baseContext)
        if (parent.nonEmpty) {
            lastDatabaseError = parent.getLastDatabaseError()
        }
    }
}