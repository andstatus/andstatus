/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.data.converter

import android.database.sqlite.SQLiteDatabase
import org.andstatus.app.backup.ProgressLogger
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.converter.DatabaseConverterController.UpgradeParams
import org.andstatus.app.util.MyLog
import java.util.concurrent.TimeUnit

internal class DatabaseConverter {
    var startTime = System.currentTimeMillis()
    var progressLogger: ProgressLogger = ProgressLogger.getEmpty("DatabaseConverter")
    var converterError: String = ""

    fun execute(params: UpgradeParams): Boolean {
        var success = false
        progressLogger = params.progressLogger
        var msgLog: String
        var endTime: Long = 0
        try {
            convertAll(params.db, params.oldVersion, params.newVersion)
            success = true
            endTime = System.currentTimeMillis()
        } catch (e: ApplicationUpgradeException) {
            endTime = System.currentTimeMillis()
            msgLog = e.message ?: ""
            progressLogger.logProgress(msgLog)
            converterError = msgLog
            MyLog.ignored(this, e)
            DbUtils.waitMs("ApplicationUpgradeException", 30000)
        } finally {
            DbUtils.waitMs("execute finally", 2000)
            if (success) {
                msgLog = ("Upgrade successfully completed in "
                        + TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                        + " seconds")
                MyLog.i(this, msgLog)
            } else {
                msgLog = ("Upgrade failed in "
                        + TimeUnit.MILLISECONDS.toSeconds(endTime - startTime)
                        + " seconds")
                MyLog.e(this, msgLog)
            }
            DbUtils.waitMs("execute finally 2", 500)
        }
        return success
    }

    private fun convertAll(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var currentVersion = oldVersion
        MyLog.i(this, "Upgrading database from version $oldVersion to version $newVersion")
        var converterNotFound = false
        var lastError = "?"
        var oneStep: ConvertOneStep?
        do {
            oneStep = null
            try {
                val prevVersion = currentVersion
                val clazz = Class.forName(this.javaClass.getPackage()?.getName() + ".Convert" + currentVersion)
                oneStep = clazz.newInstance() as ConvertOneStep
                currentVersion = oneStep.execute(db, currentVersion, progressLogger)
                if (currentVersion == prevVersion) {
                    lastError = oneStep.getLastError()
                    MyLog.e(this, "Stuck at version $prevVersion\nError: $lastError")
                    oneStep = null
                }
            } catch (e: ClassNotFoundException) {
                converterNotFound = true
                val msgLog = "No converter for version $currentVersion"
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(this, msgLog, e)
                } else {
                    MyLog.i(this, msgLog)
                }
            } catch (e: InstantiationException) {
                MyLog.e(this, "Error at version $currentVersion", e)
            } catch (e: IllegalAccessException) {
                MyLog.e(this, "Error at version $currentVersion", e)
            }
        } while (oneStep != null && currentVersion < newVersion)
        if (currentVersion == newVersion) {
            MyLog.i(this, "Successfully upgraded database from version " + oldVersion + " to version "
                    + newVersion + ".")
        } else {
            var msgLog: String
            msgLog = if (converterNotFound) {
                "This version of application doesn't support database upgrade"
            } else {
                "Error upgrading database"
            }
            msgLog += """ from version $oldVersion to version $newVersion. Current database version=$currentVersion 
Error: $lastError"""
            MyLog.e(this, msgLog)
            throw ApplicationUpgradeException(msgLog)
        }
    }
}