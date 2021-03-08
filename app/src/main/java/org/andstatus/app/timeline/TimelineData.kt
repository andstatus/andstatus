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
package org.andstatus.app.timeline

import io.vavr.control.CheckedFunction
import io.vavr.control.Try
import org.andstatus.app.activity.ActivityViewItem
import org.andstatus.app.actor.ActorViewItem
import org.andstatus.app.net.social.Actor
import org.andstatus.app.note.BaseNoteViewItem
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.TryUtils
import java.util.*

/**
 * @author yvolk@yurivolkov.com
 */
open class TimelineData<T : ViewItem<T>>(oldData: TimelineData<T>?, thisPage: TimelinePage<T>) {
    val pages // Contains at least one Page
            : MutableList<TimelinePage<T>>
    val updatedAt = MyLog.uniqueCurrentTimeMS()
    val params: TimelineParameters

    @Volatile
    private var actorViewItem: ActorViewItem?
    val isSameTimeline: Boolean
    private val duplicatesCollapser: DuplicatesCollapser<T>
    private fun dropExcessivePage(lastLoadedPage: TimelinePage<T>) {
        if (pages.size > MAX_PAGES_COUNT) {
            if (lastLoadedPage.params.whichPage == WhichPage.YOUNGER) {
                pages.removeAt(pages.size - 1)
            } else {
                pages.removeAt(0)
            }
        }
    }

    private fun addThisPage(page: TimelinePage<T?>?) {
        when (page.params.whichPage) {
            WhichPage.YOUNGEST -> if (mayHaveYoungerPage()) {
                pages.clear()
                pages.add(page)
            } else {
                removeDuplicatesWithOlder(page, 1)
                if (!pages.isEmpty()) {
                    pages.removeAt(0)
                }
                pages.add(0, page)
            }
            WhichPage.CURRENT, WhichPage.TOP -> {
                pages.clear()
                pages.add(page)
            }
            WhichPage.OLDER -> {
                removeDuplicatesWithYounger(page, pages.size - 1)
                pages.add(page)
            }
            WhichPage.YOUNGER -> {
                removeDuplicatesWithOlder(page, 0)
                pages.add(0, page)
            }
            else -> if (pages.size < 2) {
                pages.clear()
                pages.add(page)
            } else {
                var found = -1
                var ind = 0
                while (ind < pages.size) {
                    val p: TimelinePage<*>? = pages.get(ind)
                    if (p.params.maxDate == page.params.maxDate
                            && p.params.minDate == page.params.minDate) {
                        found = ind
                        break
                    }
                    ind++
                }
                if (found >= 0) {
                    removeDuplicatesWithYounger(page, found - 1)
                    removeDuplicatesWithOlder(page, found + 1)
                    pages.removeAt(found)
                    pages.add(found, page)
                } else {
                    pages.add(page)
                }
            }
        }
    }

    private fun removeDuplicatesWithYounger(page: TimelinePage<T?>?, indExistingPage: Int) {
        for (ind in Integer.min(indExistingPage, pages.size - 1) downTo 0) {
            pages.get(ind).items.removeAll(page.items)
        }
    }

    private fun mergeWithExisting(newItem: T?, existingItem: T?) {
        // TODO: Merge something...
    }

    private fun removeDuplicatesWithOlder(page: TimelinePage<T?>?, indExistingPage: Int) {
        for (ind in Integer.max(indExistingPage, 0) until pages.size) {
            pages.get(ind).items.removeAll(page.items)
        }
    }

    // See http://stackoverflow.com/questions/300522/count-vs-length-vs-size-in-a-collection
    open fun size(): Int {
        var count = 0
        for (page in pages) {
            count += page.items.size
        }
        return count
    }

    open fun getItem(position: Int): T {
        var firstPosition = 0
        for (page in pages) {
            if (position < firstPosition) {
                break
            }
            if (position < firstPosition + page.items.size) {
                return page.items[position - firstPosition]
            }
            firstPosition += page.items.size
        }
        return getEmptyItem()
    }

    open fun getById(itemId: Long): T {
        if (itemId != 0L) {
            for (page in pages) {
                for (item in page.items) {
                    if (item.getId() == itemId) {
                        return item
                    }
                }
            }
        }
        return getEmptyItem()
    }

    fun getEmptyItem(): T {
        return pages.get(0).getEmptyItem()
    }

    /** @return -1 if not found
     */
    open fun getPositionById(itemId: Long): Int {
        var position = -1
        if (itemId != 0L) {
            for (page in pages) {
                for (item in page.items) {
                    position++
                    if (item.getId() == itemId) {
                        return position
                    } else if (item.isCollapsed()) {
                        for (child in item.getChildren()) {
                            if (child.getId() == itemId) {
                                return position
                            }
                        }
                    }
                }
            }
        }
        return -1
    }

    open fun mayHaveYoungerPage(): Boolean {
        return pages.size == 0 || pages.get(0).params.mayHaveYoungerPage()
    }

