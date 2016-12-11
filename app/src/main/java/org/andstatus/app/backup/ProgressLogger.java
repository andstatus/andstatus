/**
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

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.RelativeTime;

public class ProgressLogger {
    private volatile long lastLoggedAt = 0L;

    public interface ProgressCallback {
        void onProgressMessage(CharSequence message);
        void onComplete(boolean success);
    }

    public static ProgressCallback getEmptyCallback() {
        return new ProgressCallback() {
            @Override
            public void onProgressMessage(CharSequence message) {
                // Empty
            }

            @Override
            public void onComplete(boolean success) {
                // Empty
            }
        };
    }

    private final ProgressCallback progressCallback;

    public ProgressLogger(ProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void logSuccess() {
        onComplete(true);
    }

    public void logFailure() {
        onComplete(false);
    }

    public void onComplete(boolean success) {
        logProgress(success ?"Completed successfully" : "Failed");
        if (progressCallback != null) {
            progressCallback.onComplete(success);
        }
    }

    public boolean loggedMoreSecondsAgoThan(long secondsAgo) {
        return RelativeTime.moreSecondsAgoThan(lastLoggedAt, secondsAgo);
    }

    public void logProgress(CharSequence message) {
        updateLastLoggedTime();
        MyLog.d(this, "Progress: " + message);
        if (progressCallback != null) {
            progressCallback.onProgressMessage(message);
        }
    }

    public void updateLastLoggedTime() {
        lastLoggedAt = System.currentTimeMillis();
    }

    public static ProgressLogger getEmpty() {
        return new ProgressLogger(getEmptyCallback());
    }
}
