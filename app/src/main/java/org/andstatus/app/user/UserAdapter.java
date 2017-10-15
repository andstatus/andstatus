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

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.context.UserInTimeline;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyUrlSpan;

import java.util.List;

public class UserAdapter extends BaseTimelineAdapter<UserViewItem> {
    private final UserListContextMenu contextMenu;
    private final int listItemLayoutId;
    private final boolean showAvatars = MyPreferences.getShowAvatars();
    private final boolean showWebFingerId =
            MyPreferences.getUserInTimeline().equals(UserInTimeline.WEBFINGER_ID);

    public UserAdapter(@NonNull UserListContextMenu contextMenu, TimelineData<UserViewItem> listData) {
        super(contextMenu.getActivity().getMyContext(), listData);
        this.contextMenu = contextMenu;
        this.listItemLayoutId = R.id.user_wrapper;
    }

    UserAdapter(@NonNull UserListContextMenu contextMenu, int listItemLayoutId, List<UserViewItem> items,
                Timeline timeline) {
        super(contextMenu.getActivity().getMyContext(), timeline, items);
        this.contextMenu = contextMenu;
        this.listItemLayoutId = listItemLayoutId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        UserViewItem item = getItem(position);
        populateView(view, item, position);
        return view;
    }

    public void populateView(View view, UserViewItem item, int position) {
        MyUrlSpan.showText(view, R.id.username,
                item.mbUser.toUserTitle(showWebFingerId) + ( isCombined() ?
                        " / " + myContext.persistentOrigins()
                                .fromId(item.mbUser.originId).getName() : ""),
                false, false);
        if (showAvatars) {
            showAvatar(item, view);
        }
        MyUrlSpan.showText(view, R.id.homepage, item.mbUser.getHomepage(), true, false);
        MyUrlSpan.showText(view, R.id.description, item.mbUser.getDescription(), false, false);
        MyUrlSpan.showText(view, R.id.location, item.mbUser.location, false, false);
        MyUrlSpan.showText(view, R.id.profile_url, item.mbUser.getProfileUrl(), true, false);

        showCounter(view, R.id.msg_count, item.mbUser.msgCount);
        showCounter(view, R.id.favorites_count, item.mbUser.favoritesCount);
        showCounter(view, R.id.following_count, item.mbUser.followingCount);
        showCounter(view, R.id.followers_count, item.mbUser.followersCount);

        MyUrlSpan.showText(view, R.id.location, item.mbUser.location, false, false);
        showMyFollowers(view, item);
    }

    private static void showCounter(View parentView, int viewId, long counter) {
        MyUrlSpan.showText(parentView, viewId, counter <= 0 ? "-" : String.valueOf(counter) , false, false);
    }

    private View newView() {
        return LayoutInflater.from(contextMenu.getActivity()).inflate(listItemLayoutId, null);
    }

    private void showAvatar(UserViewItem item, View view) {
        AvatarView avatarView = view.findViewById(R.id.avatar_image);
        item.showAvatar(contextMenu.getActivity(), avatarView);
    }

    private void showMyFollowers(View view, UserViewItem item) {
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
                builder.append(myContext.persistentAccounts().fromUserId(userId).getAccountName());
                count++;
            }
        }
        MyUrlSpan.showText(view, R.id.followed_by, builder.toString(), false, false);
    }
}
