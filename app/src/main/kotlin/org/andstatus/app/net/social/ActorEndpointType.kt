/*
 * Copyright (C) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.actor.GroupType

enum class ActorEndpointType(val id: Long) {
    EMPTY(0),
    API_PROFILE(1),
    API_INBOX(2),
    API_OUTBOX(3),
    API_FOLLOWING(4),
    API_FOLLOWERS(5),
    API_LISTS(10),
    API_LIST_BY_USERS(11),
    API_LIST_MEMBERS(12),
    API_LIKED(6),
    BANNER(7),
    API_SHARED_INBOX(8),
    API_UPLOAD_MEDIA(9);

    companion object {
        fun fromId(id: Long): ActorEndpointType {
            for (value in values()) {
                if (value.id == id) {
                    return value
                }
            }
            return EMPTY
        }

        fun ApiRoutineEnum.toActorEndpointType(): ActorEndpointType = when (this) {
            ApiRoutineEnum.GET_FOLLOWERS,
            ApiRoutineEnum.GET_FOLLOWERS_IDS -> API_FOLLOWERS
            ApiRoutineEnum.GET_FRIENDS,
            ApiRoutineEnum.GET_FRIENDS_IDS -> API_FOLLOWING
            ApiRoutineEnum.LISTS -> API_LISTS
            ApiRoutineEnum.LIST_BY_USERS -> API_LIST_BY_USERS
            ApiRoutineEnum.LIST_MEMBERS -> API_LIST_MEMBERS
            ApiRoutineEnum.GET_ACTOR -> API_PROFILE
            ApiRoutineEnum.HOME_TIMELINE -> API_INBOX
            ApiRoutineEnum.LIKED_TIMELINE -> API_LIKED
            ApiRoutineEnum.LIKE,
            ApiRoutineEnum.UNDO_LIKE,
            ApiRoutineEnum.FOLLOW,
            ApiRoutineEnum.UPDATE_PRIVATE_NOTE,
            ApiRoutineEnum.ANNOUNCE,
            ApiRoutineEnum.DELETE_NOTE,
            ApiRoutineEnum.UPDATE_NOTE,
            ApiRoutineEnum.ACTOR_TIMELINE -> API_OUTBOX
            ApiRoutineEnum.PUBLIC_TIMELINE -> API_SHARED_INBOX
            ApiRoutineEnum.UPLOAD_MEDIA -> API_UPLOAD_MEDIA
            else -> EMPTY
        }

        fun GroupType.toActorEndpointType(): ActorEndpointType = when (this) {
            GroupType.FOLLOWERS -> API_FOLLOWERS
            GroupType.FRIENDS -> API_FOLLOWING
            else -> EMPTY
        }
    }
}
