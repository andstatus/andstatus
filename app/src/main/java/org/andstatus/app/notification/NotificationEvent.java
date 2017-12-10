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

import org.andstatus.app.R;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.EMPTY_LIST;

/**
 *
 */
public enum NotificationEvent {
    ANNOUNCE(1, "notifications_announce", asList(TimelineType.HOME), true, R.string.notification_events_announce),
    FOLLOW(2, "notifications_follow", EMPTY_LIST, true, R.string.notification_events_follow),
    LIKE(3, "notifications_like", EMPTY_LIST, true, R.string.notification_events_like),
    MENTION(4, "notifications_mention", asList(TimelineType.HOME, TimelineType.MENTIONS), true, R.string.notification_events_mention),
    OUTBOX(5, "notifications_outbox", asList(TimelineType.OUTBOX), true, org.andstatus.app.R.string.notification_events_outbox),
    PRIVATE(6, "notifications_private", asList(TimelineType.PRIVATE), false, R.string.notification_events_private),
    OTHER(7, "", EMPTY_LIST, false, 0),
    EMPTY(0, "", EMPTY_LIST, false, 0),
    ;

    final long id;
    final String preferenceKey;
    final boolean defaultValue;
    final List<TimelineType> visibleIn;
    public final int titleResId;

    NotificationEvent(long id, String preferenceKey, List<TimelineType> visibleIn, boolean defaultValue, int titleResId) {
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

    public boolean areNotificationsEnabled() {
        return StringUtils.nonEmpty(preferenceKey) && SharedPreferencesUtil.getBoolean(preferenceKey, defaultValue);
    }

}
