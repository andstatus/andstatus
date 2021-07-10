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

import io.vavr.control.Try
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.andstatus.app.os.AsyncTask.PoolEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext

/**
 * @author yvolk@yurivolkov.com
 */
class AsyncTaskLauncher {

    companion object {
        private val TAG: String = AsyncTaskLauncher::class.java.simpleName
        private val launchedCount: AtomicLong = AtomicLong()
        private val skippedCount: AtomicLong = AtomicLong()
        private val launchedTasks: Queue<AsyncTask<*, *, *>> = ConcurrentLinkedQueue()

        @Volatile
        private var QUICK_UI_EXECUTOR: CoroutineContext? = null

        /**
         * An [Executor] that executes tasks one at a time in serial
         * order.  This serialization is global to a particular process.
         * TODO: Implement serialization. See e.g. https://github.com/Kotlin/kotlinx.coroutines/issues/261
         */
        val SERIAL_EXECUTOR: CoroutineContext = ThreadPoolExecutor(
            1, 1,
            1, TimeUnit.SECONDS, LinkedBlockingQueue(512)
        ).asCoroutineDispatcher()

        fun <Params, Result> execute(
            params: Params,
            backgroundFun: suspend (Params) -> Try<Result>,
            postExecuteFun: suspend (Params, Try<Result>) -> Unit
        ): Try<Unit> {
            return AsyncTask<Params, Unit, Result>(taskId = params, pool = PoolEnum.DEFAULT_POOL)
                .doInBackground(backgroundFun)
                .onPostExecute(postExecuteFun)
                .execute(params)
        }

        fun <Params> execute(objTag: Any?, asyncTask: AsyncTask<Params, *, *>, params: Params): Try<Unit> {
            MyLog.v(objTag) { "Launching $asyncTask" }
            return try {
                cancelStalledTasks()
                asyncTask.executeInContext(getExecutor(asyncTask.pool), params)
                launchedTasks.add(asyncTask)
                launchedCount.incrementAndGet()
                removeFinishedTasks()
                TryUtils.SUCCESS
            } catch (e: Exception) {
                val msgLog = "Launching $asyncTask in ${threadPoolInfo()}"
                MyLog.w(objTag, msgLog, e)
                Try.failure(Exception("${e.message} $msgLog", e))
            }
        }

        fun getExecutor(pool: PoolEnum): CoroutineContext {
            var executor: CoroutineContext?
            executor = when (pool) {
                PoolEnum.DEFAULT_POOL -> Dispatchers.Default
                PoolEnum.FILE_DOWNLOAD -> Dispatchers.IO
                PoolEnum.SYNC -> Dispatchers.IO
                PoolEnum.QUICK_UI -> QUICK_UI_EXECUTOR
            }
            if (executor is ExecutorCoroutineDispatcher) {
                val tpe = executor.executor
                if (tpe is ThreadPoolExecutor && tpe.isShutdown) {
                    if (tpe.isTerminating) {
                        if (MyLog.isVerboseEnabled()) {
                            MyLog.v(TAG, "Pool " + pool.name + " isTerminating. Applying shutdownNow: " + tpe)
                        }
                        tpe.shutdownNow()
                    }
                    executor = null
                }
            }
            if (executor == null) {
                MyLog.v(TAG) { "Creating pool " + pool.name }
                executor = ThreadPoolExecutor(
                    pool.corePoolSize, pool.corePoolSize + 1,
                    1, TimeUnit.SECONDS, LinkedBlockingQueue(512)
                ).asCoroutineDispatcher()
                setExecutor(pool, executor)
            }
            return executor
        }

        private fun setExecutor(pool: PoolEnum, executor: CoroutineContext?) {
            when (pool) {
                PoolEnum.QUICK_UI -> QUICK_UI_EXECUTOR = executor
                else -> throw IllegalArgumentException("Trying to set executor for $pool")
            }
        }

        fun execute(cancelable: Boolean, backgroundFunc: () -> Any?): Try<Unit> {
            val asyncTask: AsyncTask<Unit, Unit, Unit> =
                object: AsyncTask<Unit, Unit, Unit>(backgroundFunc, PoolEnum.FILE_DOWNLOAD, cancelable) {
                    override suspend fun doInBackground(params: Unit): Try<Unit> {
                        backgroundFunc()
                        return TryUtils.SUCCESS
                    }
                }
            return execute(backgroundFunc, asyncTask, Unit)
        }

        private fun cancelStalledTasks() {
            var count = 0
            for (launched in launchedTasks) {
                if (launched.needsBackgroundWork && !launched.isReallyWorking) {
                    MyLog.v(TAG) { "Found stalled task at " + launched.pool + ": " + launched }
                    count++
                    MyLog.v(TAG) { "$count. (stalled) Cancelling task: $launched" }
                    launched.cancel()
                }
            }
        }

        private fun removeFinishedTasks() {
            var count: Long = 0
            for (launched in launchedTasks) {
                if (launched.isFinished) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(TAG, (++count).toString() + ". Removing finished " + launched)
                    }
                    launchedTasks.remove(launched)
                }
            }
        }

        fun threadPoolInfo(): String {
            val builder = getLaunchedTasksInfo()
            builder.append("\nThread pools:")
            for (pool in PoolEnum.values()) {
                builder.append("\n${pool.name}: ${getExecutor(pool)}")
            }
            return builder.toString()
        }

        private fun getLaunchedTasksInfo(): StringBuilder {
            val builder = StringBuilder("\n")
            var pendingCount: Long = 0
            var queuedCount: Long = 0
            var runningCount: Long = 0
            var finishingCount: Long = 0
            var finishedCount: Long = 0
            for (launched in launchedTasks) {
                when {
                    launched.isPending -> {
                        pendingCount++
                        builder.append("P $pendingCount. $launched\n")
                    }
                    launched.isRunning -> when {
                        launched.backgroundStartedAt.get() == 0L -> {
                            queuedCount++
                            builder.append("Q $queuedCount. $launched\n")
                        }
                        launched.backgroundEndedAt.get() == 0L -> {
                            runningCount++
                            builder.append("R $runningCount. $launched\n")
                        }
                        else -> {
                            finishingCount++
                            builder.append("F $finishingCount. $launched\n")
                        }
                    }
                    launched.isFinished -> finishedCount++
                    else -> {
                        builder.append("Unexpected state of $launched\n")
                    }
                }
            }
            val builder2 = StringBuilder("Tasks:\n")
            builder2.append("Total launched: " + launchedCount.get())
            if (pendingCount > 0) {
                builder2.append("; pending: $pendingCount")
            }
            if (queuedCount > 0) {
                builder2.append("; queued: $queuedCount")
            }
            builder2.append("; running: $runningCount")
            if (finishingCount > 0) {
                builder2.append("; finishing: $finishingCount")
            }
            if (finishedCount > 0) {
                builder2.append("; just finished: $finishedCount")
            }
            builder2.append(". Skipped: " + skippedCount.get())
            builder2.append(builder)
            return builder2
        }

        fun forget() {
            listOf(*PoolEnum.values()).forEach(Consumer { pool: PoolEnum -> cancelPoolTasks(pool) })
            cancelStalledTasks()
            removeFinishedTasks()
        }

        fun cancelPoolTasks(pool: PoolEnum) {
            MyLog.v(TAG) { "Cancelling tasks for pool " + pool.name }
            var count = 0
            for (launched in launchedTasks) {
                if (!launched.cancelable ||
                    launched.isCancelled ||
                    launched.isFinished
                ) continue
                if (launched.pool == pool) {
                    try {
                        count++
                        MyLog.v(TAG) { "$count. Cancelling task: $launched" }
                        launched.cancel()
                    } catch (tr: Throwable) {
                        // Ignore
                    }
                }
            }
        }
    }
}
