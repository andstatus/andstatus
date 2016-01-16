/*
 * Copyright (c) 2015 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.msg;

import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelinePages {
    private static final int MAX_PAGES_COUNT = 5;
    final List<TimelinePage> list;

    public TimelinePages(TimelinePages oldPages, TimelinePage thisPage) {
        this.list = isSameTimeline(oldPages, thisPage) ? oldPages.list : new ArrayList<TimelinePage>();
        if (thisPage != null) {
            addThisPage(thisPage);
            dropExcessivePage(thisPage);
        }
    }

    private void dropExcessivePage(TimelinePage lastLoadedPage) {
        if (list.size() > MAX_PAGES_COUNT) {
            if (lastLoadedPage.parameters.whichPage == WhichTimelinePage.YOUNGER) {
                list.remove(list.size() - 1);
            } else {
                list.remove(0);
            }
        }
    }

    private boolean isSameTimeline(TimelinePages oldPages, TimelinePage thisPage) {
        if (oldPages == null) {
            return false;
        }
        if (thisPage == null) {
            return true;
        }
        return oldPages.list.size() > 0
                && thisPage.parameters.mContentUri.equals(oldPages.list.get(0).parameters.mContentUri);
    }

    private void addThisPage(TimelinePage page) {
        // TODO: check if loaded messages duplicate messages in adjacent pages
        switch (page.parameters.whichPage) {
            case OLDER:
                removeDuplicatesWithYounger(page, list.size() - 1);
                list.add(page);
                break;
            case YOUNGER:
                removeDuplicatesWithOlder(page, 0);
                list.add(0, page);
                break;
            default:
                if (page.parameters.whichPage == WhichTimelinePage.NEW || list.size() < 2) {
                    list.clear();
                    list.add(page);
                } else {
                    int found = -1;
                    for (int ind = 0; ind < list.size(); ind++) {
                        TimelinePage p = list.get(ind);
                        if (p.parameters.maxSentDate == page.parameters.maxSentDate
                                && p.parameters.minSentDate == page.parameters.minSentDate) {
                            found = ind;
                            break;
                        }
                    }
                    if (found >= 0) {
                        removeDuplicatesWithYounger(page, found - 1);
                        removeDuplicatesWithOlder(page, found + 1);
                        list.remove(found);
                        list.add(found, page);
                    } else {
                        list.add(page);
                    }
                }
                break;
        }
    }

    private void removeDuplicatesWithYounger(TimelinePage page, int indExistingPage) {
        if (indExistingPage < 0 || indExistingPage >= list.size()
                || list.get(indExistingPage).items.isEmpty() || page.items.isEmpty()) {
            return;
        }
        TimelinePage ePage = list.get(indExistingPage);
        if (page.parameters.maxSentDate >= ePage.parameters.maxSentDate) {
            MyLog.v(this, "Previous younger page removed");
            list.remove(indExistingPage);
            return;
        }
        long edgeDate =  ePage.parameters.minSentDateLoaded;
        List<TimelineViewItem> toRemove = new ArrayList<>();
        for (int ind = 0; ind < page.items.size(); ind++) {
            TimelineViewItem item = page.items.get(ind);
            if (item.sentDate < edgeDate) {
                break;
            } else if (item.sentDate > edgeDate) {
                MyLog.e(this, "This page has an item younger than on a younger page: " + item);
                toRemove.add(item);
            } else {
                for (int eInd = ePage.items.size() - 1; eInd >= 0; eInd--) {
                    TimelineViewItem eItem = ePage.items.get(eInd);
                    if (eItem.sentDate > item.sentDate) {
                        break;
                    }
                    if (eItem.msgId == item.msgId) {
                        mergeWithExisting(item, eItem);
                        toRemove.add(item);
                        break;
                    }
                }
            }
        }
        page.items.removeAll(toRemove);
    }

    private void mergeWithExisting(TimelineViewItem newItem, TimelineViewItem existingItem) {
        // TODO: Merge something...
    }

    private void removeDuplicatesWithOlder(TimelinePage page, int indExistingPage) {
        if (indExistingPage < 0 || indExistingPage >= list.size()
                || list.get(indExistingPage).items.isEmpty() || page.items.isEmpty()) {
            return;
        }
        TimelinePage ePage = list.get(indExistingPage);
        if (page.parameters.minSentDate <= ePage.parameters.minSentDate) {
            MyLog.v(this, "Previous older page removed");
            list.remove(indExistingPage);
            return;
        }
        long edgeDate = ePage.parameters.maxSentDateLoaded;
        List<TimelineViewItem> toRemove = new ArrayList<>();
        for (int ind = page.items.size() - 1; ind >= 0; ind--) {
            TimelineViewItem item = page.items.get(ind);
            if ( item.sentDate > edgeDate) {
                break;
            } else if (item.sentDate < edgeDate) {
                MyLog.e(this, "This page has an item older than on an older page: " + item);
                toRemove.add(item);
            } else {
                for (int eInd = 0; eInd < ePage.items.size(); eInd++) {
                    TimelineViewItem eItem = ePage.items.get(eInd);
                    if (eItem.sentDate < item.sentDate) {
                        break;
                    }
                    if (eItem.msgId == item.msgId) {
                        mergeWithExisting(item, eItem);
                        toRemove.add(item);
                        break;
                    }
                }
            }
        }
        page.items.removeAll(toRemove);
    }

    public int getCount() {
        int count = 0;
        for (TimelinePage page : list) {
            count += page.items.size();
        }
        return count;
    }

    public TimelineViewItem getItem(int position) {
        int firstPosition = 0;
        for (TimelinePage page : list) {
            if (position < firstPosition) {
                break;
            }
            if (position < firstPosition + page.items.size()) {
                return page.items.get(position - firstPosition);
            }
            firstPosition += page.items.size();
        }
        return TimelineViewItem.getEmpty();
    }

    public boolean mayHaveYoungerPage() {
        return list.size() == 0 || list.get(0).parameters.mayHaveYoungerPage();
    }

    public boolean mayHaveOlderPage() {
        return list.size() == 0 || list.get(list.size() - 1).parameters.mayHaveOlderPage();
    }
}
