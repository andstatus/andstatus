/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.SyncResult
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.util.MyLog

class MyServiceCommandsRunner(private val myContext: MyContext) {
    private var ignoreServiceAvailability = false
    fun autoSyncAccount(ma: MyAccount, syncResult: SyncResult) {
        val method = "syncAccount " + ma.accountName
        if (!myContext.isReady) {
            MyLog.d(this, "$method; Context is not ready")
            return
        }
        if (ma.nonValid) {
            MyLog.d(this, "$method; The account was not loaded")
            return
        } else if (!ma.isValidAndSucceeded()) {
            syncResult.stats.numAuthExceptions++
            MyLog.d(this, "$method; Credentials failed, skipping")
            return
        }
        val timelines = myContext.timelines.toAutoSyncForAccount(ma)
        if (timelines.isEmpty()) {
            MyLog.d(this, "$method; No timelines to sync")
            return
        }
        MyLog.v(this) { method + " started, " + timelines.size + " timelines" }
        timelines.stream()
                .map { t: Timeline -> CommandData.newTimelineCommand(CommandEnum.GET_TIMELINE, t) }
                .forEach { commandData: CommandData -> sendCommand(commandData) }
        MyLog.v(this) { method + " ended, " + timelines.size + " timelines requested: " + timelines }
    }

    private fun sendCommand(commandData: CommandData) {
        // we don't wait for completion anymore.
        // TODO: Implement Synchronous background sync ?!
        if (ignoreServiceAvailability) {
            MyServiceManager.sendCommandIgnoringServiceAvailability(commandData)
        } else {
            MyServiceManager.sendCommand(commandData)
        }
    }

    fun setIgnoreServiceAvailability(ignoreServiceAvailability: Boolean) {
        this.ignoreServiceAvailability = ignoreServiceAvailability
    }
}
