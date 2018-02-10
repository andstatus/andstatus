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

import android.support.annotation.NonNull;

import org.andstatus.app.util.MyLog;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yvolk@yurivolkov.com
 */
public class AsyncTaskLauncher<Params> {
    private static final String TAG = AsyncTaskLauncher.class.getSimpleName();

    private static final AtomicLong launchedCount = new AtomicLong();
    private static final AtomicLong skippedCount = new AtomicLong();
    private static final Queue<MyAsyncTask<?, ?, ?>> launchedTasks = new ConcurrentLinkedQueue<>();

    private static volatile ThreadPoolExecutor SYNC_POOL_EXECUTOR = null;
    private static volatile ThreadPoolExecutor LONG_UI_POOL_EXECUTOR = null;
    private static volatile ThreadPoolExecutor FILE_DOWNLOAD_EXECUTOR = null;

    private static ThreadPoolExecutor getExecutor(MyAsyncTask.PoolEnum pool) {
        ThreadPoolExecutor executor;
        switch (pool) {
            case LONG_UI:
                executor = LONG_UI_POOL_EXECUTOR;
                break;
            case FILE_DOWNLOAD:
                executor = FILE_DOWNLOAD_EXECUTOR;
                break;
            case SYNC:
                executor = SYNC_POOL_EXECUTOR;
                break;
            default:
                return (ThreadPoolExecutor) MyAsyncTask.THREAD_POOL_EXECUTOR;
        }
        if (executor != null && executor.isShutdown()) {
            if (executor.isTerminating()) {
                MyLog.v(TAG, "Pool " + pool.name() + " isTerminating. Applying shutdownNow: "
                        + executor );
                executor.shutdownNow();
            }
            executor = null;
        }
        if (executor == null) {
            MyLog.v(TAG, "Creating pool " + pool.name());
            executor = new ThreadPoolExecutor(pool.corePoolSize, pool.corePoolSize + 1,
                    1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(128));
            setExecutor(pool, executor);
        }
        return executor;
    }

    private static void setExecutor(MyAsyncTask.PoolEnum pool, ThreadPoolExecutor executor) {
        onExecutorRemoval(pool);
        switch (pool) {
            case LONG_UI:
                LONG_UI_POOL_EXECUTOR = executor;
                break;
            case FILE_DOWNLOAD:
                FILE_DOWNLOAD_EXECUTOR = executor;
                break;
            case SYNC:
                SYNC_POOL_EXECUTOR = executor;
                break;
            default:
                break;
        }
    }

    public static boolean execute(Object objTag, boolean throwOnFail, MyAsyncTask<Void, ?, ?> asyncTask) {
        AsyncTaskLauncher<Void> launcher = new AsyncTaskLauncher<>();
        return launcher.execute(objTag, throwOnFail, asyncTask, (Void) null);
    }

