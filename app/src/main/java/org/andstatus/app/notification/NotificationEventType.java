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

import org.andstatus.app.R;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Types of events, about which a User may be notified and which are shown in the "Notifications" timeline
 */
public enum NotificationEventType {
    ANNOUNCE(1, "notifications_announce", singletonList(TimelineType.HOME), true, R.string.notification_events_announce),
    FOLLOW(2, "notifications_follow", emptyList(), true, R.string.notification_events_follow),
    LIKE(3, "notifications_like", emptyList(), true, R.string.notification_events_like),
    MENTION(4, "notifications_mention", singletonList(TimelineType.HOME), true, R.string.notification_events_mention),
    OUTBOX(5, "notifications_outbox", singletonList(TimelineType.OUTBOX), true, org.andstatus.app.R.string.notification_events_outbox),
    PRIVATE(6, "notifications_private", singletonList(TimelineType.PRIVATE), true, R.string.notification_events_private),
    SERVICE_RUNNING(8, "", emptyList(), true, R.string.syncing),
    EMPTY(0, "", emptyList(), false, R.string.empty_in_parenthesis),
    ;

    public static final List<NotificationEventType> validValues = validValues();

    public final long id;
    public final String preferenceKey;
    final boolean defaultValue;
    final List<TimelineType> visibleIn;
    public final int titleResId;

    NotificationEventType(int id, String preferenceKey, List<TimelineType> visibleIn, boolean defaultValue, int titleResId) {
        this.id = id;
        this.preferenceKey = preferenceKey;
        this.visibleIn = visibleIn;
        this.defaultValue = defaultValue;
        this.titleResId = titleResId;
    }

    public boolean isShownOn(TimelineType timelineType) {
        if (this == SERVICE_RUNNING) return false;
        switch (timelineType) {
            case EVERYTHING:
            case NOTIFICATIONS:
                return true;
            case INTERACTIONS:
                return this != OUTBOX;
            case UNREAD_NOTIFICATIONS:
                return true;
            default:
                return visibleIn.contains(timelineType);
        }
    }

    public int notificationId() {
        return (int) id;
    }

    public static List<Long> idsOfShownOn(TimelineType timelineType) {
        return validValues().stream().filter(eventType -> eventType.isShownOn(timelineType))
                .map(eventType -> eventType.id).collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return StringUtils.nonEmpty(preferenceKey) ? SharedPreferencesUtil.getBoolean(preferenceKey, defaultValue) :
                defaultValue;
    }

    void setEnabled(boolean enabled) {
        if (StringUtils.nonEmpty(preferenceKey)) SharedPreferencesUtil.putBoolean(preferenceKey, enabled);
    }

    /** @return the enum or {@link #EMPTY} */
    @NonNull
    public static NotificationEventType fromId(long id) {
        for (NotificationEventType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return EMPTY;
    }

    private static List<NotificationEventType> validValues() {
        List<NotificationEventType> validValues = new ArrayList<>();
        for (NotificationEventType event : values()) {
            if (event != EMPTY) {
                validValues.add(event);
            }
        }
        return Collections.unmodifiableList(validValues);
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }
}
