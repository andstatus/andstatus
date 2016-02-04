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

package org.andstatus.app.util;

import android.os.AsyncTask;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author yvolk@yurivolkov.com
 */
public class AsyncTaskLauncher<Params> {
    private static final AtomicLong launchedCount = new AtomicLong();

    private static class LaunchedTask {
        final AsyncTask<?, ?, ?> task;
        final long startedAt = System.currentTimeMillis();

        private LaunchedTask(AsyncTask<?, ?, ?> task) {
            this.task = task;
        }
    }

    private static final Queue<LaunchedTask> launchedTasks = new ConcurrentLinkedQueue<>();

    public static boolean execute(Object objTag, AsyncTask<Void, ?, ?> asyncTask) {
        return execute(objTag, asyncTask, true);
    }

    public static boolean execute(Object objTag, AsyncTask<Void, ?, ?> asyncTask, boolean throwOnFail) {
        AsyncTaskLauncher<Void> launcher = new AsyncTaskLauncher<>();
        return launcher.execute(objTag, asyncTask, throwOnFail, null);
    }

    public boolean execute(Object objTag, AsyncTask<Params, ?, ?> asyncTask,
                           boolean throwOnFail, Params... params) {
        boolean launched = false;
        try {
            asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
            launchedTasks.add(new LaunchedTask(asyncTask));
            launchedCount.incrementAndGet();
            removeFinishedTasks();
            launched = true;
        } catch (RejectedExecutionException e) {
            MyLog.w(objTag, "Launching new task\n" + threadPoolInfo(), e);
            if (throwOnFail) {
                throw new RejectedExecutionException(MyLog.objTagToString(objTag) + "; "
                        + threadPoolInfo(), e);
            }
        }
        return launched;
    }

    public static void removeFinishedTasks() {
        for (LaunchedTask launched : launchedTasks) {
            if (launched.task.getStatus() == AsyncTask.Status.FINISHED) {
                launchedTasks.remove(launched);
            }
        }
    }

    public static String threadPoolInfo() {
        return AsyncTask.THREAD_POOL_EXECUTOR.toString() + " \n" + getLaunchedTasksInfo();
    }

    private static String getLaunchedTasksInfo() {
        StringBuilder builder = new StringBuilder("Running tasks: ");
        long pendingCount = 0;
        long runningCount = 0;
        long finishedCount = 0;
        long otherCount = 0;
        for (LaunchedTask launched : launchedTasks) {
            switch (launched.task.getStatus()) {
                case RUNNING:
                    runningCount++;
                    builder.append(RelativeTime.secondsAgo(launched.startedAt) + " sec: "
                            + launched.task.toString() + "\n");
                    break;
                case PENDING:
                    pendingCount++;
                    break;
                case FINISHED:
                    finishedCount++;
                    break;
                default:
                    otherCount++;
                    break;
            }
        }
        builder.append("Total launched: " + launchedCount.get());
        builder.append("; pending: " + pendingCount);
        builder.append("; running: " + runningCount);
        if (finishedCount > 0) {
            builder.append("; just finished: " + runningCount);
        }
        if (otherCount > 0) {
            builder.append("; other: " + otherCount);
        }
        return builder.toString();
    }
}
