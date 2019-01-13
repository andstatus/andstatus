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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import org.andstatus.app.R;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.TriState;

import java.util.concurrent.atomic.AtomicReference;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import static androidx.preference.PreferenceFragmentCompat.ARG_PREFERENCE_ROOT;

public enum MySettingsGroup {
    UNKNOWN("unknown", R.string.settings_activity_title, R.xml.preference_headers),
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

    private static final String SET_DEFAULT_VALUES = "setDefaultValues";
    private static final AtomicReference<TriState> resultOfSettingDefaults = new AtomicReference<>(TriState.UNKNOWN);

    /** key used in preference headers */
    private final String key;
    private final int titleResId;
    private final int preferencesXmlResId;

    MySettingsGroup(String key, int titleResId, int preferencesXmlResId) {
        this.key = key;
        this.titleResId = titleResId;
        this.preferencesXmlResId = preferencesXmlResId;
    }

    @Override
    public String toString() {
        return "SettingsFragment:" + key;
    }

    public int getPreferencesXmlResId() {
        return preferencesXmlResId;
    }

    /**
     * Returns the enum or UNKNOWN
     */
    public static MySettingsGroup from(Fragment fragment) {
        if (fragment == null) return UNKNOWN;

        Bundle args = fragment.getArguments();
        return args == null
                ? UNKNOWN
                : from(args.getString(ARG_PREFERENCE_ROOT));
    }

    /**
     * Returns the enum or UNKNOWN
     */
    public static MySettingsGroup from(String key) {
        for (MySettingsGroup value : MySettingsGroup.values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public String save() {
        return key;
    }

    public int getTitleResId() {
        return titleResId;
    }

    /** @return success */
    public static boolean setDefaultValues(Context context) {
        if (context == null) {
            MyLog.e(MySettingsGroup.class, SET_DEFAULT_VALUES + " no context");
            return false;
        }
        if (!Activity.class.isInstance(context)) {
            MyLog.e(MySettingsGroup.class, SET_DEFAULT_VALUES + " should be called with Activity context");
            return false;
        }

        synchronized (resultOfSettingDefaults) {
            resultOfSettingDefaults.set(TriState.UNKNOWN);
            try {
                ((Activity) context).runOnUiThread( () -> setDefaultValuesOnUiThread(context));
                for (int i = 0; i < 100; i++) {
                    DbUtils.waitMs(MySettingsGroup.class, 50);
                    if (resultOfSettingDefaults.get().known) break;
                }
            } catch (Exception e) {
                MyLog.e(MySettingsGroup.class, SET_DEFAULT_VALUES + " error:" + e.getMessage() +
                        "\n" + MyLog.getStackTrace(e));
            }
        }
        return resultOfSettingDefaults.get().toBoolean(false);
    }

    private static void setDefaultValuesOnUiThread(Context context) {
        try {
            MyLog.i(MySettingsGroup.class, SET_DEFAULT_VALUES + " started");
            for (MySettingsGroup item : values()) {
                if (item != UNKNOWN) {
                    PreferenceManager.setDefaultValues(context, item.getPreferencesXmlResId(), false);
                }
            }
            resultOfSettingDefaults.set(TriState.TRUE);
            MyLog.i(MySettingsGroup.class, SET_DEFAULT_VALUES + " completed");
            return;
        } catch (Exception e ) {
            MyLog.w(MySettingsGroup.class, SET_DEFAULT_VALUES + " error:" + e.getMessage() +
                    "\n" + MyLog.getStackTrace(e));
        }
        resultOfSettingDefaults.set(TriState.FALSE);
    }
}