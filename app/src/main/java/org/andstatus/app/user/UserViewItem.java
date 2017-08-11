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

import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.MyActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ViewItem;

import java.util.HashSet;
import java.util.Set;

public class UserViewItem implements Comparable<UserViewItem>,ViewItem {
    public static final UserViewItem EMPTY = fromMbUser(MbUser.EMPTY);
    boolean populated = false;
    @NonNull
    final MbUser mbUser;
    private AvatarFile avatarFile = null;
    Set<Long> myFollowers = new HashSet<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserViewItem that = (UserViewItem) o;
        return mbUser.equals(that.mbUser);
    }

    @Override
    public int hashCode() {
        return mbUser.hashCode();
    }

    private UserViewItem(MbUser mbUser) {
        this.mbUser = mbUser;
    }

    public static UserViewItem getEmpty(String description) {
        MbUser mbUser = TextUtils.isEmpty(description) ? MbUser.EMPTY :
                MbUser.fromOriginAndUserId(0L, 0L).setDescription(description);
        return fromMbUser(mbUser);
    }

    public static UserViewItem fromUserId(Origin origin, long userId) {
        MbUser mbUser = MbUser.EMPTY;
        if (userId != 0) {
            mbUser = MbUser.fromOriginAndUserOid(origin.getId(), MyQuery.idToOid(OidEnum.USER_OID, userId, 0));
            mbUser.userId = userId;
            mbUser.setWebFingerId(MyQuery.userIdToWebfingerId(userId));
        }
        return fromMbUser(mbUser);
    }

    public static UserViewItem fromMbUser(@NonNull MbUser mbUser) {
        return new UserViewItem(mbUser);
    }

    public long getUserId() {
        return mbUser.userId;
    }

    @Override
    public String toString() {
        return "UserListViewItem{" +
                mbUser +
                '}';
    }

    public boolean isEmpty() {
        return mbUser.isEmpty();
    }

    public boolean userIsFollowedBy(MyAccount ma) {
        return myFollowers.contains(ma.getUserId());
    }

    @Override
    public long getId() {
        return getUserId();
    }

    @Override
    public long getDate() {
        return mbUser.getUpdatedDate();
    }

    public String getWebFingerIdOrUserName() {
        return TextUtils.isEmpty(mbUser.getWebFingerId()) ? mbUser.getUserName() : mbUser.getWebFingerId();
    }

    @Override
    public int compareTo(@NonNull UserViewItem o) {
        return getWebFingerIdOrUserName().compareTo(o.getWebFingerIdOrUserName());
    }

    public void setAvatarFile(AvatarFile avatarFile) {
        this.avatarFile = avatarFile;
    }

    public void showAvatar(MyActivity myActivity, AvatarView imageView) {
        if (avatarFile != null) {
            avatarFile.showImage(myActivity, imageView);
        }
    }
}
