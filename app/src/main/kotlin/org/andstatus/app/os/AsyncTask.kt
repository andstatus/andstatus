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
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import io.vavr.control.Try
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.andstatus.app.util.Identifiable
import org.andstatus.app.util.Identified
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.Taggable
import org.andstatus.app.util.TryUtils
import org.andstatus.app.util.TryUtils.isCancelled
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

typealias AsyncRunnable = AsyncTask<Unit, Unit, Unit>
typealias AsyncEffects<Params> = AsyncTask<Params, Unit, Unit>
typealias AsyncResult<Params, Result> = AsyncTask<Params, Unit, Result>

/**
 * AsyncTask implementation using Kotlin Coroutines.
 * No dependencies on (deprecated in API 30) Android's AsyncTask
 * @author yvolk@yurivolkov.com
 */
open class AsyncTask<Params, Progress, Result>(
    taskId: Any,
    val pool: AsyncEnum,
    open val cancelable: Boolean = true,
    identifiable: Identifiable = Identified.fromAny(taskId)
) : Identifiable by identifiable {

    constructor(pool: AsyncEnum) : this(AsyncTask::class, pool)

    var maxCommandExecutionSeconds = pool.maxCommandExecutionSeconds
    private val taskId: String = Taggable.anyToTag(taskId)
    protected val createdAt = MyLog.uniqueCurrentTimeMS

    val isPending: Boolean get() = startedAt.get() == 0L && !isFinished
    val isRunning: Boolean get() = startedAt.get() > 0 && !isFinished
    val startedAt: AtomicLong = AtomicLong()
    val backgroundStartedAt: AtomicLong = AtomicLong()
    val backgroundEndedAt: AtomicLong = AtomicLong()

    /** This allows to control execution time of single steps/commands by this task  */
    protected val currentlyExecutingSince: AtomicLong = AtomicLong()

    /** Description of execution or lack of it  */
    @Volatile
    protected var currentlyExecutingDescription: String = "(didn't start)"

    val isCancelled: Boolean get() = cancelledAt.get() > 0
    private var cancelledAt: AtomicLong = AtomicLong()

    val isFinished: Boolean get() = finishedAt.get() > 0
    val finishedAt: AtomicLong = AtomicLong()

    @Volatile
    var firstError: String = ""
        private set

    @Volatile
    var job: Job? = null

    fun execute(params: Params): Try<AsyncTask<Params, Progress, Result>> = execute(params, params)
    fun execute(anyTag: Any?, params: Params): Try<AsyncTask<Params, Progress, Result>> =
        AsyncTaskLauncher.execute(anyTag, this, params)

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
     * @throws IllegalStateException If the task isRunning or isFinished
     */
    @MainThread
    fun executeInContext(asyncCoroutineContext: CoroutineContext, params: Params): AsyncTask<Params, Progress, Result> {
        when {
            isRunning -> throw java.lang.IllegalStateException(
                "Cannot execute task: the task is already running."
            )
            isFinished -> throw java.lang.IllegalStateException(
                ("Cannot execute task: the task has already been executed (a task can be executed only once)")
            )
            else -> Unit
        }
        paramsRef.set(Try.success(params))
        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                startedAt.set(System.currentTimeMillis())
                onPreExecute()
                withContext(asyncCoroutineContext) { doInBackground1(params) }
                    .also { onPostExecute1(it) }
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
                    onPostExecute1(Try.failure(e))
                }
                yield()
            }
        }
        return this
    }
    private val paramsRef: AtomicReference<Try<Params>> = AtomicReference(TryUtils.notFound())

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
            return (backgroundFun.get()?.let { it(params) } ?: doInBackground(params))
                .also { resultRef.set(it) }
        } finally {
            currentlyExecutingSince.set(0)
            backgroundEndedAt.set(System.currentTimeMillis())
        }
    }

    var result: Try<Result> get() = resultRef.get()
        set(value) {
            startedAt.set(System.currentTimeMillis())
            backgroundStartedAt.set(System.currentTimeMillis())
            backgroundEndedAt.set(System.currentTimeMillis())
            resultRef.set(value)
            finishedAt.set(System.currentTimeMillis())
        }
    private val resultRef: AtomicReference<Try<Result>> = AtomicReference(TryUtils.notFound())

    protected open suspend fun doInBackground(params: Params): Try<Result> = TryUtils.notFound()

    private val backgroundFun: AtomicReference<suspend (Params) -> Try<Result>> = AtomicReference()
    fun doInBackground(backgroundFun: suspend (Params) -> Try<Result>): AsyncTask<Params, Progress, Result> {
        this.backgroundFun.set(backgroundFun)
        return this
    }

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
     * Calling this method guarantees that onPostExecute(Object) is
     * subsequently invoked with [Try.Failure] only, even if <tt>cancel</tt> returns false, but
     * [onPostExecute] has not yet run.  To finish the
     * task as early as possible, check [isCancelled] periodically from
     * [doInBackground].</p>
     *
     * <p>This only requests cancellation. It never waits for a running
     * background task to terminate.</p>
     *
     * @see isCancelled
     * @see onCancel
     */
    fun cancel() {
        if (cancelCalled.compareAndSet(false, true)) {
            if (isCancelled || isFinished || noMoreBackgroundWork) return

            CoroutineScope(Dispatchers.Main).launch {
                MyLog.v(this, "Cancelling $this")
                job?.cancel()
                onCancel1()
            }
        }
    }

    private suspend fun onCancel1() {
        if (cancelledAt.compareAndSet(0, System.currentTimeMillis())) {
            try {
                job?.join()
                onCancel()
            } finally {
                onPostExecute1(TryUtils.cancelled())
            }
        }
    }

    @MainThread
    protected open suspend fun onCancel() {}

    private val onPostExecuteCalled = AtomicBoolean()
    private suspend fun onPostExecute1(result: Try<Result>) {
        if (onPostExecuteCalled.compareAndSet(false, true)) {
            try {
                postExecuteFun.get()?.let {
                    it(
                        paramsRef.get().getOrElseThrow { IllegalStateException("No params in $this") },
                        result
                    )
                } ?: onPostExecute(result)
                ExceptionsCounter.showErrorDialogIfErrorsPresent()
            } finally {
                finishedAt.set(System.currentTimeMillis())
            }
        }
    }

    /** If the task was started, this method should be called, before changing status to FINISH */
    @MainThread
    protected open suspend fun onPostExecute(result: Try<Result>) {}

    private val postExecuteFun: AtomicReference<suspend (Params, Try<Result>) -> Unit> = AtomicReference()
    fun onPostExecute(postExecuteFun: suspend (Params, Try<Result>) -> Unit): AsyncTask<Params, Progress, Result> {
        this.postExecuteFun.set(postExecuteFun)
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is AsyncTask<*, *, *>) return false

        /** Actually equals to itself only */
        return false
    }

    override fun hashCode(): Int {
        return taskId.hashCode()
    }

    val isBackgroundStarted: Boolean get() = backgroundStartedAt.get() > 0
    val isBackgroundEnded: Boolean get() = backgroundEndedAt.get() > 0

    override fun toString(): String {
        return (instanceTag + " on " + pool.name +
            ", age: " + RelativeTime.secMsAgo(createdAt) +
            (if (startedAt.get() > 0) ", started: " + RelativeTime.secMsAgo(startedAt) else ", not started") +
            ", " + stateSummary +
            ", instanceId: " + instanceId)
    }

    private val stateSummary: String
        get() = (if (isCancelled) "Cancelled " + RelativeTime.secMsAgo(cancelledAt) + ", " else "") +
                stateSummary2()

    private fun stateSummary2() = when {
        isPending -> "PENDING: " + RelativeTime.secMsAgo(createdAt)
        isFinished -> when {
            startedAt.get() == 0L -> {
                "FINISHED but didn't start: " + RelativeTime.secMsAgo(finishedAt)
            }
            backgroundEndedAt.get() == 0L -> {
                "FINISHED but didn't complete background work: " + RelativeTime.secMsAgo(finishedAt)
            }
            else -> {
                "FINISHED: " + RelativeTime.secMsAgo(finishedAt) +
                        ", worked: " + RelativeTime.secMs(finishedAt.get() - startedAt.get())
                ", in background: " + RelativeTime.secMs(
                    backgroundEndedAt.get() - backgroundStartedAt.get()
                )
            }
        }
        else -> when {
            startedAt.get() == 0L -> {
                "QUEUED: " + RelativeTime.secMsAgo(createdAt)
            }
            !isBackgroundStarted -> {
                "PRE-EXECUTING: " + RelativeTime.secMsAgo(startedAt.get())
            }
            !isBackgroundEnded ->
                "RUNNING in background: " + RelativeTime.secMsAgo(backgroundStartedAt.get()) +
                        if (currentlyExecutingSince.get() > backgroundEndedAt.get()) {
                            ", Currently executing: " + RelativeTime.secMsAgo(currentlyExecutingSince.get())
                        } else ""
            else -> {
                "FINISHING: " + RelativeTime.secMsAgo(backgroundEndedAt.get())
            }
        }
    }

    open val isReallyWorking: Boolean get() = needsBackgroundWork && !expectedToBeFinishedNow

    private val expectedToBeFinishedNow: Boolean
        get() = (RelativeTime.wasButMoreSecondsAgoThan(backgroundEndedAt.get(), DELAY_AFTER_EXECUTOR_ENDED_SECONDS)
                || RelativeTime.wasButMoreSecondsAgoThan(currentlyExecutingSince.get(), maxCommandExecutionSeconds)
                || cancelledLongAgo
                || isPending
                && RelativeTime.wasButMoreSecondsAgoThan(createdAt, MAX_WAITING_BEFORE_EXECUTION_SECONDS))

    val cancelledLongAgo: Boolean
        get() = RelativeTime.wasButMoreSecondsAgoThan(cancelledAt.get(), MAX_EXECUTION_AFTER_CANCEL_SECONDS)

    /** If yes, the task may be not in FINISHED state yet: during execution of onPostExecute  */
    val noMoreBackgroundWork: Boolean get() = !needsBackgroundWork

    val needsBackgroundWork: Boolean
        get() = when {
            isCancelled && !isBackgroundStarted -> false
            isPending -> true
            isFinished -> false
            else -> backgroundEndedAt.get() == 0L
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
        if (firstError.isEmpty() && tr != null) {
            firstError = msgLog + "\n" + MyLog.getStackTrace(tr)
        }
    }

    companion object {
        private const val MAX_WAITING_BEFORE_EXECUTION_SECONDS: Long = 600
        private const val MAX_EXECUTION_AFTER_CANCEL_SECONDS: Long = 600
        private const val DELAY_AFTER_EXECUTOR_ENDED_SECONDS: Long = 1
    }
}
