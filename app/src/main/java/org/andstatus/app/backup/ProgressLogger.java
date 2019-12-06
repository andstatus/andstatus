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
import org.andstatus.app.util.StringUtils;

import java.util.Optional;
import java.util.function.Supplier;

public class ProgressLogger {
    public static final int PROGRESS_REPORT_PERIOD_SECONDS = 20;
    private volatile long lastLoggedAt = 0L;
    private volatile boolean makeServiceUnavalable = false;
    public final Optional<ProgressCallback> callback;
    public final String logTag;

    @FunctionalInterface
    public interface ProgressCallback {
        void onProgressMessage(CharSequence message);
        default void onComplete(boolean success) {}
    }

    public ProgressLogger(ProgressCallback callback, String logTag) {
        this.callback = Optional.ofNullable(callback);
        this.logTag = StringUtils.notEmpty(logTag, ProgressLogger.class.getSimpleName());
    }

    private ProgressLogger(String logTag) {
        this.callback = Optional.empty();
        this.logTag = StringUtils.notEmpty(logTag, ProgressLogger.class.getSimpleName());
    }

    public void logSuccess() {
        onComplete(true);
    }

    public void logFailure() {
        onComplete(false);
    }

    public void onComplete(boolean success) {
        logProgressAndPause(success ? "Completed successfully" : "Failed", 1);
        callback.ifPresent(c -> c.onComplete(success));
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
            MyServiceManager.setServiceUnavailable();
            logProgress(supplier.get());
        }
    }

    public void logProgressAndPause(CharSequence message, long pauseIfPositive) {
        logProgress(message);
        if (pauseIfPositive > 0 && callback.isPresent()) {
            DbUtils.waitMs(this, 2000);
        }
    }

    public void logProgress(CharSequence message) {
        updateLastLoggedTime();
        MyLog.i(logTag, "Progress: " + message);
        if (makeServiceUnavalable) MyServiceManager.setServiceUnavailable();
        callback.ifPresent(c -> c.onProgressMessage(message));
    }

    public void updateLastLoggedTime() {
        lastLoggedAt = System.currentTimeMillis();
    }

    public static ProgressLogger getEmpty(String logTag) {
        return new ProgressLogger(logTag);
    }
}
