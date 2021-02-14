/*
 * Copyright (C) 2013-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.context

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CursorFactory
import android.os.Process
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraDialog
import org.acra.annotation.AcraMailSender
import org.andstatus.app.R
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import org.andstatus.app.util.TamperingDetector
import java.io.File

@AcraMailSender(mailTo = "andstatus@gmail.com")
@AcraDialog(resIcon = R.drawable.icon, resText = R.string.crash_dialog_text, resCommentPrompt = R.string.crash_dialog_comment_prompt)
@AcraCore(alsoReportToAndroidFramework = true)
class MyApplication : Application() {
    @Volatile
    var isAcraProcess = false
    override fun onCreate() {
        super.onCreate()
        val processName = getCurrentProcessName(this)
        isAcraProcess = processName.endsWith(":acra")
        MyLog.i(this, "onCreate "
                + (if (isAcraProcess) "ACRA" else "'$processName'") + " process")
        if (!isAcraProcess) {
            MyContextHolder.Companion.myContextHolder.storeContextIfNotPresent(this, this)
            MyLocale.setLocale(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(if (isAcraProcess) newConfig else MyLocale.onConfigurationChanged(this, newConfig))
    }

    override fun getDatabasePath(name: String?): File? {
        return if (isAcraProcess) super.getDatabasePath(name) else MyStorage.getDatabasePath(name)
    }

    override fun attachBaseContext(base: Context?) {
        MyLog.v(this) { "attachBaseContext started" + if (isAcraProcess) ". ACRA process" else "" }
        super.attachBaseContext(base)
        ACRA.init(this)
        TamperingDetector.initialize(this)
    }

    override fun openOrCreateDatabase(name: String?, mode: Int, factory: CursorFactory?): SQLiteDatabase? {
        if (isAcraProcess) {
            return super.openOrCreateDatabase(name, mode, factory)
        }
        val db: SQLiteDatabase?
        val dbAbsolutePath = getDatabasePath(name)
        db = if (dbAbsolutePath != null) {
            SQLiteDatabase.openDatabase(dbAbsolutePath.path, factory,
                    SQLiteDatabase.CREATE_IF_NECESSARY + SQLiteDatabase.OPEN_READWRITE)
        } else {
            null
        }
        MyLog.v(this
        ) {
            ("openOrCreateDatabase, name:" + name
                    + if (db == null) " NOT opened" else " opened '" + db.path + "'")
        }
        return db
    }

    /**
     * Since: API Level 11
     * Simplified implementation
     */
    override fun openOrCreateDatabase(name: String?, mode: Int, factory: CursorFactory?,
                                      errorHandler: DatabaseErrorHandler?): SQLiteDatabase? {
        return if (isAcraProcess) {
            super.openOrCreateDatabase(name, mode, factory, errorHandler)
        } else openOrCreateDatabase(name, mode, factory)
    }

    override fun toString(): String {
        return "AndStatus. " + (if (isAcraProcess) "acra." else "") + super.toString()
    }

    companion object {
        private fun getCurrentProcessName(app: Application): String {
            val processId = Process.myPid()
            val manager = app.getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val processInfos = manager.runningAppProcesses
            var processName: String? = null
            if (processInfos != null) {
                for (processInfo in processInfos) {
                    if (processInfo.pid == processId) {
                        processName = processInfo.processName
                        break
                    }
                }
            }
            return if (StringUtil.isEmpty(processName)) "?" else processName
        }
    }
}