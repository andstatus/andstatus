/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.backup;

import org.andstatus.app.data.DbUtils;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;
import org.andstatus.app.util.StringUtil;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ProgressLogger {
    public static final int PROGRESS_REPORT_PERIOD_SECONDS = 20;
    private static final String TAG = ProgressLogger.class.getSimpleName();
    private volatile long lastLoggedAt = 0L;
    private volatile boolean makeServiceUnavalable = false;
    public final Optional<ProgressListener> progressListener;
    public final String logTag;

    public static final AtomicLong startedAt = new AtomicLong(0);

    public static long newStartingTime() {
        final long iStartedAt = MyLog.uniqueCurrentTimeMS();
        startedAt.set(iStartedAt);
        return iStartedAt;
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgressMessage(CharSequence message);
        default void onComplete(boolean success) {}
        default void onActivityFinish() {}
        default void setCancelable(boolean isCancelable) {}
        default void cancel() {}
        default boolean isCancelled() { return false; }
        default String getLogTag() { return TAG; }
    }

    private static class EmptyListener implements ProgressListener {
        @Override
        public void onProgressMessage(CharSequence message) {
            // Empty
        }
    }
    public static final ProgressListener EMPTY_LISTENER = new EmptyListener();

    public ProgressLogger(ProgressListener progressListener) {
        this.progressListener = Optional.ofNullable(progressListener);
        this.logTag = this.progressListener.map(ProgressListener::getLogTag).orElse(TAG);
    }

    private ProgressLogger(String logTag) {
        this.progressListener = Optional.empty();
        this.logTag = StringUtil.notEmpty(logTag, TAG);
    }

    public boolean isCancelled() {
        return progressListener.map(ProgressListener::isCancelled).orElse(false);
    }

    public void logSuccess() {
        onComplete(true);
    }

    public void logFailure() {
        onComplete(false);
    }

    public void onComplete(boolean success) {
        logProgressAndPause(success ? "Completed successfully" : "Failed", 1);
        progressListener.ifPresent(c -> c.onComplete(success));
    }

    public boolean loggedMoreSecondsAgoThan(long secondsAgo) {
        return RelativeTime.moreSecondsAgoThan(lastLoggedAt, secondsAgo);
    }

    public ProgressLogger makeServiceUnavalable() {
        this.makeServiceUnavalable = true;
        return this;
    }

    public void logProgressIfLongProcess(Supplier<CharSequence> supplier) {
        if (loggedMoreSecondsAgoThan(ProgressLogger.PROGRESS_REPORT_PERIOD_SECONDS)) {
            logProgress(supplier.get());
        }
    }

    public void logProgressAndPause(CharSequence message, long pauseIfPositive) {
        logProgress(message);
        if (pauseIfPositive > 0 && progressListener.isPresent()) {
            DbUtils.waitMs(this, 2000);
        }
    }

    public void logProgress(CharSequence message) {
        updateLastLoggedTime();
        MyLog.i(logTag, message.toString());
        if (makeServiceUnavalable) MyServiceManager.setServiceUnavailable();
        progressListener.ifPresent(c -> c.onProgressMessage(message));
    }

    public void updateLastLoggedTime() {
        lastLoggedAt = System.currentTimeMillis();
    }

    public static ProgressLogger getEmpty(String logTag) {
        return new ProgressLogger(logTag);
    }
}
