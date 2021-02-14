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

import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.TriState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;

import static java.util.stream.Collectors.toList;

/**
 * @author yvolk@yurivolkov.com
 */
public class DuplicatesCollapser<T extends ViewItem<T>> {
    private int maxDistanceBetweenDuplicates = MyPreferences.getMaxDistanceBetweenDuplicates();

    // Parameters, which may be changed during presentation of the timeline
    volatile boolean collapseDuplicates;
    final Set<Long> individualCollapsedStateIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    final TimelineData<T> data;
    volatile Origin preferredOrigin;

    private static class GroupToCollapse<T extends ViewItem<T>> {
        @NonNull
        ItemWithPage<T> parent;
        Set<ItemWithPage<T>> children = new HashSet<>();

        GroupToCollapse(@NonNull ItemWithPage<T> parent) {
            this.parent = parent;
        }

        boolean contains(long itemId) {
            return itemId != 0 && (
                    parent.item.getId() == itemId ||
                    children.stream().anyMatch(child -> child.item.getId() == itemId));
        }

    }

    private static class ItemWithPage<T extends ViewItem<T>> {
        TimelinePage<T> page;
        T item;

        ItemWithPage(TimelinePage<T> page, T item) {
            this.page = page;
            this.item = item;
        }
    }

    public DuplicatesCollapser(TimelineData<T> data, DuplicatesCollapser<T> oldDuplicatesCollapser) {
        this.data = data;
        if (oldDuplicatesCollapser == null) {
            Timeline timeline = data.params.timeline;
            switch (timeline.getTimelineType()) {
                case UNKNOWN:
                case UNREAD_NOTIFICATIONS:
                    collapseDuplicates = false;
                    preferredOrigin = Origin.EMPTY;
                    break;
                default:
                    collapseDuplicates = MyPreferences.isCollapseDuplicates();
                    preferredOrigin = timeline.preferredOrigin();
                    break;
            }
        } else {
            collapseDuplicates = oldDuplicatesCollapser.collapseDuplicates;
            individualCollapsedStateIds.addAll(oldDuplicatesCollapser.individualCollapsedStateIds);
            preferredOrigin = oldDuplicatesCollapser.preferredOrigin;
        }
    }

    public boolean isCollapseDuplicates() {
        return collapseDuplicates;
    }

    public boolean canBeCollapsed(int position) {
        if (maxDistanceBetweenDuplicates < 1) return false;
        T item = data.getItem(position);
        for (int i = Math.max(position - maxDistanceBetweenDuplicates, 0); i <= position + maxDistanceBetweenDuplicates; i++) {
            if (i != position && item.duplicates(data.params.timeline, preferredOrigin, data.getItem(i)).exists()) return true;
        }
        return false;
    }

    private void setIndividualCollapsedState(boolean collapse, T item) {
        if (collapse == isCollapseDuplicates()) {
            individualCollapsedStateIds.remove(item.getId());
        } else {
            individualCollapsedStateIds.add(item.getId());
        }
    }

    public void restoreCollapsedStates(@NonNull DuplicatesCollapser<T> oldCollapser) {
        oldCollapser.individualCollapsedStateIds.forEach(id -> collapseDuplicates(
                new LoadableListViewParameters(TriState.fromBoolean(!collapseDuplicates), id, Optional.of(preferredOrigin))));
    }

    /** For all or for only one item */
    public void collapseDuplicates(LoadableListViewParameters viewParameters) {
        if (viewParameters.collapsedItemId == 0 && viewParameters.collapseDuplicates.known &&
                this.collapseDuplicates != viewParameters.collapseDuplicates.toBoolean(false)) {
            this.collapseDuplicates = viewParameters.collapseDuplicates.toBoolean(false);
            individualCollapsedStateIds.clear();
        }
        viewParameters.preferredOrigin.ifPresent(o -> {
            this.preferredOrigin = o;
        });

        switch (viewParameters.collapseDuplicates) {
            case TRUE:
                collapseDuplicates(viewParameters.collapsedItemId);
                break;
            case FALSE:
                showDuplicates(viewParameters.collapsedItemId);
                break;
            default:
                viewParameters.preferredOrigin.ifPresent(o -> {
                    if (collapseDuplicates) {
                        showDuplicates(0);
                        collapseDuplicates(0);
                    } else {
                        collapseDuplicates(0);
                        showDuplicates(0);
                    }
                });
                break;
        }
    }

