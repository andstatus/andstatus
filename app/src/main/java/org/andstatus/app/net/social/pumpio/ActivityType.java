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

/**
 * This was called "verb" in ActivityStreams 1
 * These are only partially the same as:
 * @see <a href="https://www.w3.org/TR/activitystreams-vocabulary/#activity-types">Activity Types</a>
 */
enum ActivityType {
    DELETE("delete", false, false),
    FAVORITE("favorite", false, false),
    FOLLOW("follow", false, false),
    POST("post", true, false),
    SHARE("share", true, true),
    STOP_FOLLOWING("stop-following", false, false),
    UNFAVORITE("unfavorite", false, false),
    UNKNOWN("unknown", false, false),
    UPDATE("update", false, false);

    final String code;
    final boolean addPublic;
    final boolean addFollowers;

    ActivityType(String code, boolean addPublic, boolean addFollowers) {
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
