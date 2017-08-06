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
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.widget.DuplicatesCollapsible;
import org.andstatus.app.widget.DuplicationLink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineData<T extends DuplicatesCollapsible> {
    // Parameters, which may be changed during presentation of the timeline
    protected volatile boolean collapseDuplicates = MyPreferences.isCollapseDuplicates();
    protected final Set<Long> individualCollapsedStateIds = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    @NonNull
    protected final T emptyViewItem;

    private static final int MAX_PAGES_COUNT = 5;
    final List<TimelinePage<T>> pages;
    final long updatedAt = MyLog.uniqueCurrentTimeMS();
    final TimelineListParameters params;
    final boolean isSameTimeline;

    public TimelineData(@NonNull T emptyViewItem,  TimelineData<T> oldData, @NonNull TimelinePage<T> thisPage) {
        this.emptyViewItem = emptyViewItem;
        if (oldData != null) {
            this.collapseDuplicates = oldData.collapseDuplicates;
            this.individualCollapsedStateIds.addAll(oldData.individualCollapsedStateIds);
        }
        this.params = thisPage.params;
        isSameTimeline = oldData != null &&
                params.getContentUri().equals(oldData.params.getContentUri());
        this.pages = isSameTimeline ? copyPages(oldData.pages) : new ArrayList<TimelinePage<T>>();
        addThisPage(thisPage);
        collapseDuplicates(isCollapseDuplicates(), 0);
        dropExcessivePage(thisPage);
    }

    private List<TimelinePage<T>> copyPages(List<TimelinePage<T>> pages) {
        ArrayList<TimelinePage<T>> copiedPages = new ArrayList<>();
        for (TimelinePage<T> page : pages) {
            copiedPages.add(page);
        }
        return copiedPages;
    }

    private void dropExcessivePage(TimelinePage<T> lastLoadedPage) {
        if (pages.size() > MAX_PAGES_COUNT) {
            if (lastLoadedPage.params.whichPage == WhichPage.YOUNGER) {
                pages.remove(pages.size() - 1);
            } else {
                pages.remove(0);
            }
        }
    }

    private void addThisPage(TimelinePage<T> page) {
        switch (page.params.whichPage) {
            case YOUNGEST:
                if (mayHaveYoungerPage()) {
                    pages.clear();
                    pages.add(page);
                } else {
                    removeDuplicatesWithOlder(page, 1);
                    pages.remove(0);
                    pages.add(0, page);
                }
                break;
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

    private void removeDuplicatesWithYounger(TimelinePage<T> page, int indExistingPage) {
        if (indExistingPage < 0 || indExistingPage >= pages.size()
                || pages.get(indExistingPage).items.isEmpty() || page.items.isEmpty()) {
            return;
        }
        TimelinePage<T> ePage = pages.get(indExistingPage);
        if (ePage.params.maxSentDate > 0 && page.params.maxSentDate >= ePage.params.maxSentDate) {
            MyLog.v(this, "Previous younger page removed");
            pages.remove(indExistingPage);
            return;
        }
        long edgeDate =  ePage.params.minSentDateLoaded;
        List<T> toRemove = new ArrayList<>();
        for (int ind = 0; ind < page.items.size(); ind++) {
            T item = page.items.get(ind);
            if (item.getDate() < edgeDate) {
                break;
            } else if (item.getDate() > edgeDate) {
                MyLog.e(this, "This page has an item younger than on a younger page: " + item);
                toRemove.add(item);
            } else {
                for (int eInd = ePage.items.size() - 1; eInd >= 0; eInd--) {
                    T eItem = ePage.items.get(eInd);
                    if (eItem.getDate() > item.getDate()) {
                        break;
                    }
                    if (eItem.getId() == item.getId()) {
                        mergeWithExisting(item, eItem);
                        toRemove.add(item);
                        break;
                    }
                }
            }
        }
        page.items.removeAll(toRemove);
    }

    private void mergeWithExisting(T newItem, T existingItem) {
        // TODO: Merge something...
    }

    private void removeDuplicatesWithOlder(TimelinePage<T> page, int indExistingPage) {
        if (indExistingPage < 0 || indExistingPage >= pages.size()
                || pages.get(indExistingPage).items.isEmpty() || page.items.isEmpty()) {
            return;
        }
        TimelinePage<T> ePage = pages.get(indExistingPage);
        if (page.params.minSentDate <= ePage.params.minSentDate) {
            MyLog.v(this, "Previous older page removed");
            pages.remove(indExistingPage);
            return;
        }
        long edgeDate = ePage.params.maxSentDateLoaded;
        List<T> toRemove = new ArrayList<>();
        for (int ind = page.items.size() - 1; ind >= 0; ind--) {
            T item = page.items.get(ind);
            if ( item.getDate() > edgeDate) {
                break;
            } else if (item.getDate() < edgeDate) {
                MyLog.e(this, "This page has an item older than on an older page: " + item);
                toRemove.add(item);
            } else {
                for (int eInd = 0; eInd < ePage.items.size(); eInd++) {
                    T eItem = ePage.items.get(eInd);
                    if (eItem.getDate() < item.getDate()) {
                        break;
                    }
                    if (eItem.getId() == item.getId()) {
                        mergeWithExisting(item, eItem);
                        toRemove.add(item);
                        break;
                    }
                }
            }
        }
        page.items.removeAll(toRemove);
    }

    // See http://stackoverflow.com/questions/300522/count-vs-length-vs-size-in-a-collection
    public int size() {
        int count = 0;
        for (TimelinePage page : pages) {
            count += page.items.size();
        }
        return count;
    }

    @NonNull
    public T getItem(int position) {
        int firstPosition = 0;
        for (TimelinePage<T> page : pages) {
            if (position < firstPosition) {
                break;
            }
            if (position < firstPosition + page.items.size()) {
                return page.items.get(position - firstPosition);
            }
            firstPosition += page.items.size();
        }
        return emptyViewItem; // TODO: replace with static T.getItem() when language allows
    }

    @NonNull
    public T getById(long itemId) {
        for (TimelinePage<T> page : pages) {
            for (T item : page.items) {
                if (item.getId() == itemId) {
                    return item;
                }
            }
        }
        return emptyViewItem;
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

    public boolean isCollapseDuplicates() {
        return collapseDuplicates;
    }

    public boolean canBeCollapsed(int position) {
        T item = getItem(position);
        if (item != null) {
            if (position > 0) {
                if (item.duplicates(getItem(position - 1)) != DuplicationLink.NONE) {
                    return true;
                }
            }
            if (item.duplicates(getItem(position + 1)) != DuplicationLink.NONE) {
                return true;
            }
        }
        return false;
    }

    protected void setIndividualCollapsedStatus(boolean collapse, long itemId) {
        if (collapse == isCollapseDuplicates()) {
            individualCollapsedStateIds.remove(itemId);
        } else {
            individualCollapsedStateIds.add(itemId);
        }
    }

    /** For all or for only one item */
    public void collapseDuplicates(boolean collapse, long itemId) {
        if (itemId == 0 && this.collapseDuplicates != collapse) {
            this.collapseDuplicates = collapse;
            individualCollapsedStateIds.clear();
        }
        if (collapse) {
            collapseDuplicates(itemId);
        } else {
            showDuplicates(itemId);
        }
    }

    private void collapseDuplicates(long itemId) {
        Set<Pair<TimelinePage<T>, T>> toCollapse = new HashSet<>();
        innerCollapseDuplicates(itemId, toCollapse);
        for (Pair<TimelinePage<T>, T> pair : toCollapse) {
            pair.first.items.remove(pair.second);
        }
    }

    private void innerCollapseDuplicates(long itemId, Collection<Pair<TimelinePage<T>, T>> toCollapse) {
        Pair<TimelinePage<T>, T> parent = new Pair<>(null, null);
        Set<Pair<TimelinePage<T>, T>> group = new HashSet<>();
        for (TimelinePage<T> page : pages) {
            for (T item : page.items) {
                Pair<TimelinePage<T>, T> itemPair =new Pair<>(page, item);
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

    private boolean collapseThisGroup(long itemId, Pair<TimelinePage<T>, T> parent, Set<Pair<TimelinePage<T>, T>> group,
                                      Collection<Pair<TimelinePage<T>, T>> toCollapse) {
        if (group.isEmpty()) {
            return false;
        }

        boolean groupOfSelectedItem = false;
        if (itemId != 0) {
            for (Pair<TimelinePage<T>, T> itemPair : group) {
                if (itemId == itemPair.second.getId()) {
                    groupOfSelectedItem = true;
                    break;
                }
            }
        }
        if (groupOfSelectedItem) {
            for (Pair<TimelinePage<T>, T> itemPair : group) {
                setIndividualCollapsedStatus(true, itemPair.second.getId());
            }
        }

        boolean hasIndividualCollapseState = false;
        if (!groupOfSelectedItem && !individualCollapsedStateIds.isEmpty()) {
            for (Pair<TimelinePage<T>, T> itemPair : group) {
                if (individualCollapsedStateIds.contains(itemPair.second.getId())) {
                    hasIndividualCollapseState = true;
                    break;
                }
            }
        }
        if (!hasIndividualCollapseState) {
            for (Pair<TimelinePage<T>, T> itemPair : group) {
                if (!parent.equals(itemPair)) {
                    parent.second.collapse(itemPair.second);
                    toCollapse.add(itemPair);
                }
            }
        }
        return groupOfSelectedItem;
    }

    private void showDuplicates(long itemId) {
        for (TimelinePage<T> page : pages) {
            for (int ind = page.items.size() - 1; ind >= 0; ind--) {
                if (page.items.get(ind).isCollapsed()) {
                    if (showDuplicatesOfOneItem(itemId, page, ind)) {
                        return;
                    }
                }
            }
        }
    }

    private boolean showDuplicatesOfOneItem(long itemId, TimelinePage<T> page, int ind) {
        T item = page.items.get(ind);
        boolean groupOfSelectedItem = itemId == item.getId();
        if (itemId != 0 && !groupOfSelectedItem) {
            for (DuplicatesCollapsible child : item.getChildren()) {
                if (itemId == child.getId()) {
                    groupOfSelectedItem = true;
                    break;
                }
            }
        }
        if (groupOfSelectedItem) {
            setIndividualCollapsedStatus(false, item.getId());
            for (DuplicatesCollapsible child : item.getChildren()) {
                setIndividualCollapsedStatus(false, child.getId());
            }
        }

        boolean hasIndividualCollapseState = false;
        if (!groupOfSelectedItem && !individualCollapsedStateIds.isEmpty()) {
            for (DuplicatesCollapsible child : item.getChildren()) {
                if (individualCollapsedStateIds.contains(child.getId())) {
                    hasIndividualCollapseState = true;
                    break;
                }
            }
        }
        if (!hasIndividualCollapseState && (itemId == 0 || groupOfSelectedItem)) {
            int ind2 = ind + 1;
            for (DuplicatesCollapsible child : item.getChildren()) {
                page.items.add(ind2++, (T) child);
            }
            item.getChildren().clear();
        }
        return groupOfSelectedItem;
    }
}
