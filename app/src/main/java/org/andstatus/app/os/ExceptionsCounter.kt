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
package org.andstatus.app.os

import android.app.Dialog
import androidx.annotation.MainThread
import org.acra.ACRA
import org.andstatus.app.R
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.util.DialogFactory
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StringUtil
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * @author yvolk@yurivolkov.com
 */
object ExceptionsCounter {
    private val TAG: String = ExceptionsCounter::class.java.simpleName
    private val diskIoExceptionsCount: AtomicLong = AtomicLong()
    private val diskIoExceptionsCountShown: AtomicLong = AtomicLong()

    @Volatile
    private var diskIoDialog: Dialog? = null
    val firstError: AtomicReference<String?> = AtomicReference()
    fun getDiskIoExceptionsCount(): Long {
        return diskIoExceptionsCount.get()
    }

    fun onDiskIoException(e: Throwable?) {
        diskIoExceptionsCount.incrementAndGet()
        logSystemInfo(e)
    }

    fun logSystemInfo(throwable: Throwable?) {
        val systemInfo: String = MyContextHolder.myContextHolder.getSystemInfo(MyContextHolder.myContextHolder.getNow().context(), true)
        ACRA.errorReporter.putCustomData("systemInfo", systemInfo)
        ExceptionsCounter.logError(systemInfo, throwable)
    }

    private fun logError(msgLog: String?, tr: Throwable?) {
        MyLog.e(ExceptionsCounter.TAG, msgLog, tr)
        if (!ExceptionsCounter.firstError.get().isNullOrEmpty() || tr == null) {
            return
        }
        ExceptionsCounter.firstError.set(MyLog.getStackTrace(tr))
    }

    fun forget() {
        DialogFactory.dismissSafely(ExceptionsCounter.diskIoDialog)
        ExceptionsCounter.diskIoExceptionsCount.set(0)
        ExceptionsCounter.diskIoExceptionsCountShown.set(0)
    }

    @MainThread
    fun showErrorDialogIfErrorsPresent() {
        if (ExceptionsCounter.diskIoExceptionsCountShown.get() == diskIoExceptionsCount.get()) return
        ExceptionsCounter.diskIoExceptionsCountShown.set(diskIoExceptionsCount.get())
        DialogFactory.dismissSafely(ExceptionsCounter.diskIoDialog)
        val text: String = StringUtil.format( MyContextHolder.myContextHolder.getNow().context(), R.string.database_disk_io_error,
                diskIoExceptionsCount.get())
        ExceptionsCounter.diskIoDialog = DialogFactory.showOkAlertDialog(ExceptionsCounter::class.java,  MyContextHolder.myContextHolder.getNow().context(),
                R.string.app_name, text)
    }
}