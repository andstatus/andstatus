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
package org.andstatus.app.syncadapter

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.MyLog

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
class SyncService : Service() {
    override fun onCreate() {
        MyLog.d(this, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MyLog.d(this, "onStartCommand")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        MyLog.d(this, "onBind")
        return ResourceHolder.syncAdapter.getSyncAdapterBinder()
    }

    private object ResourceHolder {
        // TODO: Fix the leak!
        var syncAdapter: SyncAdapter = SyncAdapter( MyContextHolder.myContextHolder.getNow().context(), true)
    }

    override fun onDestroy() {
        MyLog.d(this, "onDestroy")
        super.onDestroy()
    }
}