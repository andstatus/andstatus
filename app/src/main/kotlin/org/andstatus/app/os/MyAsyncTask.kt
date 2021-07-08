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
import kotlinx.coroutines.yield
import org.andstatus.app.util.IdentifiableInstance
import org.andstatus.app.util.InstanceId
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.TryUtils.isCancelled
import org.andstatus.app.util.TryUtils.onSuccessS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * @author yvolk@yurivolkov.com
 */
abstract class MyAsyncTask<Params, Progress, Result>(taskId: Any?, val pool: PoolEnum) : IdentifiableInstance {
    constructor(pool: PoolEnum) : this(MyAsyncTask::class.java, pool)

    var maxCommandExecutionSeconds = pool.maxCommandExecutionSeconds
    private val taskId: String = MyStringBuilder.objToTag(taskId)
    protected val createdAt = MyLog.uniqueCurrentTimeMS()
    override val instanceId = InstanceId.next()

    val startedAt: AtomicLong = AtomicLong()
    val backgroundStartedAt: AtomicLong = AtomicLong()

    val backgroundEndedAt: AtomicLong = AtomicLong()
    val finishedAt: AtomicLong = AtomicLong()

    /** This allows to control execution time of single steps/commands by this task  */
    protected val currentlyExecutingSince: AtomicLong = AtomicLong()

    /** Description of execution or lack of it  */
    @Volatile
    protected var currentlyExecutingDescription: String = "(didn't start)"

    open val cancelable = true
    private var cancelledAt: AtomicLong = AtomicLong()

    @Volatile
    var firstError: String = ""
        private set

    val hasExecutor = AtomicBoolean(true)
    @Volatile
    var job: Job? = null

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

    val isCancelled: Boolean get() = onCancelCalled.get()

    enum class PoolEnum(val corePoolSize: Int, val maxCommandExecutionSeconds: Long) {
        SYNC(0, MAX_COMMAND_EXECUTION_SECONDS),
        FILE_DOWNLOAD(0, MAX_COMMAND_EXECUTION_SECONDS),
        QUICK_UI(2, 20),
        DEFAULT_POOL(0, MAX_COMMAND_EXECUTION_SECONDS)
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
    fun executeInContext(asyncCoroutineContext: CoroutineContext, params: Params): MyAsyncTask<Params, Progress, Result> {
        when (status) {
            Status.RUNNING -> throw java.lang.IllegalStateException(
                "Cannot execute task: the task is already running."
            )
            Status.FINISHED -> throw java.lang.IllegalStateException(
                ("Cannot execute task: the task has already been executed (a task can be executed only once)")
            )
            else -> Unit
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                startedAt.set(System.currentTimeMillis())
                status = Status.RUNNING
                onPreExecute()
                withContext(asyncCoroutineContext) { doInBackground1(params) }
                    .onSuccessS { onPostExecute(it) }
                    .also { onFinish1(it) }
            } catch (e: CancellationException) {
                CoroutineScope(Dispatchers.Main).launch {
                    onCancel1()
                }
                yield()
            } catch (e: Exception) {
                when(e) {
                    is SQLiteDiskIOException -> {
                        ExceptionsCounter.onDiskIoException(e)
                    }
                    is SQLiteDatabaseLockedException -> {
                        // see also https://github.com/greenrobot/greenDAO/issues/191
                        logError("Database lock error, probably related to the application re-initialization", e)
                    }
                    is AssertionError -> {
                        ExceptionsCounter.logSystemInfo(e)
                    }
                    else -> ExceptionsCounter.logSystemInfo(e)
                }
                logError("Exception during execution", e)
                CoroutineScope(Dispatchers.Main).launch {
                    onFinish1(Try.failure(e))
                }
                yield()
            }
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
    protected open suspend fun onPreExecute() {}

    private suspend fun doInBackground1(params: Params): Try<Result> {
        backgroundStartedAt.set(System.currentTimeMillis())
        currentlyExecutingSince.set(backgroundStartedAt.get())
        try {
            return doInBackground(params)
        } finally {
            backgroundEndedAt.set(System.currentTimeMillis())
        }
    }

    protected abstract suspend fun doInBackground(params: Params): Try<Result>

    private val cancelCalled = AtomicBoolean()
    /**
     * <p>Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when <tt>cancel</tt> is called,
     * this task should never run.</p>
     *
     * <p>Calling this method will result in [onCancel] being
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
     * @see onCancel
     */
    fun cancel() {
        if (cancelCalled.compareAndSet(false, true)) {
            if (isCancelled || status == Status.FINISHED) return

            CoroutineScope(Dispatchers.Main).launch {
                MyLog.v(this, "Cancelling $this")
                job?.cancel()
                onCancel1()
            }
        }
    }

    private val onCancelCalled = AtomicBoolean()
    private suspend fun onCancel1() {
        if (onCancelCalled.compareAndSet(false, true)) {
            cancelledAt.set(System.currentTimeMillis())
            try {
                job?.join()
                onCancel()
            } finally {
                onFinish1(TryUtils.cancelled())
            }
        }
    }

    @MainThread
    protected open suspend fun onCancel() {}

    @MainThread
    protected open suspend fun onPostExecute(result: Result) {}

    private val onFinishCalled = AtomicBoolean()
    private suspend fun onFinish1(result: Try<Result>) {
        if (onFinishCalled.compareAndSet(false, true)) {
            try {
                onFinish(result)
                ExceptionsCounter.showErrorDialogIfErrorsPresent()
            } finally {
                finishedAt.set(System.currentTimeMillis())
                status = Status.FINISHED
            }
        }
    }

    /** If the task was started, this method should be called, before changing status to FINISH */
    @MainThread
    protected open suspend fun onFinish(result: Try<Result>) {}

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is MyAsyncTask<*, *, *>) return false

