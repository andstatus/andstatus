/*
 * Copyright (c) 2016-2021 yvolk (Yuri Volkov), http://yurivolkov.com
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
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.vavr.control.Try
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

/**
 * @author yvolk@yurivolkov.com
 */
abstract class MyAsyncTask<Params, Progress, Result>(taskId: Any?, val pool: PoolEnum) : IdentifiableInstance {
    constructor(pool: PoolEnum) : this(MyAsyncTask::class.java, pool)

    var maxCommandExecutionSeconds = pool.maxCommandExecutionSeconds
    private val taskId: String = MyStringBuilder.objToTag(taskId)
    protected val createdAt = MyLog.uniqueCurrentTimeMS()
    override val instanceId = InstanceId.next()

    @Volatile
    var backgroundStartedAt: Long = 0

    @Volatile
    var backgroundEndedAt: Long = 0

    /** This allows to control execution time of single steps/commands by this task  */
    @Volatile
    protected var currentlyExecutingSince: Long = 0

    /** Description of execution or lack of it  */
    @Volatile
    protected var currentlyExecutingDescription: String = "(didn't start)"

    open val cancelable = true
    @Volatile
    private var cancelledAt: Long = 0

    @Volatile
    var firstError: String = ""
        private set

    val hasExecutor = AtomicBoolean(true)
    @Volatile
    private var job: Job? = null

    /**
     * Indicates the current status of the task. Each status will be set only once
     * during the lifetime of a task.
     */
    enum class Status {
        /** Indicates that the task has not been executed yet. */
        PENDING,

        /** Indicates that the task is running. */
        RUNNING,

        /** Indicates that [onFinish] has finished. */
        FINISHED
    }

    private val mStatus = AtomicReference(Status.PENDING)
    var status: Status get() = mStatus.get()
        private set(value) = mStatus.set(value)

    private val mCancelled = AtomicBoolean()
    val isCancelled: Boolean get() = mCancelled.get()

    enum class PoolEnum(val corePoolSize: Int, val maxCommandExecutionSeconds: Long, val mayBeShutDown: Boolean) {
        SYNC(3, MAX_COMMAND_EXECUTION_SECONDS, true),
        FILE_DOWNLOAD(1, MAX_COMMAND_EXECUTION_SECONDS, true),
        QUICK_UI(0, 20, false),
        LONG_UI(1, MAX_COMMAND_EXECUTION_SECONDS, true),
        DATABASE_WRITE(0, 20, false);

        companion object {
            fun thatCannotBeShutDown(): PoolEnum {
                for (pool in values()) {
                    if (!pool.mayBeShutDown) return pool
                }
                throw IllegalStateException("All pools may be shut down")
            }
        }
    }

    /**
     * Executes the task with the specified parameters. The task returns
     * itself (this) so that the caller can keep a reference to it.
     *
     * *Warning:* Allowing multiple tasks to run in parallel from
     * a thread pool is generally *not* what one wants, because the order
     * of their operation is not defined.  For example, if these tasks are used
     * to modify any state in common (such as writing a file due to a button click),
     * there are no guarantees on the order of the modifications.
     * Without careful work it is possible in rare cases for the newer version
     * of the data to be over-written by an older one, leading to obscure data
     * loss and stability issues.  Such changes are best
     * executed in serial; to guarantee such work is serialized regardless of
     * platform version you can use this function with [.SERIAL_EXECUTOR].
     *
     * This method must be invoked on the UI thread.
     *
     * @param asyncCoroutineContext The CoroutineContext to use for background work.
     * @param params The parameters of the task.
     *
     * @return This instance
     *
     * @throws IllegalStateException If [.getStatus] returns either
     * [Status.RUNNING] or [Status.FINISHED].
     */
    @MainThread
    fun executeInContext(
        asyncCoroutineContext: CoroutineContext,
        vararg params: Params
    ): MyAsyncTask<Params, Progress, Result> {
        when (status) {
            Status.RUNNING -> throw java.lang.IllegalStateException(
                "Cannot execute task:"
                        + " the task is already running."
            )
            Status.FINISHED -> throw java.lang.IllegalStateException(
                ("Cannot execute task:"
                        + " the task has already been executed "
                        + "(a task can be executed only once)")
            )
            else -> {
            }
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            var result: Result? = null
            var success = false
            status = Status.RUNNING
            try {
                onPreExecute()
                withContext(asyncCoroutineContext) {
                    try {
                        result = doInBackground1(*params)
                        success = true // TOD: better result type needed
                    } catch (e: Exception) {
                        logError("Exception during background execution", e)
                    }
                    backgroundEndedAt = System.currentTimeMillis()
                }
                result?.also {
                    onPostExecute(it)
                }
            } catch (e: Exception) {
                logError("Exception during execution", e)
            }
            onFinish1(result, success)
        }
        return this
    }

    /**
     * Runs on the UI thread before [doInBackground].
     * Invoked directly by [executeInContext].
     * The default version does nothing.
     *
     * @see onPostExecute
     * @see doInBackground
     */
    @MainThread
    protected open fun onPreExecute() {
    }

