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

import android.support.annotation.NonNull;

import org.andstatus.app.net.social.MbActivityType;

/**
 * This was called "verb" in ActivityStreams 1
 * These are only partially the same as:
 * @see <a href="https://www.w3.org/TR/activitystreams-vocabulary/#activity-types">Activity Types</a>
 */
enum ActivityType {
    DELETE(MbActivityType.DELETE, "delete", false, false),
    FAVORITE(MbActivityType.LIKE, "favorite", false, false),
    FOLLOW(MbActivityType.FOLLOW, "follow", false, false),
    POST(MbActivityType.CREATE, "post", true, false),
    SHARE(MbActivityType.ANNOUNCE, "share", false, false),
    STOP_FOLLOWING(MbActivityType.UNDO_FOLLOW, "stop-following", false, false),
    UNFAVORITE(MbActivityType.UNDO_LIKE, "unfavorite", false, false),
    UNKNOWN(MbActivityType.EMPTY, "unknown", false, false),
    UPDATE(MbActivityType.UPDATE, "update", false, false);

    final MbActivityType mbActivityType;
    final String code;
    final boolean addPublic;
    final boolean addFollowers;

    ActivityType(MbActivityType mbActivityType, String code, boolean addPublic, boolean addFollowers) {
        this.mbActivityType = mbActivityType;
        this.code = code;
        this.addPublic = addPublic;
        this.addFollowers = addFollowers;
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static ActivityType load(String strCode) {
        if ("unfollow".equalsIgnoreCase(strCode)) {
            return STOP_FOLLOWING;
        }
        if ("unlike".equalsIgnoreCase(strCode)) {
            return UNFAVORITE;
        }
        for (ActivityType value : ActivityType.values()) {
            if (value.code.equalsIgnoreCase(strCode)) {
                return value;
            }
        }
        return UNKNOWN;
    }

}
