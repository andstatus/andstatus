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

package org.andstatus.app.data;

import org.andstatus.app.graphics.CacheName;
import org.andstatus.app.util.MyLog;

public enum DownloadType {
    AVATAR(1, "av", CacheName.AVATAR),
    ATTACHMENT(2, "at", CacheName.ATTACHED_IMAGE),
    UNKNOWN(0, "un", CacheName.ATTACHED_IMAGE);

    private static final String TAG = DownloadType.class.getSimpleName();
    
    private final long code;
    public final String filePrefix;
    public final CacheName cacheName;

    DownloadType(long code, String filePrefix, CacheName cacheName) {
        this.code = code;
        this.filePrefix = filePrefix;
        this.cacheName = cacheName;
    }

    public String save() {
        return Long.toString(code);
    }
    
    public static DownloadType load(String strCode) {
        try {
            return load(Long.parseLong(strCode));
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static DownloadType load( long code) {
        for(DownloadType val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return UNKNOWN;
    }
    
}
