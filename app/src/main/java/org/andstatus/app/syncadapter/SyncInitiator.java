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
import org.andstatus.app.service.MyServiceCommandsRunner;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

/**
 * Periodic syncing doesn't work reliably, when a device is in a Doze mode, so we need
 * additional way to check if syncing is needed.
 */
public class SyncInitiator extends BroadcastReceiver {
    private final static BroadcastReceiver BROADCAST_RECEIVER = new SyncInitiator();

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isDeviceIdleMode()
                && ConnectionRequired.SYNC.isConnectionStateOk(UriUtils.getConnectionState(context))) {
            MyContextHolder.getMyFutureContext(context, this).thenRun(SyncInitiator::syncIfNeeded);
        }
    }

    private static void syncIfNeeded(MyContext myContext) {
        for (MyAccount myAccount: myContext.persistentAccounts().accountsToSync()) {
            if (!myContext.persistentTimelines().toAutoSyncForAccount(myAccount).isEmpty()) {
                new MyServiceCommandsRunner(myContext).autoSyncAccount(myAccount, new SyncResult());
            }
        }
    }

    public static void register(MyContext myContext) {
        if (myContext != null) myContext.context()
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
