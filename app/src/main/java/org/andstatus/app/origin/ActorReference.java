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

package org.andstatus.app.origin;

import org.andstatus.app.actor.GroupType;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActorReference {
    public final static ActorReference EMPTY = new ActorReference(-1, GroupType.UNKNOWN);
    public final int index;
    public final GroupType groupType;

    ActorReference(int index, GroupType groupType) {
        this.index = index;
        this.groupType = groupType;
    }
}
