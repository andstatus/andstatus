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

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * This service asynchronously executes commands, mostly related to communication
 * between this Android Device and Social networks.
 */
class MyService(
    private val identifiable: Identifiable = Identified(MyService::class)
) : JobService(), Identifiable by identifiable {

    @Volatile
    private var jobParameters: JobParameters? = null
    private val needToRescheduleJob: AtomicBoolean = AtomicBoolean(false)

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
    private val succeededCommands: AtomicInteger = AtomicInteger()
    private val failedCommands: AtomicInteger = AtomicInteger()

    /** We are stopping this service  */
    private val isStopping: AtomicBoolean = AtomicBoolean(false)
    val executors: QueueExecutors = QueueExecutors(this)
    val heartBeatRef: AtomicReference<MyServiceHeartBeat> = AtomicReference()

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

    override fun onStartJob(params: JobParameters?): Boolean {
        if (isWorking) {
            MyLog.e(this, "onStartJob while working")
        } else {
            MyLog.v(this) { "onStartJob" }
        }
        if (!isStarting.compareAndSet(false, true)) return true

        if (jobParameters == null) jobParameters = params
        if (myContext.isEmpty) {
            myContext = MyContextHolder.myContextHolder.initialize(this).getBlocking()
        }
        needToRescheduleJob.set(false)
        latestActivityTime = System.currentTimeMillis()
        if (isForcedToStop()) {
            stopDelayed(true)
        } else {
            ensureInitialized()
            startStopExecution()
        }
        isStarting.set(false)
        return getServiceState() != MyServiceState.STOPPED
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        MyLog.v(this) { "onStopJob" }
        return executors.isReallyWorking()
            .also {
                needToRescheduleJob.set(it)
                stopDelayed(false)
            }
    }

    private fun isForcedToStop(): Boolean {
        return mForcedToStop || MyContextHolder.myContextHolder.isShuttingDown()
    }

    fun broadcastBeforeExecutingCommand(commandData: CommandData) {
        if (commandData.getResult().hasError()) failedCommands.incrementAndGet()
        else succeededCommands.incrementAndGet()
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
            .setCommandData(commandData).setEvent(MyServiceEvent.BEFORE_EXECUTING_COMMAND).broadcast()
    }

    fun broadcastAfterExecutingCommand(commandData: CommandData) {
        MyServiceEventsBroadcaster.newInstance(myContext, getServiceState())
            .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast()
    }

    private fun ensureInitialized() {
        if (initialized.get() || isStopping.get()) return
        if (!myContext.isReady) {
            myContext = MyContextHolder.myContextHolder.initialize(this).getBlocking()
            if (!myContext.isReady) return
        }
        if (initialized.compareAndSet(false, true)) {
            initializedTime = System.currentTimeMillis()
            if (widgetsInitialized.compareAndSet(false, true)) {
                AppWidgets.of(myContext).updateViews()
            }
            reviveHeartBeat()
            MyLog.d(this, "Initialized")
            myServiceRef.set(this)
            MyServiceEventsBroadcaster.newInstance(myContext, getServiceState()).broadcast()
        }
    }

    fun onExecutorFinished() {
        latestActivityTime = System.currentTimeMillis()
        reviveHeartBeat()
        startStopExecution()
    }

    private fun reviveHeartBeat() {
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
        try {
            executors.ensureExecutorsStarted()
        } catch (e: Exception) {
            MyLog.i(this, "Couldn't start executor", e)
            executors.stopAll(true)
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
        isStarting.set(false)
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
            mForcedToStop = false
            val heartBeat = heartBeatRef.get()
            if (heartBeat != null && heartBeatRef.compareAndSet(heartBeat, null)) {
                MyLog.v(this) { "(unInit) Cancelling task: $heartBeat" }
                heartBeat.cancel()
            }
            AsyncTaskLauncher.cancelPoolTasks(AsyncEnum.SYNC)
            stopSelf()
            myContext.notifier.clearAndroidNotification(NotificationEventType.SERVICE_RUNNING)
            MyLog.i(
                this, "Stopped, myServiceWorkMs:" + (System.currentTimeMillis() - initializedTime) +
                        ", succeeded:" + succeededCommands.get() +
                        ", failed:" + failedCommands.get()
            )
        } finally {
            succeededCommands.set(0)
            failedCommands.set(0)
            isStopping.set(false)
            myServiceRef.set(null)
            jobFinished(jobParameters, needToRescheduleJob.get())
        }
    }

    companion object {
        private val isStarting: AtomicBoolean = AtomicBoolean(false)
        private val myServiceRef: AtomicReference<MyService> = AtomicReference()
        private const val STOP_ON_INACTIVITY_AFTER_SECONDS: Long = 10
        private val widgetsInitialized: AtomicBoolean = AtomicBoolean(false)

        val serviceState: MyServiceState
            get() = myServiceRef.get()
                ?.getServiceState()
                ?: MyServiceState.STOPPED

        val isWorking: Boolean get() = isStarting.get() || myServiceRef.get() != null

        fun stopService() {
            myServiceRef.get()?.stopDelayed(false)
        }
    }
}
