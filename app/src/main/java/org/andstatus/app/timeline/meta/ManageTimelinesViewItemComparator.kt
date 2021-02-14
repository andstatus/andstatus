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
        var result = 0
        when (sortByField) {
            R.id.displayedInSelector -> {
                result = compareAny<Timeline?>(lhs.timeline, rhs.timeline)
                if (result != 0) {
                    break
                }
                result = compareAny<String?>(lhs.timelineTitle.toString().toLowerCase(),
                        rhs.timelineTitle.toString().toLowerCase())
                if (result != 0) {
                    break
                }
                result = compareAny<String?>(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) {
                    break
                }
                return compareAny<String?>(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
            }
            R.id.title -> {
                result = compareAny<String?>(lhs.timelineTitle.toString().toLowerCase(),
                        rhs.timelineTitle.toString().toLowerCase())
                if (result != 0) {
                    break
                }
                result = compareAny<String?>(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) {
                    break
                }
                return compareAny<String?>(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
            }
            R.id.account -> {
                result = compareAny<String?>(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                if (result != 0) {
                    break
                }
                return compareAny<String?>(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
            }
            R.id.origin -> {
                result = compareAny<String?>(lhs.timelineTitle.originName, rhs.timelineTitle.originName)
                result = if (result != 0) {
                    break
                } else {
                    compareAny<String?>(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName)
                }
                result = if (result != 0) {
                    break
                } else {
                    compareAny<String?>(lhs.timelineTitle.title, rhs.timelineTitle.title)
                }
                if (result != 0) {
                    break
                }
                return compareSynced(lhs, rhs)
            }
            R.id.synced -> return compareSynced(lhs, rhs)
            R.id.syncedTimesCount -> {
                result = compareLongDescending(lhs.timeline.getSyncedTimesCount(isTotal),
                        rhs.timeline.getSyncedTimesCount(isTotal))
                if (result != 0) {
                    break
                }
                result = compareLongDescending(lhs.timeline.getDownloadedItemsCount(isTotal),
                        rhs.timeline.getDownloadedItemsCount(isTotal))
                if (result != 0) {
                    break
                }
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal))
                if (result != 0) {
                    break
                }
                result = compareLongDescending(lhs.timeline.syncSucceededDate,
                        rhs.timeline.syncSucceededDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            R.id.downloadedItemsCount -> {
                result = compareLongDescending(lhs.timeline.getDownloadedItemsCount(isTotal),
                        rhs.timeline.getDownloadedItemsCount(isTotal))
                if (result != 0) {
                    break
                }
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal))
                if (result != 0) {
                    break
                }
                result = compareLongDescending(lhs.timeline.syncSucceededDate,
                        rhs.timeline.syncSucceededDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            R.id.newItemsCount -> {
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal))
                if (result != 0) {
                    break
                }
                result = compareLongDescending(lhs.timeline.syncSucceededDate,
                        rhs.timeline.syncSucceededDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            R.id.syncSucceededDate -> {
                result = compareLongDescending(lhs.timeline.syncSucceededDate,
                        rhs.timeline.syncSucceededDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            R.id.syncFailedDate, R.id.syncFailedTimesCount -> {
                result = compareLongDescending(lhs.timeline.syncFailedDate,
                        rhs.timeline.syncFailedDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            R.id.errorMessage -> {
                result = compareAny<String?>(lhs.timeline.errorMessage, rhs.timeline.errorMessage)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
                result = compareLongDescending(lhs.timeline.lastChangedDate,
                        rhs.timeline.lastChangedDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            R.id.lastChangedDate -> {
                result = compareLongDescending(lhs.timeline.lastChangedDate,
                        rhs.timeline.lastChangedDate)
                if (result == 0) {
                    return compareSynced(lhs, rhs)
                }
            }
            else -> {
            }
        }
        return result
    }

    private fun <T : Comparable<T?>?> compareAny(lhs: T?, rhs: T?): Int {
        val result = lhs?.compareTo(rhs) ?: 0
        return if (result == 0) 0 else if (sortDefault) result else 0 - result
    }

    private fun compareSynced(lhs: ManageTimelinesViewItem?, rhs: ManageTimelinesViewItem?): Int {
        var result = compareLongDescending(lhs.timeline.lastSyncedDate,
                rhs.timeline.lastSyncedDate)
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncedAutomatically,
                    rhs.timeline.isSyncedAutomatically)
        }
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncableAutomatically, rhs.timeline.isSyncableAutomatically)
        }
        return result
    }

    private fun compareLongDescending(lhs: Long, rhs: Long): Int {
        val result = if (lhs == rhs) 0 else if (lhs > rhs) 1 else -1
        return if (result == 0) 0 else if (!sortDefault) result else 0 - result
    }

    private fun compareCheckbox(lhs: Boolean?, rhs: Boolean?): Int {
        val result = CollectionsUtil.compareCheckbox(lhs, rhs)
        return if (result == 0) 0 else if (sortDefault) result else 0 - result
    }
}