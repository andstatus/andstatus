/*
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.NonNull;

import org.andstatus.app.msg.MessageViewItem;
import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.TimelinePage;

class TimelineDataMessageWrapper extends TimelineData<MessageViewItem> {
    final TimelineData<ActivityViewItem> listData;

    TimelineDataMessageWrapper(TimelineData<ActivityViewItem> listData) {
        super(null, new TimelinePage<MessageViewItem>(listData.params, null));
        this.listData = listData;
    }

    @Override
    public int size() {
        return listData.size();
    }

    @NonNull
    @Override
    public MessageViewItem getItem(int position) {
        return listData.getItem(position).message;
    }

    @NonNull
    @Override
    public MessageViewItem getById(long itemId) {
        return MessageViewItem.EMPTY;
    }

    @Override
    public int getPositionById(long itemId) {
        return -1;
    }

    @Override
    public boolean mayHaveYoungerPage() {
        return listData != null && listData.mayHaveYoungerPage();
    }

    @Override
    public boolean mayHaveOlderPage() {
        return listData.mayHaveOlderPage();
    }

    @Override
    public String toString() {
        return listData.toString();
    }

    @Override
    public boolean isCollapseDuplicates() {
        return listData != null && listData.isCollapseDuplicates();
    }

    @Override
    public boolean canBeCollapsed(int position) {
        return listData.canBeCollapsed(position);
    }

    @Override
    public void collapseDuplicates(boolean collapse, long itemId) {
        // Empty
    }
}
