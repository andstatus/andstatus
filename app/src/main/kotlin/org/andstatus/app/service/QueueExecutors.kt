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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.andstatus.app.service.CommandQueue.AccessorType
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import kotlin.math.min

/** Two specialized async tasks to execute [CommandQueue]  */
class QueueExecutors(private val myService: MyService) {
    private val executors: MutableList<QueueExecutor> = ArrayList()
    private val mutex = Mutex()

    suspend fun ensureExecutorsStarted() {
        mutex.withLock {
            removeUnneeded()
            AccessorType.values().forEach(::addNeeded)
        }
    }

    private fun removeUnneeded() {
        executors.filter { it.noMoreBackgroundWork }.foldIndexed("") { ind, acc, executor ->
            removeExecutor(executor)
            acc + (if (ind == 0) "Removing completed ${executor.instanceTag}"
            else ", ${executor.instanceTag}")
        }.takeIf( String::isNotBlank )?.let {
            MyLog.v(myService) { it }
        }
        executors.filter { !it.isReallyWorking }.foldIndexed("") { ind, acc, executor ->
            removeExecutor(executor)
            acc + (if (ind == 0) "Removing stalled: ${executor.instanceTag}"
            else ", ${executor.instanceTag}")
        }.takeIf( String::isNotBlank )?.let {
            MyLog.v(myService) { it }
        }
    }

    private fun addNeeded(accessorType: AccessorType) {
        val maxToAdd = accessorType.numberOfExecutors - executors.count { it.accessorType == accessorType}
        if (maxToAdd < 1) return

        val toAdd = min(
            myService.myContext.queues.getAccessor(accessorType).countToExecuteNow().toInt() -
                    // The below executors will take commands soon
                    executors.count { it.accessorType == accessorType && it.executedCounter.get() == 0L },
            maxToAdd)
        if (toAdd < 1) return

        val logMessageBuilder = MyStringBuilder()
        repeat(toAdd) {
            val executor = QueueExecutor(myService, accessorType)
            executors.add(executor)
            logMessageBuilder.withComma(executor.instanceTag)
            executor.execute(myService, Unit)
                .onFailure { throwable: Throwable? ->
                    logMessageBuilder.withComma("Failed to start $executor: $throwable")
                }
        }
        MyLog.v(myService) { "Added $logMessageBuilder" }
    }

    fun contains(other: QueueExecutor): Boolean {
        return executors.contains(other)
    }

    private fun removeExecutor(executor: QueueExecutor) {
        if ( executors.removeIf { it === executor }) {
            if (executor.needsBackgroundWork) {
                executor.cancel()
            }
        }
    }

    suspend fun stopAll(forceNow: Boolean): Boolean {
        return mutex.withLock {
            // toList is actually used to copy the list (and avoid concurrent modifications exception)
            executors.toList().fold(true) { acc, executor ->
                acc && stopOne(executor, forceNow)
            }
        }
    }

    private fun stopOne(previous: QueueExecutor, forceNow: Boolean): Boolean {
        val method = this::stopOne.name
        val logMessageBuilder = MyStringBuilder()
        var success = false
        var doStop = true
        if (doStop && previous.needsBackgroundWork && previous.isReallyWorking) {
            if (forceNow) {
                logMessageBuilder.withComma("Cancelling working $previous")
            } else {
                logMessageBuilder.withComma("Cannot stop now $previous")
                doStop = false
            }
        }
        if (doStop) {
            removeExecutor(previous)
            success = true
        }
        if (logMessageBuilder.nonEmpty) {
            MyLog.v(myService) { "$method; $logMessageBuilder" }
        }
        return success
    }

    fun isReallyWorking(): Boolean {
        return executors.any{ it.isReallyWorking }
    }

    override fun toString(): String {
        return executors.toString()
    }
}
