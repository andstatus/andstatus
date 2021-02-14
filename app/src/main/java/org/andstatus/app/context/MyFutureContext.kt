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

import android.content.Intent;

import androidx.annotation.NonNull;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.HelpActivity;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.net.http.TlsSniSocketFactory;
import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.os.ExceptionsCounter;
import org.andstatus.app.os.NonUiThreadExecutor;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.syncadapter.SyncInitiator;
import org.andstatus.app.util.IdentifiableInstance;
import org.andstatus.app.util.InstanceId;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.SharedPreferencesUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import io.vavr.control.Try;

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

    public MyFutureContext releaseNow(Supplier<String> reason) {
        MyContext previousContext = getNow();
        release(previousContext, reason);
        return completed(previousContext);
    }

    public boolean isCompletedExceptionally() {
        return future.isCompletedExceptionally();
    }

    public static MyFutureContext fromPrevious(MyFutureContext previousFuture, Object calledBy) {
        CompletableFuture<MyContext> future = previousFuture.getHealthyFuture(calledBy)
            .thenApplyAsync(initializeMyContextIfNeeded(calledBy), NonUiThreadExecutor.INSTANCE);
        return new MyFutureContext(previousFuture.getNow(), future);
    }

    private static UnaryOperator<MyContext> initializeMyContextIfNeeded(Object calledBy) {
        return previousContext -> {
            String reason;
            if (!previousContext.isReady()) {
                reason = "Context not ready";
            } else if (previousContext.isExpired()) {
                reason = "Context expired";
            } else if (previousContext.isPreferencesChanged()) {
                reason = "Preferences changed";
            } else {
                return previousContext;
            }
            Supplier<String> reasonSupplier = () -> "Initialization: " + reason
                    + ", previous:" + MyStringBuilder.objToTag(previousContext)
                    + " by " + MyStringBuilder.objToTag(calledBy);
            MyLog.v(TAG, () -> "Preparing for " + reasonSupplier.get());
            release(previousContext, reasonSupplier);
            MyContext myContext = previousContext.newInitialized(calledBy);
            SyncInitiator.register(myContext);
            return myContext;
        };
    }

    private static void release(MyContext previousContext, Supplier<String> reason) {
        SyncInitiator.unregister(previousContext);
        MyServiceManager.setServiceUnavailable();
        TlsSniSocketFactory.forget();
        previousContext.save(reason);
        AsyncTaskLauncher.forget();
        ExceptionsCounter.forget();
        MyLog.forget();
        SharedPreferencesUtil.forget();
        FirstActivity.isFirstrun.set(true);
        previousContext.release(reason);
        // There is InterruptedException after above..., so we catch it below:
        DbUtils.waitMs(TAG, 10);
        MyLog.d(TAG,"Release completed, " + reason.get());
    }

    static MyFutureContext completed(MyContext myContext) {
        return new MyFutureContext(MyContext.EMPTY, completedFuture(myContext));
    }

    private static CompletableFuture<MyContext> completedFuture(MyContext myContext) {
        CompletableFuture<MyContext> future = new CompletableFuture<>();
        future.complete(myContext);
        return future;
    }

    private MyFutureContext(@NonNull MyContext previousContext, CompletableFuture<MyContext> future) {
        this.previousContext = previousContext;
        this.future = future;
    }

    public boolean isReady() {
        return getNow().isReady();
    }

    public MyContext getNow() {
        return tryNow().getOrElse(previousContext);
    }

    public Try<MyContext> tryNow() {
        return Try.success(previousContext).map(future::getNow);
    }

    @Override
    public long getInstanceId() {
        return instanceId;
    }

    MyFutureContext whenSuccessAsync(Consumer<MyContext> consumer, Executor executor) {
        return with(future -> future.whenCompleteAsync((myContext, throwable) -> {
            MyLog.d(TAG, "whenSuccessAsync " + myContext + ", " + future);
            if (myContext != null) {
                consumer.accept(myContext);
            }
        }, executor));
    }

    MyFutureContext whenSuccessOrPreviousAsync(Consumer<MyContext> consumer, Executor executor) {
        return with(future -> future.whenCompleteAsync((myContext, throwable) -> {
            consumer.accept(myContext == null ? previousContext : myContext);
        }, executor));
    }

    public MyFutureContext with(UnaryOperator<CompletableFuture<MyContext>> futures) {
        CompletableFuture<MyContext> healthyFuture = getHealthyFuture("(with)");
        CompletableFuture<MyContext> nextFuture = futures.apply(healthyFuture);
        MyLog.d(TAG, "with, after apply, next: " + nextFuture);
        return new MyFutureContext(previousContext, nextFuture);
    }

    private CompletableFuture<MyContext> getHealthyFuture(Object calledBy) {
        if (future.isDone()) {
            tryNow().onFailure(throwable ->
                    MyLog.i(TAG, future.isCancelled()
                            ? "Previous initialization was cancelled"
                            : "Previous initialization completed exceptionally"
                            + ", now called by " + calledBy, throwable));
        }
        return future.isCompletedExceptionally()
                ? completedFuture(previousContext)
                : future;
    }

    static void startActivity(MyContext myContext, Intent intent) {
        if (intent != null) {
            boolean launched = false;
            if (myContext.isReady()) {
                try {
                    MyLog.d(TAG, "Start activity with intent:" + intent);
                    myContext.context().startActivity(intent);
                    launched = true;
                } catch (android.util.AndroidRuntimeException e) {
                    try {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MyLog.d(TAG, "Start activity with intent (new task):" + intent);
                        myContext.context().startActivity(intent);
                        launched = true;
                    } catch (Exception e2) {
                        MyLog.e(TAG, "Launching activity with Intent.FLAG_ACTIVITY_NEW_TASK flag", e);
                    }
                } catch (SecurityException e) {
                    MyLog.d(TAG, "Launching activity", e);
                }
            }
            if (!launched) {
                HelpActivity.startMe(myContext.context(), true, HelpActivity.PAGE_LOGO);
            }
        }
    }

    public Try<MyContext> tryBlocking() {
        return Try.of(future::get);
    }

    @Override
    public String classTag() {
        return TAG;
    }
}
