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
 * distributed under MyDatabaseConverterExecutor License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.data.converter

import android.app.Activity
import io.vavr.control.Try
import org.andstatus.app.MyActivity
import org.andstatus.app.R
import org.andstatus.app.backup.DefaultProgressListener
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncRunnable
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TryUtils

class AsyncUpgrade(val upgradeRequester: Activity, val isRestoring: Boolean) : AsyncRunnable(AsyncEnum.DEFAULT_POOL) {
    var progressLogger: ProgressLogger = ProgressLogger.getEmpty(DatabaseConverterController.TAG)
    override val cancelable: Boolean = false

    override suspend fun doInBackground(params: Unit): Try<Unit> {
        syncUpgrade()
        return TryUtils.SUCCESS
    }

    fun syncUpgrade() {
        var success = false
        try {
            progressLogger.logProgress(upgradeRequester.getText(R.string.label_upgrading))
            success = doUpgrade()
        } finally {
            progressLogger.onComplete(success)
        }
    }

    private fun doUpgrade(): Boolean {
        var success = false
        var locUpgradeStarted = false
        try {
            synchronized(DatabaseConverterController.upgradeLock) { DatabaseConverterController.mProgressLogger = progressLogger }
            MyLog.i(
                DatabaseConverterController.TAG,
                "Upgrade triggered by " + MyStringBuilder.objToTag(upgradeRequester)
            )
            MyServiceManager.setServiceUnavailable()
            MyContextHolder.myContextHolder.release { "doUpgrade" }
            // Upgrade will occur inside this call synchronously
            // TODO: Add completion stage instead of blocking...
            MyContextHolder.myContextHolder.initializeDuringUpgrade(upgradeRequester).getBlocking()
            synchronized(DatabaseConverterController.upgradeLock) { DatabaseConverterController.shouldTriggerDatabaseUpgrade = false }
        } catch (e: Exception) {
            MyLog.i(DatabaseConverterController.TAG, "Failed to trigger database upgrade, will try later", e)
        } finally {
            synchronized(DatabaseConverterController.upgradeLock) {
                success = DatabaseConverterController.upgradeEndedSuccessfully
                DatabaseConverterController.mProgressLogger = ProgressLogger.getEmpty(DatabaseConverterController.TAG)
                locUpgradeStarted = DatabaseConverterController.upgradeStarted
                DatabaseConverterController.upgradeStarted = false
                DatabaseConverterController.upgradeEndTime = 0L
            }
        }
        if (!locUpgradeStarted) {
            MyLog.v(DatabaseConverterController.TAG, "Upgrade didn't start")
        }
        if (success) {
            MyLog.i(DatabaseConverterController.TAG, "success " + MyContextHolder.myContextHolder.getNow().state)
            onUpgradeSucceeded()
        }
        return success
    }

    private fun onUpgradeSucceeded() {
        MyServiceManager.setServiceUnavailable()
        if (!MyContextHolder.myContextHolder.getNow().isReady) {
            MyContextHolder.myContextHolder.release { "onUpgradeSucceeded1" }
            MyContextHolder.myContextHolder.initialize(upgradeRequester).getBlocking()
        }
        MyServiceManager.setServiceUnavailable()
        MyServiceManager.stopService()
        if (isRestoring) return
        DataChecker.fixData(progressLogger, false, false)
        MyContextHolder.myContextHolder.release { "onUpgradeSucceeded2" }
        MyContextHolder.myContextHolder.initialize(upgradeRequester).getBlocking()
        MyServiceManager.setServiceAvailable()
    }

    init {
        progressLogger = if (upgradeRequester is MyActivity) {
            val progressListener: ProgressLogger.ProgressListener =
                DefaultProgressListener(upgradeRequester, R.string.label_upgrading, "ConvertDatabase")
            ProgressLogger(progressListener)
        } else {
            ProgressLogger.getEmpty("ConvertDatabase")
        }
    }
}
