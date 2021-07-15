/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.backup

import org.andstatus.app.data.DbUtils
import org.andstatus.app.service.MyServiceManager
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

class ProgressLogger {
    @Volatile
    private var lastLoggedAt = 0L

    @Volatile
    private var makeServiceUnavalable = false
    val progressListener: Optional<ProgressListener>
    val logTag: String

    fun interface ProgressListener {
        fun onProgressMessage(message: CharSequence)
        fun onComplete(success: Boolean) {}
        fun onActivityFinish() {}
        fun setCancelable(isCancelable: Boolean) {}
        fun cancel() {}
        fun isCancelled(): Boolean {
            return false
        }

        fun getLogTag(): String {
            return TAG
        }
    }

    private class EmptyListener : ProgressListener {
        override fun onProgressMessage(message: CharSequence) {
            // Empty
        }
    }

    constructor(progressListener: ProgressListener?) {
        this.progressListener = Optional.ofNullable(progressListener)
        logTag = this.progressListener.map { obj: ProgressListener -> obj.getLogTag() }.orElse(TAG)
    }

    private constructor(logTag: String?) {
        progressListener = Optional.empty()
        this.logTag = StringUtil.notEmpty(logTag, TAG)
    }

    val isCancelled: Boolean get() {
        return progressListener.map { obj: ProgressListener -> obj.isCancelled() }.orElse(false)
    }

    fun logSuccess() {
        onComplete(true)
    }

    fun logFailure() {
        onComplete(false)
    }

    fun onComplete(success: Boolean) {
        logProgressAndPause(if (success) "Completed successfully" else "Failed", 1)
        progressListener.ifPresent { c: ProgressListener -> c.onComplete(success) }
    }

    fun loggedMoreSecondsAgoThan(secondsAgo: Long): Boolean {
        return RelativeTime.moreSecondsAgoThan(lastLoggedAt, secondsAgo)
    }

    fun makeServiceUnavalable(): ProgressLogger {
        makeServiceUnavalable = true
        return this
    }

    fun logProgressIfLongProcess(supplier: Supplier<CharSequence>) {
        if (loggedMoreSecondsAgoThan(PROGRESS_REPORT_PERIOD_SECONDS.toLong())) {
            logProgress(supplier.get())
        }
    }

    fun logProgressAndPause(message: CharSequence, pauseIfPositive: Long) {
        logProgress(message)
        if (pauseIfPositive > 0 && progressListener.isPresent()) {
            DbUtils.waitMs(this, 2000)
        }
    }

    fun logProgress(message: CharSequence) {
        updateLastLoggedTime()
        MyLog.i(logTag, message.toString())
        if (makeServiceUnavalable) MyServiceManager.setServiceUnavailable()
        progressListener.ifPresent { c: ProgressListener -> c.onProgressMessage(message) }
    }

    fun updateLastLoggedTime() {
        lastLoggedAt = System.currentTimeMillis()
    }

    companion object {
        const val PROGRESS_REPORT_PERIOD_SECONDS = 20
        private val TAG: String = ProgressLogger::class.java.simpleName
        val startedAt: AtomicLong = AtomicLong(0)
        fun newStartingTime(): Long {
            val iStartedAt = MyLog.uniqueCurrentTimeMS
            startedAt.set(iStartedAt)
            return iStartedAt
        }

        val EMPTY_LISTENER: ProgressListener = EmptyListener()
        fun getEmpty(logTag: String?): ProgressLogger {
            return ProgressLogger(logTag)
        }
    }
}

