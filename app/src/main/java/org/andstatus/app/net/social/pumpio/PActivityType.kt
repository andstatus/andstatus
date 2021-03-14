package org.andstatus.app.net.social.pumpio

import org.andstatus.app.net.social.ActivityType

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
 */ /**
 * This was called "verb" in ActivityStreams 1
 * These are only partially the same as:
 * @see [Activity Types](https://www.w3.org/TR/activitystreams-vocabulary/.activity-types)
 */
internal enum class PActivityType(val activityType: ActivityType?, val code: String?) {
    DELETE(ActivityType.DELETE, "delete"),
    FAVORITE(ActivityType.LIKE, "favorite"),
    FOLLOW(ActivityType.FOLLOW, "follow"),
    POST(ActivityType.CREATE, "post"),
    SHARE(ActivityType.ANNOUNCE, "share"),
    STOP_FOLLOWING(ActivityType.UNDO_FOLLOW, "stop-following"),
    UNFAVORITE(ActivityType.UNDO_LIKE, "unfavorite"),
    UNKNOWN(ActivityType.EMPTY, "unknown"),
    UPDATE(ActivityType.UPDATE, "update");

    companion object {
        /** Returns the enum or UNKNOWN  */
        fun load(strCode: String?): PActivityType {
            if ("unfollow".equals(strCode, ignoreCase = true)) {
                return STOP_FOLLOWING
            }
            if ("unlike".equals(strCode, ignoreCase = true)) {
                return UNFAVORITE
            }
            for (value in values()) {
                if (value.code.equals(strCode, ignoreCase = true)) {
                    return value
                }
            }
            return UNKNOWN
        }
    }
}