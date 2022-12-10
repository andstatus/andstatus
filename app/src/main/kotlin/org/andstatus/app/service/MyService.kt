/*
 * Copyright (c) 2011-2022 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.andstatus.app.MyAction
import org.andstatus.app.appwidget.AppWidgets
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextEmpty
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.Identified
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * This service asynchronously executes commands, mostly related to communication
 * between this Android Device and Social networks.
 */
class MyService(
    private val identifiable: Identifiable = Identified(MyService::class)
) : Service(), Identifiable by identifiable {

    @Volatile
    var myContext: MyContext = MyContextEmpty.EMPTY

    /** No way back  */
    @Volatile
    private var mForcedToStop = false

    @Volatile
    private var latestActivityTime: Long = 0

    /** Flag to control the Service state persistence  */
    val initialized: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var initializedTime: Long = 0

    /** We are stopping this service  */
    private val isStopping: AtomicBoolean = AtomicBoolean(false)
    val executors: QueueExecutors = QueueExecutors(this)
    val heartBeatRef: AtomicReference<MyServiceHeartBeat> = AtomicReference()

    /**
     * The reference to the wake lock used to keep the CPU from stopping during
     * background operations.
     */
    private val wakeLockRef: AtomicReference<WakeLock> = AtomicReference()
    private fun getServiceState(): MyServiceState {
        return if (initialized.get()) {
            if (isStopping.get()) {
                MyServiceState.STOPPING
            } else {
                MyServiceState.RUNNING
            }
        } else MyServiceState.STOPPED
    }

    fun isStopping(): Boolean {
        return isStopping.get()
    }

    override fun onCreate() {
        MyLog.v(this) { "Created" }
        myContext = MyContextHolder.myContextHolder.initialize(this).getNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        receiveCommand(intent, startId)
        return START_NOT_STICKY
    }

    private val intentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context?, intent: Intent?) {
            receiveCommand(intent, null)
        }
    }

    private fun receiveCommand(intent: Intent?, startId: Int?) {
        val commandData: CommandData = CommandData.fromIntent(myContext, intent)
        MyLog.v(this) {
            "receiveCommand; ${commandData.command}" +
                    (intent?.let { ", intent:$intent" } ?: "") +
                    (startId?.let { ", startId:$it" } ?: "")
        }
        when (commandData.command) {
            CommandEnum.STOP_SERVICE -> {
                stopDelayed(false)
            }
            CommandEnum.BROADCAST_SERVICE_STATE -> {
                if (isStopping.get()) {
                    stopDelayed(false)
                }
                broadcastAfterExecutingCommand(commandData)
            }
            else -> {
                latestActivityTime = System.currentTimeMillis()
                if (isForcedToStop()) {
                    stopDelayed(true)
                } else {
                    ensureInitialized()
                    startStopExecution()
                }
            }
        }
    }

    private fun isForcedToStop(): Boolean {
        return mForcedToStop || MyContextHolder.myContextHolder.isShuttingDown()
    }

    fun broadcastBeforeExecutingCommand(commandData: CommandData) {
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
            .setCommandData(commandData).setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast()
    }

    fun broadcastAfterExecutingCommand(commandData: CommandData) {
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
            .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast()
    }

    fun ensureInitialized() {
        if (initialized.get() || isStopping.get()) return
        if (!myContext.isReady) {
            myContext = MyContextHolder.myContextHolder.initialize(this).getBlocking()
            if (!myContext.isReady) return
        }
        if (initialized.compareAndSet(false, true)) {
            initializedTime = System.currentTimeMillis()
            registerReceiver(intentReceiver, IntentFilter(MyAction.EXECUTE_COMMAND.action))
            if (widgetsInitialized.compareAndSet(false, true)) {
                AppWidgets.of(myContext).updateViews()
            }
            reviveHeartBeat()
            MyLog.d(this, "Initialized")
            MyServiceEventsBroadcaster.newInstance(myContext, getServiceState()).broadcast()
        }
    }

    fun onExecutorFinished() {
        latestActivityTime = System.currentTimeMillis()
        reviveHeartBeat()
        startStopExecution()
    }

    fun reviveHeartBeat() {
        val previous = heartBeatRef.get()
        var replace = previous == null
        if (!replace && !previous.isReallyWorking) {
            replace = true
        }
        if (replace) {
            val current = MyServiceHeartBeat(this)
            if (heartBeatRef.compareAndSet(previous, current)) {
                previous?.let {
                    MyLog.v(this) { "(revive heartbeat) Cancelling task: $it" }
                    it.cancel()
                }
                current.execute(instanceTag, Unit)
                    .onFailure { t: Throwable? ->
                        heartBeatRef.compareAndSet(current, null)
                        MyLog.w(this, "Failed to revive heartbeat", t)
                    }
            }
        }
    }

    fun startStopExecution() {
        if (!initialized.get()) return

        CoroutineScope(Dispatchers.Default).launch {
            val shouldStop = isStopping.get() || !myContext.isReady || isForcedToStop()
            val needToExecute = !shouldStop && isAnythingToExecuteNow()
            MyLog.v(this@MyService) {
                val method = this@MyService::startStopExecution.name
                "$method; shouldStop:$shouldStop, needToExecute:$needToExecute"
            }
            if (shouldStop || (!needToExecute
                        && RelativeTime.moreSecondsAgoThan(latestActivityTime, STOP_ON_INACTIVITY_AFTER_SECONDS))
            ) {
                stopDelayed(false)
            } else if (needToExecute) startExecution()
        }
    }

    private suspend fun startExecution() {
        acquireWakeLock()
        try {
            executors.ensureExecutorsStarted()
        } catch (e: Exception) {
            MyLog.i(this, "Couldn't start executor", e)
            executors.stopAll(true)
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        val previous = wakeLockRef.get()
        if (previous == null) {
            MyLog.v(this) { "Acquiring wakelock" }
            val pm = getSystemService(POWER_SERVICE) as PowerManager?
            if (pm == null) {
                MyLog.w(this, "No Power Manager ???")
                return
            }
            val current = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, MyService::class.java.name)
            if (current != null && wakeLockRef.compareAndSet(previous, current)) {
                current.acquire(60 * 1000L /*1 minute*/)
            }
        }
    }

    private suspend fun isAnythingToExecuteNow(): Boolean =
        myContext.queues.isAnythingToExecuteNow() || executors.isReallyWorking()

    override fun onDestroy() {
        if (initialized.get()) {
            mForcedToStop = true
            MyLog.v(this) { "onDestroy" }
            stopDelayed(true)
        }
        MyLog.v(this) { "Destroyed" }
        MyLog.setNextLogFileName()
    }

    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     */
    private fun stopDelayed(forceNow: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            if (isStopping.compareAndSet(false, true)) {
                MyLog.v(this) { "Stopping" + if (forceNow) ", forced" else "" }
            }
            if (!executors.stopAll(forceNow) && !forceNow) {
                // TODO
            } else {
                unInitialize()
                MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
                    .setEvent(MyServiceEvent.ON_STOP).broadcast()
            }
        }
    }

    private fun unInitialize() {
        if (!initialized.compareAndSet(true, false)) return
        try {
            unregisterReceiver(intentReceiver)
        } catch (e: Exception) {
            MyLog.d(this, "on unregisterReceiver", e)
        }
        mForcedToStop = false
        val heartBeat = heartBeatRef.get()
        if (heartBeat != null && heartBeatRef.compareAndSet(heartBeat, null)) {
            MyLog.v(this) { "(unInit) Cancelling task: $heartBeat" }
            heartBeat.cancel()
        }
        AsyncTaskLauncher.cancelPoolTasks(AsyncEnum.SYNC)
        releaseWakeLock()
        stopSelf()
        myContext.notifier.clearAndroidNotification(NotificationEventType.SERVICE_RUNNING)
        MyLog.i(this, "Stopped, myServiceWorkMs:" + (System.currentTimeMillis() - initializedTime))
        isStopping.set(false)
    }

    private fun releaseWakeLock() {
        val wakeLock = wakeLockRef.get()
        if (wakeLock != null && wakeLockRef.compareAndSet(wakeLock, null)) {
            if (wakeLock.isHeld) {
                MyLog.v(this) { "Releasing wakelock: $wakeLock" }
                wakeLock.release()
            } else {
                MyLog.v(this) { "Wakelock is not held: $wakeLock" }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val STOP_ON_INACTIVITY_AFTER_SECONDS: Long = 10
        private val widgetsInitialized: AtomicBoolean = AtomicBoolean(false)
    }
}
