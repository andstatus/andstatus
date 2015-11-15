package org.andstatus.app.user;

import android.text.TextUtils;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.LoadableListActivity.SyncLoader;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.data.MyDatabase;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

public class UserListLoader implements SyncLoader {
    private final UserListType mUserListType;
    private final MyAccount ma;
    private final long mSelectedMessageId;
    private final Origin mOriginOfSelectedMessage;
    final String messageBody;

    public List<UserListViewItem> getList() {
        return mItems;
    }

    private final List<UserListViewItem> mItems = new ArrayList<>();
    LoadableListActivity.ProgressPublisher mProgress;

    public UserListLoader(UserListType userListType, MyAccount ma, long selectedMessageId) {
        mUserListType = userListType;
        this.ma = ma;
        mSelectedMessageId = selectedMessageId;
        messageBody = MyQuery.msgIdToStringColumnValue(MyDatabase.Msg.BODY, mSelectedMessageId);
        mOriginOfSelectedMessage = MyContextHolder.get().persistentOrigins().fromId(
                MyQuery.msgIdToOriginId(mSelectedMessageId));
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
        // TODO:
    }

    private void addUserToList(UserListViewItem oUser) {
        if (!mItems.contains(oUser)) {
            mItems.add(oUser);
            if (mProgress != null) {
                mProgress.publish(Integer.toString(size()));
            }
        }
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
