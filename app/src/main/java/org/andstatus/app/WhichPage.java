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

package org.andstatus.app;

import android.content.Context;
import android.os.Bundle;

import org.andstatus.app.util.MyLog;

/**
 * @author yvolk@yurivolkov.com
 */
public enum WhichPage {
	ANY(8, R.string.page_current),
    CURRENT(1, R.string.page_current),
    YOUNGER(2, R.string.page_younger),
    YOUNGEST(3, R.string.page_youngest),
    TOP(4, R.string.page_top_of),
    OLDER(6, R.string.page_older),
    EMPTY(7, R.string.page_empty);

    private static final String TAG = WhichPage.class.getSimpleName();
    private final long code;
    private final int titleResId;

    WhichPage(long code, int titleResId) {
        this.code = code;
        this.titleResId = titleResId;
    }

    public Bundle toBundle() {
        return save(new Bundle());
    }

    public Bundle save(Bundle bundle) {
        bundle.putString(IntentExtra.WHICH_PAGE.key, save());
        return bundle;
    }

    public String save() {
        return Long.toString(code);
    }

    public static WhichPage load(String strCode, WhichPage defaultPage) {
        if (strCode != null) {
            try {
                return load(Long.parseLong(strCode));
            } catch (NumberFormatException e) {
                MyLog.v(TAG, "Error converting '" + strCode + "'", e);
            }
        }
        return defaultPage;
    }

    public static WhichPage load(long code) {
        for(WhichPage val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return EMPTY;
    }

    public static WhichPage load(Bundle args) {
        if (args != null) {
            return WhichPage.load(args.getString(IntentExtra.WHICH_PAGE.key), EMPTY);
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

    public boolean isYoungest() {
        switch (this) {
            case TOP:
            case YOUNGEST:
                return true;
            default :
                return false;
        }
    }
}
