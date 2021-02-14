/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 * Based on the sample: com.example.android.samplesync.syncadapter
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

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.service.MyServiceCommandsRunner
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog

class SyncAdapter(private val mContext: Context?, autoInitialize: Boolean) : AbstractThreadedSyncAdapter(mContext, autoInitialize) {
    override fun onPerformSync(account: Account?, extras: Bundle?, authority: String?,
                               provider: ContentProviderClient?, syncResult: SyncResult?) {
        if (!MyServiceManager.Companion.isServiceAvailable()) {
            MyLog.d(this, account.name + " Service unavailable")
            return
        }
        val myContext: MyContext = MyContextHolder.Companion.myContextHolder.initialize(mContext, this).getBlocking()
        MyServiceCommandsRunner(myContext).autoSyncAccount(
                myContext.accounts().fromAccountName(account.name), syncResult)
    }

    init {
        MyLog.v(this) { "created, context:" + mContext.javaClass.canonicalName }
    }
}