    private void collapseDuplicates(long itemId) {
        if (maxDistanceBetweenDuplicates < 1) return;
        Set<ItemWithPage<T>> toCollapse = new HashSet<>();
        innerCollapseDuplicates(itemId, toCollapse);
        for (ItemWithPage<T> itemWithPage : toCollapse) {
            itemWithPage.page.items.remove(itemWithPage.item);
        }
    }

    private void innerCollapseDuplicates(long itemId, Set<ItemWithPage<T>> toCollapse) {
        List<GroupToCollapse<T>> groups = new ArrayList<>();
        for (TimelinePage<T> page : data.pages) {
            for (T item : page.items) {
                ItemWithPage<T> itemPair = new ItemWithPage<>(page, item);
                boolean found = false;
                for (GroupToCollapse<T> group : groups) {
                    switch (item.duplicates(data.params.timeline, preferredOrigin, group.parent.item)) {
                        case DUPLICATES:
                            found = true;
                            group.children.add(itemPair);
                            break;
                        case IS_DUPLICATED:
                            found = true;
                            group.children.add(group.parent);
                            group.parent = itemPair;
                            break;
                        default:
                            break;
                    }
                    if (found) break;
                }
                if (!found) {
                    if (itemId != 0) {
                        Optional<GroupToCollapse<T>> selectedGroupOpt =
                                groups.stream().filter(group -> group.contains(itemId)).findAny();
                        if (selectedGroupOpt.isPresent()) {
                            collapseThisGroup(itemId, selectedGroupOpt.get(), toCollapse);
                            return;
                        }
                    }
                    if (groups.size() > maxDistanceBetweenDuplicates) {
                        GroupToCollapse<T> group = groups.remove(0);
                        if (itemId == 0 || group.contains(itemId)) {
                            collapseThisGroup(itemId, group, toCollapse);
                            if (itemId != 0) return;
                        }
                    }
                    groups.add(new GroupToCollapse<>(itemPair));
                }

            }
        }
        for (GroupToCollapse<T> group : groups) {
            if (itemId == 0 || group.contains(itemId)) {
                collapseThisGroup(itemId, group, toCollapse);
            }
        }
    }

    private void collapseThisGroup(long itemId, GroupToCollapse<T> group,
                                      Collection<ItemWithPage<T>> toCollapse) {
        if (group.children.isEmpty()) return;

        boolean groupOfSelectedItem = group.contains(itemId);
        if (groupOfSelectedItem) {
            setIndividualCollapsedState(true, group.parent.item);
            group.children
                    .forEach(child -> setIndividualCollapsedState(true, child.item));
        }

        boolean noIndividualCollapseState = groupOfSelectedItem || individualCollapsedStateIds.isEmpty()
                || group.children.stream().noneMatch(child -> individualCollapsedStateIds.contains(child.item.getId()));
        if (noIndividualCollapseState) {
            group.children.stream().filter(child -> !group.parent.equals(child))
                    .forEach(child -> {
                        group.parent.item.collapse(child.item);
                        toCollapse.add(child);
                    });
        }
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
        boolean groupOfSelectedItem = itemId == item.getId()
                || (itemId != 0 && item.getChildren().stream().anyMatch(child -> child.getId() == itemId));
        if (groupOfSelectedItem) {
            setIndividualCollapsedState(false, item);
            item.getChildren().forEach(child -> setIndividualCollapsedState(false, child));
        }

        boolean noIndividualCollapseState = groupOfSelectedItem || individualCollapsedStateIds.isEmpty()
                || item.getChildren().stream().noneMatch(child -> individualCollapsedStateIds.contains(child.getId()));
        if (noIndividualCollapseState && (itemId == 0 || groupOfSelectedItem)) {
            int ind2 = ind + 1;
            for (T child : item.getChildren().stream().sorted(
                    Comparator.comparing(T::getDate).reversed()).collect(toList())) {
                page.items.add(ind2++, child);
            }
            item.getChildren().clear();
        }
        return groupOfSelectedItem;
    }
}
