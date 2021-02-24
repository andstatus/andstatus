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
package org.andstatus.app.timeline

import android.widget.ListView
import org.andstatus.app.util.MyLog

/**
 * Determines where to save / retrieve position in the list
 * Information on two rows is stored for each "position" hence two keys.
 * Plus Query string is being stored for the search results.
 * 2014-11-15 We are storing [ViewItem.getDate] for the last item to retrieve, not its ID as before
 * @author yvolk@yurivolkov.com
 */
internal class TimelineViewPositionStorage<T : ViewItem<T?>?>(private val activity: LoadableListActivity<T?>?, listParameters: TimelineParameters?) {
    private val adapter: BaseTimelineAdapter<T?>?
    private val listView: ListView?
    private val params: TimelineParameters?
    fun save() {
        val method = "save" + params.timeline.id
        if (isEmpty()) {
            MyLog.v(TAG) { "$method; skipped" }
            return
        }
        val itemCount = adapter.getCount()
        val firstVisibleAdapterPosition = Integer.min(
                Integer.max(listView.getFirstVisiblePosition(), 0),
                itemCount - 1)
        val pos = activity.getCurrentListPosition()
        val lastPosition = Integer.min(listView.getLastVisiblePosition() + 10, itemCount - 1)
        val minDate = adapter.getItem(lastPosition).getDate()
        if (pos.itemId > 0) {
            saveListPosition(pos.itemId, minDate, pos.y)
        } else if (minDate > 0) {
            saveListPosition(0, minDate, 0)
        } else {
            // Don'r clear!
        }
        if (pos.itemId <= 0 || MyLog.isVerboseEnabled()) {
            val msgLog = ("id:" + pos.itemId
                    + ", y:" + pos.y
                    + " at pos=" + firstVisibleAdapterPosition
                    + (if (pos.position != firstVisibleAdapterPosition) " found pos=" + pos.position else "")
                    + (if (!pos.description.isNullOrEmpty()) ", description=" + pos.description else "")
                    + ", minDate=" + MyLog.formatDateTime(minDate)
                    + " at pos=" + lastPosition + " of " + itemCount
                    + ", listViews=" + listView.getCount()
                    + "; " + params.getTimeline())
            if (pos.itemId <= 0) {
                MyLog.i(TAG, "$method; failed $msgLog")
            } else {
                MyLog.v(TAG) { "$method; succeeded $msgLog" }
            }
        }
    }

    private fun isEmpty(): Boolean {
        return listView == null || adapter == null || params.isEmpty() || adapter.count == 0
    }

    private fun saveListPosition(firstVisibleItemId: Long, minDate: Long, y: Int) {
        params.getTimeline().visibleItemId = firstVisibleItemId
        params.getTimeline().visibleOldestDate = minDate
        params.getTimeline().visibleY = y
    }

    fun clear() {
        params.getTimeline().visibleItemId = 0
        params.getTimeline().visibleOldestDate = 0
        params.getTimeline().visibleY = 0
        MyLog.v(TAG) { "Position forgot " + params.getTimeline() }
    }

    /**
     * Restore (the first visible item) position saved for this timeline
     * see http://stackoverflow.com/questions/3014089/maintain-save-restore-scroll-position-when-returning-to-a-listview?rq=1
     */
    fun restore() {
        val method = "restore" + params.timeline.id
        if (isEmpty()) {
            MyLog.v(TAG) { "$method; skipped" }
            return
        }
        val pos = loadListPosition(params)
        val restored: Boolean = LoadableListPosition.Companion.restore(listView, adapter, pos)
        if (MyLog.isVerboseEnabled()) {
            pos.logV(method + "; stored " + (if (restored) "succeeded" else "failed") + " " + params.getTimeline())
            activity.getCurrentListPosition().logV(method + "; actual " + if (restored) "succeeded" else "failed")
        }
        if (!restored) clear()
        adapter.setPositionRestored(true)
    }

    companion object {
        private val TAG: String? = TimelineViewPositionStorage::class.java.simpleName
        fun loadListPosition(params: TimelineParameters?): LoadableListPosition<*>? {
            val itemId = params.getTimeline().visibleItemId
            return if (itemId > 0) LoadableListPosition.Companion.saved(
                    itemId,
                    params.getTimeline().visibleY,
                    params.getTimeline().visibleOldestDate, "saved itemId:$itemId") else LoadableListPosition.Companion.EMPTY
        }
    }

    init {
        adapter = activity.getListAdapter()
        listView = activity.getListView()
        params = listParameters
    }
}