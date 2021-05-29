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
package org.andstatus.app.net.social

import android.content.Context
import org.andstatus.app.R
import org.andstatus.app.net.social.ActivityType

/**
 * Activity type in a sense of
 * [Activity Vocabulary](https://www.w3.org/TR/activitystreams-vocabulary/#activity-types)
 * and ActivityPub, see [announce-activity-inbox](https://www.w3.org/TR/activitypub/#announce-activity-inbox)
 */
enum class ActivityType(val id: Long, val actedResourceId: Int, val activityPubValue: String?) {
    ANNOUNCE(1, R.string.reblogged, "Announce"),  // known also as Reblog, Repost, Retweet, Boost...
    CREATE(2, R.string.created, "Create"), DELETE(3, R.string.deleted, "Delete"), FOLLOW(4, R.string.followed, "Follow"), LIKE(5, R.string.liked, "Like"), UPDATE(6, R.string.updated, "Update"), UNDO_ANNOUNCE(7, R.string.undid_reblog, "Undo"), UNDO_FOLLOW(8, R.string.undid_follow, "Undo"), UNDO_LIKE(9, R.string.undid_like, "Undo"), JOIN(10, R.string.joined, "Join"), EMPTY(0, R.string.empty_in_parenthesis, "(empty)");

    fun getActedTitle(context: Context?): CharSequence? {
        return if (actedResourceId == 0 || context == null) {
            name
        } else {
            context.getText(actedResourceId)
        }
    }

    companion object {
        fun undo(type: ActivityType): ActivityType {
            return when (type) {
                ActivityType.ANNOUNCE -> ActivityType.UNDO_ANNOUNCE
                ActivityType.FOLLOW -> ActivityType.UNDO_FOLLOW
                ActivityType.LIKE -> ActivityType.UNDO_LIKE
                else -> ActivityType.EMPTY
            }
        }

        /** @return the enum or [.EMPTY]
         */
        fun fromId(id: Long): ActivityType {
            for (type in ActivityType.values()) {
                if (type.id == id) {
                    return type
                }
            }
            return ActivityType.EMPTY
        }

        fun from(activityPubValue: String?): ActivityType {
            for (type in ActivityType.values()) {
                if (type.activityPubValue == activityPubValue) {
                    return type
                }
            }
            return ActivityType.EMPTY
        }
    }
}
