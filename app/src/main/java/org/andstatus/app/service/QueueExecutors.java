/*
 * Copyright (c) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import androidx.annotation.NonNull;

import org.andstatus.app.os.AsyncTaskLauncher;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;

import java.util.concurrent.atomic.AtomicReference;

/** Two specialized threads to execute {@link CommandQueue} */
class QueueExecutors {
    private final MyService myService;
    private final AtomicReference<QueueExecutor> general = new AtomicReference<>();
    private final AtomicReference<QueueExecutor> downloads = new AtomicReference<>();

    QueueExecutors(MyService myService) {
        this.myService = myService;
    }

    void ensureExecutorStarted() {
        ensureExecutorStarted(CommandQueue.AccessorType.GENERAL);
        ensureExecutorStarted(CommandQueue.AccessorType.DOWNLOADS);
    }

    private void ensureExecutorStarted(CommandQueue.AccessorType accessorType) {
        final String method = "ensureExecutorStarted-" + accessorType;
        MyStringBuilder logMessageBuilder = new MyStringBuilder();
        QueueExecutor previous = getRef(accessorType).get();
        boolean replace = previous == null;
        if ( !replace && previous.completedBackgroundWork()) {
            logMessageBuilder.withComma("Removing completed Executor " + previous);
            replace = true;
        }
        if ( !replace && !previous.isReallyWorking()) {
            logMessageBuilder.withComma("Cancelling stalled Executor " + previous);
            replace = true;
        }
        if (replace) {
            QueueExecutor current = new QueueExecutor(myService, accessorType);
            if (replaceExecutor(logMessageBuilder, accessorType, previous, current)) {
                logMessageBuilder.withComma("Starting new Executor " + current);
                AsyncTaskLauncher.execute( myService.classTag() + "-" + accessorType, current)
                .onFailure(throwable -> {
                    logMessageBuilder.withComma("Failed to start new executor: " + throwable);
                    replaceExecutor(logMessageBuilder, accessorType, current, null);
                });
            }
        } else {
            logMessageBuilder.withComma("There is an Executor already " + previous);
        }
        if (logMessageBuilder.length() > 0) {
            MyLog.v(myService, () -> method + "; " + logMessageBuilder);
        }
    }

    @NonNull
    AtomicReference<QueueExecutor> getRef(CommandQueue.AccessorType accessorType) {
        return accessorType == CommandQueue.AccessorType.GENERAL
                ? general
                : downloads;
    }

    private boolean replaceExecutor(MyStringBuilder logMessageBuilder, CommandQueue.AccessorType accessorType,
                                    QueueExecutor previous, QueueExecutor current) {
        if (getRef(accessorType).compareAndSet(previous, current)) {
            if (previous == null) {
                logMessageBuilder.withComma(current == null
                        ? "No executor"
                        : "Executor set to " + current);
            } else {
                if (previous.needsBackgroundWork()) {
                    logMessageBuilder.withComma("Cancelling previous");
                    previous.cancelLogged(true);
                }
                logMessageBuilder.withComma(current == null
                        ? "Removed executor " + previous
                        : "Replaced executor " + previous + " with " + current);
            }
            return true;
        }
        return false;
    }

    boolean stopExecutor(boolean forceNow) {
        return stopExecutor(CommandQueue.AccessorType.GENERAL, forceNow)
              && stopExecutor(CommandQueue.AccessorType.DOWNLOADS, forceNow);
    }

    private boolean stopExecutor(CommandQueue.AccessorType accessorType, boolean forceNow) {
        final String method = "couldStopExecutor-" + accessorType;
        MyStringBuilder logMessageBuilder = new MyStringBuilder();

        AtomicReference<QueueExecutor> executorRef = getRef(accessorType);
        QueueExecutor previous = executorRef.get();
        boolean success = previous == null;
        boolean doStop = !success;
        if (doStop && previous.needsBackgroundWork() && previous.isReallyWorking() ) {
            if (forceNow) {
                logMessageBuilder.withComma("Cancelling working Executor" + previous);
            } else {
                logMessageBuilder.withComma("Cannot stop now Executor " + previous);
                doStop = false;
            }
        }
        if (doStop) {
            success = replaceExecutor(logMessageBuilder, accessorType, previous, null);
        }
        if (logMessageBuilder.nonEmpty()) {
            MyLog.v(myService, () -> method + "; " + logMessageBuilder);
        }
        return success;
    }

    boolean isReallyWorking() {
        return general.get().isReallyWorking() || downloads.get().isReallyWorking();
    }

    @NonNull
    @Override
    public String toString() {
        return general.toString() + "; " + downloads.toString();
    }
}
