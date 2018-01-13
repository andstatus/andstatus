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

package org.andstatus.app.notification;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

/**
 * How to notify a User
 */
public enum NotificationMethodType {
    NOTIFICATION_AREA(1, "notification_in_notification_area", true),
    VIBRATION(2, "vibration", true),
    SOUND(3, MyPreferences.KEY_NOTIFICATION_METHOD_SOUND, false),
    EMPTY(0, "", false),
    ;

    public final long id;
    public final String preferenceKey;
    final boolean defaultValue;

    NotificationMethodType(long id, String preferenceKey, boolean defaultValue) {
        this.id = id;
        this.preferenceKey = preferenceKey;
        this.defaultValue = defaultValue;
    }

    public boolean isEnabled() {
        if (TextUtils.isEmpty(preferenceKey) ) return false;
        switch (this) {
            case SOUND:
                return StringUtils.nonEmpty(SharedPreferencesUtil.getString(preferenceKey, ""));
            default:
                return SharedPreferencesUtil.getBoolean(preferenceKey, defaultValue);
        }
    }

    @NonNull
    public String getString() {
        switch (this) {
            case SOUND:
                return SharedPreferencesUtil.getString(preferenceKey, "");
            default:
                return "";
        }
    }

    void setEnabled(boolean enabled) {
        if (StringUtils.nonEmpty(preferenceKey)) SharedPreferencesUtil.putBoolean(preferenceKey, enabled);
    }

    /** @return the enum or {@link #EMPTY} */
    @NonNull
    public static NotificationMethodType fromId(long id) {
        for (NotificationMethodType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return EMPTY;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }
}
