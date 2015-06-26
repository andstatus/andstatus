package org.andstatus.app.user;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.LoadableListActivity.ProgressPublisher;
import org.andstatus.app.LoadableListActivity.SyncLoader;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

public class UserListLoader implements SyncLoader {
    private List<UserListViewItem> mItems = new ArrayList<>();
    LoadableListActivity.ProgressPublisher mProgress;
    
    @Override
    public void allowLoadingFromInternet() {
        // TODO Auto-generated method stub
    }

    @Override
    public void load(ProgressPublisher publisher) {
        mProgress = publisher;
        
        UserListViewItem oUser = new UserListViewItem(1L, "Test user");
        addUserToList(oUser);
    }

    private boolean addUserToList(UserListViewItem oUser) {
        boolean added = false;
        if (mItems.contains(oUser)) {
            MyLog.v(this, "User id=" + oUser.getUserId() + " is in the list already");
        } else {
            mItems.add(oUser);
            if (mProgress != null) {
                mProgress.publish(Integer.toString(size()));
            }
            added = true;
        }
        return added;
    }
    
    @Override
    public int size() {
        return mItems.size();
    }

    @Override
    public long getId(int location) {
        if (location < size()) {
            return mItems.get(location).mUserId;
        }
        return 0;
    }
}
