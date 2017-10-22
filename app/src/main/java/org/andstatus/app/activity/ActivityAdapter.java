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

package org.andstatus.app.activity;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.msg.MessageAdapter;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.user.UserAdapter;
import org.andstatus.app.util.MyUrlSpan;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActivityAdapter extends BaseTimelineAdapter<ActivityViewItem> {
    private ActivityContextMenu contextMenu;
    private final MessageAdapter messageAdapter;
    private final UserAdapter userAdapter;

    public ActivityAdapter(ActivityContextMenu contextMenu, TimelineData<ActivityViewItem> listData) {
        super(contextMenu.message.getMyContext(), listData);
        this.contextMenu = contextMenu;
        messageAdapter = new MessageAdapter(contextMenu.message, new TimelineDataMessageWrapper(listData));
        userAdapter = new UserAdapter(contextMenu.user, new TimelineDataUserWrapper(listData));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {
        ViewGroup view = getEmptyView(convertView);
        view.setOnClickListener(this);
        setPosition(view, position);
        ActivityViewItem item = getItem(position);
        showActor(view, item);
        final ViewGroup messageView = view.findViewById(R.id.message_wrapper);
        if (item.message.getId() == 0) {
            messageView.setVisibility(View.GONE);
        } else {
            messageAdapter.populateView(messageView, item.message, position);
            messageView.setOnCreateContextMenuListener(contextMenu.message);
            messageView.setOnClickListener(messageAdapter);
            messageView.setVisibility(View.VISIBLE);
        }
        final ViewGroup userView = view.findViewById(R.id.user_wrapper);
        if (item.user.getId() == 0) {
            userView.setVisibility(View.GONE);
        } else {
            userAdapter.populateView(userView, item.user, position);
            userView.setOnCreateContextMenuListener(contextMenu.user);
            userView.setOnClickListener(userAdapter);
            userView.setVisibility(View.VISIBLE);
        }
        return view;
    }

    private ViewGroup getEmptyView(View convertView) {
        if (convertView == null) {
            return (ViewGroup) LayoutInflater.from(contextMenu.message.getActivity()).inflate(R.layout.activity, null);
        }
        convertView.setBackgroundResource(0);
        View messageIndented = convertView.findViewById(R.id.message_indented);
        messageIndented.setBackgroundResource(0);
        if (showAvatars) {
            convertView.findViewById(R.id.actor_avatar_image).setVisibility(View.GONE);
            convertView.findViewById(R.id.avatar_image).setVisibility(View.GONE);
        }
        return (ViewGroup) convertView;
    }

    private void showActor(ViewGroup view, ActivityViewItem item) {
        final ViewGroup actorView = view.findViewById(R.id.action_wrapper);
        if (item.activityType == MbActivityType.CREATE || item.activityType == MbActivityType.UPDATE) {
            actorView.setVisibility(View.GONE);
        } else {
            item.message.hideActor(item.actor.getUserId());
            item.user.hideActor(item.actor.getUserId());
            if (showAvatars) {
                AvatarView avatarView = view.findViewById(R.id.actor_avatar_image);
                item.actor.showAvatar(contextMenu.user.getActivity(), avatarView);
            }
            MyUrlSpan.showText(view, R.id.action_title, item.actor.getWebFingerIdOrUserName()
                    + " " + item.activityType.getActedTitle(contextMenu.user.getActivity()), false, false);
            MyUrlSpan.showText(view, R.id.action_details, item.getDetails(contextMenu.user.getActivity()), false, false);
            actorView.setVisibility(View.VISIBLE);
        }
    }

}
