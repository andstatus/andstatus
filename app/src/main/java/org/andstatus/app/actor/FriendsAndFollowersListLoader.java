/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.GroupMembership;
import org.andstatus.app.origin.Origin;

import java.util.Collections;

/**
 * @author yvolk@yurivolkov.com
 */
public class FriendsAndFollowersListLoader extends ActorListLoader {

    public FriendsAndFollowersListLoader(MyContext myContext, ActorListType actorListType, Origin origin,
                                         long centralItemId, String searchQuery) {
        super(myContext, actorListType, origin, centralItemId, searchQuery);
    }

    protected String getSqlActorIds() {
        GroupType groupType = mActorListType == ActorListType.FOLLOWERS ? GroupType.FOLLOWERS : GroupType.FRIENDS;
        return " IN (" + GroupMembership.selectMemberIds(Collections.singletonList(centralActorId), groupType, false) + ")";
    }

    @Override
    protected String getSubtitle() {
        return ma.toAccountButtonText();
    }
}
