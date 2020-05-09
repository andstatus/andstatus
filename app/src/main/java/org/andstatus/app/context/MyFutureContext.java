/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.os.NonUiThreadExecutor;
import org.andstatus.app.syncadapter.SyncInitiator;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import io.vavr.control.Try;

import static org.andstatus.app.context.MyContextHolder.myContextHolder;

/**
 * @author yvolk@yurivolkov.com
 */
public class MyFutureContext implements IdentifiableInstance {
    private static final String TAG = MyFutureContext.class.getSimpleName();

    protected final long createdAt = MyLog.uniqueCurrentTimeMS();
    protected final long instanceId = InstanceId.next();

    @NonNull
    private final MyContext previousContext;
    public final CompletableFuture<MyContext> future;

    public static MyFutureContext fromPrevious(MyFutureContext previousFuture) {
        if (previousFuture.needToInitialize()) {
            MyContext previousContext = previousFuture.getNow();
            CompletableFuture<MyContext> future =
                    completedFuture(previousContext).future
                    .thenApplyAsync(MyFutureContext::initializeMyContext, NonUiThreadExecutor.INSTANCE);
            return new MyFutureContext(previousContext, future);
        } else {
            return previousFuture;
        }
    }

    private static MyContext initializeMyContext(MyContext previousContext) {
        myContextHolder.release(previousContext, () -> "Starting initialization by " + previousContext);
        MyContext myContext = previousContext.newInitialized(previousContext);
        SyncInitiator.register(myContext);
        return myContext;
    }

    public static MyFutureContext completedFuture(MyContext myContext) {
        CompletableFuture<MyContext> future = new CompletableFuture<>();
        future.complete(myContext);
        return new MyFutureContext(MyContext.EMPTY, future);
    }

    private MyFutureContext(@NonNull MyContext previousContext, CompletableFuture<MyContext> future) {
        this.previousContext = previousContext;
        this.future = future;
    }

    public boolean needToInitialize() {
        if (future.isDone()) {
            return future.isCancelled() || future.isCompletedExceptionally()
                    || getNow().isExpired() || !getNow().isReady();
        }
        return false;
    }

    public MyContext getNow() {
        return Try.success(previousContext).map(future::getNow).getOrElse(previousContext);
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    public static BiConsumer<MyContext, Throwable> startNextActivity(FirstActivity firstActivity) {
        return (myContext, throwable) -> {
            boolean launched = false;
            if (myContext != null && myContext.isReady() && !myContext.isExpired()) {
                try {
                    firstActivity.startNextActivitySync(myContext);
                    launched = true;
                } catch (android.util.AndroidRuntimeException e) {
                    MyLog.w(TAG, "Launching next activity from firstActivity", e);
                } catch (java.lang.SecurityException e) {
                    MyLog.d(TAG, "Launching activity", e);
                }
            }
            if (!launched) {
                HelpActivity.startMe(
                        myContext == null ? myContextHolder.getNow().context() : myContext.context(),
                        true, HelpActivity.PAGE_LOGO);
            }
            firstActivity.finish();
        };
    }

    public MyFutureContext whenSuccessAsync(Consumer<MyContext> consumer, Executor executor) {
        return with(future -> future.whenCompleteAsync((myContext, throwable) -> {
            if (myContext != null) {
                consumer.accept(myContext);
            }
        }, executor));
    }

    public MyFutureContext with(UnaryOperator<CompletableFuture<MyContext>> futures) {
        return new MyFutureContext(previousContext, futures.apply(future));
    }

    public static Consumer<MyContext> startActivity(Activity activity) {
        return startIntent(activity.getIntent());
    }

    public static Consumer<MyContext> startIntent(Intent intent) {
        return myContext -> {
            if (intent != null) {
                boolean launched = false;
                if (myContext.isReady() && !myContext.isExpired()) {
                    try {
                        myContext.context().startActivity(intent);
                        launched = true;
                    } catch (android.util.AndroidRuntimeException e) {
                        try {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            myContext.context().startActivity(intent);
                            launched = true;
                        } catch (Exception e2) {
                            MyLog.e(TAG, "Launching activity with Intent.FLAG_ACTIVITY_NEW_TASK flag", e);
                        }
                    } catch (java.lang.SecurityException e) {
                        MyLog.d(TAG, "Launching activity", e);
                    }
                }
                if (!launched) {
                    HelpActivity.startMe(myContext.context(), true, HelpActivity.PAGE_LOGO);
                }
            }
        };
    }

    public Try<MyContext> tryBlocking() {
        return Try.of(future::get);
    }

    @Override
    public String classTag() {
        return TAG;
    }
}