    open fun mayHaveOlderPage(): Boolean {
        return pages.size == 0 || pages.get(pages.size - 1).params.mayHaveOlderPage()
    }

    override fun toString(): String {
        val builder: MyStringBuilder = MyStringBuilder.Companion.of("pages:" + pages.size + ", total items:" + size())
        for (page in pages) {
            builder.atNewLine("Page size:" + page.items.size + ", params: " + page.params)
        }
        return MyStringBuilder.Companion.formatKeyValue(this, builder)
    }

    open fun isCollapseDuplicates(): Boolean {
        return duplicatesCollapser.isCollapseDuplicates()
    }

    open fun canBeCollapsed(position: Int): Boolean {
        return duplicatesCollapser.canBeCollapsed(position)
    }

    /**
     * For all or for only one item
     */
    open fun updateView(viewParameters: LoadableListViewParameters?) {
        if (viewParameters.preferredOrigin.isPresent) {
            setActorViewItem(viewParameters.preferredOrigin.get())
        }
        duplicatesCollapser.collapseDuplicates(viewParameters)
    }

    private fun setActorViewItem(preferredOrigin: Origin?) {
        if (params.timeline.hasActorProfile()) {
            findActorViewItem(params.timeline.actor, preferredOrigin).onSuccess { a: ActorViewItem? -> actorViewItem = a }
        }
    }

    fun getActorViewItem(): ActorViewItem? {
        return actorViewItem
    }

    fun findActorViewItem(actor: Actor?, preferredOrigin: Origin?): Try<ActorViewItem?> {
        for (page in pages) {
            for (item in page.items) {
                val found = findInOneItemWithChildren(item, actor, preferredOrigin)
                if (found.isSuccess()) return found
            }
        }
        return TryUtils.notFound()
    }

    private fun findInOneItemWithChildren(item: T?, actor: Actor?, preferredOrigin: Origin?): Try<ActorViewItem> {
        val found = findInOneItem(item, actor, preferredOrigin)
        if (found.isSuccess()) return found
        for (child in item.getChildren()) {
            val foundChild = findInOneItem(child, actor, preferredOrigin)
            if (foundChild.isSuccess()) return foundChild
        }
        return TryUtils.notFound()
    }

    private fun findInOneItem(item: T?, actor: Actor?, preferredOrigin: Origin?): Try<ActorViewItem> {
        if (item is BaseNoteViewItem<*>) {
            return filterSameActorAtOrigin(actor, preferredOrigin, (item as BaseNoteViewItem<*>?).getAuthor())
        } else if (item is ActivityViewItem) {
            val activityViewItem = item as ActivityViewItem?
            return filterSameActorAtOrigin(actor, preferredOrigin, activityViewItem.getObjActorItem())
                    .recoverWith(NoSuchElementException::class.java, CheckedFunction<NoSuchElementException?, Try<out ActorViewItem>> { e: NoSuchElementException? -> filterSameActorAtOrigin(actor, preferredOrigin, activityViewItem.noteViewItem.author) })
                    .recoverWith(NoSuchElementException::class.java) { e: NoSuchElementException? -> filterSameActorAtOrigin(actor, preferredOrigin, activityViewItem.getActor()) }
        }
        return TryUtils.notFound()
    }

    private fun filterSameActorAtOrigin(actor: Actor?, origin: Origin?, actorViewItem: ActorViewItem?): Try<ActorViewItem> {
        val otherActor = actorViewItem.getActor()
        return if (otherActor.origin == origin && otherActor.isSame(actor)) Try.success(actorViewItem) else TryUtils.notFound()
    }

    fun getPreferredOrigin(): Origin? {
        return duplicatesCollapser.preferredOrigin
    }

    companion object {
        private const val MAX_PAGES_COUNT = 5
    }

    init {
        params = thisPage.params
        isSameTimeline = oldData != null && params.getContentUri() == oldData.params.getContentUri()
        pages = if (isSameTimeline) ArrayList(oldData.pages) else ArrayList()
        val oldCollapser = if (isSameTimeline) oldData.duplicatesCollapser else null
        duplicatesCollapser = DuplicatesCollapser(this, oldCollapser)
        val collapsed = isCollapseDuplicates()
        if (!duplicatesCollapser.individualCollapsedStateIds.isEmpty()) {
            duplicatesCollapser.collapseDuplicates(LoadableListViewParameters.Companion.collapseDuplicates(false))
        }
        addThisPage(thisPage)
        if (collapsed) {
            duplicatesCollapser.collapseDuplicates(LoadableListViewParameters.Companion.collapseDuplicates(true))
        }
        dropExcessivePage(thisPage)
        actorViewItem = thisPage.actorViewItem
        if (getPreferredOrigin().nonEmpty && getPreferredOrigin() != actorViewItem.getActor().origin) {
            setActorViewItem(getPreferredOrigin())
        }
        if (oldCollapser != null && collapsed == oldCollapser.collapseDuplicates && !oldCollapser.individualCollapsedStateIds.isEmpty()) {
            duplicatesCollapser.restoreCollapsedStates(oldCollapser)
        }
    }
}