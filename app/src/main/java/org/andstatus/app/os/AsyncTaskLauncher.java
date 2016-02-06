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

import org.andstatus.app.util.MyLog;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yvolk@yurivolkov.com
 */
public class AsyncTaskLauncher<Params> {
    private static final AtomicLong launchedCount = new AtomicLong();
    private static final AtomicLong skippedCount = new AtomicLong();
    private static final Queue<MyAsyncTask<?, ?, ?>> launchedTasks = new ConcurrentLinkedQueue<>();

    public static boolean execute(Object objTag, MyAsyncTask<Void, ?, ?> asyncTask) {
        return execute(objTag, asyncTask, true);
    }

    public static boolean execute(Object objTag, MyAsyncTask<Void, ?, ?> asyncTask,
                                  boolean throwOnFail) {
        AsyncTaskLauncher<Void> launcher = new AsyncTaskLauncher<>();
        return launcher.execute(objTag, asyncTask, throwOnFail, null);
    }

    public boolean execute(Object objTag, MyAsyncTask<Params, ?, ?> asyncTask,
                           boolean throwOnFail, Params... params) {
        boolean launched = false;
        try {
            if (asyncTask.isSingleInstance() && foundUnfinished(asyncTask)) {
                skippedCount.incrementAndGet();
            } else {
                asyncTask.executeOnExecutor(MyAsyncTask.THREAD_POOL_EXECUTOR, params);
                launchedTasks.add(asyncTask);
                launchedCount.incrementAndGet();
                launched = true;
            }
            removeFinishedTasks();
        } catch (RejectedExecutionException e) {
            String msgLog = "Launching task:\n" + asyncTask.toString()
                    + "\nOn pool:\n" + threadPoolInfo();
            MyLog.w(objTag, msgLog, e);
            if (throwOnFail) {
                throw new RejectedExecutionException(MyLog.objTagToString(objTag) + "; "
                        + msgLog, e);
            }
        }
        return launched;
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

    public static void removeFinishedTasks() {
        for (MyAsyncTask<?, ?, ?> launched : launchedTasks) {
            if (launched.getStatus() == MyAsyncTask.Status.FINISHED) {
                launchedTasks.remove(launched);
            }
        }
    }

    public static String threadPoolInfo() {
        return MyAsyncTask.THREAD_POOL_EXECUTOR.toString() + " \n" + getLaunchedTasksInfo();
    }

    private static String getLaunchedTasksInfo() {
        StringBuilder builder = new StringBuilder("Queued and Running tasks:\n");
        long pendingCount = 0;
        long queuedCount = 0;
        long runningCount = 0;
        long finishingCount = 0;
        long finishedCount = 0;
        long otherCount = 0;
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
                default:
                    otherCount++;
                    builder.append("O " + otherCount + ". " + launched.toString() + "\n");
                    break;
            }
        }
        builder.append("Skipped: " + skippedCount.get() + ". ");
        builder.append("Total launched: " + launchedCount.get());
        if (pendingCount > 0) {
            builder.append("; pending: " + pendingCount);
        }
        if (queuedCount > 0) {
            builder.append("; queued: " + queuedCount);
        }
        builder.append("; running: " + runningCount);
        if (finishingCount > 0) {
            builder.append("; finishing: " + finishingCount);
        }
        if (finishedCount > 0) {
            builder.append("; just finished: " + finishedCount);
        }
        if (otherCount > 0) {
            builder.append("; other: " + otherCount);
        }
        return builder.toString();
    }
}
