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

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.FriendshipTable;

/**
 * @author yvolk@yurivolkov.com
 */
public class FollowersListLoader extends ActorListLoader {
    private long actorId;
    private final String actorName;

    public FollowersListLoader(ActorListType actorListType, MyAccount ma, long centralItemId, String searchQuery) {
        super(actorListType, ma, ma.getOrigin(), centralItemId, searchQuery);
        actorId = centralItemId;
        actorName = MyQuery.actorIdToWebfingerId(actorId);
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
    protected String getTitle() {
        return actorName;
    }
}
