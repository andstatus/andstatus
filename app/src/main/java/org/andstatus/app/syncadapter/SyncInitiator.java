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

package org.andstatus.app.syncadapter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.andstatus.app.MyAction;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.service.ConnectionRequired;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;

/**
 * Periodic syncing doesn't work reliably, when a device is in a Doze mode, so we need
 * additional way(s) to check if syncing is needed.
 */
public class SyncInitiator extends BroadcastReceiver {
    private final static SyncInitiator BROADCAST_RECEIVER = new SyncInitiator();

    public static void tryToSync(Context context) {
        BROADCAST_RECEIVER.initializeApp(context);
    }

    // Testing Doze: https://developer.android.com/training/monitoring-device-state/doze-standby.html#testing_doze
    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        MyLog.v(this, () -> "onReceive "
                + (pm == null || pm.isDeviceIdleMode() ? " idle" : " " + UriUtils.getConnectionState(context))
        );
        if (pm == null || pm.isDeviceIdleMode()) return;
        initializeApp(context);
    }

    private void initializeApp(Context context) {
        MyContextHolder.getMyFutureContext(context, this).thenRun(this::checkConnectionState);
    }

    private void checkConnectionState(MyContext myContext) {
        if (!syncIfNeeded(myContext)) {
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    syncIfNeeded(myContext);
                }
            }, getRandomDelayMillis(5));
        }
    }

    private static long getRandomDelayMillis(int minSeconds) {
        return TimeUnit.SECONDS.toMillis((long) minSeconds + new Random().nextInt(minSeconds * 4));
    }

    private boolean syncIfNeeded(MyContext myContext) {
        if (!MyServiceManager.isServiceAvailable()) {
            MyLog.v(this, () -> "syncIfNeeded Service is unavailable");
            return false;
        }

        final ConnectionState connectionState = UriUtils.getConnectionState(myContext.context());
        MyLog.v(this, () -> "syncIfNeeded " + UriUtils.getConnectionState(myContext.context()));
        if (!ConnectionRequired.SYNC.isConnectionStateOk(connectionState)) return false;

        for (MyAccount myAccount: myContext.accounts().accountsToSync()) {
            if (!myContext.timelines().toAutoSyncForAccount(myAccount).isEmpty()) {
                myAccount.requestSync();
            }
        }
        return true;
    }

    public static void register(MyContext myContext) {
        scheduleRepeatingAlarm(myContext);
        registerBroadcastReceiver(myContext);
    }

    private static void scheduleRepeatingAlarm(@NonNull MyContext myContext) {
        long minSyncIntervalMillis = myContext.accounts().minSyncIntervalMillis();
        if (minSyncIntervalMillis > 0) {
            final AlarmManager alarmManager = myContext.context().getSystemService(AlarmManager.class);
            if (alarmManager == null) {
                MyLog.w(SyncInitiator.class, "No AlarmManager ???");
                return;
            }
            final long randomDelay = getRandomDelayMillis(30);
            MyLog.d(SyncInitiator.class, "Scheduling repeating alarm in "
                    + TimeUnit.MILLISECONDS.toSeconds(randomDelay) + " seconds");
            alarmManager.setInexactRepeating(ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + randomDelay,
                    minSyncIntervalMillis,
                    PendingIntent.getBroadcast(myContext.context(), 0, MyAction.SYNC.getIntent(), 0)
            );
        }
    }

    private static void registerBroadcastReceiver(MyContext myContext) {
        if (myContext != null && myContext.accounts().hasSyncedAutomatically()) myContext.context()
                .registerReceiver(BROADCAST_RECEIVER, new IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED));
    }

    public static void unregister(MyContext myContext) {
        try {
            if (myContext != null) myContext.context().unregisterReceiver(BROADCAST_RECEIVER);
        } catch (IllegalArgumentException e) {
            MyLog.ignored(BROADCAST_RECEIVER, e);
        }
    }
}
