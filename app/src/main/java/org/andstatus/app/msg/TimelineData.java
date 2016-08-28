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

import android.support.annotation.NonNull;
import android.support.v4.util.Pair;

import org.andstatus.app.WhichPage;
import org.andstatus.app.list.ListData;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineData extends ListData {
    private static final int MAX_PAGES_COUNT = 5;
    final List<TimelinePage> pages;
    final long updatedAt = MyLog.uniqueCurrentTimeMS();
    final TimelineListParameters params;
    final boolean isSameTimeline;

    public TimelineData(TimelineData oldData, @NonNull TimelinePage thisPage) {
        super(oldData);
        this.params = thisPage.params;
        isSameTimeline = oldData != null &&
                params.getContentUri().equals(oldData.params.getContentUri());
        this.pages = isSameTimeline ? copyPages(oldData.pages) : new ArrayList<TimelinePage>();
        addThisPage(thisPage);
        collapseDuplicates(isCollapseDuplicates(), 0);
        dropExcessivePage(thisPage);
    }

    private List<TimelinePage> copyPages(List<TimelinePage> pages) {
        ArrayList<TimelinePage> copiedPages = new ArrayList<>();
        for (TimelinePage page : pages) {
            copiedPages.add(page);
        }
        return copiedPages;
    }

    private void dropExcessivePage(TimelinePage lastLoadedPage) {
        if (pages.size() > MAX_PAGES_COUNT) {
            if (lastLoadedPage.params.whichPage == WhichPage.YOUNGER) {
                pages.remove(pages.size() - 1);
            } else {
                pages.remove(0);
            }
        }
    }

    private void addThisPage(TimelinePage page) {
        switch (page.params.whichPage) {
            case YOUNGEST:
                if (!mayHaveYoungerPage()) {
                    removeDuplicatesWithOlder(page, 1);
                    pages.remove(0);
                    pages.add(0, page);
                    break;
                }
            case CURRENT:
            case TOP:
                pages.clear();
                pages.add(page);
                break;
            case OLDER:
                removeDuplicatesWithYounger(page, pages.size() - 1);
                pages.add(page);
                break;
            case YOUNGER:
                removeDuplicatesWithOlder(page, 0);
                pages.add(0, page);
                break;
            default:
                if (pages.size() < 2) {
                    pages.clear();
                    pages.add(page);
                } else {
                    int found = -1;
                    for (int ind = 0; ind < pages.size(); ind++) {
                        TimelinePage p = pages.get(ind);
                        if (p.params.maxSentDate == page.params.maxSentDate
                                && p.params.minSentDate == page.params.minSentDate) {
                            found = ind;
                            break;
                        }
                    }
                    if (found >= 0) {
                        removeDuplicatesWithYounger(page, found - 1);
                        removeDuplicatesWithOlder(page, found + 1);
                        pages.remove(found);
                        pages.add(found, page);
                    } else {
                        pages.add(page);
                    }
                }
                break;
        }
    }

    private void removeDuplicatesWithYounger(TimelinePage page, int indExistingPage) {
        if (indExistingPage < 0 || indExistingPage >= pages.size()
                || pages.get(indExistingPage).items.isEmpty() || page.items.isEmpty()) {
            return;
        }
        TimelinePage ePage = pages.get(indExistingPage);
        if (ePage.params.maxSentDate > 0 && page.params.maxSentDate >= ePage.params.maxSentDate) {
            MyLog.v(this, "Previous younger page removed");
            pages.remove(indExistingPage);
            return;
        }
        long edgeDate =  ePage.params.minSentDateLoaded;
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
                    if (eItem.getMsgId() == item.getMsgId()) {
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
        if (indExistingPage < 0 || indExistingPage >= pages.size()
                || pages.get(indExistingPage).items.isEmpty() || page.items.isEmpty()) {
            return;
        }
        TimelinePage ePage = pages.get(indExistingPage);
        if (page.params.minSentDate <= ePage.params.minSentDate) {
            MyLog.v(this, "Previous older page removed");
            pages.remove(indExistingPage);
            return;
        }
        long edgeDate = ePage.params.maxSentDateLoaded;
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
                    if (eItem.getMsgId() == item.getMsgId()) {
                        mergeWithExisting(item, eItem);
                        toRemove.add(item);
                        break;
                    }
                }
            }
        }
        page.items.removeAll(toRemove);
    }

    @Override
    public int size() {
        int count = 0;
        for (TimelinePage page : pages) {
            count += page.items.size();
        }
        return count;
    }

    @Override
    public TimelineViewItem getItem(int position) {
        int firstPosition = 0;
        for (TimelinePage page : pages) {
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
        return pages.size() == 0 || pages.get(0).params.mayHaveYoungerPage();
    }

    public boolean mayHaveOlderPage() {
        return pages.size() == 0 || pages.get(pages.size() - 1).params.mayHaveOlderPage();
    }

    @Override
    public String toString() {
        String s = "pages:" + pages.size() + ", total items:" + size() + ",";
        for (TimelinePage page : pages) {
            s += "\nPage size:" + page.items.size() + ", params: " + page.params + ",";
        }
        return MyLog.formatKeyValue(this, s );
    }

    /** For all or for only one item */
    @Override
    public void collapseDuplicates(boolean collapse, long itemId) {
        super.collapseDuplicates(collapse, itemId);
        if (collapse) {
            collapseDuplicates(itemId);
        } else {
            showDuplicates(itemId);
        }
    }

    private void collapseDuplicates(long itemId) {
        Set<Pair<TimelinePage, TimelineViewItem>> toCollapse = new HashSet<>();
        innerCollapseDuplicates(itemId, toCollapse);
        for (Pair<TimelinePage, TimelineViewItem> pair : toCollapse) {
            pair.first.items.remove(pair.second);
        }
    }

    private void innerCollapseDuplicates(long itemId, Collection<Pair<TimelinePage, TimelineViewItem>> toCollapse) {
        Pair<TimelinePage, TimelineViewItem> parent = new Pair<>(null, null);
        Set<Pair<TimelinePage, TimelineViewItem>> group = new HashSet<>();
        for (TimelinePage page : pages) {
            for (TimelineViewItem item : page.items) {
                Pair<TimelinePage, TimelineViewItem> itemPair =new Pair<>(page, item);
                switch (item.duplicates(parent.second)) {
                    case DUPLICATES:
                        break;
                    case IS_DUPLICATED:
                        parent = itemPair;
                        break;
                    default:
                        if (collapseThisGroup(itemId, parent, group, toCollapse)) {
                            return;
                        }
                        group.clear();
                        parent = itemPair;
                        break;
                }
                group.add(itemPair);
            }
        }
        collapseThisGroup(itemId, parent, group, toCollapse);
    }

    private boolean collapseThisGroup(long itemId, Pair<TimelinePage, TimelineViewItem> parent, Set<Pair<TimelinePage, TimelineViewItem>> group, Collection<Pair<TimelinePage, TimelineViewItem>> toCollapse) {
        if (group.isEmpty()) {
            return false;
        }

        boolean groupOfSelectedItem = false;
        if (itemId != 0) {
            for (Pair<TimelinePage, TimelineViewItem> itemPair : group) {
                if (itemId == itemPair.second.getMsgId()) {
                    groupOfSelectedItem = true;
                    break;
                }
            }
        }
        if (groupOfSelectedItem) {
            for (Pair<TimelinePage, TimelineViewItem> itemPair : group) {
                setIndividualCollapsedStatus(true, itemPair.second.getMsgId());
            }
        }

        boolean hasIndividualCollapseState = false;
        if (!groupOfSelectedItem && !individualCollapsedStateIds.isEmpty()) {
            for (Pair<TimelinePage, TimelineViewItem> itemPair : group) {
                if (individualCollapsedStateIds.contains(itemPair.second.getMsgId())) {
                    hasIndividualCollapseState = true;
                    break;
                }
            }
        }
        if (!hasIndividualCollapseState) {
            for (Pair<TimelinePage, TimelineViewItem> itemPair : group) {
                if (!parent.equals(itemPair)) {
                    parent.second.collapse(itemPair.second);
                    toCollapse.add(itemPair);
                }
            }
        }
        return groupOfSelectedItem;
    }

    private void showDuplicates(long itemId) {
        for (TimelinePage page : pages) {
            for (int ind = page.items.size() - 1; ind >= 0; ind--) {
                if (page.items.get(ind).isCollapsed()) {
                    if (showDuplicatesOfOneItem(itemId, page, ind)) {
                        return;
                    }
                }
            }
        }
    }

    private boolean showDuplicatesOfOneItem(long itemId, TimelinePage page, int ind) {
        TimelineViewItem item = page.items.get(ind);
        boolean groupOfSelectedItem = itemId == item.getMsgId();
        if (itemId != 0 && !groupOfSelectedItem) {
            for (TimelineViewItem child : item.getChildren()) {
                if (itemId == child.getMsgId()) {
                    groupOfSelectedItem = true;
                    break;
                }
            }
        }
        if (groupOfSelectedItem) {
            setIndividualCollapsedStatus(false, item.getMsgId());
            for (TimelineViewItem child : item.getChildren()) {
                setIndividualCollapsedStatus(false, child.getMsgId());
            }
        }

        boolean hasIndividualCollapseState = false;
        if (!groupOfSelectedItem && !individualCollapsedStateIds.isEmpty()) {
            for (TimelineViewItem child : item.getChildren()) {
                if (individualCollapsedStateIds.contains(child.getMsgId())) {
                    hasIndividualCollapseState = true;
                    break;
                }
            }
        }
        if (!hasIndividualCollapseState && (itemId == 0 || groupOfSelectedItem)) {
            int ind2 = ind + 1;
            for (TimelineViewItem child : item.getChildren()) {
                page.items.add(ind2++, child);
            }
            item.getChildren().clear();
        }
        return groupOfSelectedItem;
    }

}