    public boolean execute(Object objTag, boolean throwOnFail, MyAsyncTask<Params, ?, ?> asyncTask,
                           Params... params) {
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(objTag, asyncTask.toString() + " Launching task");
        }
        boolean launched = false;
        try {
            cancelStalledTasks();
            if (asyncTask.isSingleInstance() && foundUnfinished(asyncTask)) {
                skippedCount.incrementAndGet();
            } else {
                asyncTask.executeOnExecutor(getExecutor(asyncTask.pool), params);
                launchedTasks.add(asyncTask);
                launchedCount.incrementAndGet();
                launched = true;
            }
            removeFinishedTasks();
        } catch (RejectedExecutionException e) {
            String msgLog = asyncTask.toString() + " Launching task"
                    + "\n" + threadPoolInfo();
            MyLog.w(objTag, msgLog, e);
            if (throwOnFail) {
                throw new RejectedExecutionException(MyLog.objToTag(objTag) + "; "
                        + msgLog, e);
            }
        }
        return launched;
    }

    private static void cancelStalledTasks() {
        Set<MyAsyncTask.PoolEnum> poolsToShutDown = new HashSet<>();
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            if (launched.needsBackgroundWork() && !launched.isReallyWorking()) {
                MyLog.v(TAG, "Found stalled task at " + launched.pool + ": " + launched);
                if (launched.pool.mayBeShutDown && launched.cancelledLongAgo() && launched.hasExecutor) {
                    poolsToShutDown.add(launched.pool);
                } else {
                    launched.cancelLogged(true);
                }
            }
        }
        shutdownExecutors(poolsToShutDown);
    }

    private boolean foundUnfinished(MyAsyncTask<Params, ?, ?> asyncTask) {
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            if (launched.equals(asyncTask) && launched.needsBackgroundWork()) {
                MyLog.v(this, "Found unfinished " + launched);
                return true;
            }
        }
        return false;
    }

    private static void removeFinishedTasks() {
        long count = 0;
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            if (launched.getStatus() == MyAsyncTask.Status.FINISHED) {
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(TAG, Long.toString(++count) + ". Removing finished " + launched);
                }
                launchedTasks.remove(launched);
            }
        }
    }

    private static void onExecutorRemoval(MyAsyncTask.PoolEnum pool) {
        MyLog.v(TAG, "On removing executor for pool " + pool.name());
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            if (launched.pool == pool) launched.hasExecutor = false;
        }
    }

    public static String threadPoolInfo() {
        StringBuilder builder = getLaunchedTasksInfo();
        builder.append("\nThread pools:");
        for (MyAsyncTask.PoolEnum pool : MyAsyncTask.PoolEnum.values()) {
            builder.append("\n" + pool.name() + ": " + getExecutor(pool).toString());
        }
        return builder.toString();
    }

    private static StringBuilder getLaunchedTasksInfo() {
        StringBuilder builder = new StringBuilder("\n");
        long pendingCount = 0;
        long queuedCount = 0;
        long runningCount = 0;
        long finishingCount = 0;
        long finishedCount = 0;
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            switch (launched.getStatus()) {
                case PENDING:
                    pendingCount++;
                    builder.append("P " + pendingCount + ". " + launched.toString() + "\n");
                    break;
                case RUNNING:
                    if (launched.backgroundStartedAt == 0) {
                        queuedCount++;
                        builder.append("Q " + queuedCount + ". " + launched.toString() + "\n");
                    } else if (launched.backgroundEndedAt == 0) {
                        runningCount++;
                        builder.append("R " + runningCount + ". " + launched.toString() + "\n");
                    } else {
                        finishingCount++;
                        builder.append("F " + finishingCount + ". " + launched.toString() + "\n");
                    }
                    break;
                case FINISHED:
                    finishedCount++;
                    break;
            }
        }
        StringBuilder builder2 = new StringBuilder("Tasks:\n");
        builder2.append("Total launched: " + launchedCount.get());
        if (pendingCount > 0) {
            builder2.append("; pending: " + pendingCount);
        }
        if (queuedCount > 0) {
            builder2.append("; queued: " + queuedCount);
        }
        builder2.append("; running: " + runningCount);
        if (finishingCount > 0) {
            builder2.append("; finishing: " + finishingCount);
        }
        if (finishedCount > 0) {
            builder2.append("; just finished: " + finishedCount);
        }
        builder2.append(". Skipped: " + skippedCount.get());
        builder2.append(builder);        
        return builder2;
    }

    private static void shutdownExecutors(@NonNull Collection<MyAsyncTask.PoolEnum> pools) {
        for (MyAsyncTask.PoolEnum pool : pools) {
            ThreadPoolExecutor executor = getExecutor(pool);
            if (executor == null) continue;
            MyLog.v(TAG, "Shutting down executor " + pool + ":" + executor);
            executor.shutdownNow();
            setExecutor(pool, null);
        }
    }

    public static void forget() {
        Arrays.asList(MyAsyncTask.PoolEnum.values()).forEach(AsyncTaskLauncher::cancelPoolTasks);
        cancelStalledTasks();
        removeFinishedTasks();
    }

    public static void cancelPoolTasks(MyAsyncTask.PoolEnum pool) {
        MyLog.v(TAG, "Cancelling tasks for pool " + pool.name());
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            if (launched.pool == pool) {
                try {
                    launched.cancelLogged(true);
                } catch (Throwable tr){
                    // Ignore
                }
            }
        }
    }
}
