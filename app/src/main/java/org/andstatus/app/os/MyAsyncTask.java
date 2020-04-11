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

package org.andstatus.app.os;

import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteDiskIOException;
import android.os.AsyncTask;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.acra.ACRA;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtil;

import java.util.function.Consumer;
import java.util.function.Function;

import io.vavr.control.Try;

import static org.andstatus.app.os.ExceptionsCounter.logSystemInfo;
import static org.andstatus.app.os.ExceptionsCounter.onDiskIoException;

/**
 * @author yvolk@yurivolkov.com
 */
public abstract class MyAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result>
        implements IdentifiableInstance {
    private static final long MAX_WAITING_BEFORE_EXECUTION_SECONDS = 600;
    static final long MAX_COMMAND_EXECUTION_SECONDS = 600;
    private static final long MAX_EXECUTION_AFTER_CANCEL_SECONDS = 600;
    private static final long DELAY_AFTER_EXECUTOR_ENDED_SECONDS = 1;
    private long maxCommandExecutionSeconds = MAX_COMMAND_EXECUTION_SECONDS;

    private final String taskId;
    protected final long createdAt = MyLog.uniqueCurrentTimeMS();
    protected final long instanceId = InstanceId.next();
    private boolean singleInstance = true;

    protected volatile long backgroundStartedAt;
    protected volatile long backgroundEndedAt;
    /** This allows to control execution time of single steps/commands by this AsyncTask */
    protected volatile long currentlyExecutingSince = 0;

    boolean cancelable = true;
    private volatile long cancelledAt = 0;
    private volatile String firstError = "";
    volatile boolean hasExecutor = true;

    public enum PoolEnum {
        SYNC(2, MAX_COMMAND_EXECUTION_SECONDS, true),
        FILE_DOWNLOAD(1, MAX_COMMAND_EXECUTION_SECONDS, true),
        QUICK_UI(0, 20, false),
        LONG_UI(1, MAX_COMMAND_EXECUTION_SECONDS, true);

        protected final int corePoolSize;
        final long maxCommandExecutionSeconds;
        final boolean mayBeShutDown;

        PoolEnum(int corePoolSize, long maxCommandExecutionSeconds, boolean mayBeShutDown) {
            this.corePoolSize = corePoolSize;
            this.maxCommandExecutionSeconds = maxCommandExecutionSeconds;
            this.mayBeShutDown = mayBeShutDown;
        }

        public static PoolEnum thatCannotBeShutDown() {
            for (PoolEnum pool: PoolEnum.values()) {
                if (!pool.mayBeShutDown) return pool;
            }
            throw new IllegalStateException("All pools may be shut down");
        }
    }

    public final PoolEnum pool;
    public boolean isSingleInstance() {
        return singleInstance;
    }

    public void setSingleInstance(boolean singleInstance) {
        this.singleInstance = singleInstance;
    }

    public MyAsyncTask setMaxCommandExecutionSeconds(long seconds) {
        this.maxCommandExecutionSeconds = seconds;
        return this;
    }

    public MyAsyncTask setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public MyAsyncTask(PoolEnum pool) {
        this(MyAsyncTask.class, pool);
    }

    public MyAsyncTask(@NonNull Object taskId, PoolEnum pool) {
        this.taskId = MyStringBuilder.objToTag(taskId);
        this.pool = pool;
        maxCommandExecutionSeconds = pool.maxCommandExecutionSeconds;
    }

    @Override
    protected void onCancelled() {
        rememberWhenCancelled();
        ExceptionsCounter.showErrorDialogIfErrorsPresent();
        super.onCancelled();
    }

    @Override
    protected final Result doInBackground(Params... params) {
        backgroundStartedAt = System.currentTimeMillis();
        currentlyExecutingSince = backgroundStartedAt;
        try {
            if (!isCancelled()) {
                return doInBackground2(params != null && params.length > 0 ? params[0] : null);
            }
        } catch (SQLiteDiskIOException e) {
            onDiskIoException(e);
        } catch (SQLiteDatabaseLockedException e) {
            // see also https://github.com/greenrobot/greenDAO/issues/191
            logError("Database lock error, probably related to the application re-initialization", e);
        } catch (AssertionError | Exception e) {
            logSystemInfo(e);
        }
        return null;
    }

    protected abstract Result doInBackground2(Params params);

    @Override
    final protected void onPostExecute(Result result) {
        backgroundEndedAt = System.currentTimeMillis();
        ExceptionsCounter.showErrorDialogIfErrorsPresent();
        onPostExecute2(result);
        super.onPostExecute(result);
        onFinish(result, true);
    }

    protected void onPostExecute2(Result result) {
    }

    @Override
    final protected void onCancelled(Result result) {
        backgroundEndedAt = System.currentTimeMillis();
        onCancelled2(result);
        super.onCancelled(result);
        onFinish(result, false);
    }

    protected void onCancelled2(Result result) {
    }

    /** Is called in both cases: Cancelled or not, before changing status to FINISH  */
    protected void onFinish(Result result, boolean success) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MyAsyncTask<?, ?, ?> that = (MyAsyncTask<?, ?, ?>) o;

        return taskId.equals(that.taskId);
    }

    @Override
    public int hashCode() {
        return taskId.hashCode();
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    public boolean isBackgroundStarted() {
        return backgroundStartedAt > 0;
    }

    public boolean isBackgroundCompleted() {
        return backgroundEndedAt > 0;
    }

    @Override
    public String toString() {
        return taskId + " on " + pool.name()
                + "; age " + RelativeTime.secondsAgo(createdAt) + "sec"
                + "; " + stateSummary()
                + "; instanceId=" + instanceId + "; " + super.toString();
    }

    private String stateSummary() {
        String summary = "";
        switch (getStatus()) {
            case PENDING:
                summary = "PENDING " + RelativeTime.secondsAgo(createdAt) + " sec ago";
                break;
            case FINISHED:
                if (backgroundEndedAt == 0) {
                    summary = "FINISHED, but didn't complete";
                } else {
                    summary = "FINISHED " + RelativeTime.secondsAgo(backgroundEndedAt) + " sec ago";
                }
                break;
            default:
                if (backgroundStartedAt == 0) {
                    summary = "QUEUED " + RelativeTime.secondsAgo(createdAt) + " sec ago";
                } else if (backgroundEndedAt == 0) {
                    summary = "RUNNING for " + RelativeTime.secondsAgo(backgroundStartedAt) + " sec";
                } else {
                    summary = "FINISHING " +  RelativeTime.secondsAgo(backgroundEndedAt) + " sec ago";
                }
                break;
        }
        if (isCancelled()) {
            rememberWhenCancelled();
            summary += ", cancelled " + RelativeTime.secondsAgo(cancelledAt) + " sec ago";
        }
        if (!hasExecutor) summary += ", no executor";
        return summary;
    }

    public boolean isReallyWorking() {
        return needsBackgroundWork() && !expectedToBeFinishedNow();
    }

    private boolean expectedToBeFinishedNow() {
        return RelativeTime.wasButMoreSecondsAgoThan(backgroundEndedAt, DELAY_AFTER_EXECUTOR_ENDED_SECONDS)
                || RelativeTime.wasButMoreSecondsAgoThan(currentlyExecutingSince, maxCommandExecutionSeconds)
                || cancelledLongAgo()
                || (getStatus() == Status.PENDING
                    && RelativeTime.wasButMoreSecondsAgoThan(createdAt, MAX_WAITING_BEFORE_EXECUTION_SECONDS));
    }

    public boolean cancelledLongAgo() {
        return RelativeTime.wasButMoreSecondsAgoThan(cancelledAt, MAX_EXECUTION_AFTER_CANCEL_SECONDS);
    }

    /** If yes, the task may be not in FINISHED state yet: during execution of onPostExecute */
    public boolean completedBackgroundWork() {
        return !needsBackgroundWork();
    }

    public boolean needsBackgroundWork() {
        switch (getStatus()) {
            case PENDING:
                return true;
            case FINISHED:
                return false;
            default:
                return backgroundEndedAt == 0;
        }
    }

    public boolean cancelLogged(boolean mayInterruptIfRunning) {
        rememberWhenCancelled();
        return super.cancel(mayInterruptIfRunning);
    }

    private void rememberWhenCancelled() {
        if (cancelledAt == 0) {
            cancelledAt = System.currentTimeMillis();
        }
    }

    public String getFirstError() {
        return firstError;
    }

    private void logError(String msgLog, Throwable tr) {
        MyLog.e(this, msgLog, tr);
        if (!StringUtil.isEmpty(firstError) || tr == null) {
            return;
        }
        firstError = MyLog.getStackTrace(tr);
    }

    public static boolean nonUiThread() {
        return !isUiThread();
    }

    // See http://stackoverflow.com/questions/11411022/how-to-check-if-current-thread-is-not-main-thread
    public static boolean isUiThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static <Params, Progress, Result> MyAsyncTask<Params, Progress, Try<Result>> fromFunc(Params params,
        Function<Params, Try<Result>> backgroundFunc,
        Function<Params, Consumer<Try<Result>>> uiConsumer) {
        return new MyAsyncTask<Params, Progress, Try<Result>>(params, PoolEnum.LONG_UI) {
            @Override
            protected Try<Result> doInBackground2(Params params) {
                return backgroundFunc.apply(params);
            }

            @Override
            protected void onFinish(Try<Result> results, boolean success) {
                Try<Result> results2 = results == null
                    ? Try.failure(new Exception("No results of the Async task"))
                    : success
                        ? results
                        : (results.isFailure()
                            ? results
                            : Try.failure(new Exception("Failed to execute Async task")));
                uiConsumer.apply(params).accept(results2);
            }
        };
    }
}
