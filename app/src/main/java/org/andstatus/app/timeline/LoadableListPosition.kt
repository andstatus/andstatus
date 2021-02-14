/*
 * Copyright (c) 2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.view.View;
import android.widget.ListView;

import androidx.annotation.Nullable;

import org.andstatus.app.util.IsEmpty;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.MyStringBuilder;
import org.andstatus.app.util.TaggedClass;

public class LoadableListPosition<T extends ViewItem<T>> implements IsEmpty, TaggedClass {
    private static final String TAG = LoadableListPosition.class.getSimpleName();
    public final static LoadableListPosition EMPTY = saved(0, 0, 0, "(empty position)");
    final int position;
    final long itemId;
    final int y;
    final long minSentDate;
    final String description;

    private static LoadableListPosition current(long itemId, int y, int position, long minSentDate, String description) {
        return new LoadableListPosition(itemId, y, position, minSentDate, description);
    }

    static LoadableListPosition saved(long itemId, int y, long minSentDate, String description) {
        return new LoadableListPosition(itemId, y, 0, minSentDate, description);
    }

    private LoadableListPosition(long itemId, int y, int position, long minSentDate, String description) {
        this.position = position;
        this.itemId = itemId;
        this.y = y;
        this.minSentDate = minSentDate;
        this.description = description;
    }

    public static LoadableListPosition getCurrent(ListView list, BaseTimelineAdapter adapter, long itemIdDefault) {
        int firstVisiblePosition = Integer.min(
                Integer.max(list.getFirstVisiblePosition(), 0),
                Integer.max(adapter.getCount() - 1, 0)
        );

        int position = firstVisiblePosition;
        View viewOfPosition = getViewOfPosition(list, firstVisiblePosition);
        if (viewOfPosition == null && position > 0) {
            position = firstVisiblePosition - 1;
            viewOfPosition = getViewOfPosition(list, position);
        }
        if (viewOfPosition == null) {
            position = Math.max(firstVisiblePosition + 1, 0);
            viewOfPosition = getViewOfPosition(list, position);
        }
        if (viewOfPosition == null) {
            position = adapter.getPositionById(itemIdDefault);
        }
        long itemIdFound = viewOfPosition == null ? 0 : adapter.getItemId(position);
        long itemId = itemIdFound == 0 ? itemIdDefault : itemIdFound;
        int y = itemIdFound == 0 ? 0 : viewOfPosition.getTop() - list.getPaddingTop();

        int lastPosition = Integer.min(list.getLastVisiblePosition() + 10, adapter.getCount() - 1);
        long minDate = adapter.getItem(lastPosition).getDate();
        String description = "";
        if (itemId <= 0 && minDate > 0) {
            description = "from lastPosition, ";
            position = lastPosition;
            itemId = adapter.getItemId(lastPosition);
        }
        description = description + "currentPosition:" + position
                + ", firstVisiblePos:" + firstVisiblePosition
                + "; viewsInList:" + list.getChildCount()
                + ", headers:" + list.getHeaderViewsCount()
                + (viewOfPosition == null ? ", view not found" : ", y:" + y)
                + "; items:" + adapter.getCount()
                + ", itemId:" + itemId + " defaultId:" + itemIdDefault
        ;
        return current(itemId, y, position, minDate, description);
    }

    @Nullable
    static View getViewOfPosition(ListView list, int position) {
        View viewOfPosition = null;
        for (int ind = 0; ind < list.getChildCount(); ind++) {
            View view = list.getChildAt(ind);
            final int positionForView = list.getPositionForView(view);
            if (MyLog.isVerboseEnabled()) {
                int ind2 = ind;
                MyLog.v(TAG, () -> "getViewOfPosition " + position + ", ind " + ind2 + " => " + positionForView);
            }
            if (positionForView == position) {
                viewOfPosition = view;
                break;
            }
        }
        return viewOfPosition;
    }

    static <T extends ViewItem<T>> boolean restore(ListView list, BaseTimelineAdapter<T> adapter,
                                                   LoadableListPosition pos) {
        int position = -1;
        try {
            if (pos.itemId > 0) {
                position = adapter.getPositionById(pos.itemId);
            }
            if (position >= 0) {
                list.setSelectionFromTop(position, pos.y);
                return true;
            } else {
                // There is no stored position - starting from the Top
                position = 0;
                setPosition(list, position);
            }
        } catch (Exception e) {
            MyLog.v(TAG, "restore " + pos, e);
        }
        return false;
    }

    static void setPosition(ListView listView, int position) {
        if (listView == null) {
            return;
        }
        int viewHeight = listView.getHeight();
        int childHeight = 30;
        int y = position == 0 ? 0 : viewHeight - childHeight;
        int headerViewsCount = listView.getHeaderViewsCount();
        MyLog.v(TAG, () -> "Set item at position " + position + " to " + y + " px," +
                " header views: " + headerViewsCount);
        listView.setSelectionFromTop(position, y);
    }

    @Override
    public boolean isEmpty() {
        return itemId == 0;
    }

    LoadableListPosition<T> logV(String description) {
        MyLog.v(TAG, () -> description + "; " + this.description);
        return this;
    }

    @Override
    public String toString() {
        return MyStringBuilder.formatKeyValue(this, description);
    }

    @Override
    public String classTag() {
        return TAG;
    }
}
