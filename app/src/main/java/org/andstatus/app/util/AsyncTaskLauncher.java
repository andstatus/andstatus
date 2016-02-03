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

import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author yvolk@yurivolkov.com
 */
public class AsyncTaskLauncher<Params> {

    public static boolean execute(Object objTag, AsyncTask<Void, ?, ?> asyncTask) {
        return execute(objTag, asyncTask, true);
    }

    public static boolean execute(Object objTag, AsyncTask<Void, ?, ?> asyncTask, boolean throwOnFail) {
        AsyncTaskLauncher<Void> launcher = new AsyncTaskLauncher<>();
        return launcher.execute(objTag, asyncTask, throwOnFail, null);
    }

    public boolean execute(Object objTag, AsyncTask<Params, ?, ?> asyncTask, boolean throwOnFail, Params... params) {
        boolean launched = false;
        try {
            asyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
            launched = true;
        } catch (RejectedExecutionException e) {
            MyLog.w(objTag, "Launching new task\n" + threadPoolInfo(), e);
            if (throwOnFail) {
                throw new RejectedExecutionException(MyLog.objTagToString(objTag) + "; " + threadPoolInfo(), e);
            }
        }
        return launched;
    }

    public static String threadPoolInfo() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) AsyncTask.THREAD_POOL_EXECUTOR;
        return executor.toString() + ", tasks: " + Arrays.toString(executor.getQueue().toArray());
    }
}
