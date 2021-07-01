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
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function
import kotlin.coroutines.CoroutineContext

/**
 * @author yvolk@yurivolkov.com
 */
abstract class MyAsyncTask<Params, Progress, Result>(taskId: Any?, pool: PoolEnum) : IdentifiableInstance {
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

    @Volatile
    private var sDefaultExecutor: CoroutineContext = Dispatchers.Default

    /**
     * Indicates the current status of the task. Each status will be set only once
     * during the lifetime of a task.
     */
    enum class Status {
        /** Indicates that the task has not been executed yet. */
        PENDING,

        /** Indicates that the task is running. */
        RUNNING,

        /** Indicates that [AsyncTask.onPostExecute] has finished. */
        FINISHED
    }

    @Volatile
    private var mStatus = Status.PENDING
    private val mCancelled = AtomicBoolean()
    private val mTaskInvoked = AtomicBoolean()

    @Volatile
    private var job: Job? = null

    val status: Status get() = mStatus
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

    /**
     * Executes the task with the specified parameters. The task returns
     * itself (this) so that the caller can keep a reference to it.
     *
     *
     * Note: this function schedules the task on a queue for a single background
     * thread or pool of threads depending on the platform version.  When first
     * introduced, AsyncTasks were executed serially on a single background thread.
     * Starting with [android.os.Build.VERSION_CODES.DONUT], this was changed
     * to a pool of threads allowing multiple tasks to operate in parallel. Starting
     * [android.os.Build.VERSION_CODES.HONEYCOMB], tasks are back to being
     * executed on a single thread to avoid common application errors caused
     * by parallel execution.  If you truly want parallel execution, you can use
     * the [.executeOnExecutor] version of this method
     * with [.THREAD_POOL_EXECUTOR]; however, see commentary there for warnings
     * on its use.
     *
     *
     * This method must be invoked on the UI thread.
     *
     * @param params The parameters of the task.
     *
     * @return This instance of AsyncTask.
     *
     * @throws IllegalStateException If [.getStatus] returns either
     * [Status.RUNNING] or [Status.FINISHED].
     *
     * @see executeOnExecutor
     * @see execute
     */
    @MainThread
    fun execute(vararg params: Params): MyAsyncTask<Params, Progress, Result> {
        return executeOnExecutor(Dispatchers.Main, *params)
    }

    /**
     * Executes the task with the specified parameters. The task returns
     * itself (this) so that the caller can keep a reference to it.
     *
     *
     * This method is typically used with [.THREAD_POOL_EXECUTOR] to
     * allow multiple tasks to run in parallel on a pool of threads managed by
     * AsyncTask, however you can also use your own [Executor] for custom
     * behavior.
     *
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
     *
     * This method must be invoked on the UI thread.
     *
     * @param exec The executor to use.  [.THREAD_POOL_EXECUTOR] is available as a
     * convenient process-wide thread pool for tasks that are loosely coupled.
     * @param params The parameters of the task.
     *
     * @return This instance of AsyncTask.
     *
     * @throws IllegalStateException If [.getStatus] returns either
     * [Status.RUNNING] or [Status.FINISHED].
     *
     * @see execute
     */
    @MainThread
    fun executeOnExecutor(
        exec: CoroutineContext,
        vararg params: Params
    ): MyAsyncTask<Params, Progress, Result> {
        when (mStatus) {
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
        try {
            mStatus = Status.RUNNING
            onPreExecute()
            job = CoroutineScope(exec).launch {
                try {
                    val result: Result? = doInBackground(*params)
                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            onPostExecute(result)
                        }
                        onFinish(result, result != null)
                        mStatus = Status.FINISHED
                    }
                } catch (e: Exception) {
                    onFinish(null, false)
                    mStatus = Status.FINISHED
                }
            }
        } catch (e: Exception) {
            onFinish(null, false)
            mStatus = Status.FINISHED
        }
        return this
    }

    /**
     * Runs on the UI thread before [.doInBackground].
     * Invoked directly by [.execute] or [.executeOnExecutor].
     * The default version does nothing.
     *
     * @see onPostExecute
     * @see doInBackground
     */
    @MainThread
    protected open fun onPreExecute() {
    }

    open fun onCancelled() {
        ExceptionsCounter.showErrorDialogIfErrorsPresent()
    }

    fun doInBackground(vararg params: Params): Result? {
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

    fun onPostExecute(result: Result) {
        backgroundEndedAt = System.currentTimeMillis()
        ExceptionsCounter.showErrorDialogIfErrorsPresent()
        onPostExecute2(result)
    }

    protected open fun onPostExecute2(result: Result) {}

    fun onCancelled(result: Result) {
        backgroundEndedAt = System.currentTimeMillis()
        onCancelled2(result)
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

    /**
     * <p>Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when <tt>cancel</tt> is called,
     * this task should never run. If the task has already started,
     * then the <tt>mayInterruptIfRunning</tt> parameter determines
     * whether the thread executing this task should be interrupted in
     * an attempt to stop the task.</p>
     *
     * <p>Calling this method will result in {@link #onCancelled(Object)} being
     * invoked on the UI thread after {@link #doInBackground(Object[])} returns.
     * Calling this method guarantees that onPostExecute(Object) is never
     * subsequently invoked, even if <tt>cancel</tt> returns false, but
     * {@link #onPostExecute} has not yet run.  To finish the
     * task as early as possible, check {@link #isCancelled()} periodically from
     * {@link #doInBackground(Object[])}.</p>
     *
     * <p>This only requests cancellation. It never waits for a running
     * background task to terminate, even if <tt>mayInterruptIfRunning</tt> is
     * true.</p>
     *
     * @see isCancelled
     * @see onCancelled
     */
    fun cancel() {
        if (cancelledAt == 0L) {
            cancelledAt = System.currentTimeMillis()
        }
        mCancelled.set(true)
        job?.cancel()
        onCancelled()
        mStatus = Status.FINISHED
    }

    /**
     * This method can be invoked from [.doInBackground] to
     * publish updates on the UI thread while the background computation is
     * still running. Each call to this method will trigger the execution of
     * [.onProgressUpdate] on the UI thread.
     *
     * [.onProgressUpdate] will not be called if the task has been
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
     * Runs on the UI thread after [.publishProgress] is invoked.
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

        fun <Params, Progress, Result> fromFunc(
            params: Params,
            backgroundFunc: Function<Params?, Try<Result>>,
            uiConsumer: Function<Params, Consumer<Try<Result>>>
        ):
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
        this.taskId = MyStringBuilder.objToTag(taskId)
        this.pool = pool
        maxCommandExecutionSeconds = pool.maxCommandExecutionSeconds
    }
}
