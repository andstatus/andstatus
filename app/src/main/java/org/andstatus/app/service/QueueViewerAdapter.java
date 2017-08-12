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

package org.andstatus.app.service;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.util.MyUrlSpan;
import org.andstatus.app.view.MyBaseAdapter;

import java.util.List;

class QueueViewerAdapter extends MyBaseAdapter<QueueData> {
    private final QueueViewer contextMenu;
    private final List<QueueData> items;

    public QueueViewerAdapter(QueueViewer contextMenu, List<QueueData> items) {
        super(contextMenu.getMyContext());
        this.contextMenu = contextMenu;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public QueueData getItem(int position) {
        if (position >= 0 && position < getCount()) {
            return items.get(position);
        }
        return QueueData.getEmpty();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView == null ? newView() : convertView;
        view.setOnCreateContextMenuListener(contextMenu);
        view.setOnClickListener(this);
        setPosition(view, position);
        QueueData item = items.get(position);
        MyUrlSpan.showText(view, R.id.queue_type, item.queueType.getAcronym(), false, false);
        MyUrlSpan.showText(view, R.id.command_summary, item.commandData.toCommandSummary(MyContextHolder.get())
                + "\t "
                + item.commandData.createdDateWithLabel(myContext.context()), false, false);
        MyUrlSpan.showText(view, R.id.result_summary, item.commandData.getResult().toSummary(), false, false);
        return view;
    }

    private View newView() {
        return LayoutInflater.from(contextMenu).inflate(R.layout.queue_item, null);
    }
}
