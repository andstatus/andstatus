/* 
 * Copyright (c) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import org.andstatus.app.MyAction
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.os.AsyncUtil
import org.andstatus.app.syncadapter.SyncInitiator
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * This receiver starts and stops [MyService] and also queries its state.
 * Android system creates new instance of this type on each Intent received.
 * This is why we're keeping a state in static fields.
 */
class MyServiceManager : BroadcastReceiver(), Identifiable {
    override val instanceId = InstanceId.next()

    override fun onReceive(context: Context, intent: Intent) {
        registerReceiver(context, this)
        when (val myAction = MyAction.fromIntent(intent)) {
            MyAction.ACTION_SHUTDOWN -> {
                MyLog.d(this, "onReceive $instanceId ShutDown")
                setServiceUnavailable()
                MyContextHolder.myContextHolder.onShutDown()
                stopService()
            }
            else -> {
                if (serviceAvailability.get().isAvailable() && !MyContextHolder.myContextHolder.getNow().isReady) {
                    MyContextHolder.myContextHolder.initialize(context, this)
                }
                when (myAction) {
                    MyAction.BOOT_COMPLETED -> {
                        MyLog.d(this, "Start service on boot $instanceId")
                        sendCommand(CommandData.EMPTY)
                    }
                    MyAction.SYNC -> {
                        MyLog.v(TAG) { "onReceive $instanceId SYNC" }
                        SyncInitiator.tryToSync(context)
                    }
                    else -> MyLog.v(TAG) { "onReceive $instanceId $intent" }
                }
            }
        }
    }

    private class ServiceAvailability private constructor(private val timeWhenServiceWillBeAvailable: Long) {
        fun isAvailable(): Boolean {
            return willBeAvailableInMillis() == 0L
        }

        fun willBeAvailableInMillis(): Long {
            return if (timeWhenServiceWillBeAvailable == 0L) 0 else Math.max(
                timeWhenServiceWillBeAvailable - System.currentTimeMillis(),
                0
            )
        }

        fun checkAndGet(): ServiceAvailability {
            return if (isAvailable()) AVAILABLE else this
        }

        companion object {
            val AVAILABLE: ServiceAvailability = ServiceAvailability(0)
            fun newUnavailable(): ServiceAvailability {
                return ServiceAvailability(
                    System.currentTimeMillis() +
                            TimeUnit.MINUTES.toMillis(15)
                )
            }
        }
    }

    companion object {
        private const val MyServiceJobId: Int = 1
        private val myServiceJobInfo: JobInfo by lazy {
            val context = MyContextHolder.myContextHolder.getBlocking().context
            JobInfo.Builder(MyServiceJobId, ComponentName(context, MyService::class.java))
                .apply {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        // Prior to Android version Build.VERSION_CODES.Q, you had to specify
                        // at least one constraint on the JobInfo object that you are creating.
                        // Otherwise, the builder would throw an exception when building.
                        setMinimumLatency(1L)
                    }
                }
                .build()
        }
        private val TAG: String = MyServiceManager::class.simpleName!!

        private val registeredReceiver: AtomicReference<MyServiceManager?> = AtomicReference()
        fun registerReceiver(contextIn: Context, receiverIn: MyServiceManager? = null) {
            val oldReceiver = registeredReceiver.get()
            if (oldReceiver != null && (receiverIn == null || oldReceiver == receiverIn)) return

            val receiver = receiverIn ?: MyServiceManager()
            if (registeredReceiver.compareAndSet(oldReceiver, receiver)) {
                val context = contextIn.applicationContext
                oldReceiver?.let {
                    MyLog.v(TAG) { "Receiver is unregistered ${it.instanceId}" }
                    context.unregisterReceiver(it)
                }

                val filter = IntentFilter()
                filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                filter.addAction(Intent.ACTION_DREAMING_STOPPED)
                filter.addAction(MyAction.BOOT_COMPLETED.action)
                filter.addAction(MyAction.SYNC.action)
                filter.addAction(MyAction.ACTION_SHUTDOWN.action)
                context.registerReceiver(receiver, filter)
                MyLog.v(TAG) { "Receiver is registered ${receiver.instanceId}" }
            }
        }

