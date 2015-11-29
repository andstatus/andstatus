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
import android.widget.ListAdapter;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyHtml;

/**
 *  List of users for different contexts 
 *  e.g. "Users of the message", "Followers of my account(s)" etc.
 *  @author yvolk@yurivolkov.com
 */
public class UserList extends LoadableListActivity {
    private UserListType mUserListType = UserListType.UNKNOWN;
    private MyAccount ma = MyAccount.getEmpty(MyContextHolder.get(), "");
    private long mSelectedMessageId = 0;
    private boolean mIsListCombined = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLayoutId = R.layout.my_list_fragment;
        super.onCreate(savedInstanceState);
        ma = MyContextHolder.get().persistentAccounts()
                .fromUserId(getParsedUri().getAccountUserId());
        mUserListType = getParsedUri().getUserListType();
        mSelectedMessageId = getParsedUri().getMessageId();
        mIsListCombined = getParsedUri().isCombined();
    }

    @Override
    protected SyncLoader newSyncLoader() {
        return new UserListLoader(mUserListType, ma, mSelectedMessageId, mIsListCombined);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        UserListViewAdapter adapter = (UserListViewAdapter) getListAdapter();
        if (adapter != null) {
            adapter.onContextItemSelected(item);
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected ListAdapter newListAdapter() {
        return new UserListViewAdapter(this, R.layout.user, getListLoader().getList());
    }

    @SuppressWarnings("unchecked")
    protected UserListLoader getListLoader() {
        return (UserListLoader) getLoaded();
    }

    @Override
    protected CharSequence getCustomTitle() {
        mSubtitle = I18n.trimTextAt(MyHtml.fromHtml(getListLoader().messageBody), 80);
        return mUserListType.getTitle(this);
    }

}
