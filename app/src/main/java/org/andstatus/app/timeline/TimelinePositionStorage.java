/* 
 * Copyright (c) 2014-2016 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.support.annotation.Nullable;
import android.view.View;
import android.widget.ListView;

import org.andstatus.app.util.MyLog;

/**
 * Determines where to save / retrieve position in the list
 * Information on two rows is stored for each "position" hence two keys. 
 * Plus Query string is being stored for the search results.
 * 2014-11-15 We are storing {@link ViewItem#getDate()} for the last item to retrieve, not its ID as before
 * @author yvolk@yurivolkov.com
 */
class TimelinePositionStorage<T extends ViewItem<T>> {
    private static final int NOT_STORED = -1;

    private final BaseTimelineAdapter<T> adapter;
    private final ListView mListView;
    private final TimelineParameters mListParameters;

    public static YOfPosition getYOfPosition(ListView list, BaseTimelineAdapter adapter, int positionIn) {
        int position = positionIn;
        View viewOfPosition = getViewOfPosition(list, adapter, positionIn);
        if (viewOfPosition == null && position > 0) {
            position -= 1;
            viewOfPosition = getViewOfPosition(list, adapter, position);
        }
        int y = viewOfPosition == null ? 0 : viewOfPosition.getTop() - list.getPaddingTop();
        if (MyLog.isVerboseEnabled() ) {
            MyLog.v(TimelinePositionStorage.class, "getYOfPosition; " + position
                    + " listFirstVisiblePos:" + list.getFirstVisiblePosition()
                    + ", listViews=" + list.getCount()
                    + ", headers:" + list.getHeaderViewsCount()
                    + ", items=" + adapter.getCount()
                    + (viewOfPosition == null ? " view not found" : " y=" + y)
//                    + "\n" + MyLog.getStackTrace(new Throwable())
            );
        }
        if (viewOfPosition == null) {
            return new YOfPosition(positionIn, adapter.getItemId(positionIn), 0);
        }
        return new YOfPosition(position, adapter.getItemId(position), y);
    }

    @Nullable
    private static View getViewOfPosition(ListView list, BaseTimelineAdapter adapter, int position) {
        View viewOfPosition = null;
        for (int ind = 0; ind < list.getChildCount(); ind++) {
            View view = list.getChildAt(ind);
            if (adapter.getPosition(view) == position) {
                viewOfPosition = view;
                break;
            }
        }
        return viewOfPosition;
    }

    static class TLPosition {
        long firstVisibleItemId = NOT_STORED;
        long minSentDate = NOT_STORED;
        int y = NOT_STORED;
    }

    static class YOfPosition {
        final int position;
        final long itemId;
        final int y;

        YOfPosition(int position, long itemId, int y) {
            this.position = position;
            this.itemId = itemId;
            this.y = y;
        }
    }

    TimelinePositionStorage(BaseTimelineAdapter<T> listAdapter, ListView listView, TimelineParameters listParameters) {
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
        YOfPosition yop = getYOfPosition(mListView, adapter, firstVisibleAdapterPosition);

        int lastPosition = Integer.min(mListView.getLastVisiblePosition() + 10, itemCount - 1);
        long minDate = adapter.getItem(lastPosition).getDate();

        if (yop.itemId <= 0) {
            clear();
        } else {
            saveTLPosition(yop.itemId, minDate, yop.y);
        }
        if (MyLog.isVerboseEnabled()) {
            String msgLog = "id:" + yop.itemId
                    + ", y:" + yop.y
                    + " at pos=" + firstVisibleAdapterPosition
                    + (yop.position != firstVisibleAdapterPosition ? " found pos=" + yop.position : "")
                    + ", minDate=" + MyLog.formatDateTime(minDate)
                    + " at pos=" + lastPosition + " of " + itemCount
                    + ", listViews=" + mListView.getCount()
                    + " " + mListParameters.getTimeline();
            if (yop.itemId <= 0) {
                MyLog.v(this, method + "; failed " + msgLog
                        + "\n no visible items");
            } else {
                MyLog.v(this, method + "; succeeded " + msgLog);
            }
        }

    }
    
    private void saveTLPosition(long firstVisibleItemId, long minDate, int y) {
        mListParameters.getTimeline().setVisibleItemId(firstVisibleItemId);
        mListParameters.getTimeline().setVisibleOldestDate(minDate);
        mListParameters.getTimeline().setVisibleY(y);
    }

    public TLPosition getTLPosition() {
        TLPosition tlPosition = new TLPosition();
        if (mListParameters.getTimeline().getVisibleItemId() > 0) {
            tlPosition.firstVisibleItemId = mListParameters.getTimeline().getVisibleItemId();
            tlPosition.y = mListParameters.getTimeline().getVisibleY();
            tlPosition.minSentDate = mListParameters.getTimeline().getVisibleOldestDate();
        }
        return tlPosition;
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
     */
    public void restore() {
        final String method = "restore" + mListParameters.timeline.getId();
        if (mListView == null || adapter == null || mListParameters.isEmpty() || adapter.getCount() == 0) {
            MyLog.v(this, method + "; skipped");
            return;
        }
        boolean restored = false;
        int position = -1;
        TLPosition tlPosition = getTLPosition();
        try {
            if (tlPosition.firstVisibleItemId > 0) {
                position = getPositionById(tlPosition.firstVisibleItemId);
            }
            if (position >= 0) {
                mListView.setSelectionFromTop(position + mListView.getHeaderViewsCount(), tlPosition.y);
                restored = true;
            } else {
                // There is no stored position - starting from the Top
                position = 0;
                setPosition(mListView, position);
            }
        } catch (Exception e) {
            MyLog.v(this, method, e);
        }
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(this, method + "; " + (restored ? "succeeded" : "failed" )
                    + ", id:" + tlPosition.firstVisibleItemId + ", y:" + tlPosition.y
                    + ", at pos=" + position + " of " + adapter.getCount()
                    + ", listViews=" + mListView.getCount()
                    + " " + mListParameters.getTimeline() );
        }
        if (!restored) {
            clear();
        }
        adapter.setPositionRestored(true);
    }

    static void setPosition(ListView listView, int position) {
        if (listView == null) {
            return;
        }
        int viewHeight = listView.getHeight();
        int childHeight = 30;
        int y = position == 0 ? 0 : viewHeight - childHeight;
        int headerViewsCount = listView.getHeaderViewsCount();
        if (MyLog.isVerboseEnabled()) {
            MyLog.v(TimelinePositionStorage.class, "Set position of " + position + " item to " + y + " px," +
                    " header views: " + headerViewsCount);
        }
        listView.setSelectionFromTop(position, y);
    }

    /**
     * @return the position in the adapter or -1 if the item was not found
     */
    private int getPositionById(long itemId) {
        if (adapter == null) {
            return -1;
        }
        return adapter.getPositionById(itemId);
    }
}