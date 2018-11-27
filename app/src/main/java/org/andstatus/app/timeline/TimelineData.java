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

package org.andstatus.app.timeline;

import android.support.annotation.NonNull;

import org.andstatus.app.actor.ActorViewItem;
import org.andstatus.app.util.MyLog;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yvolk@yurivolkov.com
 */
public class TimelineData<T extends ViewItem<T>> {
    private static final int MAX_PAGES_COUNT = 5;
    protected final List<TimelinePage<T>> pages; // Contains at least one Page
    final long updatedAt = MyLog.uniqueCurrentTimeMS();
    public final TimelineParameters params;
    public final ActorViewItem actorViewItem;
    final boolean isSameTimeline;
    private final DuplicatesCollapser<T> duplicatesCollapser;

    public TimelineData(TimelineData<T> oldData, @NonNull TimelinePage<T> thisPage) {
        this.params = thisPage.params;
        isSameTimeline = oldData != null && params.getContentUri().equals(oldData.params.getContentUri());
        actorViewItem = thisPage.actorViewItem;
        this.pages = isSameTimeline ? new ArrayList<>(oldData.pages) : new ArrayList<>();
        final DuplicatesCollapser<T> oldCollapser = isSameTimeline ? oldData.duplicatesCollapser : null;
        duplicatesCollapser = new DuplicatesCollapser<>(this, oldCollapser);
        boolean collapsed = isCollapseDuplicates();
        if (!duplicatesCollapser.individualCollapsedStateIds.isEmpty()) {
            duplicatesCollapser.collapseDuplicates(false, 0);
        }
        addThisPage(thisPage);
        if (collapsed) {
            duplicatesCollapser.collapseDuplicates(true, 0);
        }
        dropExcessivePage(thisPage);
        if (oldCollapser != null && collapsed == oldCollapser.collapseDuplicates
                && !oldCollapser.individualCollapsedStateIds.isEmpty()) {
            duplicatesCollapser.restoreCollapsedStates(oldCollapser);
        }
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
                    if (!pages.isEmpty()) {
                        pages.remove(0);
                    }
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
                        if (p.params.maxDate == page.params.maxDate
                                && p.params.minDate == page.params.minDate) {
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
        for (int ind = Integer.min(indExistingPage, pages.size() - 1); ind >= 0; ind--) {
            pages.get(ind).items.removeAll(page.items);
        }
    }

    private void mergeWithExisting(T newItem, T existingItem) {
        // TODO: Merge something...
    }

    private void removeDuplicatesWithOlder(TimelinePage<T> page, int indExistingPage) {
        for (int ind = Integer.max(indExistingPage, 0); ind < pages.size(); ind++) {
            pages.get(ind).items.removeAll(page.items);
        }
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
        return getEmptyItem();
    }

    @NonNull
    public T getById(long itemId) {
        if (itemId != 0) {
            for (TimelinePage<T> page : pages) {
                for (T item : page.items) {
                    if (item.getId() == itemId) {
                        return item;
                    }
                }
            }
        }
        return getEmptyItem();
    }

    @NonNull
    public T getEmptyItem() {
        return pages.get(0).getEmptyItem();
    }

    /** @return -1 if not found */
    public int getPositionById(long itemId) {
        int position = -1;
        if (itemId != 0) {
            for (TimelinePage<T> page : pages) {
                for (T item : page.items) {
                    position++;
                    if (item.getId() == itemId) {
                        return position;
                    } else if (item.isCollapsed()) {
                        for (T child : item.getChildren()) {
                            if (child.getId() == itemId) {
                                return position;
                            }
                        }
                    }
                }
            }
        }
        return -1;
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
        return MyLog.formatKeyValue(this, s);
    }

    public boolean isCollapseDuplicates() {
        return duplicatesCollapser.isCollapseDuplicates();
    }

    public boolean canBeCollapsed(int position) {
        return duplicatesCollapser.canBeCollapsed(position);
    }

    /**
     * For all or for only one item
     */
    public void collapseDuplicates(boolean collapse, long itemId) {
        duplicatesCollapser.collapseDuplicates(collapse, itemId);
    }
}
