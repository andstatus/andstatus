package org.andstatus.app.user;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.LoadableListActivity.SyncLoader;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.UserListSql;
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
            addEmptyItem("...");
        }
        populateItems();
    }

    protected void loadInternal() {
        addEmptyItem("Not implemented " + toString());
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

    protected void populateItems() {
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

    private void populateItem(Cursor c) {
        long userId = DbUtils.getLong(c, BaseColumns._ID);
        UserListViewItem item = getById(userId);
        if (item == null) {
            Origin origin = MyContextHolder.get().persistentOrigins().fromId(
                    DbUtils.getLong(c, MyDatabase.User.ORIGIN_ID));
            item = addUserIdToList(origin, userId);
        }
        item.populated = true;
        item.mbUser.setUserName(DbUtils.getString(c, MyDatabase.User.USERNAME));
        item.mbUser.setProfileUrl(DbUtils.getString(c, MyDatabase.User.URL));
        item.mbUser.setWebFingerId(DbUtils.getString(c, MyDatabase.User.WEBFINGER_ID));
        item.mbUser.realName = DbUtils.getString(c, MyDatabase.User.REAL_NAME);
        item.mbUser.setHomepage(DbUtils.getString(c, MyDatabase.User.HOMEPAGE));
        item.mbUser.setDescription(DbUtils.getString(c, MyDatabase.User.DESCRIPTION));
        item.myFollowers = MyQuery.getMyFollowersOf(userId);
        if (MyPreferences.showAvatars()) {
            item.mAvatarDrawable = new AvatarDrawable(item.getUserId(),
                    DbUtils.getString(c, MyDatabase.Download.AVATAR_FILE_NAME));
        }
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
