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
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;

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

    /** key used in preference headers */
    public final String key;
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

    /** @return  the enum or UNKNOWN */
    public static MySettingsGroup from(String key) {
        for (MySettingsGroup value : MySettingsGroup.values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return UNKNOWN;
    }

    /** @return  the enum or UNKNOWN */
    public static MySettingsGroup fromIntent(Intent intent) {
        return from(intent.getStringExtra(IntentExtra.SETTINGS_GROUP.key));
    }

    public Intent add(Intent intent) {
        return intent.putExtra(IntentExtra.SETTINGS_GROUP.key, key);
    }

    public int getTitleResId() {
        return titleResId;
    }

    public static void setDefaultValues(@NonNull Activity activity) {
        for (MySettingsGroup item : MySettingsGroup.values()) {
            if (item != UNKNOWN) {
                PreferenceManager.setDefaultValues(activity, item.getPreferencesXmlResId(), false);
            }
        }
    }
}