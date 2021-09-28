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
enum class GroupType(
    val code: String,
    val id: Long,
    isGroupLike: IsGroupLike,
    isCollection: IsCollection,
    hasParentActor: HasParentActor,
    isSingleForParent: IsSingleForParent,
    /** Used to figure out, which value is more specific  */
    val precision: Long
) {
    NOT_A_GROUP("NotAGroup", 1, IsGroupLike.NO, IsCollection.NO, HasParentActor.NO, IsSingleForParent.NO, 1),
    PUBLIC("Public", 2, IsGroupLike.YES, IsCollection.NO, HasParentActor.NO, IsSingleForParent.NO, 3),
    GENERIC("Generic", 6, IsGroupLike.YES, IsCollection.NO, HasParentActor.NO, IsSingleForParent.NO, 1),
    COLLECTION("Collection", 7, IsGroupLike.YES, IsCollection.YES, HasParentActor.NO, IsSingleForParent.NO, 1),
    FRIENDS("Friends", 3, IsGroupLike.YES, IsCollection.YES, HasParentActor.YES, IsSingleForParent.YES, 3),
    FOLLOWERS("Followers", 4, IsGroupLike.YES, IsCollection.YES, HasParentActor.YES, IsSingleForParent.YES, 3),
    ACTOR_OWNED("ActorOwned", 5, IsGroupLike.YES, IsCollection.NO, HasParentActor.YES, IsSingleForParent.NO, 2),
    LISTS("Lists", 8, IsGroupLike.YES, IsCollection.YES, HasParentActor.YES, IsSingleForParent.YES, 3),
    LIST_MEMBERS("ListMembers", 9, IsGroupLike.YES, IsCollection.YES, HasParentActor.YES, IsSingleForParent.NO, 3),
    UNKNOWN("Unknown", 0, IsGroupLike.NO, IsCollection.NO, HasParentActor.NO, IsSingleForParent.NO, 0);

    /** true if this is a "Group" object or simply a "Collection" of Actors  */
    val isGroupLike: Boolean = isGroupLike == IsGroupLike.YES

    /** The Group id points to a Collection object  */
    val isCollection: Boolean = isCollection == IsCollection.YES

    /** A groupLike that has a parent actor  */
    val hasParentActor: Boolean = hasParentActor == HasParentActor.YES

    /** A parent Actor has exactly one group of this type (e.g. Friends, Followers or Lists) */
    val isSingleForParent: Boolean = isSingleForParent == IsSingleForParent.YES

    private enum class IsGroupLike {
        YES, NO
    }

    private enum class IsCollection {
        YES, NO
    }

    private enum class HasParentActor {
        YES, NO
    }

    private enum class IsSingleForParent {
        YES, NO
    }

    /** We can tell that these are different Actors looking at their [GroupType]s only */
    fun isDifferentActor(other: GroupType): Boolean {
        if (this == other) return false
        if (this == UNKNOWN || other == UNKNOWN) return false
        return this != COLLECTION && this != GENERIC && this != ACTOR_OWNED &&
            other != COLLECTION && other != GENERIC && other != ACTOR_OWNED
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
}
