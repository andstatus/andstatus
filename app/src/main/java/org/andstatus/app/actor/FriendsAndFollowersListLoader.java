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
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.database.table.FriendshipTable;
import org.andstatus.app.origin.Origin;

/**
 * @author yvolk@yurivolkov.com
 */
public class FriendsAndFollowersListLoader extends ActorListLoader {
    private long actorId;

    public FriendsAndFollowersListLoader(MyContext myContext, ActorListType actorListType, Origin origin,
                                         long centralItemId, String searchQuery) {
        super(myContext, actorListType, origin, centralItemId, searchQuery);
        actorId = centralItemId;
    }

    protected String getSqlActorIds() {
        String sql = "SELECT ";
        switch (mActorListType) {
            case FOLLOWERS:
                sql += FriendshipTable.ACTOR_ID
                        + " FROM " + FriendshipTable.TABLE_NAME
                        + " WHERE " + FriendshipTable.FRIEND_ID + "=" + actorId;
                break;
            default:
                sql += FriendshipTable.FRIEND_ID
                        + " FROM " + FriendshipTable.TABLE_NAME
                        + " WHERE " + FriendshipTable.ACTOR_ID + "=" + actorId;
                break;
        }
        sql += " AND " + FriendshipTable.FOLLOWED + "=1";
        return " IN (" + sql + ")";
    }

    @Override
    protected String getSubtitle() {
        return ma.toAccountButtonText(MyContextHolder.get());
    }
}
