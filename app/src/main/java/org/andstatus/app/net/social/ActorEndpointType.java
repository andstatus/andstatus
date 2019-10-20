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

package org.andstatus.app.net.social;

public enum ActorEndpointType {
    EMPTY(0),
    API_PROFILE(1),
    API_INBOX(2),
    API_OUTBOX(3),
    API_FOLLOWING(4),
    API_FOLLOWERS(5),
    API_LIKED(6),
    BANNER(7),
    API_SHARED_INBOX(8),
    API_UPLOAD_MEDIA(9);

    public final long id;

    ActorEndpointType(long id) {
        this.id = id;
    }

    public static ActorEndpointType fromId(long id) {
        for(ActorEndpointType val : values()) {
            if (val.id == id) {
                return val;
            }
        }
        return EMPTY;
    }

    public static ActorEndpointType from(Connection.ApiRoutineEnum routine) {
        switch (routine) {
            case GET_FOLLOWERS:
            case GET_FOLLOWERS_IDS:
                return API_FOLLOWERS;
            case GET_FRIENDS:
            case GET_FRIENDS_IDS:
                return API_FOLLOWING;
            case GET_ACTOR:
                return API_PROFILE;
            case HOME_TIMELINE:
                return API_INBOX;
            case LIKED_TIMELINE:
                return API_LIKED;
            case LIKE:
            case UNDO_LIKE:
            case FOLLOW:
            case UPDATE_PRIVATE_NOTE:
            case ANNOUNCE:
            case DELETE_NOTE:
            case UPDATE_NOTE:
            case ACTOR_TIMELINE:
                return API_OUTBOX;
            case PUBLIC_TIMELINE:
                return API_SHARED_INBOX;
            case UPDATE_NOTE_WITH_MEDIA:
                return API_UPLOAD_MEDIA;
            default:
                return EMPTY;
        }
    }

}