        /**
         * Starts MyService  asynchronously if it is not already started
         * and send command to it.
         *
         * @param commandData to the service or null
         */
        fun sendCommand(commandData: CommandData) {
            if (!isServiceAvailable()) {
                if (commandData != CommandData.EMPTY) {
                    // Imitate a soft service error
                    commandData.getResult().incrementNumIoExceptions()
                    commandData.getResult().setMessage("Service is not available")
                    MyServiceEventsBroadcaster.newInstance(
                        MyContextHolder.myContextHolder.getNow(),
                        MyServiceState.STOPPED
                    )
                        .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast()
                }
                return
            }
            sendCommandIgnoringServiceAvailability(commandData)
        }

        fun sendManualForegroundCommand(commandData: CommandData) {
            sendForegroundCommand(commandData.setManuallyLaunched(true))
        }

        fun sendForegroundCommand(commandData: CommandData) {
            sendCommand(commandData.setInForeground(true))
        }

        fun sendCommandIgnoringServiceAvailability(commandData: CommandData) {
            val myContext = MyContextHolder.myContextHolder.getNow()
            if (myContext.isEmpty) {
                MyLog.w(TAG, "Couldn't send command ${commandData.command} to MyService: MyContext is empty")
                return
            }
            CommandQueue.addToPreQueue(commandData)
            // TODO: Maybe we should use worker instead of JobScheduler, see https://stackoverflow.com/a/72131422/297710
            try {
                val jobScheduler = myContext.context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?
                if (jobScheduler == null) {
                    MyLog.e(TAG, "JobScheduler is unavailable in ${myContext.context}")
                    return
                }
                if (jobScheduler.getPendingJob(MyServiceJobId) != null || MyService.isWorking) {
                    MyLog.v(TAG, "Skip Scheduling MyService job ${commandData.command}, id:${commandData.commandId}")
                } else {
                    MyLog.v(TAG, "Scheduling MyService job ${commandData.command}, id:${commandData.commandId}")
                    jobScheduler.schedule(myServiceJobInfo)
                }
            } catch (e: Exception) {
                MyLog.e(TAG, "Failed to schedule MyService job", e)
            }
        }

        /**
         * Stop  [MyService] asynchronously
         */
        fun stopService() {
            MyService.stopService()
        }

        fun getServiceState(): MyServiceState {
            return MyService.serviceState
        }

        private val serviceAvailability: AtomicReference<ServiceAvailability> =
            AtomicReference(ServiceAvailability.AVAILABLE)

        fun isServiceAvailable(): Boolean {
            var myContext = MyContextHolder.myContextHolder.getNow()
            if (!myContext.isReady) {
                if (serviceAvailability.get().isAvailable()
                    && AsyncUtil.nonUiThread // Don't block on UI thread
                    && !MyContextHolder.myContextHolder.getNow().initialized
                ) {
                    myContext = MyContextHolder.myContextHolder.initialize(null, TAG).getBlocking()
                }
            }
            return if (myContext.isReady) {
                val availableInMillis = serviceAvailability.updateAndGet { it.checkAndGet() }
                    .willBeAvailableInMillis()
                (availableInMillis == 0L).also {
                    if (!it && MyLog.isVerboseEnabled()) {
                        MyLog.v(
                            TAG, "Service will be available in "
                                    + TimeUnit.MILLISECONDS.toSeconds(availableInMillis)
                                    + " seconds"
                        )
                    }
                }
            } else {
                MyLog.v(TAG, "Service is unavailable: Context is not Ready: $myContext")
                false
            }
        }

        fun setServiceAvailable() {
            serviceAvailability.set(ServiceAvailability.AVAILABLE)
        }

        fun setServiceUnavailable() {
            serviceAvailability.set(ServiceAvailability.newUnavailable())
        }
    }

    init {
        MyLog.v(this) { "Created, instanceId=$instanceId" }
    }
}
