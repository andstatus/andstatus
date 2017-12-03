/*
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.content.Intent;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.TestSuite;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimelinePositionStorageTest extends TimelineActivityTest {
    private static volatile ViewItem previousItem = EmptyViewItem.EMPTY;

    @Override
    protected Intent getActivityIntent() {
        MyLog.i(this, "setUp started");
        TestSuite.initializeWithData(this);
        MyLog.i(this, "setUp ended");
        return new Intent(Intent.ACTION_VIEW,
                Timeline.getTimeline(TimelineType.HOME, MyAccount.EMPTY, 0, null).getUri());
    }

    @Test
    public void shouldStoreTimelinePosition1() throws InterruptedException {
        oneTimelineOpening(1);
    }

    @Test
    public void shouldStoreTimelinePosition2() throws InterruptedException {
        oneTimelineOpening(2);
    }

    @Test
    public void shouldStoreTimelinePosition3() throws InterruptedException {
        oneTimelineOpening(3);
    }

    private void oneTimelineOpening(int iteration) throws InterruptedException {
        final String method = "oneTimelineOpening" + iteration;
        TestSuite.waitForListLoaded(getActivity(), 3);

        final ListActivityTestHelper<TimelineActivity> testHelper = new ListActivityTestHelper<>(getActivity());
        int position1 = getFirstVisibleAdapterPosition();
        final BaseTimelineAdapter listAdapter = getActivity().getListAdapter();
        ViewItem item1 = listAdapter.getItem(position1);
        if (!previousItem.isEmpty()) {
            int positionOfPreviousItem = getPositionById(previousItem.getId());
            assertEquals("; previous:" + previousItem
                    + "\n  " + (positionOfPreviousItem >=0 ? "at position " + positionOfPreviousItem : "not found now")
                    + "\ncurrent:" + item1
                    + "\n  at position " + position1,
                    previousItem.getId(), item1.getId());
        }

        int nextPosition = position1 + 5 >= listAdapter.getCount() ? 0 : position1 + 5;
        testHelper.selectListPosition(method, nextPosition + getActivity().getListView().getHeaderViewsCount());
        DbUtils.waitMs(this, 2000);
        previousItem = listAdapter.getItem(getFirstVisibleAdapterPosition());
    }

    /** @return -1 if not found */
    private int getPositionById(long itemId) {
        final BaseTimelineAdapter listAdapter = getActivity().getListAdapter();
        for (int ind = 0; ind < listAdapter.getCount(); ind++) {
            if (listAdapter.getItem(ind).getId() == itemId) return ind;
        }
        return -1;
    }

    private int getFirstVisibleAdapterPosition() {
        int headers = getActivity().getListView().getHeaderViewsCount();
        return Integer.max(getActivity().getListView().getFirstVisiblePosition(), headers) - headers;
    }
}
