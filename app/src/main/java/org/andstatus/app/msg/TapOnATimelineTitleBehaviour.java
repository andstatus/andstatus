/**
 * Copyright (C) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import android.text.TextUtils;

import org.andstatus.app.util.MyLog;

public enum TapOnATimelineTitleBehaviour {
    SWITCH_TO_DEFAULT_TIMELINE(1),
    GO_TO_THE_TOP(2),
    SELECT_TIMELINE(3),
    DISABLED(4);

    public static final TapOnATimelineTitleBehaviour DEFAULT = SELECT_TIMELINE;
    private static final String TAG = TapOnATimelineTitleBehaviour.class.getSimpleName();

    private final long code;

    TapOnATimelineTitleBehaviour(long code) {
        this.code = code;
    }
    
    public String save() {
        return Long.toString(code);
    }

    /**
     * Returns the enum or {@link #DEFAULT}
     */
    public static TapOnATimelineTitleBehaviour load(String strCode) {
        try {
            if (!TextUtils.isEmpty(strCode)) {
                return load(Long.parseLong(strCode));
            }
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return DEFAULT;
    }
    
    public static TapOnATimelineTitleBehaviour load(long code) {
        for(TapOnATimelineTitleBehaviour val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return DEFAULT;
    }
    
}
