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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncResult;
import android.os.PowerManager;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.service.ConnectionRequired;
import org.andstatus.app.service.ConnectionState;
import org.andstatus.app.service.MyServiceCommandsRunner;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Periodic syncing doesn't work reliably, when a device is in a Doze mode, so we need
 * additional way to check if syncing is needed.
 */
public class SyncInitiator extends BroadcastReceiver {
    private final static BroadcastReceiver BROADCAST_RECEIVER = new SyncInitiator();

    // Testing Doze: https://developer.android.com/training/monitoring-device-state/doze-standby.html#testing_doze
    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        MyLog.v(this, "onReceive "
                + (pm == null || pm.isDeviceIdleMode() ? " idle" : " " + UriUtils.getConnectionState(context))
        );
        if (pm == null || pm.isDeviceIdleMode()) return;
        MyContextHolder.getMyFutureContext(context, this).thenRun(this::checkConnectionState);
    }

    private void checkConnectionState(MyContext myContext) {
        if (!syncIfNeeded(myContext))
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    syncIfNeeded(myContext);
                }
            }, (5 + new Random().nextInt(20)) * 1000);
    }

    private boolean syncIfNeeded(MyContext myContext) {
        final ConnectionState connectionState = UriUtils.getConnectionState(myContext.context());
        MyLog.v(this, "syncIfNeeded " + UriUtils.getConnectionState(myContext.context()));
        if (ConnectionRequired.SYNC.isConnectionStateOk(connectionState)) {
            for (MyAccount myAccount: myContext.persistentAccounts().accountsToSync()) {
                if (!myContext.persistentTimelines().toAutoSyncForAccount(myAccount).isEmpty()) {
                    new MyServiceCommandsRunner(myContext).autoSyncAccount(myAccount, new SyncResult());
                }
            }
            return true;
        }
        return false;
    }

    public static void register(MyContext myContext) {
        if (myContext != null && myContext.persistentAccounts().hasSyncedAutomatically()) myContext.context()
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
