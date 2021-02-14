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
import android.os.Build
import org.andstatus.app.IntentExtra
import org.andstatus.app.MyAction
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.service.CommandEnum
import org.andstatus.app.syncadapter.SyncInitiator
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

/**
 * This receiver starts and stops [MyService] and also queries its state.
 * Android system creates new instance of this type on each Intent received.
 * This is why we're keeping a state in static fields.
 */
class MyServiceManager : BroadcastReceiver(), IdentifiableInstance {
    private val instanceId = InstanceId.next()
    override fun getInstanceId(): Long {
        return instanceId
    }

    private class MyServiceStateInTime {
        /** Is the service started.
         * @See [here](http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d)
         */
        @Volatile
        private var stateEnum: MyServiceState? = MyServiceState.UNKNOWN

        /**
         * [System.nanoTime] when the state was queued or received last time ( 0 - never )
         */
        @Volatile
        private var stateQueuedTime: Long = 0

        /** If true, we sent state request and are waiting for reply from [MyService]  */
        @Volatile
        private val isWaiting = false

        companion object {
            fun getUnknown(): MyServiceStateInTime? {
                return MyServiceStateInTime()
            }

            fun fromIntent(intent: Intent?): MyServiceStateInTime? {
                val state = MyServiceStateInTime()
                state.stateQueuedTime = System.nanoTime()
                state.stateEnum = MyServiceState.Companion.load(intent.getStringExtra(IntentExtra.SERVICE_STATE.key))
                return state
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (MyAction.Companion.fromIntent(intent)) {
            MyAction.BOOT_COMPLETED -> {
                MyLog.d(this, "Trying to start service on boot")
                sendCommand(CommandData.Companion.EMPTY)
            }
            MyAction.SYNC -> SyncInitiator.Companion.tryToSync(context)
            MyAction.SERVICE_STATE -> {
                if (isServiceAvailable()) {
                    MyContextHolder.Companion.myContextHolder.initialize(context, this)
                }
                stateInTime = MyServiceStateInTime.fromIntent(intent)
                MyLog.d(this, "Notification received: Service state=" + stateInTime.stateEnum)
            }
            MyAction.ACTION_SHUTDOWN -> {
                setServiceUnavailable()
                MyLog.d(this, "Stopping service on Shutdown")
                MyContextHolder.Companion.myContextHolder.onShutDown()
                stopService()
            }
            else -> {
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

        fun checkAndGetNew(): ServiceAvailability? {
            return if (isAvailable()) AVAILABLE else newUnavailable()
        }

        companion object {
            val AVAILABLE: ServiceAvailability? = ServiceAvailability(0)
            fun newUnavailable(): ServiceAvailability? {
                return ServiceAvailability(System.currentTimeMillis() +
                        TimeUnit.MINUTES.toMillis(15))
            }
        }
    }

    companion object {
        private val TAG: String? = MyServiceManager::class.java.simpleName

        @Volatile
        private var stateInTime = MyServiceStateInTime.getUnknown()

        /**
         * How long are we waiting for [MyService] response before deciding that the service is stopped
         */
        private const val STATE_QUERY_TIMEOUT_SECONDS = 3

        /**
         * Starts MyService  asynchronously if it is not already started
         * and send command to it.
         *
         * @param commandData to the service or null
         */
        fun sendCommand(commandData: CommandData?) {
            if (!isServiceAvailable()) {
                // Imitate a soft service error
                commandData.getResult().incrementNumIoExceptions()
                commandData.getResult().message = "Service is not available"
                MyServiceEventsBroadcaster.Companion.newInstance(MyContextHolder.Companion.myContextHolder.getNow(), MyServiceState.STOPPED)
                        .setCommandData(commandData).setEvent(MyServiceEvent.AFTER_EXECUTING_COMMAND).broadcast()
                return
            }
            sendCommandIgnoringServiceAvailability(commandData)
        }

        fun sendManualForegroundCommand(commandData: CommandData?) {
            sendForegroundCommand(commandData.setManuallyLaunched(true))
        }

        fun sendForegroundCommand(commandData: CommandData?) {
            sendCommand(commandData.setInForeground(true))
        }

        fun sendCommandIgnoringServiceAvailability(commandData: CommandData?) {
            // Using explicit Service intent,
            // see http://stackoverflow.com/questions/18924640/starting-android-service-using-explicit-vs-implicit-intent
            var serviceIntent: Intent? = Intent(MyContextHolder.Companion.myContextHolder.getNow().context(), MyService::class.java)
            when (commandData.getCommand()) {
                CommandEnum.STOP_SERVICE, CommandEnum.BROADCAST_SERVICE_STATE -> serviceIntent = commandData.toIntent(serviceIntent)
                CommandEnum.UNKNOWN -> {
                }
                else -> CommandQueue.Companion.addToPreQueue(commandData)
            }
            try {
                MyContextHolder.Companion.myContextHolder.getNow().context().startService(serviceIntent)
            } catch (e: IllegalStateException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        // Since Android Oreo TODO: https://developer.android.com/about/versions/oreo/android-8.0-changes.html#back-all
                        // See also https://github.com/evernote/android-job/issues/254
                        MyContextHolder.Companion.myContextHolder.getNow().context().startForegroundService(serviceIntent)
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
        @Synchronized
        fun stopService() {
            if (!MyContextHolder.Companion.myContextHolder.getNow().isReady()) {
                return
            }
            // Don't do "context.stopService", because we may lose some information and (or) get Force Close
            // This is "mild" stopping
            MyContextHolder.Companion.myContextHolder.getNow().context()
                    .sendBroadcast(CommandData.Companion.newCommand(CommandEnum.STOP_SERVICE)
                            .toIntent(MyAction.EXECUTE_COMMAND.intent))
        }

        /**
         * Returns previous service state and queries service for its current state asynchronously.
         * Doesn't start the service, so absence of the reply will mean that service is stopped
         * @See [here](http://groups.google.com/group/android-developers/browse_thread/thread/8c4bd731681b8331/bf3ae8ef79cad75d)
         */
        fun getServiceState(): MyServiceState? {
            val time = System.nanoTime()
            var state = stateInTime
            if (state.isWaiting && time - state.stateQueuedTime > TimeUnit.SECONDS.toMillis(STATE_QUERY_TIMEOUT_SECONDS.toLong())) {
                // Timeout expired
                state = MyServiceStateInTime()
                state.stateEnum = MyServiceState.STOPPED
                stateInTime = state
            } else if (!state.isWaiting && state.stateEnum == MyServiceState.UNKNOWN) {
                // State is unknown, we need to query the Service again
                state = MyServiceStateInTime()
                state.stateEnum = MyServiceState.UNKNOWN
                state.isWaiting = true
                state.stateQueuedTime = time
                stateInTime = state
                MyContextHolder.Companion.myContextHolder.getNow().context()
                        .sendBroadcast(CommandData.Companion.newCommand(CommandEnum.BROADCAST_SERVICE_STATE)
                                .toIntent(MyAction.EXECUTE_COMMAND.intent))
            }
            return state.stateEnum
        }

        private val serviceAvailability: AtomicReference<ServiceAvailability?>? = AtomicReference(ServiceAvailability.AVAILABLE)
        fun isServiceAvailable(): Boolean {
            var isAvailable: Boolean = MyContextHolder.Companion.myContextHolder.getNow().isReady()
            if (!isAvailable) {
                val tryToInitialize = serviceAvailability.get().isAvailable()
                if (tryToInitialize
                        && MyAsyncTask.Companion.nonUiThread() // Don't block on UI thread
                        && !MyContextHolder.Companion.myContextHolder.getNow().initialized()) {
                    MyContextHolder.Companion.myContextHolder.initialize(null, TAG).getBlocking()
                    isAvailable = MyContextHolder.Companion.myContextHolder.getNow().isReady()
                }
            }
            if (isAvailable) {
                val availableInMillis = serviceAvailability.updateAndGet(UnaryOperator { obj: ServiceAvailability? -> obj.checkAndGetNew() })
                        .willBeAvailableInMillis()
                isAvailable = availableInMillis == 0L
                if (!isAvailable && MyLog.isVerboseEnabled()) {
                    MyLog.v(TAG, "Service will be available in "
                            + TimeUnit.MILLISECONDS.toSeconds(availableInMillis)
                            + " seconds")
                }
            } else {
                MyLog.v(TAG, "Service is unavailable: Context is not Ready")
            }
            return isAvailable
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