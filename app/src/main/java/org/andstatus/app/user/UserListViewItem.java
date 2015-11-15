/**
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.user;

import org.andstatus.app.net.social.MbUser;

public class UserListViewItem {
    private final long mUserId;
    private final long mOriginId;
    final String mUserName;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserListViewItem that = (UserListViewItem) o;

        if (mUserId != that.mUserId) return false;
        if (mOriginId != that.mOriginId) return false;
        return mUserName.equals(that.mUserName);

    }

    @Override
    public int hashCode() {
        int result = (int) (mUserId ^ (mUserId >>> 32));
        result = 31 * result + (int) (mOriginId ^ (mOriginId >>> 32));
        result = 31 * result + mUserName.hashCode();
        return result;
    }

    public static UserListViewItem fromMbUser(MbUser mbUser) {
        return new UserListViewItem(mbUser.userId, mbUser.originId,
                mbUser.getUserName());
    }

    public UserListViewItem(long userId, long originId, String userName) {
        mUserId = userId;
        mOriginId = originId;
        mUserName = userName;
    }

    public long getUserId() {
        return mUserId;
    }

    @Override
    public String toString() {
        return "UserListViewItem{" +
                "userId=" + mUserId +
                ", userName='" + mUserName + '\'' +
                '}';
    }
}
