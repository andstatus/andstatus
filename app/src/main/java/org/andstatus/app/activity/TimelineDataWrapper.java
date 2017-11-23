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

import org.andstatus.app.timeline.TimelineData;
import org.andstatus.app.timeline.TimelinePage;
import org.andstatus.app.timeline.ViewItem;

abstract class TimelineDataWrapper<T extends ViewItem<T>> extends TimelineData<T> {
    final TimelineData<ActivityViewItem> listData;

    TimelineDataWrapper(TimelineData<ActivityViewItem> listData) {
        super(null, new TimelinePage<>(listData.params, null));
        this.listData = listData;
    }

    @Override
    public int size() {
        return listData.size();
    }

    @NonNull
    @Override
    public abstract T getItem(int position);

    @NonNull
    @Override
    public T getById(long itemId) {
        int position = getPositionById(itemId);
        if (position < 0) {
            return pages.get(0).getEmptyItem();
        }
        return getItem(position);
    }

    @Override
    public abstract int getPositionById(long itemId);

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
