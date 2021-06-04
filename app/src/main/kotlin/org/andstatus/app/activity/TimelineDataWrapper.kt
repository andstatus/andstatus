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
package org.andstatus.app.activity

import org.andstatus.app.timeline.LoadableListViewParameters
import org.andstatus.app.timeline.TimelineData
import org.andstatus.app.timeline.TimelinePage
import org.andstatus.app.timeline.ViewItem

internal abstract class TimelineDataWrapper<T : ViewItem<T>>(val listData: TimelineData<ActivityViewItem>) :
        TimelineData<T>(null, TimelinePage<T>(listData.params, null)) {
    override fun size(): Int {
        return listData.size()
    }

    abstract override fun getItem(position: Int): T

    override fun getById(itemId: Long): T {
        val position = getPositionById(itemId)
        return if (position < 0) {
            pages[0].getEmptyItem()
        } else getItem(position)
    }

    abstract override fun getPositionById(itemId: Long): Int
    override fun mayHaveYoungerPage(): Boolean {
        return listData != null && listData.mayHaveYoungerPage()
    }

    override fun mayHaveOlderPage(): Boolean {
        return listData.mayHaveOlderPage()
    }

    override fun toString(): String {
        return listData.toString()
    }

    override fun isCollapseDuplicates(): Boolean {
        return listData != null && listData.isCollapseDuplicates()
    }

    override fun canBeCollapsed(position: Int): Boolean {
        return listData.canBeCollapsed(position)
    }

    override fun updateView(viewParameters: LoadableListViewParameters) {
        // Empty
    }
}
