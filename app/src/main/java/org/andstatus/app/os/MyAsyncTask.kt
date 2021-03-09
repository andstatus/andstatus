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

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.os.AsyncTask
import android.os.Looper
import io.vavr.control.Try
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import java.util.function.Consumer
import java.util.function.Function

/**
 * @author yvolk@yurivolkov.com
 */
abstract class MyAsyncTask<Params, Progress, Result>(taskId: Any?, pool: PoolEnum) : AsyncTask<Params, Progress, Result>(), IdentifiableInstance {
    private var maxCommandExecutionSeconds = MAX_COMMAND_EXECUTION_SECONDS
    private val taskId: String?
    protected val createdAt = MyLog.uniqueCurrentTimeMS()
    override val instanceId = InstanceId.next()
    private var singleInstance = true

    @Volatile
    var backgroundStartedAt: Long = 0

    @Volatile
    var backgroundEndedAt: Long = 0

    /** This allows to control execution time of single steps/commands by this AsyncTask  */
    @Volatile
    protected var currentlyExecutingSince: Long = 0

    /** Description of execution or lack of it  */
    @Volatile
    protected var currentlyExecutingDescription: String? = "(didn't start)"
    var cancelable = true

    @Volatile
    private var cancelledAt: Long = 0

    @Volatile
    private var firstError: String? = ""

    @Volatile
    var hasExecutor = true

    enum class PoolEnum(val corePoolSize: Int, val maxCommandExecutionSeconds: Long, val mayBeShutDown: Boolean) {
        SYNC(3, MAX_COMMAND_EXECUTION_SECONDS, true),
        FILE_DOWNLOAD(1, MAX_COMMAND_EXECUTION_SECONDS, true),
        QUICK_UI(0, 20, false),
        LONG_UI(1, MAX_COMMAND_EXECUTION_SECONDS, true);

        companion object {
            fun thatCannotBeShutDown(): PoolEnum {
                for (pool in values()) {
                    if (!pool.mayBeShutDown) return pool
                }
                throw IllegalStateException("All pools may be shut down")
            }
        }
    }

    val pool: PoolEnum
    fun isSingleInstance(): Boolean {
        return singleInstance
    }

    fun setSingleInstance(singleInstance: Boolean) {
        this.singleInstance = singleInstance
    }

    fun setMaxCommandExecutionSeconds(seconds: Long): MyAsyncTask<*, *, *> {
        maxCommandExecutionSeconds = seconds
        return this
    }

    fun setCancelable(cancelable: Boolean): MyAsyncTask<*, *, *> {
        this.cancelable = cancelable
        return this
    }

    constructor(pool: PoolEnum) : this(MyAsyncTask::class.java, pool) {}

    override fun onCancelled() {
        rememberWhenCancelled()
        ExceptionsCounter.showErrorDialogIfErrorsPresent()
        super.onCancelled()
    }

    override fun doInBackground(vararg params: Params): Result? {
        backgroundStartedAt = System.currentTimeMillis()
        currentlyExecutingSince = backgroundStartedAt
        try {
            if (!isCancelled) {
                return doInBackground2(if (params.size > 0) params[0] else null)
            }
        } catch (e: SQLiteDiskIOException) {
            ExceptionsCounter.onDiskIoException(e)
        } catch (e: SQLiteDatabaseLockedException) {
            // see also https://github.com/greenrobot/greenDAO/issues/191
            logError("Database lock error, probably related to the application re-initialization", e)
        } catch (e: AssertionError) {
            ExceptionsCounter.logSystemInfo(e)
        } catch (e: Exception) {
            ExceptionsCounter.logSystemInfo(e)
        }
        return null
    }

    protected abstract fun doInBackground2(params: Params?): Result?

    override fun onPostExecute(result: Result) {
        backgroundEndedAt = System.currentTimeMillis()
        ExceptionsCounter.showErrorDialogIfErrorsPresent()
        onPostExecute2(result)
        super.onPostExecute(result)
        onFinish(result, true)
    }

    protected open fun onPostExecute2(result: Result) {}

    override fun onCancelled(result: Result) {
        backgroundEndedAt = System.currentTimeMillis()
        onCancelled2(result)
        super.onCancelled(result)
        onFinish(result, false)
    }

    protected open fun onCancelled2(result: Result) {}

