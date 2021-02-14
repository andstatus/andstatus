/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.os.AsyncTaskLauncher
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

/** Two specialized threads to execute [CommandQueue]  */
internal class QueueExecutors(private val myService: MyService?) {
    private val general: AtomicReference<QueueExecutor?>? = AtomicReference()
    private val downloads: AtomicReference<QueueExecutor?>? = AtomicReference()
    fun ensureExecutorsStarted() {
        ensureExecutorStarted(AccessorType.GENERAL)
        ensureExecutorStarted(AccessorType.DOWNLOADS)
    }

    private fun ensureExecutorStarted(accessorType: AccessorType?) {
        val method = "ensureExecutorStarted-$accessorType"
        val logMessageBuilder = MyStringBuilder()
        val previous = getRef(accessorType).get()
        var replace = previous == null
        if (!replace && previous.completedBackgroundWork()) {
            logMessageBuilder.withComma("Removing completed Executor $previous")
            replace = true
        }
        if (!replace && !previous.isReallyWorking()) {
            logMessageBuilder.withComma("Cancelling stalled Executor $previous")
            replace = true
        }
        if (replace) {
            val accessor = myService.myContext.queues().getAccessor(accessorType)
            val current = if (accessor.isAnythingToExecuteNow) QueueExecutor(myService, accessorType) else null
            if (current == null && previous == null) {
                logMessageBuilder.withComma("Nothing to execute")
            } else {
                if (replaceExecutor(logMessageBuilder, accessorType, previous, current)) {
                    if (current == null) {
                        logMessageBuilder.withComma("Nothing to execute")
                    } else {
                        logMessageBuilder.withComma("Starting new Executor $current")
                        AsyncTaskLauncher.Companion.execute(myService.classTag() + "-" + accessorType, current)
                                .onFailure(Consumer { throwable: Throwable? ->
                                    logMessageBuilder.withComma("Failed to start new executor: $throwable")
                                    replaceExecutor(logMessageBuilder, accessorType, current, null)
                                })
                    }
                }
            }
        } else {
            logMessageBuilder.withComma("There is an Executor already $previous")
        }
        if (logMessageBuilder.length > 0) {
            MyLog.v(myService) { "$method; $logMessageBuilder" }
        }
    }

    fun getRef(accessorType: AccessorType?): AtomicReference<QueueExecutor?> {
        return if (accessorType == AccessorType.GENERAL) general else downloads
    }

    private fun replaceExecutor(logMessageBuilder: MyStringBuilder?, accessorType: AccessorType?,
                                previous: QueueExecutor?, current: QueueExecutor?): Boolean {
        if (getRef(accessorType).compareAndSet(previous, current)) {
            if (previous == null) {
                logMessageBuilder.withComma(if (current == null) "No executor" else "Executor set to $current")
            } else {
                if (previous.needsBackgroundWork()) {
                    logMessageBuilder.withComma("Cancelling previous")
                    previous.cancelLogged(true)
                }
                logMessageBuilder.withComma(if (current == null) "Removed executor $previous" else "Replaced executor $previous with $current")
            }
            return true
        }
        return false
    }

    fun stopExecutor(forceNow: Boolean): Boolean {
        return (stopExecutor(AccessorType.GENERAL, forceNow)
                && stopExecutor(AccessorType.DOWNLOADS, forceNow))
    }

    private fun stopExecutor(accessorType: AccessorType?, forceNow: Boolean): Boolean {
        val method = "couldStopExecutor-$accessorType"
        val logMessageBuilder = MyStringBuilder()
        val executorRef = getRef(accessorType)
        val previous = executorRef.get()
        var success = previous == null
        var doStop = !success
        if (doStop && previous.needsBackgroundWork() && previous.isReallyWorking()) {
            if (forceNow) {
                logMessageBuilder.withComma("Cancelling working Executor$previous")
            } else {
                logMessageBuilder.withComma("Cannot stop now Executor $previous")
                doStop = false
            }
        }
        if (doStop) {
            success = replaceExecutor(logMessageBuilder, accessorType, previous, null)
        }
        if (logMessageBuilder.nonEmpty()) {
            MyLog.v(myService) { "$method; $logMessageBuilder" }
        }
        return success
    }

    fun isReallyWorking(): Boolean {
        val gExecutor = general.get()
        val dExecutor = downloads.get()
        return gExecutor != null && (gExecutor.isReallyWorking
                || dExecutor != null && dExecutor.isReallyWorking)
    }

    override fun toString(): String {
        return general.toString() + "; " + downloads.toString()
    }
}