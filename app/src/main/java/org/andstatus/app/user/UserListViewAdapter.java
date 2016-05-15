/**
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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.widget.MyBaseAdapter;

import java.util.List;

class UserListViewAdapter extends MyBaseAdapter {
    private final UserListContextMenu contextMenu;
    private final int listItemLayoutId;
    private final List<UserListViewItem> items;
    private final boolean showAvatars = MyPreferences.getShowAvatars();
    private final boolean showWebFingerId =
            MyPreferences.getUserInTimeline().equals(UserInTimeline.WEBFINGER_ID);

    public UserListViewAdapter(UserListContextMenu contextMenu, int listItemLayoutId, List<UserListViewItem> items) {
        this.contextMenu = contextMenu;
        this.listItemLayoutId = listItemLayoutId;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        if (position >= 0 && position < getCount()) {
            return items.get(position);
        }
        return UserListViewItem.getEmpty("");
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getUserId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        UserListViewItem item = items.get(position);
        MyUrlSpan.showText(view, R.id.username, item.mbUser.toUserTitle(showWebFingerId), false);
        if (showAvatars) {
            showAvatar(item, view);
        }
        MyUrlSpan.showText(view, R.id.homepage, item.mbUser.getHomepage(), true);
        MyUrlSpan.showText(view, R.id.description, item.mbUser.getDescription(), false);
        MyUrlSpan.showText(view, R.id.location, item.mbUser.location, false);
        MyUrlSpan.showText(view, R.id.profile_url, item.mbUser.getProfileUrl(), true);

        showCounter(view, R.id.msg_count, item.mbUser.msgCount);
        showCounter(view, R.id.favorites_count, item.mbUser.favoritesCount);
        showCounter(view, R.id.following_count, item.mbUser.followingCount);
        showCounter(view, R.id.followers_count, item.mbUser.followersCount);

        MyUrlSpan.showText(view, R.id.location, item.mbUser.location, false);
        showMyFollowers(view, item);
        return view;
    }

    public static void showCounter(View parentView, int viewId, long counter) {
        MyUrlSpan.showText(parentView, viewId, counter <= 0 ? "-" : String.valueOf(counter) , false);
    }

    private View newView() {
        return LayoutInflater.from(contextMenu.getActivity()).inflate(listItemLayoutId, null);
    }

    private void showAvatar(UserListViewItem item, View view) {
        ImageView avatar = (ImageView) view.findViewById(R.id.avatar_image);
        avatar.setImageDrawable(item.getAvatar());
    }

    private void showMyFollowers(View view, UserListViewItem item) {
        StringBuilder builder = new StringBuilder();
        if (!item.myFollowers.isEmpty()) {
            int count = 0;
            builder.append(contextMenu.getActivity().getText(R.string.followed_by));
            for (long userId : item.myFollowers) {
                if (count == 0) {
                    builder.append(" ");
                } else {
                    builder.append(", ");
                }
                builder.append(MyContextHolder.get().persistentAccounts().fromUserId(userId).getAccountName());
                count++;
            }
        }
        MyUrlSpan.showText(view, R.id.followed_by, builder.toString(), false);
    }
}
