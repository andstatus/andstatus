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

import android.support.annotation.NonNull;

/**
 * Activity type in a sense of
 * Activity Vocabulary https://www.w3.org/TR/activitystreams-vocabulary/#activity-types
 * and ActivityPub, see https://www.w3.org/TR/activitypub/#announce-activity-inbox
 */
public enum MbActivityType {
    ANNOUNCE(1),  // known also as Repost, Retweet, Boost...
    CREATE(2),
    DELETE(3),
    FOLLOW(4),
    LIKE(5),
    UPDATE(6),
    UNDO_ANNOUNCE(7),
    UNDO_FOLLOW(8),
    UNDO_LIKE(9),
    EMPTY(10);

    final long id;

    MbActivityType(long id) {
        this.id = id;
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
}
