/*
 * Copyright (C) 2013 - 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import io.vavr.control.Try
import org.andstatus.app.FirstActivity
import org.andstatus.app.data.converter.DatabaseConverterController
import org.andstatus.app.graphics.ImageCaches
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.Taggable
import org.andstatus.app.util.TaggedInstance
import org.andstatus.app.util.TamperingDetector
import org.andstatus.app.util.TryUtils
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Holds globally cached state of the application: [MyContext]
 * @author yvolk@yurivolkov.com
 */
class MyContextHolder private constructor(
    private val taggedInstance: TaggedInstance = TaggedInstance(MyContextHolder::class)
) : Taggable by taggedInstance {
    private val appStartedAt = SystemClock.elapsedRealtime()

    @Volatile
    var isShuttingDown = false

    // TODO: Should be one object for atomic updates. start ---
    private val contextLock: Any = Any()

    @Volatile
    private var myFutureContext: MyFutureContext = MyFutureContext.completed(MyContextEmpty.EMPTY)
    // end ---

    @Volatile
    private var onRestore = false

    @Volatile
    var executionMode = ExecutionMode.UNKNOWN
        get() {
            if (field == ExecutionMode.UNKNOWN) {
                val myContext = getNow()
                if (myContext.nonEmpty) {
                    if ("true" == Settings.System.getString(myContext.context.contentResolver, "firebase.test.lab")) {
                        // See https://firebase.google.com/docs/test-lab/android-studio
                        field = if (myContext.isTestRun) ExecutionMode.FIREBASE_TEST else ExecutionMode.ROBO_TEST
                    } else {
                        field = if (myContext.isTestRun) ExecutionMode.TEST else ExecutionMode.DEVICE
                    }
                }
            }
            return field
        }
        set(value) {
            if (field != value) {
                field = value
                if (field != ExecutionMode.DEVICE) {
                    MyLog.i(this, "Executing: " + getVersionText(getNow().context))
                }
            }
        }

    /** Immediately get currently available context, even if it's empty,
     * return previous context if current is in error */
    fun getNow(): MyContext {
        return getFuture().getNow()
    }

    /** Immediately get current state of MyContext initialization,
     * error if not completed yet or if completed with error */
    val tryCurrent: Try<MyContext> get() = getFuture().tryCurrent

    /**
     * Immediately return current or previous context or failure if it is not ready
     */
    fun tryReadyNow(): Try<MyContext> = getNow()
        .let {
            if (it.isReady) Try.success(it)
            else TryUtils.failure("No ready context: ${it.state}")
        }

    fun getBlocking(): MyContext {
        return myFutureContext.tryBlocking().getOrElse(myFutureContext.getNow())
    }

    fun getFuture(): MyFutureContext {
        return myFutureContext
    }

    /** This is mainly for stubbing / testing
     * @return true if succeeded
     */
    fun trySetCreator(contextCreatorNew: MyContext): Boolean {
        synchronized(contextLock) {
            if (!myFutureContext.future.isFinished) return false
            myFutureContext.getNow().release { "trySetCreator" }
            myFutureContext = MyFutureContext.completed(contextCreatorNew)
        }
        return true
    }

    fun needToRestartActivity(): Boolean {
        return !getFuture().isReady
    }

    fun initialize(context: Context?): MyContextHolder {
        return initializeInner(context, context, false)
    }

    /** Reinitialize in a case preferences have been changed  */
    fun initialize(context: Context?, calledBy: Any?): MyContextHolder {
        return initializeInner(context, calledBy, false)
    }

    fun initializeDuringUpgrade(upgradeRequestor: Context?): MyContextHolder {
        return initializeInner(upgradeRequestor, upgradeRequestor, true)
    }

    private fun initializeInner(context: Context?, calledBy: Any?, duringUpgrade: Boolean): MyContextHolder {
        storeContextIfNotPresent(context, calledBy)
        if (isShuttingDown) {
            MyLog.d(this, "Skipping initialization: device is shutting down (called by: $calledBy)")
        } else if (!duringUpgrade && DatabaseConverterController.isUpgrading()) {
            MyLog.d(this, "Skipping initialization: upgrade in progress (called by: $calledBy)")
        } else {
            synchronized(contextLock) {
                myFutureContext = MyFutureContext.fromPrevious(myFutureContext, calledBy, duringUpgrade)
            }
        }
        return this
    }

    fun thenStartActivity(intent: Intent?): MyContextHolder {
        return whenSuccessAsync(true) { myContext: MyContext ->
            MyFutureContext.startActivity(myContext, intent)
        }
    }

    fun thenStartApp(): MyContextHolder {
        return whenSuccessAsync(true) { myContext: MyContext ->
            FirstActivity.startApp(myContext)
        }
    }

    fun whenSuccessAsync(mainThread: Boolean, consumer: Consumer<MyContext>): MyContextHolder {
        myFutureContext.whenSuccessAsync(mainThread) { it -> consumer.accept(it) }
        return this
    }

    fun with(
        actionName: String,
        mainThread: Boolean = true,
        action: (Try<MyContext>) -> Try<Unit>
    ) = myFutureContext.with(MyContextAction(actionName, action, mainThread))

    /**
     * This allows to refer to the context even before myInitializedContext is initialized.
     * Quickly returns, providing context for the deferred initialization
     */
    fun storeContextIfNotPresent(context: Context?, calledBy: Any?): MyContextHolder {
        if (context == null || getNow().nonEmpty) return this
        synchronized(contextLock) {
            if (getNow().isEmpty) {
                val contextCreator = MyContextImpl(MyContextEmpty.EMPTY, context, calledBy)
                checkNotNull(contextCreator.baseContext) {
                    "No compatible context" + ", called by " +
                        Taggable.anyToTag(calledBy)
                }
                myFutureContext = MyFutureContext.completed(contextCreator)
            }
        }
        return this
    }

    fun upgradeIfNeeded(upgradeRequestor: Activity?) {
        if (getNow().state == MyContextState.UPGRADING && upgradeRequestor != null) {
            DatabaseConverterController.attemptToTriggerDatabaseUpgrade(upgradeRequestor)
        }
    }

    fun getSystemInfo(context: Context, showVersion: Boolean): String {
        val builder = StringBuilder()
        if (showVersion) builder.append(getVersionText(context))
        MyStringBuilder.appendWithSpace(builder, MyLog.currentDateTimeForLogLine())
        MyStringBuilder.appendWithSpace(builder, ", started")
        MyStringBuilder.appendWithSpace(
            builder, RelativeTime.getDifference(
                context, appStartedAt,
                SystemClock.elapsedRealtime()
            )
        )
        builder.append("\n")
        builder.append(ImageCaches.getCacheInfo())
        builder.append("\n")
        builder.append(AsyncTaskLauncher.threadPoolInfo)
        return builder.toString()
    }

    fun getVersionText(context: Context?): String {
        val builder = StringBuilder()
        if (context != null) {
            try {
                val pm = context.packageManager
                val pi = pm.getPackageInfo(context.packageName, 0)
                builder.append(pi.packageName + " v." + pi.versionName + " (" + pi.versionCode + ")")
            } catch (e: PackageManager.NameNotFoundException) {
                MyLog.w(this, "Unable to obtain package information", e)
            }
        }
        if (builder.isEmpty()) {
            builder.append("AndStatus v.?")
        }
        MyStringBuilder.appendWithSpace(
            builder,
            if (executionMode == ExecutionMode.DEVICE) "" else executionMode.code
        )
        MyStringBuilder.appendWithSpace(builder, TamperingDetector.getAppSignatureInfo())
        return builder.toString()
    }

    fun setOnRestore(onRestore: Boolean): MyContextHolder {
        this.onRestore = onRestore
        return this
    }

    fun isOnRestore(): Boolean {
        return onRestore
    }

    fun onShutDown() {
        isShuttingDown = true
        release { "onShutDown" }
    }

    fun release(reason: Supplier<String>) {
        synchronized(contextLock) {
            myFutureContext = myFutureContext.releaseNow(reason)
        }
    }

    companion object {
        val myContextHolder: MyContextHolder = MyContextHolder()
    }
}
