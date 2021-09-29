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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import org.andstatus.app.IntentExtra
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

    private class MyServiceStateInTime {
        /** Is the service started.
         * @See [here](http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d)
         */
        @Volatile
        var stateEnum: MyServiceState = MyServiceState.UNKNOWN

        /**
         * [System.currentTimeMillis] when the state was queued or received last time ( 0 - never )
         */
        val stateQueuedTime: Long = System.currentTimeMillis()

        /** If true, we sent state request and are waiting for reply from [MyService]  */
        @Volatile
        var isWaiting = false

        companion object {
            fun getUnknown(): MyServiceStateInTime {
                return MyServiceStateInTime()
            }

            fun fromIntent(intent: Intent): MyServiceStateInTime {
                val state = MyServiceStateInTime()
                state.stateEnum = MyServiceState.load(intent.getStringExtra(IntentExtra.SERVICE_STATE.key))
                return state
            }
        }
    }

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
                        MyLog.v(TAG) { "onReceive $instanceId SYNC"}
                        SyncInitiator.tryToSync(context)
                    }
                    MyAction.SERVICE_STATE -> {
                        stateInTime = MyServiceStateInTime.fromIntent(intent)
                        MyLog.d(this, "onReceive $instanceId service:" + stateInTime.stateEnum)
                    }
                    else -> MyLog.v(TAG) { "onReceive $instanceId $intent"}
                }
            }
        }
    }

    private class ServiceAvailability private constructor(private val timeWhenServiceWillBeAvailable: Long) {
        fun isAvailable(): Boolean {
            return willBeAvailableInMillis() == 0L
        }

        fun willBeAvailableInMillis(): Long {
            return if (timeWhenServiceWillBeAvailable == 0L) 0 else Math.max(timeWhenServiceWillBeAvailable - System.currentTimeMillis(), 0)
        }

        fun checkAndGet(): ServiceAvailability {
            return if (isAvailable()) AVAILABLE else this
        }

        companion object {
            val AVAILABLE: ServiceAvailability = ServiceAvailability(0)
            fun newUnavailable(): ServiceAvailability {
                return ServiceAvailability(System.currentTimeMillis() +
                        TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    companion object {
        private val TAG: String = MyServiceManager::class.simpleName!!

        @Volatile
        private var stateInTime = MyServiceStateInTime.getUnknown()

        /**
         * How long are we waiting for [MyService] response before deciding that the service is stopped
         */
        private const val STATE_QUERY_TIMEOUT_SECONDS = 3

        private val registeredReceiver: AtomicReference<MyServiceManager?> = AtomicReference()
        fun registerReceiver(contextIn: Context, receiverIn: MyServiceManager? = null) {
            val oldReceiver = registeredReceiver.get()
            if(oldReceiver != null && ( receiverIn == null || oldReceiver == receiverIn)) return

            val receiver = receiverIn ?: MyServiceManager()
            if (registeredReceiver.compareAndSet(oldReceiver, receiver)) {
                val context = contextIn.applicationContext
                oldReceiver?.let {
                    MyLog.i(TAG, "Receiver is unregistered ${it.instanceId}")
                    context.unregisterReceiver(it)
                }

                val filter = IntentFilter()
                filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED)
                filter.addAction(Intent.ACTION_DREAMING_STOPPED)
                filter.addAction(MyAction.BOOT_COMPLETED.action)
                filter.addAction(MyAction.SYNC.action)
                filter.addAction(MyAction.SERVICE_STATE.action)
                filter.addAction(MyAction.ACTION_SHUTDOWN.action)
                context.registerReceiver(receiver, filter)
                MyLog.i(TAG, "Receiver is registered ${receiver.instanceId}")
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
                    MyServiceEventsBroadcaster.newInstance(MyContextHolder.myContextHolder.getNow(), MyServiceState.STOPPED)
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
            // Using explicit Service intent,
            // see http://stackoverflow.com/questions/18924640/starting-android-service-using-explicit-vs-implicit-intent
            var serviceIntent = Intent(MyContextHolder.myContextHolder.getNow().context, MyService::class.java)
            when (commandData.command) {
                CommandEnum.STOP_SERVICE,
                CommandEnum.BROADCAST_SERVICE_STATE -> serviceIntent = commandData.toIntent(serviceIntent)
                CommandEnum.UNKNOWN -> {
                }
                else -> CommandQueue.addToPreQueue(commandData)
            }
            try {
                MyContextHolder.myContextHolder.getNow().context.startService(serviceIntent)
            } catch (e: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // Since Android Oreo TODO: https://developer.android.com/about/versions/oreo/android-8.0-changes.html#back-all
                        // See also https://github.com/evernote/android-job/issues/254
                        MyContextHolder.myContextHolder.getNow().context.startForegroundService(serviceIntent)
                    } catch (e2: IllegalStateException) {
                        MyLog.e(TAG, "Failed to start MyService in the foreground", e2)
                    }
                } else {
                    MyLog.e(TAG, "Failed to start MyService", e)
                }
            } catch (e: NullPointerException) {
                MyLog.e(TAG, "Failed to start MyService", e)
            }
        }

        /**
         * Stop  [MyService] asynchronously
         */
        fun stopService() {
            stateInTime = MyServiceStateInTime.getUnknown()
            MyContextHolder.myContextHolder.getNow().takeIf { it.nonEmpty }?.context?.sendBroadcast(
                    CommandData.newCommand(CommandEnum.STOP_SERVICE)
                            .toIntent(MyAction.EXECUTE_COMMAND.newIntent())
            )
        }

        /**
         * Returns previous service state and queries service for its current state asynchronously.
         * Doesn't start the service, so absence of the reply will mean that service is stopped
         * @See [here](http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d)
         */
        fun getServiceState(): MyServiceState {
            var state = stateInTime
            if (state.isWaiting && (System.currentTimeMillis() - state.stateQueuedTime >
                            TimeUnit.SECONDS.toMillis(STATE_QUERY_TIMEOUT_SECONDS.toLong()))) {
                // Timeout expired
                state = MyServiceStateInTime()
                state.stateEnum = MyServiceState.STOPPED
                stateInTime = state
            } else if (!state.isWaiting && state.stateEnum == MyServiceState.UNKNOWN) {
                // State is unknown, we need to query the Service again
                state = MyServiceStateInTime()
                state.stateEnum = MyServiceState.UNKNOWN
                state.isWaiting = true
                stateInTime = state
                MyContextHolder.myContextHolder.getNow().context
                        .sendBroadcast(CommandData.newCommand(CommandEnum.BROADCAST_SERVICE_STATE)
                                .toIntent(MyAction.EXECUTE_COMMAND.newIntent()))
            }
            return state.stateEnum
        }

        private val serviceAvailability: AtomicReference<ServiceAvailability> = AtomicReference(ServiceAvailability.AVAILABLE)
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
                        MyLog.v(TAG, "Service will be available in "
                                + TimeUnit.MILLISECONDS.toSeconds(availableInMillis)
                                + " seconds")
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
