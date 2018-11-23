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

package org.andstatus.app.service;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.BaseTimelineAdapter;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyUrlSpan;

import java.util.List;

class QueueViewerAdapter extends BaseTimelineAdapter<QueueData> {
    private final QueueViewer container;

    QueueViewerAdapter(QueueViewer container, List<QueueData> items) {
        super(container.getMyContext(),
                Timeline.getTimeline(TimelineType.COMMANDS_QUEUE, 0, Origin.EMPTY),
                items);
        this.container = container;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(container);
        view.setOnClickListener(this);
        setPosition(view, position);
        QueueData item = getItem(position);
        MyUrlSpan.showText(view, R.id.queue_type, item.queueType.getAcronym(), false, false);
        MyUrlSpan.showText(view, R.id.command_summary, item.commandData.toCommandSummary(MyContextHolder.get())
                + "\t "
                + item.commandData.createdDateWithLabel(myContext.context()), false, false);
        MyUrlSpan.showText(view, R.id.result_summary, item.commandData.getResult().toSummary(), false, false);
        return view;
    }

    private View newView() {
        return LayoutInflater.from(container).inflate(R.layout.queue_item, null);
    }
}
