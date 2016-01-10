/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public enum WhichTimelinePage {
    YOUNGER(1, "younger"),
    SAME(2, "same"),
    OLDER(3, "older");

    private static final String TAG = WhichTimelinePage.class.getSimpleName();
    private final long code;
    public final String title;

    WhichTimelinePage(long code, String title) {
        this.code = code;
        this.title = title;
    }

    public String save() {
        return Long.toString(code);
    }

    public static WhichTimelinePage load(String strCode) {
        if (strCode != null) {
            try {
                return load(Long.parseLong(strCode));
            } catch (NumberFormatException e) {
                MyLog.v(TAG, "Error converting '" + strCode + "'", e);
            }
        }
        return SAME;
    }

    public static WhichTimelinePage load( long code) {
        for(WhichTimelinePage val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return SAME;
    }
}
