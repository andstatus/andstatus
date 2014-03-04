/*
* Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
* Copyright (C) 2010 The Android Open Source Project
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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.andstatus.app.util.MyLog;

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
public class SyncService extends Service {
    static final String TAG = SyncService.class.getSimpleName();

    private static final Object SYNC_ADAPTER_LOCK = new Object();
    private static SyncAdapter syncAdapter = null;

    @Override
    public void onCreate() {
        synchronized (SYNC_ADAPTER_LOCK) {
            if (syncAdapter == null) {
                syncAdapter = new SyncAdapter(getApplicationContext(), true);
            }
            MyLog.d(TAG, "onCreate");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MyLog.d(TAG, "onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        MyLog.d(TAG, "onBind");
        return syncAdapter.getSyncAdapterBinder();
    }

    @Override
    public void onDestroy() {
        MyLog.d(TAG, "onDestroy");
        super.onDestroy();
    }
}
