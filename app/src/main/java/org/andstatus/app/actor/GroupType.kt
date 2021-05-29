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
package org.andstatus.app.actor

/** See https://www.w3.org/TR/activitystreams-vocabulary/#dfn-group
 * @author yvolk@yurivolkov.com
 */
enum class GroupType(val code: String, val id: Long, isGroupLike: IsGroupLike, isCollection: IsCollection,
                     isActorOwned: IsActorOwned, precision: Long) {
    NOT_A_GROUP("NotAGroup", 1, IsGroupLike.NO, IsCollection.NO, IsActorOwned.NO, 1),
    PUBLIC("Public", 2, IsGroupLike.YES, IsCollection.NO, IsActorOwned.NO, 3),
    GENERIC("Generic", 6, IsGroupLike.YES, IsCollection.NO, IsActorOwned.NO, 1),
    COLLECTION("Collection", 7, IsGroupLike.YES, IsCollection.YES, IsActorOwned.NO, 1),
    FRIENDS("Friends", 3, IsGroupLike.YES, IsCollection.YES, IsActorOwned.YES, 3),
    FOLLOWERS("Followers", 4, IsGroupLike.YES, IsCollection.YES, IsActorOwned.YES, 3),
    ACTOR_OWNED("ActorOwned", 5, IsGroupLike.YES, IsCollection.NO, IsActorOwned.YES, 2),
    UNKNOWN("Unknown", 0, IsGroupLike.NO, IsCollection.NO, IsActorOwned.NO, 0);

    /** true if this is a "Group" object or simply a "Collection" of Actors  */
    val isGroupLike: Boolean

    /** The Group id points to a Collection object  */
    val isCollection: Boolean

    /** A groupLike that has a parent actor  */
    val parentActorRequired: Boolean

    /** Used to figure out, which value is more specific  */
    val precision: Long

    private enum class IsGroupLike {
        YES, NO
    }

    private enum class IsCollection {
        YES, NO
    }

    private enum class IsActorOwned {
        YES, NO
    }

    fun isSameActor(other: GroupType?): Boolean {
        if (this == other) return true
        if (this == UNKNOWN || other == UNKNOWN) return true
        return if (this == NOT_A_GROUP || this == PUBLIC || this == FRIENDS || this == FOLLOWERS) false else true
    }

    companion object {
        fun fromId(id: Long): GroupType {
            for (values in values()) {
                if (values.id == id) {
                    return values
                }
            }
            return UNKNOWN
        }
    }

    init {
        this.isGroupLike = isGroupLike == IsGroupLike.YES
        this.isCollection = isCollection == IsCollection.YES
        parentActorRequired = isActorOwned == IsActorOwned.YES
        this.precision = precision
    }
}
