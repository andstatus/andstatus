/*
 * Copyright (C) 2013 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.data;

import org.andstatus.app.util.MyLog;

public enum DownloadStatus {
    LOADED(2),
    SOFT_ERROR(4),
    HARD_ERROR(5),
    ABSENT(6),
    SENDING(7),
    UNKNOWN(0);

    private static final String TAG = DownloadStatus.class.getSimpleName();

    private long code;

    DownloadStatus(long codeIn) {
        code = codeIn;
    }
    
    public String save() {
        return Long.toString(code);
    }
    
    public static DownloadStatus load(String strCode) {
        try {
            return load(Long.parseLong(strCode));
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static DownloadStatus load(long codeIn) {
        for (DownloadStatus val : values()) {
            if (val.code == codeIn) {
                return val;
            }
        }
        return UNKNOWN;
    }
}