    /** Is called in both cases: Cancelled or not, before changing status to FINISH   */
    protected open fun onFinish(result: Result?, success: Boolean) {}

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is MyAsyncTask<*, *, *>) return false

        return taskId == other.taskId
    }

    override fun hashCode(): Int {
        return taskId.hashCode()
    }

    fun isBackgroundStarted(): Boolean {
        return backgroundStartedAt > 0
    }

    fun isBackgroundCompleted(): Boolean {
        return backgroundEndedAt > 0
    }

    override fun toString(): String {
        return (taskId + " on " + pool.name
                + "; age " + RelativeTime.secondsAgo(createdAt) + "sec"
                + "; " + stateSummary()
                + "; instanceId=" + instanceId + "; " + super.toString())
    }

    private fun stateSummary(): String {
        var summary = when (status) {
            Status.PENDING -> "PENDING " + RelativeTime.secondsAgo(createdAt) + " sec ago"
            Status.FINISHED -> if (backgroundEndedAt == 0L) {
                "FINISHED, but didn't complete"
            } else {
                "FINISHED " + RelativeTime.secondsAgo(backgroundEndedAt) + " sec ago"
            }
            else -> if (backgroundStartedAt == 0L) {
                "QUEUED " + RelativeTime.secondsAgo(createdAt) + " sec ago"
            } else if (backgroundEndedAt == 0L) {
                "RUNNING for " + RelativeTime.secondsAgo(backgroundStartedAt) + " sec"
            } else {
                "FINISHING " + RelativeTime.secondsAgo(backgroundEndedAt) + " sec ago"
            }
        }
        if (isCancelled) {
            rememberWhenCancelled()
            summary += ", cancelled " + RelativeTime.secondsAgo(cancelledAt) + " sec ago"
        }
        if (!hasExecutor) summary += ", no executor"
        return summary
    }

    open fun isReallyWorking(): Boolean {
        return needsBackgroundWork() && !expectedToBeFinishedNow()
    }

    private fun expectedToBeFinishedNow(): Boolean {
        return (RelativeTime.wasButMoreSecondsAgoThan(backgroundEndedAt, DELAY_AFTER_EXECUTOR_ENDED_SECONDS)
                || RelativeTime.wasButMoreSecondsAgoThan(currentlyExecutingSince, maxCommandExecutionSeconds)
                || cancelledLongAgo()
                || (status == Status.PENDING
                && RelativeTime.wasButMoreSecondsAgoThan(createdAt, MAX_WAITING_BEFORE_EXECUTION_SECONDS)))
    }

    fun cancelledLongAgo(): Boolean {
        return RelativeTime.wasButMoreSecondsAgoThan(cancelledAt, MAX_EXECUTION_AFTER_CANCEL_SECONDS)
    }

    /** If yes, the task may be not in FINISHED state yet: during execution of onPostExecute  */
    fun completedBackgroundWork(): Boolean {
        return !needsBackgroundWork()
    }

    fun needsBackgroundWork(): Boolean {
        return when (status) {
            Status.PENDING -> true
            Status.FINISHED -> false
            else -> backgroundEndedAt == 0L
        }
    }

    fun cancelLogged(mayInterruptIfRunning: Boolean): Boolean {
        rememberWhenCancelled()
        return super.cancel(mayInterruptIfRunning)
    }

    private fun rememberWhenCancelled() {
        if (cancelledAt == 0L) {
            cancelledAt = System.currentTimeMillis()
        }
    }

    fun getFirstError(): String? {
        return firstError
    }

    private fun logError(msgLog: String?, tr: Throwable?) {
        MyLog.w(this, msgLog, tr)
        if (!firstError.isNullOrEmpty() || tr == null) {
            return
        }
        firstError = MyLog.getStackTrace(tr)
    }

    companion object {
        private const val MAX_WAITING_BEFORE_EXECUTION_SECONDS: Long = 600
        const val MAX_COMMAND_EXECUTION_SECONDS: Long = 600
        private const val MAX_EXECUTION_AFTER_CANCEL_SECONDS: Long = 600
        private const val DELAY_AFTER_EXECUTOR_ENDED_SECONDS: Long = 1
        fun nonUiThread(): Boolean {
            return !isUiThread()
        }

        // See http://stackoverflow.com/questions/11411022/how-to-check-if-current-thread-is-not-main-thread
        fun isUiThread(): Boolean {
            return Looper.myLooper() == Looper.getMainLooper()
        }

        fun <Params, Progress, Result> fromFunc(params: Params,
                                                backgroundFunc: Function<Params?, Try<Result>>,
                                                uiConsumer: Function<Params, Consumer<Try<Result>>>):
                MyAsyncTask<Params, Progress, Try<Result>> {
            return object : MyAsyncTask<Params, Progress, Try<Result>>(params, PoolEnum.LONG_UI) {

                override fun doInBackground2(params: Params?): Try<Result> {
                    return backgroundFunc.apply(params)
                }

                override fun onFinish(results: Try<Result>?, success: Boolean) {
                    val results2 = if (results == null) Try.failure(Exception("No results of the Async task"))
                        else if (success) results
                        else if (results.isFailure) results
                        else Try.failure(Exception("Failed to execute Async task"))
                    uiConsumer?.apply(params)?.accept(results2)
                }
            }
        }
    }

    init {
        this.taskId = MyStringBuilder.Companion.objToTag(taskId)
        this.pool = pool
        maxCommandExecutionSeconds = pool.maxCommandExecutionSeconds
    }
}