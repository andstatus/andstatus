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
import org.andstatus.app.msg.MessageContextMenu;
import org.andstatus.app.net.social.MbActivityType;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.util.MyUrlSpan;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActivityAdapter extends BaseTimelineAdapter<ActivityViewItem> {
    private MessageContextMenu contextMenu; // TODO: Create ContextMenu for activities
    private final MessageAdapter messageAdapter;

    public ActivityAdapter(MessageContextMenu contextMenu, TimelineData<ActivityViewItem> listData) {
        super(contextMenu.getMyContext(), listData);
        this.contextMenu = contextMenu;
        messageAdapter = new MessageAdapter(contextMenu, new TimelineDataMessageWrapper(listData));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {
        ViewGroup view = getEmptyView(convertView);
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        ActivityViewItem item = getItem(position);
        final ViewGroup actorView = view.findViewById(R.id.actor_wrapper);
        if (item.activityType == MbActivityType.CREATE || item.activityType == MbActivityType.UPDATE) {
            actorView.setVisibility(View.GONE);
        } else {
            if (showAvatars) {
                AvatarView avatarView = view.findViewById(R.id.actor_avatar_image);
                item.actor.showAvatar(contextMenu.getActivity(), avatarView);
            }
            MyUrlSpan.showText(view, R.id.action_details, item.actor.getWebFingerIdOrUserName()
                    + " " + item.activityType.getActedTitle(contextMenu.getActivity()), false, false);
            actorView.setVisibility(View.VISIBLE);
        }
        final ViewGroup messageView = view.findViewById(R.id.message_wrapper);
        if (item.message.getId() == 0) {
            messageView.setVisibility(View.GONE);
        } else {
            messageAdapter.populateView(messageView, item.message, position);
            messageView.setVisibility(View.VISIBLE);
        }
        return view;
    }

    protected ViewGroup getEmptyView(View convertView) {
        if (convertView == null) {
            return (ViewGroup) LayoutInflater.from(contextMenu.getActivity()).inflate(R.layout.activity, null);
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

}
