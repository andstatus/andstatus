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

import android.content.Context;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

public enum DownloadStatus {
    LOADED(2, 0),
    SOFT_ERROR(4, 0),
    HARD_ERROR(5, 0),
    ABSENT(6, 0),
    SENDING(7, R.string.download_status_unsent),
    DRAFT(8, R.string.download_status_draft),
    DELETED(9, 0),
    UNKNOWN(0, 0);

    private static final String TAG = DownloadStatus.class.getSimpleName();

    private long code;
    private int titleResourceId;

    DownloadStatus(long codeIn, int titleResourceIdIn) {
        code = codeIn;
        titleResourceId = titleResourceIdIn;
    }
    
    public long save() {
        return code;
    }
    
    public static DownloadStatus load(long codeIn) {
        for (DownloadStatus val : values()) {
            if (val.code == codeIn) {
                return val;
            }
        }
        return UNKNOWN;
    }

    public CharSequence getTitle(Context context) {
        if (titleResourceId == 0 || context == null) {
            return this.toString();
        } else {
            return context.getText(titleResourceId);
        }
    }

    public boolean mayBeSent() {
        return mayBeSent(this);
    }

    private static boolean mayBeSent(DownloadStatus status) {
        switch (status) {
            case SENDING:
            case HARD_ERROR:
            case SOFT_ERROR:
                return true;
            default:
                return false;
        }
    }
}