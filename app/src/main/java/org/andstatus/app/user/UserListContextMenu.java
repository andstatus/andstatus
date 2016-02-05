/*
 * Copyright (C) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Activity;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.net.social.MbUser;
import org.andstatus.app.util.MyLog;

public class UserListContextMenu implements View.OnCreateContextMenuListener {
    private final LoadableListActivity listActivity;
    private long mAccountUserIdToActAs;
    private View viewOfTheContext = null;
    private UserListViewItem mViewItem = UserListViewItem.getEmpty("");

    public UserListContextMenu(LoadableListActivity listActivity) {
        this.listActivity = listActivity;
        mAccountUserIdToActAs = listActivity.getMa().getUserId();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        viewOfTheContext = v;
        mViewItem = (UserListViewItem) listActivity.getListAdapter().getItem(v);

        int order = 0;
        try {
            menu.setHeaderTitle(mViewItem.mbUser.getUserName());
            if (mViewItem.mbUser.isIdentified()) {
                UserListContextMenuItem.USER_MESSAGES.addTo(menu, order++,
                        String.format(getActivity().getText(R.string.menu_item_user_messages).toString(),
                                mViewItem.mbUser.getNamePreferablyWebFingerId()));
                /** TODO: implement
                UserListContextMenuItem.FOLLOWERS.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.followers_of).toString(),
                                mViewItem.mbUser.getNamePreferablyWebFingerId()));
                */
                if (mViewItem.userIsFollowedBy(MyContextHolder.get().persistentAccounts().getCurrentAccount())) {
                    UserListContextMenuItem.STOP_FOLLOWING.addTo(menu, order++,
                            String.format(
                                    getActivity().getText(R.string.menu_item_stop_following_user).toString(),
                                    mViewItem.mbUser.getNamePreferablyWebFingerId()));
                } else {
                    UserListContextMenuItem.FOLLOW.addTo(menu, order++,
                            String.format(
                                    getActivity().getText(R.string.menu_item_follow_user).toString(),
                                    mViewItem.mbUser.getNamePreferablyWebFingerId()));
                }
            }
            UserListContextMenuItem.GET_USER.addTo(menu, order++, R.string.get_user);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }

    }

    public void showContextMenu() {
        if (viewOfTheContext != null) {
            viewOfTheContext.post(new Runnable() {

                @Override
                public void run() {
                    try {
                        viewOfTheContext.showContextMenu();
                    } catch (NullPointerException e) {
                        MyLog.d(this, "on showContextMenu; " + (viewOfTheContext != null ? "viewOfTheContext is not null" : ""), e);
                    }
                }
            });
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        MyAccount ma = MyContextHolder.get().persistentAccounts().fromUserId(mAccountUserIdToActAs);
        if (ma.isValid()) {
            UserListContextMenuItem contextMenuItem = UserListContextMenuItem.fromId(item.getItemId());
            MyLog.v(this, "onContextItemSelected: " + contextMenuItem + "; actor="
                    + ma.getAccountName() + "; user=" + getViewItem().mbUser.getNamePreferablyWebFingerId());
            return contextMenuItem.execute(this, ma);
        } else {
            return false;
        }
    }

    protected Activity getActivity() {
        return listActivity;
    }

    public void setAccountUserIdToActAs(long accountUserIdToActAs) {
        mAccountUserIdToActAs = accountUserIdToActAs;
    }

    public UserListViewItem getViewItem() {
        return mViewItem;
    }
}
