/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.app.Activity;
import android.content.Intent;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.http.TlsSniSocketFactory;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.syncadapter.SyncInitiator;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import androidx.annotation.NonNull;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyFutureContext extends MyAsyncTask<Object, Void, MyContext> {
    @NonNull
    final MyContext previousContext;

    private volatile FirstActivity firstActivity = null;
    private volatile Intent activityIntentPostRun = null;
    private volatile Consumer<MyContext> postRunConsumer = null;

    private static class DirectExecutor implements Executor {
        private DirectExecutor() {
        }

        public void execute(@NonNull Runnable command) {
            command.run();
        }
    }

    MyFutureContext(@NonNull MyContext previousContext) {
        super(MyFutureContext.class.getSimpleName(), PoolEnum.QUICK_UI);
        Objects.requireNonNull(previousContext);
        this.previousContext = previousContext;
    }

    void executeOnNonUiThread(Object initializer) {
        if (isUiThread()) {
            execute(initializer);
        } else {
            executeOnExecutor(new DirectExecutor(), initializer);
        }
    }

    @Override
    protected MyContext doInBackground2(Object... params) {
        MyLog.d(this, "Starting initialization by " + params[0]);
        releaseGlobal();
        return previousContext.newInitialized(params[0]);
    }

    private void releaseGlobal() {
        SyncInitiator.unregister(previousContext);
        TlsSniSocketFactory.forget();
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        SharedPreferencesUtil.forget();
        previousContext.release(() -> "releaseGlobal");
        MyLog.d(this, "releaseGlobal completed");
    }

    @Override
    protected void onPostExecute2(MyContext myContext) {
        runRunnable(myContext);
        startActivity(myContext);
        SyncInitiator.register(myContext);
    }

    public boolean isEmpty() {
        return false;
    }


    public void thenStartNextActivity(FirstActivity firstActivity) {
        this.firstActivity = firstActivity;
        if (completedBackgroundWork()) {
            startActivity(getNow());
        }
    }

    public void thenStartActivity(Activity activity) {
        if (activity != null) {
            thenStartActivity(activity.getIntent());
        }
    }

    public void thenStartActivity(Intent intent) {
        if (activityIntentPostRun != null) {
            return;
        }
        activityIntentPostRun = intent;
        if (completedBackgroundWork()) {
            startActivity(getNow());
        }
    }

    private void startActivity(MyContext myContext) {
        if (myContext == null) return;
        Intent intent = activityIntentPostRun;
        if (intent != null || firstActivity != null) {
            postRunConsumer = null;
            boolean launched = false;
            if (myContext.isReady() && !myContext.isExpired()) {
                try {
                    if (firstActivity != null) {
                        firstActivity.startNextActivitySync(myContext);
                    } else {
                        myContext.context().startActivity(intent);
                    }
                    launched = true;
                } catch (android.util.AndroidRuntimeException e) {
                    if (intent == null) {
                        MyLog.e(this, "Launching next activity from firstActivity", e);
                    } else {
                        try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            myContext.context().startActivity(intent);
                            launched = true;
                        } catch (Exception e2) {
                            MyLog.e(this, "Launching activity with Intent.FLAG_ACTIVITY_NEW_TASK flag", e);
                        }
                    }
                } catch (java.lang.SecurityException e) {
                    MyLog.d(this, "Launching activity", e);
                }
            }
            if (!launched) {
                HelpActivity.startMe(myContext.context(), true, HelpActivity.PAGE_LOGO);
            }
            activityIntentPostRun = null;
            if (firstActivity != null) {
                firstActivity.finish();
                firstActivity = null;
            }
        }
    }

    public void thenRun(Consumer<MyContext> consumer) {
        this.postRunConsumer = consumer;
        if (completedBackgroundWork()) {
            runRunnable(getNow());
        }
    }

    private void runRunnable(MyContext myContext) {
        if (postRunConsumer != null && myContext != null) {
            if (myContext.isReady()) {
                postRunConsumer.accept(myContext);
            } else {
                HelpActivity.startMe(myContext.context(), true, HelpActivity.PAGE_LOGO);
            }
            postRunConsumer = null;
        }
    }

    /**
     * Immediately get currently available context, even if it's empty
     */
    @NonNull
    public MyContext getNow() {
        if (completedBackgroundWork()) {
            return getBlocking();
        }
        return previousContext;
    }

    @NonNull
    public MyContext getBlocking() {
        MyContext myContext = previousContext;
        try {
            for(int i = 1; i < 10; i++) {
                myContext = get();
                if (completedBackgroundWork()) break;
                MyLog.v(this, "Didn't complete background work yet " + i);
                DbUtils.waitMs(this, 50 * i);
            }
            if (!completedBackgroundWork()) MyLog.w(this, "Didn't complete background work");
        } catch (Exception e) {
            MyLog.i(this, "getBlocking failed", e);
        }
        return myContext == null ? previousContext : myContext;
    }

}
