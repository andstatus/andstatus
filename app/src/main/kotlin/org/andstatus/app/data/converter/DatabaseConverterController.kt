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
import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.os.AsyncTask
import org.andstatus.app.os.AsyncTask.PoolEnum.DEFAULT_POOL
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TryUtils
import java.util.concurrent.TimeUnit

class DatabaseConverterController {
    private class AsyncUpgrade(val upgradeRequester: Activity, val isRestoring: Boolean) :
        AsyncTask<Unit, Unit, Unit>(DEFAULT_POOL) {
        var progressLogger: ProgressLogger = ProgressLogger.getEmpty(TAG)
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
                synchronized(upgradeLock) { mProgressLogger = progressLogger }
                MyLog.i(TAG, "Upgrade triggered by " + MyStringBuilder.objToTag(upgradeRequester))
                MyServiceManager.setServiceUnavailable()
                MyContextHolder.myContextHolder.release { "doUpgrade" }
                // Upgrade will occur inside this call synchronously
                // TODO: Add completion stage instead of blocking...
                MyContextHolder.myContextHolder.initializeDuringUpgrade(upgradeRequester).getBlocking()
                synchronized(upgradeLock) { shouldTriggerDatabaseUpgrade = false }
            } catch (e: Exception) {
                MyLog.i(TAG, "Failed to trigger database upgrade, will try later", e)
            } finally {
                synchronized(upgradeLock) {
                    success = upgradeEndedSuccessfully
                    mProgressLogger = ProgressLogger.getEmpty(TAG)
                    locUpgradeStarted = upgradeStarted
                    upgradeStarted = false
                    upgradeEndTime = 0L
                }
            }
            if (!locUpgradeStarted) {
                MyLog.v(TAG, "Upgrade didn't start")
            }
            if (success) {
                MyLog.i(TAG, "success " + MyContextHolder.myContextHolder.getNow().state)
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

    fun onUpgrade(upgradeParams: DatabaseUpgradeParams) {
        if (!shouldTriggerDatabaseUpgrade) {
            MyLog.v(this, "onUpgrade - Trigger not set yet")
            throw IllegalStateException("onUpgrade - Trigger not set yet")
        }
        synchronized(upgradeLock) {
            shouldTriggerDatabaseUpgrade = false
            stillUpgrading()
        }
        MyContextHolder.myContextHolder.getNow().isInForeground = true
        val databaseConverter = DatabaseConverter(mProgressLogger)
        val success = databaseConverter.execute(upgradeParams)
        synchronized(upgradeLock) {
            upgradeEnded = true
            upgradeEndedSuccessfully = success
        }
        if (!success) {
            throw ApplicationUpgradeException(databaseConverter.converterError)
        }
    }

    companion object {
        private val TAG: String = DatabaseConverterController::class.java.simpleName

        // TODO: Should be one object for atomic updates. start ---
        private val upgradeLock: Any = Any()

        @Volatile
        private var shouldTriggerDatabaseUpgrade = false

        /** Semaphore enabling uninterrupted system upgrade */
        private var upgradeEndTime = 0L
        private var upgradeStarted = false
        private var upgradeEnded = false
        private var upgradeEndedSuccessfully = false
        @Volatile
        private var mProgressLogger: ProgressLogger = ProgressLogger.getEmpty(TAG)
        // end ---

        private const val SECONDS_BEFORE_UPGRADE_TRIGGERED = 5L
        private const val UPGRADE_LENGTH_SECONDS_MAX = 90

        fun attemptToTriggerDatabaseUpgrade(upgradeRequestorIn: Activity) {
            val requestorName: String = MyStringBuilder.objToTag(upgradeRequestorIn)
            var skip = false
            if (isUpgrading()) {
                MyLog.v(
                    TAG, "Attempt to trigger database upgrade by " + requestorName
                            + ": already upgrading"
                )
                skip = true
            }
            if (!skip && !MyContextHolder.myContextHolder.getNow().initialized) {
                MyLog.v(
                    TAG, "Attempt to trigger database upgrade by " + requestorName
                            + ": not initialized yet"
                )
                skip = true
            }
            if (!skip && acquireUpgradeLock(requestorName)) {
                val asyncUpgrade = AsyncUpgrade(upgradeRequestorIn, MyContextHolder.myContextHolder.isOnRestore())
                if (MyContextHolder.myContextHolder.isOnRestore()) {
                    asyncUpgrade.syncUpgrade()
                } else {
                    AsyncTaskLauncher.execute(TAG, asyncUpgrade)
                }
            }
        }

        private fun acquireUpgradeLock(requestorName: String?): Boolean {
            var skip = false
            synchronized(upgradeLock) {
                if (isUpgrading()) {
                    MyLog.v(
                        TAG, "Attempt to trigger database upgrade by " + requestorName
                                + ": already upgrading"
                    )
                    skip = true
                }
                if (!skip && upgradeEnded) {
                    MyLog.v(
                        TAG, "Attempt to trigger database upgrade by " + requestorName
                                + ": already completed " + if (upgradeEndedSuccessfully) " successfully" else "(failed)"
                    )
                    skip = true
                    if (!upgradeEndedSuccessfully) {
                        upgradeEnded = false
                    }
                }
                if (!skip) {
                    MyLog.v(TAG, "Upgrade lock acquired for $requestorName")
                    val startTime = System.currentTimeMillis()
                    upgradeEndTime = startTime + TimeUnit.SECONDS.toMillis(SECONDS_BEFORE_UPGRADE_TRIGGERED)
                    shouldTriggerDatabaseUpgrade = true
                }
            }
            return !skip
        }

        fun stillUpgrading() {
            var wasStarted: Boolean
            synchronized(upgradeLock) {
                wasStarted = upgradeStarted
                upgradeStarted = true
                upgradeEndTime =
                    System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(UPGRADE_LENGTH_SECONDS_MAX.toLong())
            }
            MyLog.w(
                TAG,
                (if (wasStarted) "Still upgrading" else "Upgrade started") + ". Wait " + UPGRADE_LENGTH_SECONDS_MAX + " seconds"
            )
        }

        fun isUpgradeError(): Boolean {
            synchronized(upgradeLock) {
                if (upgradeEnded && !upgradeEndedSuccessfully) {
                    return true
                }
            }
            return false
        }

        fun isUpgrading(): Boolean {
            synchronized(upgradeLock) {
                if (upgradeEndTime == 0L) {
                    return false
                }
                val currentTime = System.currentTimeMillis()
                if (currentTime > upgradeEndTime) {
                    MyLog.v(TAG, "Upgrade end time came")
                    upgradeEndTime = 0L
                    return false
                }
            }
            return true
        }

    }

}