    private fun doInBackground1(vararg params: Params): Result? {
        backgroundStartedAt = System.currentTimeMillis()
        currentlyExecutingSince = backgroundStartedAt
        try {
            if (!isCancelled) {
                return doInBackground(if (params.isNotEmpty()) params[0] else null)
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

    protected abstract fun doInBackground(params: Params?): Result?

    /**
     * <p>Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when <tt>cancel</tt> is called,
     * this task should never run.</p>
     *
     * <p>Calling this method will result in [onCancelled] being
     * invoked on the UI thread after [doInBackground] returns.
     * Calling this method guarantees that onPostExecute(Object) is never
     * subsequently invoked, even if <tt>cancel</tt> returns false, but
     * [onPostExecute] has not yet run.  To finish the
     * task as early as possible, check [isCancelled] periodically from
     * [doInBackground].</p>
     *
     * <p>This only requests cancellation. It never waits for a running
     * background task to terminate, even if <tt>mayInterruptIfRunning</tt> is
     * true.</p>
     *
     * @see isCancelled
     * @see onCancelled
     */
    fun cancel() {
        if (mCancelled.compareAndSet(false, true)) {
            cancelledAt = System.currentTimeMillis()
            MyLog.v(this, "Cancelling $this")
            job?.cancel()
            CoroutineScope(Dispatchers.Main).launch {
                job?.join()
                onCancelled()
                onFinish1(null, false)
            }
        }
    }

    @MainThread
    protected open fun onCancelled() {}

    @MainThread
    protected open fun onPostExecute(result: Result) {}

    private val onFinishCalled = AtomicBoolean()
    private fun onFinish1(result: Result?, success: Boolean) {
        if (onFinishCalled.compareAndSet(false, true)) {
            try {
                onFinish(result, success)
                ExceptionsCounter.showErrorDialogIfErrorsPresent()
            } finally {
                status = Status.FINISHED
            }
        }
    }

    /** If the task was started, this method should be called, before changing status to FINISH */
    @MainThread
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
                + "; instanceId=" + instanceId)
    }

    private fun stateSummary(): String {
        var summary = when (status) {
            Status.PENDING -> "PENDING " + RelativeTime.secondsAgo(createdAt) + " sec ago"
            Status.FINISHED ->
                if (backgroundEndedAt == 0L) {
                    "FINISHED, but didn't complete"
                } else {
                    "FINISHED " + RelativeTime.secondsAgo(backgroundEndedAt) + " sec ago"
                }
            else -> when {
                backgroundStartedAt == 0L -> {
                    "QUEUED " + RelativeTime.secondsAgo(createdAt) + " sec ago"
                }
                backgroundEndedAt == 0L -> {
                    "RUNNING for " + RelativeTime.secondsAgo(backgroundStartedAt) + " sec"
                }
                else -> {
                    "FINISHING " + RelativeTime.secondsAgo(backgroundEndedAt) + " sec ago"
                }
            }
        }
        if (isCancelled) {
            summary += ", cancelled " + RelativeTime.secondsAgo(cancelledAt) + " sec ago"
        }
        if (!hasExecutor.get()) summary += ", no executor"
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

    /**
     * This method can be invoked from [doInBackground] to
     * publish updates on the UI thread while the background computation is
     * still running. Each call to this method will trigger the execution of
     * [onProgressUpdate] on the UI thread.
     *
     * [onProgressUpdate] will not be called if the task has been
     * canceled.
     *
     * @param values The progress values to update the UI with.
     *
     * @see onProgressUpdate
     * @see doInBackground
     */
    @WorkerThread
    protected fun publishProgress(vararg values: Progress) {
        if (!isCancelled) {
            CoroutineScope(Dispatchers.Main).launch {
                onProgressUpdate(*values)
            }
        }
    }

    /**
     * Runs on the UI thread after [publishProgress] is invoked.
     * The specified values are the values passed to [.publishProgress].
     * The default version does nothing.
     *
     * @param values The values indicating progress.
     *
     * @see publishProgress
     * @see doInBackground
     */
    @MainThread
    protected open fun onProgressUpdate(vararg values: Progress) {
    }

    private fun logError(msgLog: String, tr: Throwable?) {
        MyLog.w(this, msgLog, tr)
        if (firstError.isNotEmpty() || tr == null) {
            return
        }
        firstError = msgLog + "\n" + MyLog.getStackTrace(tr)
    }

    companion object {
        private const val MAX_WAITING_BEFORE_EXECUTION_SECONDS: Long = 600
        private const val MAX_COMMAND_EXECUTION_SECONDS: Long = 600
        private const val MAX_EXECUTION_AFTER_CANCEL_SECONDS: Long = 600
        private const val DELAY_AFTER_EXECUTOR_ENDED_SECONDS: Long = 1

        fun nonUiThread(): Boolean {
            return !isUiThread()
        }

        // See http://stackoverflow.com/questions/11411022/how-to-check-if-current-thread-is-not-main-thread
        fun isUiThread(): Boolean {
            return Looper.myLooper() == Looper.getMainLooper()
        }

        fun <Params, Progress, Result> fromFunc(
            params: Params,
            backgroundFunc: (Params?) -> Try<Result>,
            uiConsumer: (Params?) -> (Try<Result>) -> Unit
        ):
                MyAsyncTask<Params, Progress, Try<Result>> {
            return object : MyAsyncTask<Params, Progress, Try<Result>>(params, PoolEnum.LONG_UI) {

                override fun doInBackground(params: Params?): Try<Result> {
                    return backgroundFunc(params)
                }

                override fun onFinish(result: Try<Result>?, success: Boolean) {
                    val result2 = when {
                        result == null -> Try.failure(Exception("No results of the Async task"))
                        success -> result
                        result.isFailure -> result
                        else -> Try.failure(Exception("Failed to execute Async task"))
                    }
                    uiConsumer(params)(result2)
                }
            }
        }
    }
}
