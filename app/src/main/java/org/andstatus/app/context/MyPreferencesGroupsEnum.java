/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.context;

import android.content.Context;
import android.preference.PreferenceManager;

import org.andstatus.app.R;
import org.andstatus.app.util.MyLog;

public enum MyPreferencesGroupsEnum {
    UNKNOWN("unknown", 0, 0),
    ACCOUNTS("accounts", R.string.header_accounts, R.xml.preferences_accounts),
    APPEARANCE("appearance", R.string.title_preference_appearance, R.xml.preferences_appearance),
    GESTURES("gestures", R.string.gestures, R.xml.preferences_gestures),
    TIMELINE("timeline", R.string.title_timeline, R.xml.preferences_timeline),
    ATTACHMENTS("attachments", R.string.attachments, R.xml.preferences_attachments),
    SYNCING("syncing", R.string.title_preference_syncing, R.xml.preferences_syncing),
    FILTERS("filters", R.string.filters_title, R.xml.preferences_filters),
    NOTIFICATIONS("notifications", R.string.notifications_title, R.xml.preferences_notifications),
    STORAGE("storage", R.string.title_preference_storage, R.xml.preferences_storage),
    INFORMATION("information", R.string.category_title_preference_information, R.xml.preferences_information),
    DEBUGGING("debugging", R.string.title_preference_debugging, R.xml.preferences_debugging);

    /**
     * code of the enum that is used in preference headers
     */
    private final String code;
    private final int titleResId;
    private final int preferencesXmlResId;

    private MyPreferencesGroupsEnum(String code, int titleResId, int preferencesXmlResId) {
        this.code = code;
        this.titleResId = titleResId;
        this.preferencesXmlResId = preferencesXmlResId;
    }

    @Override
    public String toString() {
        return "SettingsFragment:" + code;
    }

    public int getPreferencesXmlResId() {
        return preferencesXmlResId;
    }

    /**
     * Returns the enum or UNKNOWN
     */
    public static MyPreferencesGroupsEnum load(String strCode) {
        for (MyPreferencesGroupsEnum tt : MyPreferencesGroupsEnum.values()) {
            if (tt.code.equals(strCode)) {
                return tt;
            }
        }
        return UNKNOWN;
    }

    public String save() {
        return code;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public static void setDefaultValues() {
        Context context = MyContextHolder.get().context();
        if (context == null) {
            MyLog.e(MyPreferencesGroupsEnum.class, "setDefaultValues - no context");
        } else {
            for (MyPreferencesGroupsEnum item : values()) {
                if (item != UNKNOWN) {
                    PreferenceManager.setDefaultValues(context, item.getPreferencesXmlResId(), false);
                }
            }
        }
    }
}