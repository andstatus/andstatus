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

package org.andstatus.app.actor;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.meta.Timeline;

import java.util.List;

public class ActorAdapter extends BaseTimelineAdapter<ActorViewItem> {
    private final ActorContextMenu contextMenu;
    private final int listItemLayoutId;
    public final ActorViewItemPopulator populator;

    public ActorAdapter(@NonNull ActorContextMenu contextMenu, TimelineData<ActorViewItem> listData) {
        super(contextMenu.getActivity().getMyContext(), listData);
        this.contextMenu = contextMenu;
        this.listItemLayoutId = R.id.actor_wrapper;
        populator = new ActorViewItemPopulator(contextMenu.getActivity(), isCombined(), showAvatars);
    }

    ActorAdapter(@NonNull ActorContextMenu contextMenu, int listItemLayoutId, List<ActorViewItem> items,
                 Timeline timeline) {
        super(contextMenu.getActivity().getMyContext(), timeline, items);
        this.contextMenu = contextMenu;
        this.listItemLayoutId = listItemLayoutId;
        populator = new ActorViewItemPopulator(contextMenu.getActivity(), isCombined(), showAvatars);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        ActorViewItem item = getItem(position);
        populator.populateView(view, item, position);
        return view;
    }

    private View newView() {
        return LayoutInflater.from(contextMenu.getActivity()).inflate(listItemLayoutId, null);
    }
}
