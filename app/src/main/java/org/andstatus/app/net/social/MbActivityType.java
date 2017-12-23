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

package org.andstatus.app.net.social;

import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.R;

/**
 * Activity type in a sense of
 * <a href="https://www.w3.org/TR/activitystreams-vocabulary/#activity-types">Activity Vocabulary</a>
 * and ActivityPub, see <a href="https://www.w3.org/TR/activitypub/#announce-activity-inbox">announce-activity-inbox</a>
 */
public enum MbActivityType {
    ANNOUNCE(1, R.string.reblogged),  // known also as Repost, Retweet, Boost...
    CREATE(2, R.string.created),
    DELETE(3, R.string.deleted),
    FOLLOW(4, R.string.followed),
    LIKE(5, R.string.liked),
    UPDATE(6, R.string.updated),
    UNDO_ANNOUNCE(7, R.string.undid_reblog),
    UNDO_FOLLOW(8, R.string.undid_follow),
    UNDO_LIKE(9, R.string.undid_like),
    JOIN(10, R.string.joined),
    EMPTY(0, R.string.empty_in_parenthesis);

    public final long id;
    public final int actedResourceId;

    MbActivityType(long id, int actedResourceId) {
        this.id = id;
        this.actedResourceId = actedResourceId;
    }

    public static MbActivityType undo(MbActivityType type) {
        switch (type) {
            case ANNOUNCE:
                return UNDO_ANNOUNCE;
            case FOLLOW:
                return UNDO_FOLLOW;
            case LIKE:
                return UNDO_LIKE;
            default:
                return EMPTY;
        }
    }

    /** @return the enum or {@link #EMPTY} */
    @NonNull
    public static MbActivityType fromId(long id) {
        for (MbActivityType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return EMPTY;
    }

    public CharSequence getActedTitle(Context context) {
        if (actedResourceId == 0 || context == null) {
            return this.name();
        } else {
            return context.getText(actedResourceId);
        }
    }

}
