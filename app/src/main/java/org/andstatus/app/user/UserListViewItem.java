package org.andstatus.app.user;

public class UserListViewItem {
    final long mUserId;
    final String mUserName;
    
    public UserListViewItem(long userId, String userName) {
        mUserId = userId;
        mUserName = userName;
    }

    public long getUserId() {
        return mUserId;
    }
}
