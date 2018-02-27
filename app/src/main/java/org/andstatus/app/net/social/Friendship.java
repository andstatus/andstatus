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

import android.support.annotation.NonNull;

/**
 * @author yvolk@yurivolkov.com
 */
public class Friendship implements Comparable<Friendship> {
    public final Actor actor;
    public final Actor friend;

    public Friendship(Actor actor, Actor friend) {
        this.actor = actor;
        this.friend = friend;
    }

    @Override
    public int compareTo(@NonNull Friendship o) {
        switch (actor.compareTo(o.actor)) {
            case 0:
                return friend.compareTo(o.friend);
            case 1:
                return 1;
            default:
                return -1;
        }
    }
}
