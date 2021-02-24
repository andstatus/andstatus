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

import android.os.AsyncTask
import io.vavr.control.Try
import org.andstatus.app.os.MyAsyncTask.PoolEnum
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.TryUtils
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import java.util.function.Function

/**
 * @author yvolk@yurivolkov.com
 */
class AsyncTaskLauncher<Params> {
    fun execute(objTag: Any?, asyncTask: MyAsyncTask<Params?, *, *>, params: Params?): Try<Void> {
        MyLog.v(objTag) { asyncTask.toString() + " Launching task" }
        return try {
            cancelStalledTasks()
            if (asyncTask.isSingleInstance() && foundUnfinished(asyncTask)) {
                skippedCount.incrementAndGet()
                return Try.failure(IllegalStateException("Single instance and found unfinished task: $asyncTask"))
            } else {
                val paramsArray = arrayOf<Any?>(params) as Array<Params?>
                asyncTask.executeOnExecutor(getExecutor(asyncTask.pool), *paramsArray)
                launchedTasks.add(asyncTask)
                launchedCount.incrementAndGet()
            }
            removeFinishedTasks()
            TryUtils.SUCCESS
        } catch (e: Exception) {
            val msgLog = """${asyncTask.toString()} Launching task ${threadPoolInfo()}"""
            MyLog.w(objTag, msgLog, e)
            Try.failure(Exception(""" ${e.message} $msgLog """.trimIndent(), e))
        }
    }

