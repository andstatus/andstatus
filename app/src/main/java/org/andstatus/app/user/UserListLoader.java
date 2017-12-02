package org.andstatus.app.user;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.SqlUserIds;
import org.andstatus.app.data.SqlWhere;
import org.andstatus.app.data.UserListSql;
import org.andstatus.app.database.table.UserTable;
import org.andstatus.app.list.SyncLoader;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.service.CommandData;
import org.andstatus.app.service.CommandEnum;
import org.andstatus.app.service.MyServiceManager;
import org.andstatus.app.timeline.LoadableListActivity;
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.timeline.ViewItem;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StopWatch;
import org.andstatus.app.util.StringUtils;

import static java.util.stream.Collectors.toList;

public class UserListLoader extends SyncLoader<UserViewItem> {
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
        final String method = "load";
        final StopWatch stopWatch = StopWatch.createStarted();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " started");
        }
        mProgress = publisher;
        loadInternal();
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "Loaded " + size() + " items, " + stopWatch.getTime() + "ms");
        }
        if (items.isEmpty()) {
            addEmptyItem(MyContextHolder.get().context()
                    .getText(R.string.nothing_in_the_loadable_list).toString());
        }
    }

    protected void addEmptyItem(String description) {
        items.add(UserViewItem.newEmpty(description));
    }

    public UserViewItem addUserIdToList(Origin origin, long userId) {
        return addUserToList(UserViewItem.fromUserId(origin, userId));
    }

    protected UserViewItem addUserToList(UserViewItem item) {
        if (item.isEmpty()) return item;
        int existing = items.indexOf(item);
        if (existing >= 0) return items.get(existing);
        items.add(item);
        if (item.mbUser.userId == 0 && mAllowLoadingFromInternet) {
            loadFromInternet(item);
        }
        if (mProgress != null) {
            mProgress.publish(Integer.toString(size()));
        }
        return item;
    }

    private void loadFromInternet(UserViewItem oUser) {
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
        try (Cursor c = MyContextHolder.get().context().getContentResolver()
                    .query(mContentUri, UserListSql.getListProjection(), getSelection(), null, null)) {
            while ( c != null && c.moveToNext()) {
                populateItem(c);
            }
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
        UserViewItem item = getById(DbUtils.getLong(cursor, BaseColumns._ID));
        if (item == null) {
            long userId = DbUtils.getLong(cursor, BaseColumns._ID);
            Origin origin = MyContextHolder.get().persistentOrigins().fromId(
                    DbUtils.getLong(cursor, UserTable.ORIGIN_ID));
            item = addUserIdToList(origin, userId);
        }
        item.populateFromCursor(cursor);
    }

    private UserViewItem getById(long userId) {
        for (UserViewItem item : items) {
            if (item.getUserId() == userId) {
                return item;
            }
        }
        return null;
    }

    protected String getSqlUserIds() {
        return SqlUserIds.fromIds(items.stream().map(ViewItem::getId).collect(toList())).getSql();
    }

    protected String getTitle() {
        return MyPreferences.isShowDebuggingInfoInUi() ? mUserListType.toString() : "";
    }

    @Override
    public String toString() {
        return mUserListType.toString()
                + "; central=" + mCentralItemId
                + "; " + super.toString();
    }

}
