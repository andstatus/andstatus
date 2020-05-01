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
import org.andstatus.app.actor.ActorAdapter;
import org.andstatus.app.graphics.AvatarView;
import org.andstatus.app.net.social.ActivityType;
import org.andstatus.app.note.NoteAdapter;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyUrlSpan;

/**
 * @author yvolk@yurivolkov.com
 */
public class ActivityAdapter extends BaseTimelineAdapter<ActivityViewItem> {
    private ActivityContextMenu contextMenu;
    private final ActorAdapter actorAdapter;
    private final NoteAdapter noteAdapter;
    private final ActorAdapter objActorAdapter;
    private final boolean showReceivedTime;

    enum LayoutType {
        ACTOR,
        ACTOR_NOTE,
        ACTOR_OBJACTOR,
        ACTOR_ACTOR,
        NOTE;

        boolean isActorShown() {
            switch (this) {
                case NOTE:
                    return false;
                default:
                    return true;
            }
        }

        boolean isNoteShown() {
            switch (this) {
                case NOTE:
                case ACTOR_NOTE:
                    return true;
                default:
                    return false;
            }
        }

        boolean isObjActorViewShown() {
            switch (this) {
                case ACTOR_OBJACTOR:
                case ACTOR_ACTOR:
                    return true;
                default:
                    return false;
            }
        }

        boolean isObjActorShown() {
            switch (this) {
                case ACTOR_OBJACTOR:
                    return true;
                default:
                    return false;
            }
        }
    }

    public ActivityAdapter(ActivityContextMenu contextMenu, TimelineData<ActivityViewItem> listData) {
        super(contextMenu.note.getMyContext(), listData);
        this.contextMenu = contextMenu;
        actorAdapter = new ActorAdapter(contextMenu.actor, new TimelineDataActorWrapper(listData));
        noteAdapter = new NoteAdapter(contextMenu.note, new TimelineDataNoteWrapper(listData));
        objActorAdapter = new ActorAdapter(contextMenu.objActor, new TimelineDataObjActorWrapper(listData));
        showReceivedTime = listData.params.getTimelineType() == TimelineType.UNREAD_NOTIFICATIONS;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup viewGroup) {
        ViewGroup view = getEmptyView(convertView);
        view.setOnClickListener(this);
        setPosition(view, position);
        ActivityViewItem item = getItem(position);
        LayoutType layoutType = calcLayoutType(item);
        showActor(view, item, layoutType);
        final ViewGroup noteView = view.findViewById(R.id.note_wrapper);
        if (layoutType.isNoteShown()) {
            noteAdapter.populateView(view, item.noteViewItem, showReceivedTime, position);
            noteView.setOnCreateContextMenuListener(contextMenu.note);
            noteView.setOnClickListener(noteAdapter);
            noteView.setVisibility(View.VISIBLE);
        } else {
            noteAdapter.removeReplyToMeMarkerView(view);
            noteView.setVisibility(View.GONE);
        }
        final ViewGroup objActorView = view.findViewById(R.id.actor_wrapper);
        if (layoutType.isObjActorViewShown()) {
            if (layoutType.isObjActorShown()) {
                objActorAdapter.populator.populateView(objActorView, item.getObjActorItem(), position);
                objActorView.setOnCreateContextMenuListener(contextMenu.objActor);
                objActorView.setOnClickListener(objActorAdapter);
            } else {
                objActorAdapter.populator.populateView(objActorView, item.actor, position);
                objActorView.setOnCreateContextMenuListener(contextMenu.actor);
                objActorView.setOnClickListener(actorAdapter);
            }
            objActorView.setVisibility(View.VISIBLE);
        } else {
            objActorView.setVisibility(View.GONE);
        }
        return view;
    }

    private LayoutType calcLayoutType(ActivityViewItem item) {
        if (item.noteViewItem.getId() == 0) {
            if (item.getObjActorItem().getId() == 0) {
                return LayoutType.ACTOR;
            } else if (myContext.users().isMe(item.getObjActorItem().getActor())) {
                return LayoutType.ACTOR_ACTOR;
            } else {
                return LayoutType.ACTOR_OBJACTOR;
            }
        } else {
            if (item.activityType == ActivityType.CREATE || item.activityType == ActivityType.UPDATE) {
                return LayoutType.NOTE;
            } else {
                return LayoutType.ACTOR_NOTE;
            }
        }
    }

    private ViewGroup getEmptyView(View convertView) {
        if (convertView == null) {
            final ViewGroup viewGroup = (ViewGroup) LayoutInflater.from(contextMenu.note.getActivity())
                    .inflate(R.layout.activity, null);
            noteAdapter.setupButtons(viewGroup);
            return viewGroup;
        }
        convertView.setBackgroundResource(0);
        View noteIndented = convertView.findViewById(R.id.note_indented);
        noteIndented.setBackgroundResource(0);
        if (showAvatars) {
            convertView.findViewById(R.id.actor_avatar_image).setVisibility(View.GONE);
            convertView.findViewById(R.id.avatar_image).setVisibility(View.GONE);
        }
        return (ViewGroup) convertView;
    }

    private void showActor(ViewGroup view, ActivityViewItem item, LayoutType layoutType) {
        final ViewGroup actorView = view.findViewById(R.id.action_wrapper);
        if (layoutType.isActorShown()) {
            item.noteViewItem.hideTheReblogger(item.actor.getActor());
            item.getObjActorItem().hideFollowedBy(item.actor.getActor());
            if (showAvatars && layoutType != LayoutType.ACTOR_ACTOR) {
                AvatarView avatarView = view.findViewById(R.id.actor_avatar_image);
                item.actor.showAvatar(contextMenu.actor.getActivity(), avatarView);
            }
            MyUrlSpan.showText(view, R.id.action_title,
                    (layoutType == LayoutType.ACTOR_ACTOR
                            ? item.actor.getActor().getUniqueName()
                            : item.actor.getActor().getViewItemActorName()) +
                        " " + item.activityType.getActedTitle(contextMenu.actor.getActivity()) +
                        (layoutType == LayoutType.ACTOR_ACTOR
                            ? " " + item.getObjActorItem().getActor().getUniqueName()
                            : ""),
                    false, false);
            MyUrlSpan.showText(view, R.id.action_details,
                    item.getDetails(contextMenu.actor.getActivity(), showReceivedTime), false, false);
            actorView.setOnCreateContextMenuListener(contextMenu.actor);
            actorView.setOnClickListener(actorAdapter);
            actorView.setVisibility(View.VISIBLE);
        } else {
            actorView.setVisibility(View.GONE);
        }
    }

}
