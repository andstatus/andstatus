package org.andstatus.app.user;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.LoadableListActivity.SyncLoader;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.AvatarFile;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.UserListSql;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

public class UserListLoader implements SyncLoader {
    protected final UserListType mUserListType;
    protected final MyAccount ma;
    protected final boolean mIsListCombined;
    protected boolean mAllowLoadingFromInternet = false;
    protected final long mCentralItemId;

    public List<UserListViewItem> getList() {
        return mItems;
    }

    private final List<UserListViewItem> mItems = new ArrayList<>();
    private LoadableListActivity.ProgressPublisher mProgress;

    public UserListLoader(UserListType userListType, MyAccount ma, long centralItemId, boolean isListCombined) {
        mUserListType = userListType;
        this.ma = ma;
        mIsListCombined = isListCombined;
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

        if (mItems.isEmpty()) {
            addEmptyItem(MyContextHolder.get().context()
                    .getText(R.string.nothing_in_the_loadable_list).toString());
        }
    }

    protected void addEmptyItem(String description) {
        getList().add(UserListViewItem.getEmpty(description));
    }

    protected UserListViewItem addUserIdToList(Origin origin, long userId) {
        UserListViewItem viewItem = UserListViewItem.fromUserId(origin, userId);
        addUserToList(viewItem);
        return viewItem;
    }

    protected void addUserToList(UserListViewItem oUser) {
        if (!oUser.isEmpty() && !mItems.contains(oUser)) {
            mItems.add(oUser);
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
        MyServiceManager.sendForegroundCommand(CommandData.getUser(ma.getAccountName(),
                oUser.getUserId(), oUser.mbUser.getUserName()));
    }

    protected void loadInternal() {
        Uri mContentUri = MatchedUri.getUserListUri(ma.getUserId(), mUserListType, mIsListCombined, mCentralItemId);
        Cursor c = null;
        try {
            c = MyContextHolder.get().context().getContentResolver()
                    .query(mContentUri, UserListSql.getListProjection(),
                            MyDatabase.User.TABLE_NAME + "." + BaseColumns._ID + getSqlUserIds(),
                            null, null);
            while ( c != null && c.moveToNext()) {
                populateItem(c);
            }
        } finally {
            DbUtils.closeSilently(c);
        }
    }

    private void populateItem(Cursor cursor) {
        long userId = DbUtils.getLong(cursor, BaseColumns._ID);
        UserListViewItem item = getById(userId);
        if (item == null) {
            Origin origin = MyContextHolder.get().persistentOrigins().fromId(
                    DbUtils.getLong(cursor, MyDatabase.User.ORIGIN_ID));
            item = addUserIdToList(origin, userId);
        }
        MbUser user = item.mbUser;
        user.oid = DbUtils.getString(cursor, MyDatabase.User.USER_OID);
        user.setUserName(DbUtils.getString(cursor, MyDatabase.User.USERNAME));
        user.setWebFingerId(DbUtils.getString(cursor, MyDatabase.User.WEBFINGER_ID));
        user.setRealName(DbUtils.getString(cursor, MyDatabase.User.REAL_NAME));
        user.setDescription(DbUtils.getString(cursor, MyDatabase.User.DESCRIPTION));
        user.location = DbUtils.getString(cursor, MyDatabase.User.LOCATION);

        user.setProfileUrl(DbUtils.getString(cursor, MyDatabase.User.PROFILE_URL));
        user.setHomepage(DbUtils.getString(cursor, MyDatabase.User.HOMEPAGE));

        user.msgCount = DbUtils.getLong(cursor, MyDatabase.User.MSG_COUNT);
        user.favoritesCount = DbUtils.getLong(cursor, MyDatabase.User.FAVORITES_COUNT);
        user.followingCount = DbUtils.getLong(cursor, MyDatabase.User.FOLLOWING_COUNT);
        user.followersCount = DbUtils.getLong(cursor, MyDatabase.User.FOLLOWERS_COUNT);

        user.setCreatedDate(DbUtils.getLong(cursor, MyDatabase.User.CREATED_DATE));
        user.setUpdatedDate(DbUtils.getLong(cursor, MyDatabase.User.UPDATED_DATE));

        item.myFollowers = MyQuery.getMyFollowersOf(userId);
        item.avatarDrawable = AvatarFile.getDrawable(item.getUserId(), cursor);

        item.populated = true;
    }

    private UserListViewItem getById(long userId) {
        for (UserListViewItem item : mItems) {
            if (item.getUserId() == userId) {
                return item;
            }
        }
        return null;
    }

    protected String getSqlUserIds() {
        StringBuilder sb = new StringBuilder();
        int size = 0;
        for (UserListViewItem item : mItems) {
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

    @Override
    public int size() {
        return mItems.size();
    }

    protected String getTitle() {
        return mUserListType.toString();
    }

    @Override
    public String toString() {
        return mUserListType.toString()
                + "; central=" + mCentralItemId
                + "; " + super.toString();
    }

}
