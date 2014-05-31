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

public class ProgressLogger {
    public interface ProgressCallback {
        void onProgressMessage(String message);
        void onComplete(boolean success);
    }

    private final ProgressCallback progressCallback;

    public ProgressLogger(ProgressCallback progressCallback) {
        this.progressCallback = progressCallback;
    }

    void logSuccess() {
        logProgress("Completed successfully");
        if (progressCallback != null) {
            progressCallback.onComplete(true);
        }
    }

    void logFailure() {
        logProgress("Failed");
        if (progressCallback != null) {
            progressCallback.onComplete(false);
        }
    }

    public void logProgress(String message) {
        MyLog.v(this, "Progress: " + message);
        if (progressCallback != null) {
            progressCallback.onProgressMessage(message);
        }
    }

    public static ProgressLogger getEmpty() {
        return new ProgressLogger(null);
    }
}
