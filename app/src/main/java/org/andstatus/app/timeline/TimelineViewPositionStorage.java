/* 
 * Copyright (c) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.timeline;

import android.widget.ListView;

import org.andstatus.app.util.MyLog;

/**
 * Determines where to save / retrieve position in the list
 * Information on two rows is stored for each "position" hence two keys. 
 * Plus Query string is being stored for the search results.
 * 2014-11-15 We are storing {@link ViewItem#getDate()} for the last item to retrieve, not its ID as before
 * @author yvolk@yurivolkov.com
 */
class TimelineViewPositionStorage<T extends ViewItem<T>> {
    private final LoadableListActivity<T> activity;
    private final BaseTimelineAdapter<T> adapter;
    private final ListView listView;
    private final TimelineParameters params;

    TimelineViewPositionStorage(LoadableListActivity<T> activity, TimelineParameters listParameters) {
        this.activity = activity;
        this.adapter = activity.getListAdapter();
        this.listView = activity.getListView();
        this.params = listParameters;
    }

    void save() {
        final String method = "save" + params.timeline.getId();
        if (isEmpty()) {
            MyLog.v(this, () -> method + "; skipped");
            return;
        }
        int itemCount = adapter.getCount();
        int firstVisibleAdapterPosition = Integer.min(
                Integer.max(listView.getFirstVisiblePosition(), 0),
                itemCount - 1);
        LoadableListPosition pos = activity.getCurrentListPosition();

        int lastPosition = Integer.min(listView.getLastVisiblePosition() + 10, itemCount - 1);
        long minDate = adapter.getItem(lastPosition).getDate();

        if (pos.itemId <= 0) {
            clear();
        } else {
            saveListPosition(pos.itemId, minDate, pos.y);
        }
        if (MyLog.isVerboseEnabled()) {
            String msgLog = "id:" + pos.itemId
                    + ", y:" + pos.y
                    + " at pos=" + firstVisibleAdapterPosition
                    + (pos.position != firstVisibleAdapterPosition ? " found pos=" + pos.position : "")
                    + ", minDate=" + MyLog.formatDateTime(minDate)
                    + " at pos=" + lastPosition + " of " + itemCount
                    + ", listViews=" + listView.getCount()
                    + " " + params.getTimeline();
            if (pos.itemId <= 0) {
                MyLog.v(this, () -> method + "; failed " + msgLog + "\n no visible items");
            } else {
                MyLog.v(this, () -> method + "; succeeded " + msgLog);
            }
        }

    }

    private boolean isEmpty() {
        return listView == null || adapter == null || params.isEmpty() || adapter.getCount() == 0;
    }

    private void saveListPosition(long firstVisibleItemId, long minDate, int y) {
        params.getTimeline().setVisibleItemId(firstVisibleItemId);
        params.getTimeline().setVisibleOldestDate(minDate);
        params.getTimeline().setVisibleY(y);
    }

    public static LoadableListPosition loadListPosition(TimelineParameters params) {
        final long itemId = params.getTimeline().getVisibleItemId();
        return itemId > 0
                ? LoadableListPosition.saved(
                    itemId,
                    params.getTimeline().getVisibleY(),
                    params.getTimeline().getVisibleOldestDate(), "saved itemId:" + itemId)
                : LoadableListPosition.EMPTY;
    }

    void clear() {
        params.getTimeline().setVisibleItemId(0);
        params.getTimeline().setVisibleOldestDate(0);
        params.getTimeline().setVisibleY(0);
        MyLog.v(this, () -> "Position forgot " + params.getTimeline());
    }
    
    /**
     * Restore (the first visible item) position saved for this timeline
     * see http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview?rq=1
     */
    public void restore() {
        final String method = "restore" + params.timeline.getId();
        if (isEmpty()) {
            MyLog.v(this, () -> method + "; skipped");
            return;
        }
        final LoadableListPosition pos = loadListPosition(params);
        boolean restored = LoadableListPosition.restore(listView, adapter, pos);
        if (MyLog.isVerboseEnabled()) {
            pos.logV(method + "; stored " + (restored ? "succeeded" : "failed") + " " + params.getTimeline());
            activity.getCurrentListPosition().logV(method + "; actual " + (restored ? "succeeded" : "failed"));
        }
        if (!restored) clear();
        adapter.setPositionRestored(true);
    }
}