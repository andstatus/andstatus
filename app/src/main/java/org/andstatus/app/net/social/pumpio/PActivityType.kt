package org.andstatus.app.net.social.pumpio;
/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import androidx.annotation.NonNull;

import org.andstatus.app.net.social.ActivityType;

/**
 * This was called "verb" in ActivityStreams 1
 * These are only partially the same as:
 * @see <a href="https://www.w3.org/TR/activitystreams-vocabulary/#activity-types">Activity Types</a>
 */
enum PActivityType {
    DELETE(ActivityType.DELETE, "delete"),
    FAVORITE(ActivityType.LIKE, "favorite"),
    FOLLOW(ActivityType.FOLLOW, "follow"),
    POST(ActivityType.CREATE, "post"),
    SHARE(ActivityType.ANNOUNCE, "share"),
    STOP_FOLLOWING(ActivityType.UNDO_FOLLOW, "stop-following"),
    UNFAVORITE(ActivityType.UNDO_LIKE, "unfavorite"),
    UNKNOWN(ActivityType.EMPTY, "unknown"),
    UPDATE(ActivityType.UPDATE, "update");

    final ActivityType activityType;
    final String code;

    PActivityType(ActivityType activityType, String code) {
        this.activityType = activityType;
        this.code = code;
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static PActivityType load(String strCode) {
        if ("unfollow".equalsIgnoreCase(strCode)) {
            return STOP_FOLLOWING;
        }
        if ("unlike".equalsIgnoreCase(strCode)) {
            return UNFAVORITE;
        }
        for (PActivityType value : PActivityType.values()) {
            if (value.code.equalsIgnoreCase(strCode)) {
                return value;
            }
        }
        return UNKNOWN;
    }

}
