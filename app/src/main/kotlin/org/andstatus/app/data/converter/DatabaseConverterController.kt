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
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.Taggable
import java.util.concurrent.TimeUnit

object DatabaseConverterController {
        val TAG: String = DatabaseConverterController::class.java.simpleName

        // TODO: Should be one object for atomic updates. start ---
        internal val upgradeLock: Any = Any()

        @Volatile
        internal var shouldTriggerDatabaseUpgrade = false

        /** Semaphore enabling uninterrupted system upgrade */
        internal var upgradeEndTime = 0L
        internal var upgradeStarted = false
        private var upgradeEnded = false
        internal var upgradeEndedSuccessfully = false
        @Volatile
        internal var mProgressLogger: ProgressLogger = ProgressLogger.getEmpty(TAG)
        // end ---

        private const val SECONDS_BEFORE_UPGRADE_TRIGGERED = 5L
        private const val UPGRADE_LENGTH_SECONDS_MAX = 90

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

        fun attemptToTriggerDatabaseUpgrade(upgradeRequestorIn: Activity) {
            val requestorName: String = Taggable.anyToTag(upgradeRequestorIn)
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
                    asyncUpgrade.execute(TAG, Unit)
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
