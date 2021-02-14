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
package org.andstatus.app.context

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.andstatus.app.IntentExtra
import org.andstatus.app.R

enum class MySettingsGroup(
        /** key used in preference headers  */
        val key: String?, private val titleResId: Int, private val preferencesXmlResId: Int) {
    UNKNOWN("unknown", R.string.settings_activity_title, R.xml.preference_headers), ACCOUNTS("accounts", R.string.header_accounts, R.xml.preferences_accounts), APPEARANCE("appearance", R.string.title_preference_appearance, R.xml.preferences_appearance), GESTURES("gestures", R.string.gestures, R.xml.preferences_gestures), TIMELINE("timeline", R.string.title_timeline, R.xml.preferences_timeline), ATTACHMENTS("attachments", R.string.attachments, R.xml.preferences_attachments), SYNCING("syncing", R.string.title_preference_syncing, R.xml.preferences_syncing), FILTERS("filters", R.string.filters_title, R.xml.preferences_filters), NOTIFICATIONS("notifications", R.string.notifications_title, R.xml.preferences_notifications), STORAGE("storage", R.string.title_preference_storage, R.xml.preferences_storage), INFORMATION("information", R.string.category_title_preference_information, R.xml.preferences_information), DEBUGGING("debugging", R.string.title_preference_debugging, R.xml.preferences_debugging);

    override fun toString(): String {
        return "SettingsFragment:$key"
    }

    fun getPreferencesXmlResId(): Int {
        return preferencesXmlResId
    }

    fun add(intent: Intent?): Intent? {
        return intent.putExtra(IntentExtra.SETTINGS_GROUP.key, key)
    }

    fun getTitleResId(): Int {
        return titleResId
    }

    companion object {
        /**
         * Returns the enum or UNKNOWN
         */
        fun from(fragment: Fragment?): MySettingsGroup? {
            if (fragment == null) return UNKNOWN
            val args = fragment.arguments
            return if (args == null) UNKNOWN else from(args.getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT))
        }

        /** @return  the enum or UNKNOWN
         */
        fun from(key: String?): MySettingsGroup? {
            for (value in values()) {
                if (value.key == key) {
                    return value
                }
            }
            return UNKNOWN
        }

        /** @return  the enum or UNKNOWN
         */
        fun fromIntent(intent: Intent?): MySettingsGroup? {
            return from(intent.getStringExtra(IntentExtra.SETTINGS_GROUP.key))
        }

        fun setDefaultValues(activity: Activity) {
            for (item in values()) {
                if (item != UNKNOWN) {
                    PreferenceManager.setDefaultValues(activity, item.getPreferencesXmlResId(), false)
                }
            }
        }
    }
}