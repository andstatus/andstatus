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
import android.support.annotation.NonNull;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.net.http.TlsSniSocketFactory;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.os.MyAsyncTask;
import org.andstatus.app.syncadapter.SyncInitiator;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyFutureContext extends MyAsyncTask<Void, Void, MyContext> {
    @NonNull
    private final MyContext contextCreator;
    private volatile MyContext myPreviousContext = null;
    private final String callerName;

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

    MyFutureContext(@NonNull MyContext contextCreator, MyContext myPreviousContext, @NonNull Object calledBy) {
        super(MyFutureContext.class.getSimpleName(), PoolEnum.QUICK_UI);
        this.contextCreator = contextCreator;
        this.myPreviousContext = myPreviousContext;
        callerName = MyLog.objToTag(calledBy);
    }

    void executeOnNonUiThread() {
        if (isUiThread()) {
            execute();
        } else {
            executeOnExecutor(new DirectExecutor());
        }
    }

    @Override
    protected MyContext doInBackground2(Void... params) {
        MyLog.d(this, "Starting initialization by " + callerName);
        releaseGlobal();
        return contextCreator.newInitialized(callerName);
    }

    private void releaseGlobal() {
        SyncInitiator.unregister(myPreviousContext);
        TlsSniSocketFactory.forget();
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        SharedPreferencesUtil.forget();
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
        return getMyContext();
    }

    @NonNull
    private MyContext getMyContext() {
        if(myPreviousContext == null) {
            return contextCreator;
        }
        return myPreviousContext;
    }

    @NonNull
    public MyContext getBlocking() {
        try {
            MyContext myContext = get();
            myPreviousContext = null;
            return myContext;
        } catch (Exception e) {
            return getMyContext();
        }
    }

}
