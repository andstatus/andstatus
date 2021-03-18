/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline.meta

import org.andstatus.app.R
import org.andstatus.app.util.CollectionsUtil
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
internal class ManageTimelinesViewItemComparator(private val sortByField: Int, private val sortDefault: Boolean, private val isTotal: Boolean) : Comparator<ManageTimelinesViewItem?> {

    override fun compare(lhs: ManageTimelinesViewItem?, rhs: ManageTimelinesViewItem?): Int {
        if (lhs == null || rhs == null) return compareNulls(lhs, rhs)
        var result = 0
        when (sortByField) {
            R.id.displayedInSelector -> {
                result = compareAny(lhs.timeline, rhs.timeline)
                if (result != 0) return result
                result = compareAny(lhs.timelineTitle.toString().toLowerCase(),
                        rhs.timelineTitle.toString().toLowerCase())
                if (result != 0) return result
                result = compareAny(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) return result
                return compareAny(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
            }
            R.id.title -> {
                result = compareAny(lhs.timelineTitle.toString().toLowerCase(),
                        rhs.timelineTitle.toString().toLowerCase())
                if (result != 0) return result
                result = compareAny(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) return result
                return compareAny(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
            }
            R.id.account -> {
                result = compareAny(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) return result
                return compareAny(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
            }
            R.id.origin -> {
                result = compareAny(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
                if (result != 0) return result
                result = compareAny(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) return result

                result = compareAny(lhs.timelineTitle.title, rhs.timelineTitle.title)
                if (result != 0) return result

                return compareSynced(lhs, rhs)
            }
            R.id.synced -> return compareSynced(lhs, rhs)
            R.id.syncedTimesCount -> {
                result = compareLongDescending(lhs.timeline.getSyncedTimesCount(isTotal),
                        rhs.timeline.getSyncedTimesCount(isTotal))
                if (result != 0) return result
                result = compareLongDescending(lhs.timeline.getDownloadedItemsCount(isTotal),
                        rhs.timeline.getDownloadedItemsCount(isTotal))
                if (result != 0) return result
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal))
                if (result != 0) return result
                result = compareLongDescending(lhs.timeline.getSyncSucceededDate(),
                        rhs.timeline.getSyncSucceededDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            R.id.downloadedItemsCount -> {
                result = compareLongDescending(lhs.timeline.getDownloadedItemsCount(isTotal),
                        rhs.timeline.getDownloadedItemsCount(isTotal))
                if (result != 0) return result
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal))
                if (result != 0) return result
                result = compareLongDescending(lhs.timeline.getSyncSucceededDate(),
                        rhs.timeline.getSyncSucceededDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            R.id.newItemsCount -> {
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal))
                if (result != 0) return result
                result = compareLongDescending(lhs.timeline.getSyncSucceededDate(),
                        rhs.timeline.getSyncSucceededDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            R.id.syncSucceededDate -> {
                result = compareLongDescending(lhs.timeline.getSyncSucceededDate(),
                        rhs.timeline.getSyncSucceededDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            R.id.syncFailedDate, R.id.syncFailedTimesCount -> {
                result = compareLongDescending(lhs.timeline.getSyncFailedDate(),
                        rhs.timeline.getSyncFailedDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            R.id.errorMessage -> {
                result = compareAny(lhs.timeline.getErrorMessage(), rhs.timeline.getErrorMessage())
                // TODO: Strange logic: no return here on != 0
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
                result = compareLongDescending(lhs.timeline.getLastChangedDate(),
                        rhs.timeline.getLastChangedDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            R.id.lastChangedDate -> {
                result = compareLongDescending(lhs.timeline.getLastChangedDate(),
                        rhs.timeline.getLastChangedDate())
                if (result != 0) return result
                return compareSynced(lhs, rhs)
            }
            else -> return result
        }
    }

    private fun <T : Comparable<T>> compareAny(lhs: T?, rhs: T?): Int {
        if (lhs == null || rhs == null)
            return compareNulls(lhs, rhs)
        else {
            val result = lhs.compareTo(rhs)
            return if (result == 0) 0 else if (sortDefault) result else 0 - result
        }
    }

    private fun <T> compareNulls(lhs: T?, rhs: T?): Int {
        if (lhs == null && rhs == null) return 0
        if (rhs == null) return if (sortDefault) 1 else -1
        if (lhs == null) return if (sortDefault) -1 else 1
        return 0
    }

    private fun compareSynced(lhs: ManageTimelinesViewItem, rhs: ManageTimelinesViewItem): Int {
        var result = compareLongDescending(lhs.timeline.getLastSyncedDate(),
                rhs.timeline.getLastSyncedDate())
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncedAutomatically(),
                    rhs.timeline.isSyncedAutomatically())
        }
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncableAutomatically(), rhs.timeline.isSyncableAutomatically())
        }
        return result
    }

    private fun compareLongDescending(lhs: Long, rhs: Long): Int {
        val result = if (lhs == rhs) 0 else if (lhs > rhs) 1 else -1
        return if (result == 0) 0 else if (!sortDefault) result else 0 - result
    }

    private fun compareCheckbox(lhs: Boolean, rhs: Boolean): Int {
        val result = CollectionsUtil.compareCheckbox(lhs, rhs)
        return if (result == 0) 0 else if (sortDefault) result else 0 - result
    }
}