    private fun foundUnfinished(asyncTask: MyAsyncTask<Params?, *, *>?): Boolean {
        for (launched in launchedTasks) {
            if (launched == asyncTask && launched.needsBackgroundWork()) {
                MyLog.v(this) {
                    ("Found unfinished "
                            + (if (launched.isCancelled()) "cancelled " else "")
                            + launched)
                }
                if (!launched.isCancelled()) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private val TAG: String = AsyncTaskLauncher::class.java.simpleName
        private val launchedCount: AtomicLong = AtomicLong()
        private val skippedCount: AtomicLong = AtomicLong()
        private val launchedTasks: Queue<MyAsyncTask<*, *, *>> = ConcurrentLinkedQueue()

        @Volatile
        private var SYNC_POOL_EXECUTOR: ThreadPoolExecutor? = null

        @Volatile
        private var LONG_UI_POOL_EXECUTOR: ThreadPoolExecutor? = null

        @Volatile
        private var FILE_DOWNLOAD_EXECUTOR: ThreadPoolExecutor? = null

        fun getExecutor(pool: PoolEnum): ThreadPoolExecutor {
            var executor: ThreadPoolExecutor?
            executor = when (pool) {
                PoolEnum.LONG_UI -> LONG_UI_POOL_EXECUTOR
                PoolEnum.FILE_DOWNLOAD -> FILE_DOWNLOAD_EXECUTOR
                PoolEnum.SYNC -> SYNC_POOL_EXECUTOR
                else -> return AsyncTask.THREAD_POOL_EXECUTOR as ThreadPoolExecutor
            }
            if (executor != null && executor.isShutdown) {
                if (executor.isTerminating) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(TAG, "Pool " + pool.name + " isTerminating. Applying shutdownNow: " + executor)
                    }
                    executor.shutdownNow()
                }
                executor = null
            }
            if (executor == null) {
                MyLog.v(TAG) { "Creating pool " + pool.name }
                executor = ThreadPoolExecutor(pool.corePoolSize, pool.corePoolSize + 1,
                        1, TimeUnit.SECONDS, LinkedBlockingQueue(128))
                setExecutor(pool, executor)
            }
            return executor
        }

        private fun setExecutor(pool: PoolEnum, executor: ThreadPoolExecutor?) {
            onExecutorRemoval(pool)
            when (pool) {
                PoolEnum.LONG_UI -> LONG_UI_POOL_EXECUTOR = executor
                PoolEnum.FILE_DOWNLOAD -> FILE_DOWNLOAD_EXECUTOR = executor
                PoolEnum.SYNC -> SYNC_POOL_EXECUTOR = executor
                else -> {
                }
            }
        }

        fun execute(backgroundFunc: Runnable): Try<Void> {
            return execute<Any?, Any?>(null, { p: Any? ->
                backgroundFunc.run()
                Try.success(null)
            }, { p: Any? -> Consumer { r: Try<Any?>? -> } })
        }

        fun <Params, Result> execute(params: Params?,
                                     backgroundFunc: Function<Params?, Try<Result>?>,
                                     uiConsumer: Function<Params?, Consumer<Try<Result>?>>): Try<Void> {
            val asyncTask: MyAsyncTask<Params?, Void?, Try<Result>?> = MyAsyncTask.fromFunc(params, backgroundFunc, uiConsumer)
            return AsyncTaskLauncher<Params?>().execute(params, asyncTask, params)
        }

        fun execute(objTag: Any?, asyncTask: MyAsyncTask<Void?, *, *>): Try<Void> {
            val launcher = AsyncTaskLauncher<Void?>()
            return launcher.execute(objTag, asyncTask, null)
        }

        private fun cancelStalledTasks() {
            val poolsToShutDown: MutableSet<PoolEnum> = HashSet()
            for (launched in launchedTasks) {
                if (launched.needsBackgroundWork() && !launched.isReallyWorking()) {
                    MyLog.v(TAG) { "Found stalled task at " + launched.pool + ": " + launched }
                    if (launched.pool.mayBeShutDown && launched.cancelledLongAgo() && launched.hasExecutor) {
                        poolsToShutDown.add(launched.pool)
                    } else {
                        launched.cancelLogged(true)
                    }
                }
            }
            shutdownExecutors(poolsToShutDown)
        }

        private fun removeFinishedTasks() {
            var count: Long = 0
            for (launched in launchedTasks) {
                if (launched.getStatus() == AsyncTask.Status.FINISHED) {
                    if (MyLog.isVerboseEnabled()) {
                        MyLog.v(TAG, (++count).toString() + ". Removing finished " + launched)
                    }
                    launchedTasks.remove(launched)
                }
            }
        }

        private fun onExecutorRemoval(pool: PoolEnum) {
            MyLog.v(TAG) { "On removing executor for pool " + pool.name }
            for (launched in launchedTasks) {
                if (launched.pool == pool) launched.hasExecutor = false
            }
        }

        fun threadPoolInfo(): String {
            val builder = getLaunchedTasksInfo()
            builder.append("\nThread pools:")
            for (pool in PoolEnum.values()) {
                builder.append("""
    
    ${pool.name}: ${getExecutor(pool).toString()}
    """.trimIndent())
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
                when (val s = launched.getStatus()) {
                    AsyncTask.Status.PENDING -> {
                        pendingCount++
                        builder.append("P $pendingCount. $launched\n")
                    }
                    AsyncTask.Status.RUNNING -> if (launched.backgroundStartedAt == 0L) {
                            queuedCount++
                            builder.append("Q $queuedCount. $launched\n")
                        } else if (launched.backgroundEndedAt == 0L) {
                            runningCount++
                            builder.append("R $runningCount. $launched\n")
                        } else {
                            finishingCount++
                            builder.append("F $finishingCount. $launched\n")
                    }
                    AsyncTask.Status.FINISHED -> finishedCount++
                    else -> {
                        builder.append("$s ??. $launched\n")
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

        private fun shutdownExecutors(pools: MutableCollection<PoolEnum>) {
            for (pool in pools) {
                val executor = getExecutor(pool)
                MyLog.v(TAG) { "Shutting down executor $pool:$executor" }
                executor.shutdownNow()
                setExecutor(pool, null)
            }
        }

        fun forget() {
            Arrays.asList(*PoolEnum.values()).forEach(Consumer { pool: PoolEnum -> cancelPoolTasks(pool) })
            cancelStalledTasks()
            removeFinishedTasks()
        }

        fun cancelPoolTasks(pool: PoolEnum) {
            MyLog.v(TAG) { "Cancelling tasks for pool " + pool.name }
            for (launched in launchedTasks) {
                if (!launched.cancelable) continue
                if (launched.pool == pool) {
                    try {
                        launched.cancelLogged(true)
                    } catch (tr: Throwable) {
                        // Ignore
                    }
                }
            }
        }
    }
}