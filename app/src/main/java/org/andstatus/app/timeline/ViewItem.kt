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

import android.content.Context
import android.database.Cursor
import org.andstatus.app.R
import org.andstatus.app.actor.ActorsLoader
import org.andstatus.app.context.MyContext
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.IsEmpty
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.RelativeTime
import org.andstatus.app.util.StringUtil
import java.util.*

open class ViewItem<T : ViewItem<T>> protected constructor(private val isEmptyIn: Boolean, val updatedDate: Long) : IsEmpty {
    private val children: MutableList<T> = ArrayList()
    private var parent: ViewItem<*>? = EmptyViewItem.EMPTY
    protected var insertedDate: Long = 0
    open fun getId(): Long {
        return 0
    }

    open fun getDate(): Long {
        return 0
    }

    fun getChildren(): MutableCollection<T> {
        return children
    }

    open fun duplicates(timeline: Timeline, preferredOrigin: Origin, other: T): DuplicationLink {
        return DuplicationLink.NONE
    }

    fun isCollapsed(): Boolean {
        return getChildrenCount() > 0
    }

    fun collapse(child: T) {
        getChildren().addAll(child.getChildren())
        child.getChildren().clear()
        getChildren().add(child)
    }

    open fun fromCursor(myContext: MyContext, cursor: Cursor): T {
        return getEmpty(TimelineType.UNKNOWN)
    }

    open fun matches(filter: TimelineFilter): Boolean {
        return true
    }

    override val isEmpty: Boolean
        get() {
            return isEmptyIn
        }

    protected fun getChildrenCount(): Int {
        return if (isEmpty) 0 else Integer.max(getParent().getChildrenCount(), getChildren().size)
    }

    fun setParent(parent: ViewItem<*>?) {
        this.parent = parent
    }

    fun getParent(): ViewItem<*> {
        return parent ?: EmptyViewItem.EMPTY
    }

    fun getTopmostId(): Long {
        return if (getParent().isEmpty) getId() else getParent().getId()
    }

    protected fun getMyStringBuilderWithTime(context: Context, showReceivedTime: Boolean): MyStringBuilder {
        val difference = RelativeTime.getDifference(context, updatedDate)
        val builder: MyStringBuilder = MyStringBuilder.of(difference)
        if (showReceivedTime && updatedDate > RelativeTime.SOME_TIME_AGO && insertedDate > updatedDate) {
            val receivedDifference = RelativeTime.getDifference(context, insertedDate)
            if (receivedDifference != difference) {
                builder.withSpace("(" + StringUtil.format(context, R.string.received_sometime_ago,
                        receivedDifference) + ")")
            }
        }
        return builder
    }

    open fun addActorsToLoad(loader: ActorsLoader) {
        // Empty
    }

    open fun setLoadedActors(loader: ActorsLoader) {
        // Empty
    }

    override fun hashCode(): Int {
        val result = java.lang.Long.hashCode(getId())
        return 31 * result + java.lang.Long.hashCode(getDate())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as T
        return getId() == that.getId() && getDate() == that.getDate()
    }

    companion object {
        fun <T : ViewItem<T>> getEmpty(timelineType: TimelineType): T {
            return ViewItemType.fromTimelineType(timelineType).emptyViewItem as T
        }
    }
}
