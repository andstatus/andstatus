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

package org.andstatus.app.timeline;

import android.support.v4.util.Pair;

import org.andstatus.app.context.MyPreferences;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yvolk@yurivolkov.com
 */
public class DuplicatesCollapser<T extends ViewItem> {
    // Parameters, which may be changed during presentation of the timeline
    protected volatile boolean collapseDuplicates = MyPreferences.isCollapseDuplicates();
    private final Set<Long> individualCollapsedStateIds = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    final TimelineData<T> data;

    public DuplicatesCollapser(TimelineData<T> data, DuplicatesCollapser<T> oldDuplicatesCollapser) {
        this.data = data;
        if (oldDuplicatesCollapser != null) {
            this.collapseDuplicates = oldDuplicatesCollapser.collapseDuplicates;
            this.individualCollapsedStateIds.addAll(oldDuplicatesCollapser.individualCollapsedStateIds);
        }
    }

    public boolean isCollapseDuplicates() {
        return collapseDuplicates;
    }

    public boolean canBeCollapsed(int position) {
        T item = data.getItem(position);
        if (position > 0) {
            if (item.duplicates(data.getItem(position - 1)) != DuplicationLink.NONE) {
                return true;
            }
        }
        if (item.duplicates(data.getItem(position + 1)) != DuplicationLink.NONE) {
            return true;
        }
        return false;
    }

    private void setIndividualCollapsedStatus(boolean collapse, long itemId) {
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
        for (TimelinePage<T> page : data.pages) {
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
        for (TimelinePage<T> page : data.pages) {
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
            for (ViewItem child : item.getChildren()) {
                if (itemId == child.getId()) {
                    groupOfSelectedItem = true;
                    break;
                }
            }
        }
        if (groupOfSelectedItem) {
            setIndividualCollapsedStatus(false, item.getId());
            for (ViewItem child : item.getChildren()) {
                setIndividualCollapsedStatus(false, child.getId());
            }
        }

        boolean hasIndividualCollapseState = false;
        if (!groupOfSelectedItem && !individualCollapsedStateIds.isEmpty()) {
            for (ViewItem child : item.getChildren()) {
                if (individualCollapsedStateIds.contains(child.getId())) {
                    hasIndividualCollapseState = true;
                    break;
                }
            }
        }
        if (!hasIndividualCollapseState && (itemId == 0 || groupOfSelectedItem)) {
            int ind2 = ind + 1;
            for (ViewItem child : item.getChildren()) {
                page.items.add(ind2++, (T) child);
            }
            item.getChildren().clear();
        }
        return groupOfSelectedItem;
    }
}
