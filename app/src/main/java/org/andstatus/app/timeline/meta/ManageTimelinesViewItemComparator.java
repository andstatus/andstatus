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

package org.andstatus.app.timeline.meta;

import org.andstatus.app.R;
import org.andstatus.app.util.CollectionsUtil;

import java.util.Comparator;

/**
 * @author yvolk@yurivolkov.com
 */
class ManageTimelinesViewItemComparator implements Comparator<ManageTimelinesViewItem> {
    private final int sortByField;
    private final boolean sortDefault;
    private final boolean isTotal;

    ManageTimelinesViewItemComparator(int sortByField, boolean sortDefault, boolean isTotal) {
        this.sortByField = sortByField;
        this.sortDefault = sortDefault;
        this.isTotal = isTotal;
    }

    @Override
    public int compare(ManageTimelinesViewItem lhs, ManageTimelinesViewItem rhs) {
        int result = 0;
        switch (sortByField) {
            case R.id.displayedInSelector:
                result = compareAny(lhs.timeline, rhs.timeline);
                if (result != 0) {
                    break;
                }
            case R.id.title:
                result = compareAny(lhs.timelineTitle.title, rhs.timelineTitle.title);
                if (result != 0) {
                    break;
                }
            case R.id.account:
                result = compareAny(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName);
                if (result != 0) {
                    break;
                }
                return compareAny(lhs.timelineTitle.originName, rhs.timelineTitle.originName);
            case R.id.origin:
                result = compareAny(lhs.timelineTitle.originName, rhs.timelineTitle.originName);
                if (result != 0) {
                    break;
                } else {
                    result = compareAny(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName);
                }
                if (result != 0) {
                    break;
                } else {
                    result = compareAny(lhs.timelineTitle.title, rhs.timelineTitle.title);
                }
                if (result != 0) {
                    break;
                }
            case R.id.synced:
                return compareSynced(lhs, rhs);
            case R.id.syncedTimesCount:
                result = compareLongDescending(lhs.timeline.getSyncedTimesCount(isTotal),
                        rhs.timeline.getSyncedTimesCount(isTotal));
                if (result != 0) {
                    break;
                }
            case R.id.downloadedItemsCount:
                result = compareLongDescending(lhs.timeline.getDownloadedItemsCount(isTotal),
                        rhs.timeline.getDownloadedItemsCount(isTotal));
                if (result != 0) {
                    break;
                }
            case R.id.newItemsCount:
                result = compareLongDescending(lhs.timeline.getNewItemsCount(isTotal),
                        rhs.timeline.getNewItemsCount(isTotal));
                if (result != 0) {
                    break;
                }
            case R.id.syncSucceededDate:
                result = compareLongDescending(lhs.timeline.getSyncSucceededDate(),
                        rhs.timeline.getSyncSucceededDate());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
                break;
            case R.id.syncFailedDate:
            case R.id.syncFailedTimesCount:
                result = compareLongDescending(lhs.timeline.getSyncFailedDate(),
                        rhs.timeline.getSyncFailedDate());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
                break;
            case R.id.errorMessage:
                result = compareAny(lhs.timeline.getErrorMessage(), rhs.timeline.getErrorMessage());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
            case R.id.lastChangedDate:
                result = compareLongDescending(lhs.timeline.getLastChangedDate(),
                        rhs.timeline.getLastChangedDate());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
                break;
            default:
                break;
        }
        return result;
    }

    private <T extends Comparable<T>> int compareAny(T lhs, T rhs) {
        int result = lhs == null ? 0 : lhs.compareTo(rhs);
        return result == 0 ? 0 : sortDefault ? result : 0 - result;
    }

    private int compareSynced(ManageTimelinesViewItem lhs, ManageTimelinesViewItem rhs) {
        int result = compareLongDescending(lhs.timeline.getLastSyncedDate(),
                rhs.timeline.getLastSyncedDate());
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncedAutomatically(),
                    rhs.timeline.isSyncedAutomatically());
        }
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncableAutomatically(), rhs.timeline.isSyncableAutomatically());
        }
        return result;
    }

    private int compareLongDescending(long lhs, long rhs) {
        int result = lhs == rhs ? 0 : lhs > rhs ? 1 : -1;
        return result == 0 ? 0 : !sortDefault ? result : 0 - result;
    }

    private int compareCheckbox(Boolean lhs, Boolean rhs) {
        int result = CollectionsUtil.compareCheckbox(lhs, rhs);
        return result == 0 ? 0 : sortDefault ? result : 0 - result;
    }
}
