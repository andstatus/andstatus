/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.notification

import android.net.Uri
import android.provider.Settings
import org.andstatus.app.context.MyPreferences
import org.andstatus.app.util.SharedPreferencesUtil

/**
 * How to notify a User
 */
enum class NotificationMethodType(val id: Long, val preferenceKey: String, val defaultValue: Boolean) {
    NOTIFICATION_AREA(1, "notification_in_notification_area", true),
    VIBRATION(2, "vibration", true),
    SOUND(3, MyPreferences.KEY_NOTIFICATION_METHOD_SOUND, false),
    EMPTY(0, "", false);

    val isEnabled: Boolean get() = if (preferenceKey.isEmpty()) false else when (this) {
        SOUND -> !SharedPreferencesUtil.getString(preferenceKey, "").isNullOrEmpty()
        else -> SharedPreferencesUtil.getBoolean(preferenceKey, defaultValue)
    }

    val uri: Uri get() = when (this) {
        SOUND -> {
            val uriString = SharedPreferencesUtil.getString(preferenceKey,
                    Settings.System.DEFAULT_NOTIFICATION_URI.toString())
            if (uriString.isNullOrEmpty()) Uri.EMPTY else Uri.parse(uriString)
        }
        else -> Uri.EMPTY
    }

    fun setEnabled(enabled: Boolean) {
        if (preferenceKey.isNotEmpty()) SharedPreferencesUtil.putBoolean(preferenceKey, enabled)
    }

    fun isEmpty(): Boolean {
        return this == EMPTY
    }

    companion object {
        /** @return the enum or [.EMPTY]
         */
        fun fromId(id: Long): NotificationMethodType {
            for (type in values()) {
                if (type.id == id) {
                    return type
                }
            }
            return EMPTY
        }
    }
}
