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
    private final BaseTimelineAdapter<T> adapter;
    private final ListView mListView;
    private final TimelineParameters mListParameters;

    TimelineViewPositionStorage(ListView listView, BaseTimelineAdapter<T> listAdapter, TimelineParameters listParameters) {
        this.adapter = listAdapter;
        this.mListView = listView;
        this.mListParameters = listParameters;
    }

    void save() {
        final String method = "save" + mListParameters.timeline.getId();
        if (mListView == null || adapter == null || mListParameters.isEmpty() || adapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        int itemCount = adapter.getCount();
        int firstVisibleAdapterPosition = Integer.min(
                Integer.max(mListView.getFirstVisiblePosition(), 0),
                itemCount - 1);
        LoadableListPosition pos = LoadableListPosition.getCurrent(mListView, adapter, 0);

        int lastPosition = Integer.min(mListView.getLastVisiblePosition() + 10, itemCount - 1);
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
                    + ", listViews=" + mListView.getCount()
                    + " " + mListParameters.getTimeline();
            if (pos.itemId <= 0) {
                MyLog.v(this, method + "; failed " + msgLog
                        + "\n no visible items");
            } else {
                MyLog.v(this, method + "; succeeded " + msgLog);
            }
        }

    }
    
    private void saveListPosition(long firstVisibleItemId, long minDate, int y) {
        mListParameters.getTimeline().setVisibleItemId(firstVisibleItemId);
        mListParameters.getTimeline().setVisibleOldestDate(minDate);
        mListParameters.getTimeline().setVisibleY(y);
    }

    public LoadableListPosition loadListPosition() {
        return mListParameters.getTimeline().getVisibleItemId() > 0
                ? LoadableListPosition.saved(
                    mListParameters.getTimeline().getVisibleItemId(),
                    mListParameters.getTimeline().getVisibleY(),
                    mListParameters.getTimeline().getVisibleOldestDate())
                : LoadableListPosition.EMPTY;
    }

    void clear() {
        mListParameters.getTimeline().setVisibleItemId(0);
        mListParameters.getTimeline().setVisibleOldestDate(0);
        mListParameters.getTimeline().setVisibleY(0);
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, "Position forgot " + mListParameters.getTimeline());
        }
    }
    
    /**
     * Restore (the first visible item) position saved for this timeline
     * see http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview?rq=1
     */
    public void restore() {
        final String method = "restore" + mListParameters.timeline.getId();
        if (mListView == null || adapter == null || mListParameters.isEmpty() || adapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        final LoadableListPosition pos = loadListPosition();
        boolean restored = LoadableListPosition.restore(mListView, adapter, pos);
        if (MyLog.isVerboseEnabled()) {
            pos.logV(method + "; stored " + (restored ? "succeeded" : "failed")
                + " " + mListParameters.getTimeline());
            LoadableListPosition.getCurrent(mListView, adapter, pos.itemId)
                    .logV(method + "; actual " + (restored ? "succeeded" : "failed"));
        }
        if (!restored) clear();
        adapter.setPositionRestored(true);
    }
}