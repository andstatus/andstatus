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
package org.andstatus.app.timeline

import org.andstatus.app.context.MyPreferences
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.TriState
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * @author yvolk@yurivolkov.com
 */
class DuplicatesCollapser<T : ViewItem<T>>(val data: TimelineData<T>, oldDuplicatesCollapser: DuplicatesCollapser<T>?) {
    private val maxDistanceBetweenDuplicates = MyPreferences.getMaxDistanceBetweenDuplicates()

    // Parameters, which may be changed during presentation of the timeline
    @Volatile
    var collapseDuplicates = false
    val individualCollapsedStateIds: MutableSet<Long> = Collections.newSetFromMap(ConcurrentHashMap())

    @Volatile
    var homeOrigin: Origin = Origin.EMPTY

    private class GroupToCollapse<T : ViewItem<T>>(var parent: ItemWithPage<T>) {
        var children: MutableSet<ItemWithPage<T>> = HashSet()
        operator fun contains(itemId: Long): Boolean {
            return itemId != 0L && (parent.item.getId() == itemId ||
                    children.stream().anyMatch { child: ItemWithPage<T> -> child.item.getId() == itemId })
        }
    }

    private class ItemWithPage<T : ViewItem<T>>(var page: TimelinePage<T>, var item: T)

    fun isCollapseDuplicates(): Boolean {
        return collapseDuplicates
    }

    fun canBeCollapsed(position: Int): Boolean {
        if (maxDistanceBetweenDuplicates < 1) return false
        val item: T = data.getItem(position)
        for (i in Math.max(position - maxDistanceBetweenDuplicates, 0)..position + maxDistanceBetweenDuplicates) {
            if (i != position && item.duplicates(data.params.timeline, homeOrigin, data.getItem(i)).exists()) return true
        }
        return false
    }

    private fun setIndividualCollapsedState(collapse: Boolean, item: T) {
        if (collapse == isCollapseDuplicates()) {
            individualCollapsedStateIds.remove(item.getId())
        } else {
            individualCollapsedStateIds.add(item.getId())
        }
    }

    fun restoreCollapsedStates(oldCollapser: DuplicatesCollapser<T>) {
        oldCollapser.individualCollapsedStateIds.forEach { id: Long ->
            collapseDuplicates(
                    LoadableListViewParameters(TriState.fromBoolean(!collapseDuplicates), id, Optional.of(homeOrigin)))
        }
    }

    /** For all or for only one item  */
    fun collapseDuplicates(viewParameters: LoadableListViewParameters) {
        if (viewParameters.collapsedItemId == 0L && viewParameters.collapseDuplicates.known && collapseDuplicates != viewParameters.collapseDuplicates.toBoolean(false)) {
            collapseDuplicates = viewParameters.collapseDuplicates.toBoolean(false)
            individualCollapsedStateIds.clear()
        }
        viewParameters.preferredOrigin.ifPresent { o: Origin -> homeOrigin = o }
        when (viewParameters.collapseDuplicates) {
            TriState.TRUE -> collapseDuplicates(viewParameters.collapsedItemId)
            TriState.FALSE -> showDuplicates(viewParameters.collapsedItemId)
            else -> viewParameters.preferredOrigin.ifPresent {
                if (collapseDuplicates) {
                    showDuplicates(0)
                    collapseDuplicates(0)
                } else {
                    collapseDuplicates(0)
                    showDuplicates(0)
                }
            }
        }
    }

    private fun collapseDuplicates(itemId: Long) {
        if (maxDistanceBetweenDuplicates < 1) return
        val toCollapse: MutableSet<ItemWithPage<T>> = HashSet()
        innerCollapseDuplicates(itemId, toCollapse)
        for (itemWithPage in toCollapse) {
            itemWithPage.page.items.remove(itemWithPage.item)
        }
    }

