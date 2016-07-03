/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.SelectableEnum;
import org.andstatus.app.R;

public enum DisplayedInSelector implements SelectableEnum {
    ALWAYS("always", R.string.always),
    IN_CONTEXT("in_context", R.string.in_context),
    NO("no", R.string.no);

    /** Code - identifier of the type */
    private final String code;
    /** The id of the string resource with the localized name of this enum to use in UI */
    private final int titleResId;

    DisplayedInSelector(String code, int titleResId) {
        this.code = code;
        this.titleResId = titleResId;
    }

    /** Returns the enum or NO */
    @NonNull
    public static DisplayedInSelector load(String strCode) {
        for (DisplayedInSelector value : DisplayedInSelector.values()) {
            if (value.code.equals(strCode)) {
                return value;
            }
        }
        return NO;
    }

    /** String to be used for persistence */
    public String save() {
        return code;
    }

    @Override
    public String toString() {
        return "DisplayedInSelector:" + code;
    }

    /** Localized title for UI */
    @Override
    public CharSequence getTitle(Context context) {
        if (titleResId == 0 || context == null) {
            return this.code;
        } else {
            return context.getText(titleResId);
        }
    }

    @Override
    public boolean isSelectable() {
        return true;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public int getDialogTitleResId() {
        return R.string.select_displayed_in_selector;
    }
}
