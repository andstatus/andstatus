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
    NOT_A_GROUP("NotAGroup", 1, IsGroupLike.NO, IsCollection.NO, IsActorOwned.NO),
    PUBLIC("Public", 2, IsGroupLike.YES, IsCollection.NO, IsActorOwned.NO),
    GENERIC("Generic", 6, IsGroupLike.YES, IsCollection.NO, IsActorOwned.NO),
    COLLECTION("Collection", 7, IsGroupLike.YES, IsCollection.YES, IsActorOwned.NO),
    FRIENDS("Friends", 3, IsGroupLike.YES, IsCollection.YES, IsActorOwned.YES),
    FOLLOWERS("Followers", 4, IsGroupLike.YES, IsCollection.YES, IsActorOwned.YES),
    ACTOR_OWNED("ActorOwned", 5, IsGroupLike.YES, IsCollection.NO, IsActorOwned.YES),
    UNKNOWN("Unknown", 0, IsGroupLike.NO, IsCollection.NO, IsActorOwned.NO);

    public final long id;
    public final String name;
    /** true if this is a "Group" object or simply a "Collection" of Actors */
    public final boolean isGroupLike;
    /** The Group id points to a Collection object */
    public final boolean isCollection;
    /** A groupLike that has a parent actor */
    public final boolean parentActorRequired;

    private enum  IsGroupLike {
        YES,
        NO;
    }

    private enum  IsCollection {
        YES,
        NO;
    }

    private enum  IsActorOwned {
        YES,
        NO;
    }

    GroupType(String name, long id, IsGroupLike isGroupLike, IsCollection isCollection, IsActorOwned isActorOwned) {
        this.name = name;
        this.id = id;
        this.isGroupLike = isGroupLike == IsGroupLike.YES;
        this.isCollection = isCollection == IsCollection.YES;
        this.parentActorRequired = isActorOwned == IsActorOwned.YES;
    }

    public static GroupType fromId(long id) {
        for(GroupType val : values()) {
            if (val.id == id) {
                return val;
            }
        }
        return UNKNOWN;
    }

    public boolean isSameActor(GroupType other) {
        if (this == other) return true;
        if ( this == UNKNOWN || other == UNKNOWN) return true;
        if (this == NOT_A_GROUP || this == PUBLIC || this == FRIENDS || this == FOLLOWERS) return false;

        return true;
    }
}
