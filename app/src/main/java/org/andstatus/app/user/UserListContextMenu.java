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

import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;

import org.andstatus.app.ContextMenuHeader;
import org.andstatus.app.LoadableListActivity;
import org.andstatus.app.MyContextMenu;
import org.andstatus.app.R;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.util.MyLog;

public class UserListContextMenu extends MyContextMenu {

    public UserListContextMenu(LoadableListActivity listActivity) {
        super(listActivity);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        final String method = "onCreateContextMenu";
        super.onCreateContextMenu(menu, v, menuInfo);
        if (getViewItem() == null) {
            return;
        }

        int order = 0;
        try {
            new ContextMenuHeader(getActivity(), menu)
                    .setTitle(getViewItem().mbUser.toUserTitle(false))
                    .setSubtitle(getViewItem().mbUser.getWebFingerId());
            String shortName = getViewItem().mbUser.getUserName();
            if (getViewItem().mbUser.isIdentified()) {
                UserListContextMenuItem.USER_MESSAGES.addTo(menu, order++,
                        String.format(getActivity().getText(R.string.menu_item_user_messages).toString(), shortName));
                UserListContextMenuItem.FRIENDS.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.friends_of).toString(), shortName));
                UserListContextMenuItem.FOLLOWERS.addTo(menu, order++,
                        String.format(
                                getActivity().getText(R.string.followers_of).toString(), shortName));
                if (getViewItem().userIsFollowedBy(
                        MyContextHolder.get().persistentAccounts().getCurrentAccount())) {
                    UserListContextMenuItem.STOP_FOLLOWING.addTo(menu, order++,
                            String.format(
                                    getActivity().getText(R.string.menu_item_stop_following_user).toString(), shortName));
                } else {
                    UserListContextMenuItem.FOLLOW.addTo(menu, order++,
                            String.format(
                                    getActivity().getText(R.string.menu_item_follow_user).toString(), shortName));
                }
            }
            UserListContextMenuItem.GET_USER.addTo(menu, order++, R.string.get_user);
        } catch (Exception e) {
            MyLog.e(this, method, e);
        }

    }

    public boolean onContextItemSelected(MenuItem item) {
        MyAccount ma = getPotentialActorOrCurrentAccount();
        if (ma.isValid()) {
            UserListContextMenuItem contextMenuItem = UserListContextMenuItem.fromId(item.getItemId());
            MyLog.v(this, "onContextItemSelected: " + contextMenuItem + "; actor="
                    + ma.getAccountName() + "; user=" + getViewItem().mbUser.getNamePreferablyWebFingerId());
            return contextMenuItem.execute(this, ma);
        } else {
            return false;
        }
    }

    public UserListViewItem getViewItem() {
        if (oViewItem == null) {
            return UserListViewItem.getEmpty("");
        }
        return (UserListViewItem) oViewItem;
    }

    public Origin getOrigin() {
        return getActivity().getMyContext().persistentOrigins().fromId(
                getViewItem().mbUser.originId);
    }
}
