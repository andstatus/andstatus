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
package org.andstatus.app.timeline

import android.view.View
import android.widget.ListView
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TaggedClass

class LoadableListPosition<T : ViewItem<T>> private constructor(
        val itemId: Long, val y: Int, val position: Int,
        val minSentDate: Long, val description: String?) : IsEmpty, TaggedClass {

    override val isEmpty: Boolean
        get() {
            return itemId == 0L
        }

    fun logV(description: String?): LoadableListPosition<T> {
        MyLog.v(TAG) { description + "; " + this.description }
        return this
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, description)
    }

    override fun classTag(): String {
        return TAG
    }

    companion object {
        private val TAG: String = LoadableListPosition::class.java.simpleName
        val EMPTY = saved(0, 0, 0, "(empty position)")

        private fun current(itemId: Long, y: Int, position: Int, minSentDate: Long, description: String?): LoadableListPosition<*> {
            return LoadableListPosition<EmptyViewItem>(itemId, y, position, minSentDate, description)
        }

        fun saved(itemId: Long, y: Int, minSentDate: Long, description: String?): LoadableListPosition<*> {
            return LoadableListPosition<EmptyViewItem>(itemId, y, 0, minSentDate, description)
        }

        fun getCurrent(list: ListView, adapter: BaseTimelineAdapter<*>, itemIdDefault: Long): LoadableListPosition<*> {
            val firstVisiblePosition = Integer.min(
                    Integer.max(list.getFirstVisiblePosition(), 0),
                    Integer.max(adapter.getCount() - 1, 0)
            )
            var position = firstVisiblePosition
            var viewOfPosition = getViewOfPosition(list, firstVisiblePosition)
            if (viewOfPosition == null && position > 0) {
                position = firstVisiblePosition - 1
                viewOfPosition = getViewOfPosition(list, position)
            }
            if (viewOfPosition == null) {
                position = Math.max(firstVisiblePosition + 1, 0)
                viewOfPosition = getViewOfPosition(list, position)
            }
            if (viewOfPosition == null) {
                position = adapter.getPositionById(itemIdDefault)
            }
            val itemIdFound = if (viewOfPosition == null) 0 else adapter.getItemId(position)
            var itemId = if (itemIdFound == 0L) itemIdDefault else itemIdFound
            val y = if (itemIdFound == 0L || viewOfPosition == null) 0 else viewOfPosition.getTop() - list.getPaddingTop()
            val lastPosition = Integer.min(list.getLastVisiblePosition() + 10, adapter.getCount() - 1)
            val minDate = adapter.getItem(lastPosition)?.getDate() ?: 0
            var description = ""
            if (itemId <= 0 && minDate > 0) {
                description = "from lastPosition, "
                position = lastPosition
                itemId = adapter.getItemId(lastPosition)
            }
            description = (description + "currentPosition:" + position
                    + ", firstVisiblePos:" + firstVisiblePosition
                    + "; viewsInList:" + list.getChildCount()
                    + ", headers:" + list.getHeaderViewsCount()
                    + (if (viewOfPosition == null) ", view not found" else ", y:$y")
                    + "; items:" + adapter.getCount()
                    + ", itemId:" + itemId + " defaultId:" + itemIdDefault)
            return current(itemId, y, position, minDate, description)
        }

        fun getViewOfPosition(list: ListView?, position: Int): View? {
            list ?: return null
            var viewOfPosition: View? = null
            for (ind in 0 until list.getChildCount()) {
                val view = list.getChildAt(ind)
                val positionForView = list.getPositionForView(view)
                if (MyLog.isVerboseEnabled()) {
                    MyLog.v(TAG) { "getViewOfPosition $position, ind $ind => $positionForView" }
                }
                if (positionForView == position) {
                    viewOfPosition = view
                    break
                }
            }
            return viewOfPosition
        }

        fun <T : ViewItem<T>> restore(list: ListView, adapter: BaseTimelineAdapter<T>,
                                        pos: LoadableListPosition<*>): Boolean {
            var position = -1
            try {
                if (pos.itemId > 0) {
                    position = adapter.getPositionById(pos.itemId)
                }
                if (position >= 0) {
                    list.setSelectionFromTop(position, pos.y)
                    return true
                } else {
                    // There is no stored position - starting from the Top
                    position = 0
                    setPosition(list, position)
                }
            } catch (e: Exception) {
                MyLog.v(TAG, "restore $pos", e)
            }
            return false
        }

        fun setPosition(listView: ListView?, position: Int) {
            if (listView == null) {
                return
            }
            val viewHeight = listView.height
            val childHeight = 30
            val y = if (position == 0) 0 else viewHeight - childHeight
            val headerViewsCount = listView.headerViewsCount
            MyLog.v(TAG) {
                "Set item at position " + position + " to " + y + " px," +
                        " header views: " + headerViewsCount
            }
            listView.setSelectionFromTop(position, y)
        }
    }
}