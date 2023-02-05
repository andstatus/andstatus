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
import org.andstatus.app.context.MyContextHolder.Companion.myContextHolder
import org.andstatus.app.data.checker.DataChecker
import org.andstatus.app.os.AsyncEnum
import org.andstatus.app.os.AsyncRunnable
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.Taggable
import org.andstatus.app.util.TryUtils

class UpgradeTask(val upgradeRequester: Activity, val isRestoring: Boolean) :
    AsyncRunnable("upgradeTask", AsyncEnum.DEFAULT_POOL) {
    val progressLogger: ProgressLogger = if (upgradeRequester is MyActivity) {
        val progressListener: ProgressLogger.ProgressListener =
            DefaultProgressListener(upgradeRequester, R.string.label_upgrading, "ConvertDatabase")
        ProgressLogger(progressListener)
    } else {
        ProgressLogger.getEmpty(this.instanceTag)
    }
    override val cancelable: Boolean = false

    override suspend fun doInBackground(params: Unit): Try<Unit> {
        upgrade()
        return TryUtils.SUCCESS
    }

    suspend fun upgrade() {
        var success = false
        try {
            progressLogger.logProgress(upgradeRequester.getText(R.string.label_upgrading))
            success = upgradeInner()
        } finally {
            progressLogger.onComplete(success)
            MyLog.v(this, "Set service available")
            MyServiceManager.setServiceAvailable()
        }
    }

    private suspend fun upgradeInner(): Boolean {
        var success = false
        var locUpgradeStarted = false
        try {
            synchronized(DatabaseConverterController.upgradeLock) {
                DatabaseConverterController.mProgressLogger = progressLogger
            }
            MyLog.i(this, "Upgrade triggered by " + Taggable.anyToTag(upgradeRequester))
            MyServiceManager.setServiceUnavailable()
            myContextHolder.release { "doUpgrade" }
            // Upgrade will occur inside this call synchronously
            myContextHolder.initializeDuringUpgrade(upgradeRequester)
            myContextHolder.getCompleted()
            synchronized(DatabaseConverterController.upgradeLock) {
                DatabaseConverterController.shouldTriggerDatabaseUpgrade = false
            }
        } catch (e: Exception) {
            MyLog.i(this, "Failed to upgrade database, will try later", e)
        } finally {
            synchronized(DatabaseConverterController.upgradeLock) {
                success = DatabaseConverterController.upgradeEndedSuccessfully
                DatabaseConverterController.mProgressLogger = ProgressLogger.getEmpty(this.instanceTag)
                locUpgradeStarted = DatabaseConverterController.upgradeStarted
                DatabaseConverterController.upgradeStarted = false
                DatabaseConverterController.upgradeEndTime = 0L
            }
        }
        if (!locUpgradeStarted) {
            MyLog.v(this, "Upgrade didn't start")
        }
        if (success) {
            MyLog.i(this, "success " + myContextHolder.getNow().state)
            onUpgradeSucceeded()
            MyLog.i(this, "after onUpgradeSucceeded " + myContextHolder.future)
        }
        return success
    }

    private suspend fun onUpgradeSucceeded() {
        MyServiceManager.setServiceUnavailable()
        if (!myContextHolder.getNow().isReady) {
            myContextHolder.release { "onUpgradeSucceeded1" }
            myContextHolder.initialize(upgradeRequester)
            myContextHolder.getCompleted()
        }
        MyServiceManager.setServiceUnavailable()
        MyServiceManager.stopService()
        if (isRestoring) return
        DataChecker.fixData(progressLogger, false, false)
        myContextHolder.release { "onUpgradeSucceeded2" }
        myContextHolder.initialize(upgradeRequester).tryCompleted()
    }
}
