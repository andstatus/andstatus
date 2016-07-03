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

package org.andstatus.app.timeline;

import org.andstatus.app.R;

import java.util.Comparator;

/**
 * @author yvolk@yurivolkov.com
 */
class TimelineListViewItemComparator implements Comparator<TimelineListViewItem> {
    private final int sortByField;
    private final boolean sortDefault;

    TimelineListViewItemComparator(int sortByField, boolean sortDefault) {
        this.sortByField = sortByField;
        this.sortDefault = sortDefault;
    }

    @Override
    public int compare(TimelineListViewItem lhs, TimelineListViewItem rhs) {
        int result = 0;
        switch (sortByField) {
            case R.id.displayedInSelector:
                result = compareCheckbox(lhs.timeline.isDisplayedInSelector(), rhs.timeline.isDisplayedInSelector());
                if (result != 0) {
                    break;
                }
                result = compareLong(lhs.timeline.getSelectorOrder(), rhs.timeline.getSelectorOrder());
                if (result != 0) {
                    break;
                }
                result = lhs.timeline.getTimelineType().compareTo(rhs.timeline.getTimelineType());
                if (result != 0) {
                    break;
                }
            case R.id.title:
                result = compareString(lhs.timelineTitle.title, rhs.timelineTitle.title);
                if (result != 0) {
                    break;
                }
            case R.id.account:
                result = compareString(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName);
                if (result != 0) {
                    break;
                }
                return compareString(lhs.timelineTitle.originName, rhs.timelineTitle.originName);
            case R.id.origin:
                result = compareString(lhs.timelineTitle.originName, rhs.timelineTitle.originName);
                if (result != 0) {
                    break;
                } else {
                    result = compareString(lhs.timelineTitle.accountName, rhs.timelineTitle.accountName);
                }
                if (result != 0) {
                    break;
                } else {
                    result = compareString(lhs.timelineTitle.title, rhs.timelineTitle.title);
                }
                if (result != 0) {
                    break;
                }
            case R.id.synced:
                return compareSynced(lhs, rhs);
            case R.id.syncSucceededDate:
            case R.id.syncedTimesCount:
                result = compareDate(lhs.timeline.getSyncSucceededDate(), rhs.timeline.getSyncSucceededDate());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
                break;
            case R.id.syncFailedDate:
            case R.id.syncFailedTimesCount:
                result = compareDate(lhs.timeline.getSyncFailedDate(), rhs.timeline.getSyncFailedDate());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
                break;
            case R.id.errorMessage:
                result = compareString(lhs.timeline.getErrorMessage(), rhs.timeline.getErrorMessage());
                if (result == 0) {
                    return compareSynced(lhs, rhs);
                }
            default:
                break;
        }
        return result;
    }

    private int compareString(String lhs, String rhs) {
        int result = lhs == null ? 0 : lhs.compareTo(rhs);
        return result == 0 ? 0 : sortDefault ? result : 0 - result;
    }

    private int compareSynced(TimelineListViewItem lhs, TimelineListViewItem rhs) {
        int result = compareDate(lhs.timeline.getLastSyncedDate(), rhs.timeline.getLastSyncedDate());
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncedAutomatically(), rhs.timeline.isSyncedAutomatically());
        }
        if (result == 0) {
            result = compareCheckbox(lhs.timeline.isSyncable(), rhs.timeline.isSyncable());
        }
        return result;
    }

    private int compareDate(long lhs, long rhs) {
        int result = lhs == rhs ? 0 : lhs > rhs ? 1 : -1;
        return result == 0 ? 0 : !sortDefault ? result : 0 - result;
    }

    private int compareCheckbox(boolean lhs, boolean rhs) {
        int result = lhs == rhs ? 0 : lhs ? 1 : -1;
        return result == 0 ? 0 : !sortDefault ? result : 0 - result;
    }

    private int compareLong(long lhs, long rhs) {
        int result = lhs == rhs ? 0 : lhs > rhs ? 1 : -1;
        return result == 0 ? 0 : sortDefault ? result : 0 - result;
    }

}
