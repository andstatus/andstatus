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
    BANNER(7);

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
}
