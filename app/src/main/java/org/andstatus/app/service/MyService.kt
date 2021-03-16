/* 
 * Copyright (c) 2011-2015 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import net.jcip.annotations.GuardedBy
import org.andstatus.app.MyAction
import org.andstatus.app.appwidget.AppWidgets
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextEmpty
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.social.Actor
import org.andstatus.app.notification.NotificationData
import org.andstatus.app.notification.NotificationEventType
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.os.MyAsyncTask.PoolEnum
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * This service asynchronously executes commands, mostly related to communication
 * between this Android Device and Social networks.
 */
class MyService : Service(), IdentifiableInstance {
    override val instanceId = InstanceId.next()

    @Volatile
    var myContext: MyContext = MyContextEmpty.EMPTY

    @Volatile
    private var startedForegrounLastTime: Long = 0

    /** No way back  */
    @Volatile
    private var mForcedToStop = false

    @Volatile
    var latestActivityTime: Long = 0

    /** Flag to control the Service state persistence  */
    private val initialized: AtomicBoolean = AtomicBoolean(false)

    @Volatile
    private var initializedTime: Long = 0

    /** We are stopping this service  */
    private val isStopping: AtomicBoolean = AtomicBoolean(false)
    val executors: QueueExecutors = QueueExecutors(this)
    private val heartBeatRef: AtomicReference<HeartBeat> = AtomicReference()

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
        MyLog.v(TAG) { "MyService $instanceId created" }
        myContext =  MyContextHolder.myContextHolder.initialize(this).getNow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MyLog.v(TAG) { "MyService $instanceId onStartCommand: startid=$startId" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground()
        }
        receiveCommand(intent, startId)
        return START_NOT_STICKY
    }

    /** See https://stackoverflow.com/questions/44425584/context-startforegroundservice-did-not-then-call-service-startforeground  */
    private fun startForeground() {
        val currentTimeMillis = System.currentTimeMillis()
        if (Math.abs(currentTimeMillis - startedForegrounLastTime) < 1000) return
        startedForegrounLastTime = currentTimeMillis
        val data = NotificationData(NotificationEventType.SERVICE_RUNNING, Actor.EMPTY, currentTimeMillis)
        myContext.getNotifier().createNotificationChannel(data)
        startForeground(NotificationEventType.SERVICE_RUNNING.notificationId(), myContext.getNotifier().getAndroidNotification(data))
    }

    @GuardedBy("serviceStateLock")
    private val intentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(arg0: Context?, intent: Intent?) {
            MyLog.v(TAG) { "MyService " + instanceId + " onReceive " + intent.toString() }
            receiveCommand(intent, 0)
        }
    }

    private fun receiveCommand(intent: Intent?, startId: Int) {
        val commandData: CommandData = CommandData.fromIntent(myContext, intent)
        when (commandData.command) {
            CommandEnum.STOP_SERVICE -> {
                MyLog.v(TAG) { "MyService " + instanceId + " command " + commandData.command + " received" }
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
        return mForcedToStop ||  MyContextHolder.myContextHolder.isShuttingDown()
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
        if (!myContext.isReady()) {
            myContext =  MyContextHolder.myContextHolder.initialize(this).getBlocking()
            if (!myContext.isReady()) return
        }
        if (initialized.compareAndSet(false, true)) {
            initializedTime = System.currentTimeMillis()
            registerReceiver(intentReceiver, IntentFilter(MyAction.EXECUTE_COMMAND.action))
            if (widgetsInitialized.compareAndSet(false, true)) {
                AppWidgets.of(myContext).updateViews()
            }
            reviveHeartBeat()
            MyLog.d(TAG, "MyService $instanceId initialized")
            MyServiceEventsBroadcaster.newInstance(myContext, getServiceState()).broadcast()
        }
    }

    fun reviveHeartBeat() {
        val previous = heartBeatRef.get()
        var replace = previous == null
        if (!replace && !previous.isReallyWorking()) {
            replace = true
        }
        if (replace) {
            val current = HeartBeat(this)
            if (heartBeatRef.compareAndSet(previous, current)) {
                previous?.cancelLogged(true)
                AsyncTaskLauncher.execute(TAG, current).onFailure { t: Throwable? ->
                    heartBeatRef.compareAndSet(current, null)
                    MyLog.w(TAG, "MyService $instanceId Failed to revive heartbeat", t)
                }
            }
        }
    }

    fun startStopExecution() {
        if (!initialized.get()) return
        if (isStopping.get() || !myContext.isReady() || isForcedToStop() || (!isAnythingToExecuteNow()
                        && RelativeTime.moreSecondsAgoThan(latestActivityTime, STOP_ON_INACTIVITY_AFTER_SECONDS))) {
            stopDelayed(false)
        } else if (isAnythingToExecuteNow()) {
            startExecution()
        }
    }

    private fun startExecution() {
        acquireWakeLock()
        try {
            executors.ensureExecutorsStarted()
        } catch (e: Exception) {
            MyLog.i(TAG, "Couldn't start executor", e)
            executors.stopExecutor(true)
            releaseWakeLock()
        }
    }

    private fun acquireWakeLock() {
        val previous = wakeLockRef.get()
        if (previous == null) {
            MyLog.v(TAG) { "MyService $instanceId acquiring wakelock" }
            val pm = getSystemService(POWER_SERVICE) as PowerManager ?
            if (pm == null) {
                MyLog.w(TAG, "No Power Manager ???")
                return
            }
            val current = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, MyService::class.java.name)
            if (current != null && wakeLockRef.compareAndSet(previous, current)) {
                current.acquire(60*1000L /*1 minute*/)
            }
        }
    }

    private fun isAnythingToExecuteNow(): Boolean {
        return myContext.queues().isAnythingToExecuteNow() || executors.isReallyWorking()
    }

    override fun onDestroy() {
        if (initialized.get()) {
            mForcedToStop = true
            MyLog.v(TAG) { "MyService $instanceId onDestroy" }
            stopDelayed(true)
        }
        MyLog.v(TAG) { "MyService $instanceId destroyed" }
        MyLog.setNextLogFileName()
    }

    /**
     * Notify background processes that the service is stopping.
     * Stop if background processes has finished.
     * Persist everything that we'll need on next Service creation and free resources
     */
    private fun stopDelayed(forceNow: Boolean) {
        if (isStopping.compareAndSet(false, true)) {
            MyLog.v(TAG) { "MyService " + instanceId + " stopping" + if (forceNow) ", forced" else "" }
        }
        startedForegrounLastTime = 0
        if (!executors.stopExecutor(forceNow) && !forceNow) {
            return
        }
        unInitialize()
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
                .setEvent(MyServiceEvent.ON_STOP).broadcast()
    }

    private fun unInitialize() {
        if (!initialized.compareAndSet(true, false)) return
        try {
            unregisterReceiver(intentReceiver)
        } catch (e: Exception) {
            MyLog.d(TAG, "MyService $instanceId on unregisterReceiver", e)
        }
        mForcedToStop = false
        val heartBeat = heartBeatRef.get()
        if (heartBeat != null && heartBeatRef.compareAndSet(heartBeat, null)) {
            heartBeat.cancelLogged(true)
        }
        AsyncTaskLauncher.cancelPoolTasks(PoolEnum.SYNC)
        releaseWakeLock()
        stopSelf()
        myContext.getNotifier().clearAndroidNotification(NotificationEventType.SERVICE_RUNNING)
        MyLog.i(TAG, "MyService " + instanceId + " stopped, myServiceWorkMs:" + (System.currentTimeMillis() - initializedTime))
        isStopping.set(false)
    }

    private fun releaseWakeLock() {
        val wakeLock = wakeLockRef.get()
        if (wakeLock != null && wakeLockRef.compareAndSet(wakeLock, null)) {
            MyLog.v(TAG) { "MyService $instanceId releasing wakelock" }
            wakeLock.release()
        }
    }

    override fun instanceTag(): String {
        return TAG
    }

    private class HeartBeat constructor(myService: MyService) : MyAsyncTask<Void?, Long?, Void?>(TAG, PoolEnum.SYNC) {
        private val myServiceRef: WeakReference<MyService>

        @Volatile
        private var previousBeat = createdAt

        @Volatile
        private var mIteration: Long = 0
        override fun doInBackground2(aVoid: Void?): Void? {
            MyLog.v(this) { "Started" }
            var breakReason = ""
            for (iteration in 1..9999) {
                val myService = myServiceRef.get()
                if (myService == null) {
                    breakReason = "No reference to MyService"
                    break
                }
                val heartBeat = myService.heartBeatRef.get()
                if (heartBeat != null && heartBeat !== this && heartBeat.isReallyWorking()) {
                    breakReason = "Other instance found: $heartBeat"
                    break
                }
                if (isCancelled) {
                    breakReason = "Cancelled"
                    break
                }
                if (DbUtils.waitMs("HeartBeatSleeping",
                                Math.toIntExact(TimeUnit.SECONDS.toMillis(HEARTBEAT_PERIOD_SECONDS)))) {
                    breakReason = "InterruptedException"
                    break
                }
                if (!myService.initialized.get()) {
                    breakReason = "Not initialized"
                    break
                }
                publishProgress(iteration.toLong())
            }
            val breakReasonVal = breakReason
            MyLog.v(this) { "Ended $breakReasonVal; $this" }
            val myService = myServiceRef.get()
            myService?.heartBeatRef?.compareAndSet(this, null)
            return null
        }

        override fun onProgressUpdate(vararg values: Long?) {
            mIteration = values[0] ?: 0
            previousBeat = MyLog.uniqueCurrentTimeMS()
            if (MyLog.isVerboseEnabled()) {
                MyLog.v(this) { "onProgressUpdate; $this" }
            }
            if (MyLog.isDebugEnabled() && RelativeTime.moreSecondsAgoThan(createdAt,
                            QueueExecutor.MAX_EXECUTION_TIME_SECONDS)) {
                MyLog.d(this, AsyncTaskLauncher.threadPoolInfo())
            }
            val myService = myServiceRef.get()
            myService?.startStopExecution()
        }

        override fun toString(): String {
            return instanceTag() + "; " + super.toString()
        }

        override fun instanceTag(): String {
            return super.instanceTag() + "-it" + mIteration
        }

        override fun classTag(): String {
            return TAG
        }

        override fun isReallyWorking(): Boolean {
            return needsBackgroundWork() && !RelativeTime.wasButMoreSecondsAgoThan(previousBeat, HEARTBEAT_PERIOD_SECONDS * 2)
        }

        companion object {
            private val TAG: String = "HeartBeat"
            private const val HEARTBEAT_PERIOD_SECONDS: Long = 11
        }

        init {
            myServiceRef = WeakReference(myService)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private val TAG: String = MyService::class.java.simpleName
        private const val STOP_ON_INACTIVITY_AFTER_SECONDS: Long = 10
        private val widgetsInitialized: AtomicBoolean = AtomicBoolean(false)
    }
}