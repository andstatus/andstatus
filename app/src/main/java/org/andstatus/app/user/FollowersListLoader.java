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

package org.andstatus.app.user;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.FriendshipTable;

/**
 * @author yvolk@yurivolkov.com
 */
public class FollowersListLoader extends UserListLoader {
    private long userId;
    private final String userName;

    public FollowersListLoader(UserListType userListType, MyAccount ma, long centralItemId, boolean isListCombined) {
        super(userListType, ma, centralItemId, isListCombined);
        userId = centralItemId;
        userName = MyQuery.userIdToWebfingerId(userId);
    }

    protected String getSqlUserIds() {
        String sql = "SELECT ";
        switch (mUserListType) {
            case FOLLOWERS:
                sql += FriendshipTable.USER_ID
                        + " FROM " + FriendshipTable.TABLE_NAME
                        + " WHERE " + FriendshipTable.FRIEND_ID + "=" + userId;
                break;
            default:
                sql += FriendshipTable.FRIEND_ID
                        + " FROM " + FriendshipTable.TABLE_NAME
                        + " WHERE " + FriendshipTable.USER_ID + "=" + userId;
                break;
        }
        sql += " AND " + FriendshipTable.FOLLOWED + "=1";
        return " IN (" + sql + ")";
    }

    @Override
    protected String getTitle() {
        return userName;
    }
}
