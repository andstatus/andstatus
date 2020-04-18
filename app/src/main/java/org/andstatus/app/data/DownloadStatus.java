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

public enum DownloadStatus {
    LOADED(2, 0, CanBeDownloaded.YES),
    SOFT_ERROR(4, 0, CanBeDownloaded.NO),
    HARD_ERROR(5, 0, CanBeDownloaded.NO),
    ABSENT(6, 0, CanBeDownloaded.NO),
    SENDING(7, R.string.download_status_unsent, CanBeDownloaded.YES),
    SENT(11, R.string.sent, CanBeDownloaded.YES),
    DRAFT(8, R.string.download_status_draft, CanBeDownloaded.NO),
    DELETED(9, 0, CanBeDownloaded.NO),
    NEEDS_UPDATE(10, 0, CanBeDownloaded.YES),
    UNKNOWN(0, 0, CanBeDownloaded.YES);

    private static final String TAG = DownloadStatus.class.getSimpleName();

    private long code;
    private int titleResourceId;
    public final boolean canBeDownloaded;

    private enum CanBeDownloaded {
        YES,
        NO
    }

    DownloadStatus(long codeIn, int titleResourceIdIn, CanBeDownloaded canBeDownloaded) {
        code = codeIn;
        titleResourceId = titleResourceIdIn;
        this.canBeDownloaded = canBeDownloaded == CanBeDownloaded.YES;
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

    public boolean isUnsentDraft() {
        switch (this) {
            case SENDING:
            case DRAFT:
                return true;
            default:
                return false;
        }
    }

    public boolean mayUpdateContent() {
        switch (this) {
            case SENDING:
            case DRAFT:
            case LOADED:
                return true;
            default:
                return false;
        }
    }

    public boolean isPresentAtServer() {
        switch (this) {
            case SENT:
            case LOADED:
                return true;
            default:
                return false;
        }
    }
}