    private fun innerCollapseDuplicates(itemId: Long, toCollapse: MutableSet<ItemWithPage<T>>) {
        val groups: MutableList<GroupToCollapse<T>> = ArrayList()
        for (page in data.pages) {
            for (item in page.items) {
                val itemPair = ItemWithPage(page, item)
                var found = false
                for (group in groups) {
                    when (item.duplicates(data.params.timeline, homeOrigin, group.parent.item)) {
                        DuplicationLink.DUPLICATES -> {
                            found = true
                            group.children.add(itemPair)
                        }
                        DuplicationLink.IS_DUPLICATED -> {
                            found = true
                            group.children.add(group.parent)
                            group.parent = itemPair
                        }
                        else -> {
                        }
                    }
                    if (found) break
                }
                if (!found) {
                    if (itemId != 0L) {
                        val selectedGroupOpt = groups.stream().filter { group: GroupToCollapse<T> -> group.contains(itemId) }.findAny()
                        if (selectedGroupOpt.isPresent) {
                            collapseThisGroup(itemId, selectedGroupOpt.get(), toCollapse)
                            return
                        }
                    }
                    if (groups.size > maxDistanceBetweenDuplicates) {
                        val group = groups.removeAt(0)
                        if (itemId == 0L || group.contains(itemId)) {
                            collapseThisGroup(itemId, group, toCollapse)
                            if (itemId != 0L) return
                        }
                    }
                    groups.add(GroupToCollapse(itemPair))
                }
            }
        }
        for (group in groups) {
            if (itemId == 0L || group.contains(itemId)) {
                collapseThisGroup(itemId, group, toCollapse)
            }
        }
    }

    private fun collapseThisGroup(itemId: Long, group: GroupToCollapse<T>,
                                  toCollapse: MutableCollection<ItemWithPage<T>>) {
        if (group.children.isEmpty()) return
        val groupOfSelectedItem = group.contains(itemId)
        if (groupOfSelectedItem) {
            setIndividualCollapsedState(true, group.parent.item)
            group.children.forEach { child: ItemWithPage<T> -> setIndividualCollapsedState(true, child.item) }
        }
        val noIndividualCollapseState = (groupOfSelectedItem || individualCollapsedStateIds.isEmpty()
                || group.children.stream().noneMatch { child: ItemWithPage<T> -> individualCollapsedStateIds.contains(child.item.getId()) })
        if (noIndividualCollapseState) {
            group.children.stream().filter { child: ItemWithPage<T> -> group.parent != child }
                    .forEach { child: ItemWithPage<T> ->
                        group.parent.item.collapse(child.item)
                        toCollapse.add(child)
                    }
        }
    }

    private fun showDuplicates(itemId: Long) {
        for (page in data.pages) {
            for (ind in page.items.indices.reversed()) {
                if (page.items[ind].isCollapsed) {
                    if (showDuplicatesOfOneItem(itemId, page, ind)) {
                        return
                    }
                }
            }
        }
    }

    private fun showDuplicatesOfOneItem(itemId: Long, page: TimelinePage<T>, ind: Int): Boolean {
        val item = page.items[ind]
        val groupOfSelectedItem = (itemId == item.getId()
                || itemId != 0L && item.getChildren().stream().anyMatch { child: T -> child.getId() == itemId })
        if (groupOfSelectedItem) {
            setIndividualCollapsedState(false, item)
            item.getChildren().forEach(Consumer { child: T -> setIndividualCollapsedState(false, child) })
        }
        val noIndividualCollapseState = (groupOfSelectedItem || individualCollapsedStateIds.isEmpty()
                || item.getChildren().stream().noneMatch { child: T -> individualCollapsedStateIds.contains(child.getId()) })
        if (noIndividualCollapseState && (itemId == 0L || groupOfSelectedItem)) {
            var ind2 = ind + 1
            for (child in item.getChildren().stream()
                    .sorted(Comparator.comparing { obj: T -> obj.getDate() }.reversed())
                    .collect(Collectors.toList())) {
                page.items.add(ind2++, child)
            }
            item.getChildren().clear()
        }
        return groupOfSelectedItem
    }

    init {
        if (oldDuplicatesCollapser == null) {
            val timeline = data.params.timeline
            when (timeline.timelineType) {
                TimelineType.UNKNOWN, TimelineType.UNREAD_NOTIFICATIONS -> {
                    collapseDuplicates = false
                    homeOrigin =  Origin.EMPTY
                }
                else -> {
                    collapseDuplicates = MyPreferences.isCollapseDuplicates()
                    homeOrigin = timeline.homeOrigin
                }
            }
        } else {
            collapseDuplicates = oldDuplicatesCollapser.collapseDuplicates
            individualCollapsedStateIds.addAll(oldDuplicatesCollapser.individualCollapsedStateIds)
            homeOrigin = oldDuplicatesCollapser.homeOrigin
        }
    }
}
