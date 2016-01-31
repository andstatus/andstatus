/* 
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.user;

import android.os.Bundle;
import android.view.MenuItem;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.WhichPage;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;
import org.andstatus.app.widget.MyBaseAdapter;

/**
 *  List of users for different contexts 
 *  e.g. "Users of the message", "Followers of my account(s)" etc.
 *  @author yvolk@yurivolkov.com
 */
public class UserList extends LoadableListActivity {
    private UserListType mUserListType = UserListType.UNKNOWN;
    private boolean mIsListCombined = false;
    private UserListContextMenu contextMenu = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list_fragment;
        super.onCreate(savedInstanceState);

        mUserListType = getParsedUri().getUserListType();
        mIsListCombined = getParsedUri().isCombined();
        contextMenu = new UserListContextMenu(this);

        showList(WhichPage.NEW);
    }

    @Override
    protected SyncLoader newSyncLoader(Bundle args) {
        switch (mUserListType) {
            case USERS_OF_MESSAGE:
                return new UsersOfMessageListLoader(mUserListType, getMa(), getParsedUri().getItemId(), mIsListCombined);
            case FOLLOWERS:
                return new FollowersListLoader(mUserListType, getMa(), getParsedUri().getItemId(), mIsListCombined);
            default:
                return new UserListLoader(mUserListType, getMa(), getParsedUri().getItemId(), mIsListCombined);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (contextMenu != null) {
            contextMenu.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected MyBaseAdapter newListAdapter() {
        return new UserListViewAdapter(contextMenu, R.layout.user, getListLoader().getList());
    }

    @SuppressWarnings("unchecked")
    protected UserListLoader getListLoader() {
        return (UserListLoader) getLoaded();
    }

    @Override
    protected CharSequence getCustomTitle() {
        mSubtitle = I18n.trimTextAt(MyHtml.fromHtml(getListLoader().getTitle()), 80);
        return mUserListType.getTitle(this);
    }

}