        return taskId == other.taskId
    }

    override fun hashCode(): Int {
        return taskId.hashCode()
    }

    fun isBackgroundStarted(): Boolean {
        return backgroundStartedAt.get() > 0
    }

    fun isBackgroundCompleted(): Boolean {
        return backgroundEndedAt.get() > 0
    }

    override fun toString(): String {
        return (taskId + " on " + pool.name +
                ", age: " + RelativeTime.secMsAgo(createdAt) +
                ", " + stateSummary() +
                ", instanceId: " + instanceId)
    }

    private fun stateSummary(): String {
        var summary = when (status) {
            Status.PENDING -> "PENDING: " + RelativeTime.secMsAgo(createdAt)
            Status.RUNNING -> when {
                startedAt.get() == 0L -> {
                    "QUEUED: " + RelativeTime.secMsAgo(createdAt)
                }
                backgroundStartedAt.get() == 0L -> {
                    "PRE-EXECUTING: " + RelativeTime.secMsAgo(startedAt.get())
                }
                backgroundEndedAt.get() == 0L ->
                    "RUNNING in background: " + RelativeTime.secMsAgo(backgroundStartedAt.get()) +
                            if (currentlyExecutingSince.get() > backgroundEndedAt.get()) {
                                ", Currently executing: " + RelativeTime.secMs(finishedAt.get() - startedAt.get())
                            } else ""
                else -> {
                    "FINISHING: " + RelativeTime.secMsAgo(backgroundEndedAt.get())
                }
            }
            Status.FINISHED ->
                when {
                    startedAt.get() == 0L -> {
                        "FINISHED but didn't start: " + RelativeTime.secMsAgo(finishedAt)
                    }
                    backgroundEndedAt.get() == 0L -> {
                        "FINISHED but didn't end: " + RelativeTime.secMsAgo(finishedAt)
                    }
                    else -> {
                        "FINISHED: " + RelativeTime.secMsAgo(finishedAt) +
                                ", worked: " + RelativeTime.secMs(finishedAt.get() - startedAt.get())
                        ", in background: " + RelativeTime.secMs(
                            backgroundEndedAt.get() - backgroundStartedAt.get()
                        )
                    }
                }
        }
        if (isCancelled) {
            summary += ", cancelled " + RelativeTime.secMsAgo(cancelledAt) + " ago"
        }
        if (!hasExecutor.get()) summary += ", no executor"
        return summary
    }

    open fun isReallyWorking(): Boolean {
        return needsBackgroundWork() && !expectedToBeFinishedNow()
    }

    private fun expectedToBeFinishedNow(): Boolean {
        return (RelativeTime.wasButMoreSecondsAgoThan(backgroundEndedAt.get(), DELAY_AFTER_EXECUTOR_ENDED_SECONDS)
                || RelativeTime.wasButMoreSecondsAgoThan(currentlyExecutingSince.get(), maxCommandExecutionSeconds)
                || cancelledLongAgo()
                || (status == Status.PENDING
                && RelativeTime.wasButMoreSecondsAgoThan(createdAt, MAX_WAITING_BEFORE_EXECUTION_SECONDS)))
    }

    fun cancelledLongAgo(): Boolean {
        return RelativeTime.wasButMoreSecondsAgoThan(cancelledAt.get(), MAX_EXECUTION_AFTER_CANCEL_SECONDS)
    }

    /** If yes, the task may be not in FINISHED state yet: during execution of onPostExecute  */
    fun completedBackgroundWork(): Boolean {
        return !needsBackgroundWork()
    }

    fun needsBackgroundWork(): Boolean {
        if (isCancelled && backgroundStartedAt.get() == 0L) return false
        return when (status) {
            Status.PENDING -> true
            Status.FINISHED -> false
            else -> backgroundEndedAt.get() == 0L
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
    protected suspend fun publishProgress(values: Progress) {
        if (!isCancelled) {
            withContext(Dispatchers.Main) {
                onProgressUpdate(values)
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
    protected open suspend fun onProgressUpdate(values: Progress) {
    }

    private fun logError(msgLog: String, tr: Throwable?) {
        if (tr is kotlinx.coroutines.CancellationException) {
            return
        }
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
                MyAsyncTask<Params, Progress, Result> {
            return object : MyAsyncTask<Params, Progress, Result>(params, PoolEnum.DEFAULT_POOL) {

                override suspend fun doInBackground(params: Params): Try<Result> {
                    return backgroundFunc(params)
                }

                override suspend fun onFinish(result: Try<Result>) {
                    uiConsumer(params)(result)
                }
            }
        }
    }
}
