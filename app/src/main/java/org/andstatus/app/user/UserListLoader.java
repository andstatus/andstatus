package org.andstatus.app.user;

import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.LoadableListActivity.SyncLoader;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.AvatarDrawable;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.data.UserListSql;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.UriUtils;

import java.util.ArrayList;
import java.util.List;

public class UserListLoader implements SyncLoader {
    private final UserListType mUserListType;
    private final MyAccount ma;
    private final long mSelectedMessageId;
    private final Origin mOriginOfSelectedMessage;
    private final boolean mIsListCombined;
    final String messageBody;

    public List<UserListViewItem> getList() {
        return mItems;
    }

    private final List<UserListViewItem> mItems = new ArrayList<>();
    private LoadableListActivity.ProgressPublisher mProgress;

    public UserListLoader(UserListType userListType, MyAccount ma, long selectedMessageId, boolean isListCombined) {
        mUserListType = userListType;
        this.ma = ma;
        mSelectedMessageId = selectedMessageId;
        messageBody = MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, mSelectedMessageId);
        mOriginOfSelectedMessage = MyContextHolder.get().persistentOrigins().fromId(
                MyQuery.msgIdToOriginId(mSelectedMessageId));
        mIsListCombined = isListCombined;
    }

    @Override
    public void allowLoadingFromInternet() {
        // TODO:
    }

    @Override
    public void load(ProgressPublisher publisher) {
        mProgress = publisher;

        switch (mUserListType) {
            case USERS_OF_MESSAGE:
                addFromMessageRow();
                break;
            default:
                addUserToList(new UserListViewItem(0L, mOriginOfSelectedMessage.getId(),
                        "Unknown list type: " + mUserListType));
                break;
        }
        MyLog.v(this, "Loaded " + size() + " items");
        if (mItems.isEmpty()) {
            addUserToList(new UserListViewItem(0L, mOriginOfSelectedMessage.getId(), "..."));
        }
        populateFields();
    }

    private void addFromMessageRow() {
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.AUTHOR_ID, mSelectedMessageId));
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.SENDER_ID, mSelectedMessageId));
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.IN_REPLY_TO_USER_ID, mSelectedMessageId));
        addUserIdToList(mOriginOfSelectedMessage,
                MyQuery.msgIdToLongColumnValue(MyDatabase.Msg.RECIPIENT_ID, mSelectedMessageId));
        addUsersFromMessageBody();
        addRebloggers();
    }

    private void addUserIdToList(Origin origin, long userId) {
        if (userId == 0 || containsUserId(userId)) {
            return;
        }
        String userName = MyQuery.userIdToName(userId, UserInTimeline.USERNAME);
        if (TextUtils.isEmpty(userName)) {
            userName = "?? id=" + userId;
        }
        addUserToList(new UserListViewItem(userId, origin.getId(), userName));
    }

    private boolean containsUserId(long userId) {
        for (UserListViewItem item : mItems) {
            if (item.getUserId() == userId) {
                return true;
            }
        }
        return false;
    }

    private void addUsersFromMessageBody() {
        List<MbUser> users = MbUser.fromBodyText(mOriginOfSelectedMessage,
                MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, mSelectedMessageId), false);
        for (MbUser mbUser: users) {
            addUserToList(UserListViewItem.fromMbUser(mbUser));
        }
    }

    private void addRebloggers() {
        for (long rebloggerId : MyQuery.getRebloggers(mSelectedMessageId)) {
            addUserIdToList(mOriginOfSelectedMessage, rebloggerId);
        }
    }

    private void addUserToList(UserListViewItem oUser) {
        if (!mItems.contains(oUser)) {
            mItems.add(oUser);
            if (mProgress != null) {
                mProgress.publish(Integer.toString(size()));
            }
        }
    }

    private void populateFields() {
        Uri mContentUri = MatchedUri.getUserListUri(ma.getUserId(), mUserListType, mIsListCombined, mSelectedMessageId);
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
        long userId = c.getLong(c.getColumnIndex(BaseColumns._ID));
        UserListViewItem item = getById(userId);
        if (item == null) {
            return;
        }
        item.populated = true;
        item.mUserName = c.getString(c.getColumnIndex(MyDatabase.User.USERNAME));
        item.mRealName = c.getString(c.getColumnIndex(MyDatabase.User.REAL_NAME));
        item.mHomepage = c.getString(c.getColumnIndex(MyDatabase.User.HOMEPAGE));
        item.mDescription = c.getString(c.getColumnIndex(MyDatabase.User.DESCRIPTION));
        item.mUri = UriUtils.fromString(c.getString(c.getColumnIndex(MyDatabase.User.URL)));
        if (MyPreferences.showAvatars()) {
            item.mAvatarDrawable = new AvatarDrawable(item.getUserId(),
                    c.getString(c.getColumnIndex(MyDatabase.Download.AVATAR_FILE_NAME)));
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

    private String getSqlUserIds() {
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

    @Override
    public long getId(int location) {
        if (location < size()) {
            return mItems.get(location).getUserId();
        }
        return 0;
    }
}
