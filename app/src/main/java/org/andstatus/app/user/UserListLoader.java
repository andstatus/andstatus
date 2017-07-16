package org.andstatus.app.user;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.R;
import org.andstatus.app.SyncLoader;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.SqlWhere;
import org.andstatus.app.data.UserListSql;
import org.andstatus.app.database.UserTable;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;

public class UserListLoader extends SyncLoader<UserListViewItem> {
    protected final UserListType mUserListType;
    private String searchQuery = "";
    protected final MyAccount ma;
    protected final Origin origin;
    protected boolean mAllowLoadingFromInternet = false;
    protected final long mCentralItemId;

    private LoadableListActivity.ProgressPublisher mProgress;

    public UserListLoader(UserListType userListType, MyAccount ma, Origin origin, long centralItemId, String searchQuery) {
        mUserListType = userListType;
        this.searchQuery = searchQuery;
        this.ma = ma;
        this.origin = origin;
        mCentralItemId = centralItemId;
    }

    @Override
    public void allowLoadingFromInternet() {
        mAllowLoadingFromInternet = true;
    }

    @Override
    public final void load(ProgressPublisher publisher) {
        mProgress = publisher;

        loadInternal();
        MyLog.v(this, "Loaded " + size() + " items");

        if (items.isEmpty()) {
            addEmptyItem(MyContextHolder.get().context()
                    .getText(R.string.nothing_in_the_loadable_list).toString());
        }
    }

    protected void addEmptyItem(String description) {
        items.add(UserListViewItem.getEmpty(description));
    }

    protected UserListViewItem addUserIdToList(Origin origin, long userId) {
        UserListViewItem viewItem = UserListViewItem.fromUserId(origin, userId);
        addUserToList(viewItem);
        return viewItem;
    }

    protected void addUserToList(UserListViewItem oUser) {
        if (!oUser.isEmpty() && !items.contains(oUser)) {
            items.add(oUser);
            if (oUser.mbUser.userId == 0 && mAllowLoadingFromInternet) {
                loadFromInternet(oUser);
            }
            if (mProgress != null) {
                mProgress.publish(Integer.toString(size()));
            }
        }
    }

    private void loadFromInternet(UserListViewItem oUser) {
        MyLog.v(this, "User " + oUser + " will be loaded from the Internet");
        MyServiceManager.sendForegroundCommand(
                CommandData.newUserCommand(
                        CommandEnum.GET_USER,
                        null, MyContextHolder.get().persistentOrigins().fromId(oUser.mbUser.originId),
                        oUser.getUserId(),
                        oUser.mbUser.getUserName()));
    }

    protected void loadInternal() {
        // TODO: Why only MyAccount's ID ??
        Uri mContentUri = MatchedUri.getUserListUri(ma.getUserId(), mUserListType, ma.getOriginId(), mCentralItemId,
                searchQuery);
        Cursor c = null;
        try {
            c = MyContextHolder.get().context().getContentResolver()
                    .query(mContentUri, UserListSql.getListProjection(), getSelection(), null, null);
            while ( c != null && c.moveToNext()) {
                populateItem(c);
            }
        } finally {
            DbUtils.closeSilently(c);
        }
    }

    @NonNull
    protected String getSelection() {
        SqlWhere where = new SqlWhere();
        String sqlUserIds = getSqlUserIds();
        if (StringUtils.nonEmpty(sqlUserIds)) {
            where.append(UserTable.TABLE_NAME + "." + BaseColumns._ID + sqlUserIds);
        } else if (origin.isValid()) {
            where.append(UserTable.TABLE_NAME + "." + UserTable.ORIGIN_ID + "=" + ma.getOriginId());
        }
        return where.getCondition();

    }

    private void populateItem(Cursor cursor) {
        long userId = DbUtils.getLong(cursor, BaseColumns._ID);
        UserListViewItem item = getById(userId);
        if (item == null) {
            Origin origin = MyContextHolder.get().persistentOrigins().fromId(
                    DbUtils.getLong(cursor, UserTable.ORIGIN_ID));
            item = addUserIdToList(origin, userId);
        }
        MbUser user = item.mbUser;
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

        item.myFollowers = MyQuery.getMyFollowersOf(userId);
        item.avatarDrawable = AvatarFile.getDrawable(item.getUserId(), cursor);

        item.populated = true;
    }

    private UserListViewItem getById(long userId) {
        for (UserListViewItem item : items) {
            if (item.getUserId() == userId) {
                return item;
            }
        }
        return null;
    }

    protected String getSqlUserIds() {
        StringBuilder sb = new StringBuilder();
        int size = 0;
        for (UserListViewItem item : items) {
            if (!item.populated) {
                if (size > 0) {
                    sb.append(", ");
                }
                size++;
                sb.append(Long.toString(item.getUserId()));
            }
        }
        if (size == 1) {
            return "=" + sb.toString();
        } else if (size > 1) {
            return " IN (" + sb.toString() + ")";
        }
        return "";
    }

    protected String getTitle() {
        return MyLog.isVerboseEnabled() ? mUserListType.toString() : "";
    }

    @Override
    public String toString() {
        return mUserListType.toString()
                + "; central=" + mCentralItemId
                + "; " + super.toString();
    }

}
