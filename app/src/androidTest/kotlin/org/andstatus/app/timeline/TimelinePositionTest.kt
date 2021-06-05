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
package org.andstatus.app.timeline

import android.content.Intent
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.context.TestSuite
import org.andstatus.app.data.DbUtils
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.MyLog
import org.junit.Assert
import org.junit.Test

class TimelinePositionTest : TimelineActivityTest<ActivityViewItem>() {

    override fun getActivityIntent(): Intent {
        MyLog.i(this, "setUp started")
        TestSuite.initializeWithData(this)
        MyLog.i(this, "setUp ended")
        return Intent(Intent.ACTION_VIEW,
                 MyContextHolder.myContextHolder.getNow().timelines.get(TimelineType.HOME, Actor.EMPTY,  Origin.EMPTY).getUri())
    }

    @Test
    fun shouldStoreTimelinePosition1() {
        oneTimelineOpening(1)
    }

    @Test
    fun shouldStoreTimelinePosition2() {
        oneTimelineOpening(2)
    }

    @Test
    fun shouldStoreTimelinePosition3() {
        oneTimelineOpening(3)
    }

    private fun oneTimelineOpening(iteration: Int) {
        val method = "oneTimelineOpening$iteration"
        TestSuite.waitForListLoaded(activity, 3)
        val testHelper = ListScreenTestHelper<TimelineActivity<*>>(activity)
        val position1 = getFirstVisibleAdapterPosition()
        val listAdapter: BaseTimelineAdapter<*> = activity.getListAdapter()
        val item1 = listAdapter.getItem(position1)
        if (previousItem.nonEmpty) {
            val previousItemPosition = listAdapter.getPositionById(previousItem.getId())
            Assert.assertEquals("""; previous:$previousItem
  ${if (previousItemPosition >= 0) "at position $previousItemPosition" else "not found now"}
current:$item1
  at position $position1""",
                    previousItem.getId(), item1.getId())
        }
        val nextPosition = if (position1 + 5 >= listAdapter.count) 0 else position1 + 5
        testHelper.selectListPosition(method, nextPosition + (activity.listView?.headerViewsCount ?: 0))
        DbUtils.waitMs(this, 2000)
        previousItem = listAdapter.getItem(getFirstVisibleAdapterPosition())
    }

    private fun getFirstVisibleAdapterPosition(): Int {
        val headers = activity.listView?.headerViewsCount ?: 0
        return Integer.max(activity.listView?.firstVisiblePosition ?: 0, headers) - headers
    }

    companion object {
        @Volatile
        private var previousItem: ViewItem<*> = EmptyViewItem.EMPTY
    }
}
