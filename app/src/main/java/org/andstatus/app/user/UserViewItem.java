/*
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

import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.MyActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.OidEnum;
import org.andstatus.app.data.UserListSql;
import org.andstatus.app.database.table.DownloadTable;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.I18n;

import java.util.HashSet;
import java.util.Set;

public class UserViewItem extends ViewItem implements Comparable<UserViewItem> {
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

    private UserViewItem(@NonNull MbUser mbUser) {
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

    public String getDescription() {
        StringBuilder builder = new StringBuilder(mbUser.getDescription());
        if (MyPreferences.isShowDebuggingInfoInUi()) {
            I18n.appendWithSpace(builder, "(id=" + getUserId() + ")");
        }
        return builder.toString();
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
        return mbUser.getNamePreferablyWebFingerId();
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

    public void populateFromDatabase() {
        Uri mContentUri = MatchedUri.getUserListItemUri(0, UserListType.USERS, mbUser.originId , getId());
        try (Cursor c = MyContextHolder.get().context().getContentResolver()
                .query(mContentUri, UserListSql.getListProjection(), null, null, null)) {
            while ( c != null && c.moveToNext()) {
                populateFromCursor(c);
            }
        }
    }

    void populateFromCursor(Cursor cursor) {
        MbUser user = mbUser;
        user.oid = DbUtils.getString(cursor, UserTable.USER_OID);
        user.setUserName(DbUtils.getString(cursor, UserTable.USERNAME));
        user.setWebFingerId(DbUtils.getString(cursor, UserTable.WEBFINGER_ID));
        user.setRealName(DbUtils.getString(cursor, UserTable.REAL_NAME));
        user.setDescription(DbUtils.getString(cursor, UserTable.DESCRIPTION));
        user.location = DbUtils.getString(cursor, UserTable.LOCATION);

        user.setProfileUrl(DbUtils.getString(cursor, UserTable.PROFILE_URL));
        user.setHomepage(DbUtils.getString(cursor, UserTable.HOMEPAGE));

        user.msgCount = DbUtils.getLong(cursor, UserTable.MSG_COUNT);
        user.favoritesCount = DbUtils.getLong(cursor, UserTable.FAVORITES_COUNT);
        user.followingCount = DbUtils.getLong(cursor, UserTable.FOLLOWING_COUNT);
        user.followersCount = DbUtils.getLong(cursor, UserTable.FOLLOWERS_COUNT);

        user.setCreatedDate(DbUtils.getLong(cursor, UserTable.CREATED_DATE));
        user.setUpdatedDate(DbUtils.getLong(cursor, UserTable.UPDATED_DATE));

        myFollowers = MyQuery.getMyFollowersOf(getUserId());
        AvatarFile avatarFile = AvatarFile.fromCursor(getUserId(), cursor, DownloadTable.AVATAR_FILE_NAME);
        setAvatarFile(avatarFile);

        populated = true;
    }

    public void populateActorFromCursor(Cursor cursor) {
        MbUser user = mbUser;
        user.setRealName(DbUtils.getString(cursor, UserTable.SENDER_NAME));
        AvatarFile avatarFile = AvatarFile.fromCursor(getUserId(), cursor, DownloadTable.ACTOR_AVATAR_FILE_NAME);
        setAvatarFile(avatarFile);

        populated = true;
    }

    public void hideActor(long userId) {
        myFollowers.remove(userId);
    }
}
