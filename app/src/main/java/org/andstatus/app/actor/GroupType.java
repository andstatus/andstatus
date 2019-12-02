/*
 * Copyright (C) 2019 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.actor;

/** See https://www.w3.org/TR/activitystreams-vocabulary/#dfn-group
 * @author yvolk@yurivolkov.com
 */
public enum GroupType {
    NOT_A_GROUP("NotAGroup", 1),
    PUBLIC("Public", 2),
    FRIENDS("Friends", 3),
    FOLLOWERS("Followers", 4),
    ACTOR_OWNED("ActorOwned", 5),
    GENERIC("Generic", 6),
    UNKNOWN("Unknown", 0);

    public final long id;
    public final String name;

    GroupType(String name, long id) {
        this.name = name;
        this.id = id;
    }

    public static GroupType fromId(long id) {
        for(GroupType val : values()) {
            if (val.id == id) {
                return val;
            }
        }
        return UNKNOWN;
    }

    public boolean parentActorRequired() {
        switch (this) {
            case ACTOR_OWNED:
            case FRIENDS:
            case FOLLOWERS:
                return true;
            default:
                return false;
        }
    }

    public boolean isSameActor(GroupType other) {
        if (this == other) return true;
        if ( this == UNKNOWN || other == UNKNOWN) return true;
        if (this == NOT_A_GROUP || this == PUBLIC || this == FRIENDS || this == FOLLOWERS) return false;

        return true;
    }
}
