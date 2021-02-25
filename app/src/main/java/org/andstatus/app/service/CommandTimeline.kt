/*
 * Copyright (C) 2020 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.service

import android.content.ContentValues
import android.database.Cursor
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DbUtils
import org.andstatus.app.database.table.CommandTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.LazyVal
import org.andstatus.app.util.MyStringBuilder
import java.util.*
import java.util.function.Supplier

/**
 * Command data about a Timeline. The timeline is lazily evaluated
 * @author yvolk@yurivolkov.com
 */
class CommandTimeline(private val myContext: MyContext,
                      val origin: Origin,
                      val timelineIn: LazyVal<Timeline>?) {
    val timeline: LazyVal<Timeline> = timelineIn ?: LazyVal.of { evaluateTimeline() }
    private var id: Long = 0
    var timelineType: TimelineType = TimelineType.UNKNOWN
    private var actorId: Long = 0
    var searchQuery: String? = null

    private fun evaluateTimeline(): Timeline {
        if (id != 0L) return myContext.timelines().fromId(id)
        val actor: Actor = Actor.Companion.load(myContext, actorId)
        return myContext.timelines().get(id, timelineType, actor, origin ?: Origin.EMPTY, searchQuery)
    }

    fun toContentValues(values: ContentValues) {
        values.put(CommandTable.TIMELINE_ID, getId())
        values.put(CommandTable.TIMELINE_TYPE, timelineType.save())
        values.put(CommandTable.ACTOR_ID, actorId)
        values.put(CommandTable.ORIGIN_ID, origin.id)
        values.put(CommandTable.SEARCH_QUERY, searchQuery)
    }

    private fun getId(): Long {
        return if (timeline.isEvaluated()) timeline.get().getId() else id
    }

    fun isValid(): Boolean {
        return timelineType != TimelineType.UNKNOWN
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val data = o as CommandTimeline
        return getId() == data.getId() && actorId == data.actorId && timelineType == data.timelineType && origin == data.origin && searchQuery == data.searchQuery
    }

    override fun hashCode(): Int {
        return Objects.hash(getId(), timelineType, actorId, origin, searchQuery)
    }

    override fun toString(): String {
        if (timeline.isEvaluated()) return timeline.get().toString()
        val builder = MyStringBuilder()
        if (timelineType.isAtOrigin()) {
            builder.withComma(if (origin.isValid()) origin.name else "(all origins)")
        }
        if (timelineType.isForUser()) {
            if (actorId == 0L) {
                builder.withComma("(all accounts)")
            }
        }
        if (timelineType != TimelineType.UNKNOWN) {
            builder.withComma("type", timelineType.save())
        }
        if (actorId != 0L) {
            builder.withComma("actorId", actorId)
        }
        if (!searchQuery.isNullOrEmpty()) {
            builder.withCommaQuoted("search", searchQuery, true)
        }
        if (id != 0L) {
            builder.withComma("id", id)
        }
        return builder.toKeyValue("CommandTimeline")
    }

    companion object {
        fun of(timeline: Timeline): CommandTimeline {
            val data = CommandTimeline(timeline.myContext, timeline.getOrigin(), LazyVal.of<Timeline?>(timeline))
            data.id = timeline.getId()
            data.timelineType = timeline.timelineType
            data.actorId = timeline.getActorId()
            data.searchQuery = timeline.getSearchQuery()
            return data
        }

        fun fromCursor(myContext: MyContext, cursor: Cursor): CommandTimeline {
            val data = CommandTimeline(myContext,
                    myContext.origins().fromId(DbUtils.getLong(cursor, CommandTable.ORIGIN_ID)),
                    null)
            data.id = DbUtils.getLong(cursor, CommandTable.TIMELINE_ID)
            data.timelineType = TimelineType.Companion.load(DbUtils.getString(cursor, CommandTable.TIMELINE_TYPE))
            data.actorId = DbUtils.getLong(cursor, CommandTable.ACTOR_ID)
            data.searchQuery = DbUtils.getString(cursor, CommandTable.SEARCH_QUERY)
            return data
        }
    }
}