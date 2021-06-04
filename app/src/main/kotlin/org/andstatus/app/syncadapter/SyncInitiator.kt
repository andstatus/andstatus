/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.syncadapter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.AsyncTask
import android.os.PowerManager
import android.os.SystemClock
import org.andstatus.app.MyAction
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.service.ConnectionRequired
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.UriUtils
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Periodic syncing doesn't work reliably, when a device is in a Doze mode, so we need
 * additional way(s) to check if syncing is needed.
 */
class SyncInitiator : BroadcastReceiver() {
    // Testing Doze: https://developer.android.com/training/monitoring-device-state/doze-standby.html#testing_doze
    override fun onReceive(context: Context, intent: Intent?) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager ?
        MyLog.v(this
        ) {
            ("onReceive "
                    + if (pm == null || pm.isDeviceIdleMode) " idle" else " " + UriUtils.getConnectionState(context))
        }
        if (pm == null || pm.isDeviceIdleMode) return
        initializeApp(context)
    }

    private fun initializeApp(context: Context) {
         MyContextHolder.myContextHolder
                .initialize(context, this)
                .whenSuccessAsync({ myContext: MyContext -> checkConnectionState(myContext) }, AsyncTask.THREAD_POOL_EXECUTOR)
    }

    private fun checkConnectionState(myContext: MyContext) {
        if (!syncIfNeeded(myContext)) {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    syncIfNeeded(myContext)
                }
            }, getRandomDelayMillis(5))
        }
    }

    private fun syncIfNeeded(myContext: MyContext): Boolean {
        if (!MyServiceManager.isServiceAvailable()) {
            MyLog.v(this) { "syncIfNeeded Service is unavailable" }
            return false
        }
        val connectionState = UriUtils.getConnectionState(myContext.context)
        MyLog.v(this) { "syncIfNeeded " + UriUtils.getConnectionState(myContext.context) }
        if (!ConnectionRequired.SYNC.isConnectionStateOk(connectionState)) return false
        for (myAccount in myContext.accounts().accountsToSync()) {
            if (myContext.timelines().toAutoSyncForAccount(myAccount).isNotEmpty()) {
                myAccount.requestSync()
            }
        }
        return true
    }

    companion object {
        private val BROADCAST_RECEIVER: SyncInitiator = SyncInitiator()

        fun tryToSync(context: Context) {
            BROADCAST_RECEIVER.initializeApp(context)
        }

        private fun getRandomDelayMillis(minSeconds: Int): Long {
            return TimeUnit.SECONDS.toMillis((minSeconds + Random().nextInt(minSeconds * 4)).toLong())
        }

        fun register(myContext: MyContext) {
            scheduleRepeatingAlarm(myContext)
            registerBroadcastReceiver(myContext)
        }

        private fun scheduleRepeatingAlarm(myContext: MyContext) {
            val minSyncIntervalMillis = myContext.accounts().minSyncIntervalMillis()
            if (minSyncIntervalMillis > 0) {
                val alarmManager = myContext.context.getSystemService(AlarmManager::class.java)
                if (alarmManager == null) {
                    MyLog.w(SyncInitiator::class.java, "No AlarmManager ???")
                    return
                }
                val randomDelay = getRandomDelayMillis(30)
                MyLog.d(SyncInitiator::class.java, "Scheduling repeating alarm in "
                        + TimeUnit.MILLISECONDS.toSeconds(randomDelay) + " seconds")
                alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + randomDelay,
                        minSyncIntervalMillis,
                        PendingIntent.getBroadcast(myContext.context, 0, MyAction.SYNC.getIntent(), 0)
                )
            }
        }

        private fun registerBroadcastReceiver(myContext: MyContext?) {
            if (myContext != null && myContext.accounts().hasSyncedAutomatically()) myContext.context
                    .registerReceiver(BROADCAST_RECEIVER, IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED))
        }

        fun unregister(myContext: MyContext?) {
            try {
                myContext?.context?.unregisterReceiver(BROADCAST_RECEIVER)
            } catch (e: IllegalArgumentException) {
                MyLog.ignored(BROADCAST_RECEIVER, e)
            }
        }
    }
}
