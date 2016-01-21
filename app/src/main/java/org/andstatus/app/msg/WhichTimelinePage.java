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

import android.content.Context;
import android.os.Bundle;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public enum WhichTimelinePage {
    NEW(1, R.string.timeline_page_new),
    YOUNGER(2, R.string.timeline_page_younger),
    YOUNGEST(3, R.string.timeline_page_youngest),
    SAME(4, R.string.timeline_page_the_same),
    OLDER(5, R.string.timeline_page_older),
    EMPTY(6, R.string.timeline_page_empty);

    private static final String TAG = WhichTimelinePage.class.getSimpleName();
    private final long code;
    private final int titleResId;

    WhichTimelinePage(long code, int titleResId) {
        this.code = code;
        this.titleResId = titleResId;
    }

    public Bundle save(Bundle bundle) {
        bundle.putString(IntentExtra.WHICH_PAGE.key, save());
        return bundle;
    }

    public String save() {
        return Long.toString(code);
    }

    public static WhichTimelinePage load(String strCode, WhichTimelinePage defaultPage) {
        if (strCode != null) {
            try {
                return load(Long.parseLong(strCode));
            } catch (NumberFormatException e) {
                MyLog.v(TAG, "Error converting '" + strCode + "'", e);
            }
        }
        return defaultPage;
    }

    public static WhichTimelinePage load(long code) {
        for(WhichTimelinePage val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return EMPTY;
    }

    public static WhichTimelinePage load(Bundle args) {
        if (args != null) {
            return WhichTimelinePage.load(args.getString(IntentExtra.WHICH_PAGE.key), EMPTY);
        }
        return EMPTY;
    }

    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.name();
        } else {
            return context.getText(titleResId);
        }
    }
}
