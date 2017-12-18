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
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;

/**
 * Types of events, about which a User may be notified and which are shown in the "Notifications" timeline
 */
public enum NotificationEventType {
    ANNOUNCE(1, "notifications_announce", asList(TimelineType.HOME), true, R.string.notification_events_announce),
    FOLLOW(2, "notifications_follow", EMPTY_LIST, true, R.string.notification_events_follow),
    LIKE(3, "notifications_like", EMPTY_LIST, true, R.string.notification_events_like),
    MENTION(4, "notifications_mention", asList(TimelineType.HOME, TimelineType.MENTIONS), true, R.string.notification_events_mention),
    OUTBOX(5, "notifications_outbox", asList(TimelineType.OUTBOX), true, org.andstatus.app.R.string.notification_events_outbox),
    PRIVATE(6, "notifications_private", asList(TimelineType.PRIVATE), true, R.string.notification_events_private),
    OTHER(7, "notification_unknown", EMPTY_LIST, false, R.string.unknown_in_parenthesis),
    EMPTY(0, "", EMPTY_LIST, false, R.string.empty_in_parenthesis),
    ;

    public static final List<NotificationEventType> validValues = validValues();

    public final long id;
    public final String preferenceKey;
    final boolean defaultValue;
    final List<TimelineType> visibleIn;
    public final int titleResId;

    NotificationEventType(long id, String preferenceKey, List<TimelineType> visibleIn, boolean defaultValue, int titleResId) {
        this.id = id;
        this.preferenceKey = preferenceKey;
        this.visibleIn = visibleIn;
        this.defaultValue = defaultValue;
        this.titleResId = titleResId;
    }

    public boolean isShownOn(TimelineType timelineType) {
        switch (timelineType) {
            case NOTIFICATIONS:
            case EVERYTHING:
                return true;
            default:
                return visibleIn.contains(timelineType);
        }
    }

    public static List<Long> idsOfShownOn(TimelineType timelineType) {
        return validValues().stream().filter(eventType -> eventType.isShownOn(timelineType))
                .map(eventType -> eventType.id).collect(Collectors.toList());
    }

    public boolean isEnabled() {
        return StringUtils.nonEmpty(preferenceKey) && SharedPreferencesUtil.getBoolean(preferenceKey, defaultValue);
    }

    public void setEnabled(boolean enabled) {
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
        return validValues;
    }

    public boolean isEmpty() {
        return this == EMPTY;
    }
}
