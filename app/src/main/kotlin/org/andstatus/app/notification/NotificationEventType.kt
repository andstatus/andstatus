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

import org.andstatus.app.R
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.SharedPreferencesUtil
import java.util.*

/**
 * Types of events, about which a User may be notified and which are shown in the "Notifications" timeline
 */
enum class NotificationEventType(id: Int, preferenceKey: String?, defaultValue: Boolean, titleResId: Int) : IsEmpty {
    ANNOUNCE(1, "notifications_announce", true, R.string.notification_events_announce),
    FOLLOW(2, "notifications_follow", true, R.string.notification_events_follow),
    LIKE(3, "notifications_like", true, R.string.notification_events_like),
    MENTION(4, "notifications_mention", true, R.string.notification_events_mention),
    OUTBOX(5, "notifications_outbox", true, R.string.notification_events_outbox),
    PRIVATE(6, "notifications_private", true, R.string.notification_events_private),
    SERVICE_RUNNING(8, "", true, R.string.syncing),
    HOME(9, "notifications_home", false, R.string.options_menu_home_timeline_cond),
    EMPTY(0, "", false, R.string.empty_in_parenthesis);

    val id: Long
    val preferenceKey: String?
    val defaultValue: Boolean
    val titleResId: Int

    fun notificationId(): Int {
        return id.toInt()
    }

    fun isEnabled(): Boolean {
        return if (!preferenceKey.isNullOrEmpty()) SharedPreferencesUtil.getBoolean(preferenceKey, defaultValue) else defaultValue
    }

    fun setEnabled(enabled: Boolean) {
        if (!preferenceKey.isNullOrEmpty()) SharedPreferencesUtil.putBoolean(preferenceKey, enabled)
    }

    fun isInteracted(): Boolean {
        return when (this) {
            ANNOUNCE, FOLLOW, LIKE, MENTION, PRIVATE -> true
            else -> false
        }
    }

    override val isEmpty: Boolean
        get() {
            return this == EMPTY
        }

    companion object {
        val validValues = validValues()

        /** @return the enum or [.EMPTY]
         */
        fun fromId(id: Long): NotificationEventType {
            for (type in values()) {
                if (type.id == id) {
                    return type
                }
            }
            return EMPTY
        }

        private fun validValues(): MutableList<NotificationEventType> {
            val validValues: MutableList<NotificationEventType> = ArrayList()
            for (event in values()) {
                if (event.nonEmpty) {
                    validValues.add(event)
                }
            }
            return Collections.unmodifiableList(validValues)
        }
    }

    init {
        this.id = id.toLong()
        this.preferenceKey = preferenceKey
        this.defaultValue = defaultValue
        this.titleResId = titleResId
    }
}
