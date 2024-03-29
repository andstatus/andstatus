/*
 * Copyright (c) 2016 yvolk (Yuri Volkov), http://yurivolkov.com
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
package org.andstatus.app.timeline.meta

import android.database.Cursor
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.StopWatch
import org.andstatus.app.util.TriState
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Consumer
import java.util.stream.Stream

/**
 * @author yvolk@yurivolkov.com
 */
class PersistentTimelines private constructor(private val myContext: MyContext) {
    private val timelines: ConcurrentMap<Long, Timeline> = ConcurrentHashMap()

    suspend fun initialize(): PersistentTimelines {
        val stopWatch: StopWatch = StopWatch.createStarted()
        val method = "initialize"
        timelines.clear()
        MyQuery.getSet(
            myContext,
            "SELECT * FROM " + TimelineTable.TABLE_NAME,
            { cursor: Cursor -> Timeline.fromCursor(myContext, cursor) })
            .forEach(Consumer { timeline: Timeline ->
                if (timeline.isValid) {
                    timelines[timeline.id] = timeline
                    if (!timeline.isCombined && !timeline.isSyncable && timeline.timelineType == TimelineType.HOME) {
                        MyLog.e(this, "HOME is not syncable: $timeline" +
                            "\n  ${timeline.myAccountToSync}" +
                            "\n  ${timeline.myAccountToSync.actor.endpoints}" +
                            "\n  ${MyLog.currentStackTrace}" +
                            "\n  $myContext"
                        )
                    }
                    if (MyLog.isVerboseEnabled() && timelines.size < 5) {
                        MyLog.v(PersistentTimelines::class.java, "$method; $timeline")
                    }
                } else MyLog.w(PersistentTimelines::class.java, "$method; invalid skipped $timeline")
            })
        MyLog.i(this, "timelinesInitializedMs:" + stopWatch.time + "; " + timelines.size + " timelines")
        return this
    }

    fun fromId(id: Long): Timeline {
        return Optional.ofNullable(timelines[id]).orElseGet { addNew(Timeline.fromId(myContext, id)) }
    }

    fun getDefault(): Timeline {
        var defaultTimeline: Timeline = Timeline.EMPTY
        for (timeline in values()) {
            if (defaultTimeline.compareTo(timeline) > 0 || !defaultTimeline.isValid) {
                defaultTimeline = timeline
            }
        }
        return defaultTimeline
    }

    fun setDefault(timelineIn: Timeline) {
        val prevDefault = getDefault()
        if (timelineIn != prevDefault && timelineIn.selectorOrder >= prevDefault.selectorOrder) {
            timelineIn.selectorOrder = Math.min(prevDefault.selectorOrder - 1, -1)
        }
    }

    fun forUserAtHomeOrigin(timelineType: TimelineType, actor: Actor): Timeline {
        return forUser(timelineType, actor.toHomeOrigin())
    }

    fun forUser(timelineType: TimelineType, actor: Actor): Timeline {
        return get(0, timelineType, actor, Origin.EMPTY, "")
    }

    operator fun get(timelineType: TimelineType, actor: Actor, origin: Origin): Timeline {
        return get(0, timelineType, actor, origin, "")
    }

    operator fun get(timelineType: TimelineType, actor: Actor, origin: Origin, searchQuery: String?): Timeline {
        return get(0, timelineType, actor, origin, searchQuery)
    }

    operator fun get(
        id: Long, timelineType: TimelineType,
        actor: Actor, origin: Origin, searchQuery: String?
    ): Timeline {
        if (id != 0L) return fromId(id)
        if (timelineType == TimelineType.UNKNOWN) return Timeline.EMPTY
        val newTimeline = Timeline(myContext, id, timelineType, actor, origin, searchQuery, 0)
        return stream().filter { that: Timeline -> newTimeline.duplicates(that) }.findAny().orElse(newTimeline)
    }

    fun stream(): Stream<Timeline> {
        return values().stream()
    }

    fun values(): MutableCollection<Timeline> {
        return timelines.values
    }

    fun toAutoSyncForAccount(ma: MyAccount): MutableList<Timeline> {
        val timelines: MutableList<Timeline> = ArrayList()
        if (ma.isValidAndSucceeded()) {
            for (timeline in values()) {
                if (timeline.isSyncedAutomatically &&
                    (!timeline.timelineType.isAtOrigin && timeline.myAccountToSync == ma ||
                        timeline.timelineType.isAtOrigin && timeline.origin == ma.origin) &&
                    timeline.isTimeToAutoSync
                ) {
                    timelines.add(timeline)
                }
            }
        }
        return timelines
    }

    fun toTimelinesToSync(timelineToSync: Timeline): Stream<Timeline> {
        return when {
            timelineToSync.isSyncableForOrigins -> {
                myContext.origins.originsToSync(
                    timelineToSync.myAccountToSync.origin, true, timelineToSync.hasSearchQuery
                )
                    .stream().map { origin: Origin -> timelineToSync.cloneForOrigin(myContext, origin) }
            }
            timelineToSync.isSyncableForAccounts -> {
                myContext.accounts.accountsToSync()
                    .stream().map { account: MyAccount -> timelineToSync.cloneForAccount(myContext, account) }
            }
            timelineToSync.isSyncable -> {
                Stream.of(timelineToSync)
            }
            else -> {
                Stream.empty()
            }
        }
    }

    fun filter(
        isForSelector: Boolean,
        isTimelineCombined: TriState,
        timelineType: TimelineType,
        actor: Actor,
        origin: Origin
    ): Stream<Timeline> {
        return stream().filter { timeline: Timeline ->
            timeline.match(
                isForSelector,
                isTimelineCombined,
                timelineType,
                actor,
                origin
            )
        }
    }

    fun onAccountDelete(ma: MyAccount) {
        val toRemove: MutableList<Timeline> = ArrayList()
        for (timeline in values()) {
            if (timeline.myAccountToSync == ma) {
                timeline.delete(myContext)
                toRemove.add(timeline)
            }
        }
        for (timeline in toRemove) {
            timelines.remove(timeline.id)
        }
    }

    fun delete(timeline: Timeline) {
        if (myContext.isReady) {
            timeline.delete(myContext)
            timelines.remove(timeline.id)
        }
    }

    fun saveChanged(postExecute: (Boolean) -> Unit = {}) {
        return TimelineSaver().execute(myContext, postExecute)
    }

    fun addNew(timeline: Timeline): Timeline {
        if (timeline.isValid && timeline.id != 0L) timelines.putIfAbsent(timeline.id, timeline)
        return timelines.getOrDefault(timeline.id, timeline)
    }

    fun resetCounters(all: Boolean) {
        for (timeline in values()) {
            timeline.resetCounters(all)
        }
    }

    fun resetDefaultSelectorOrder() {
        for (timeline in values()) {
            timeline.selectorOrder = timeline.getDefaultSelectorOrder()
        }
    }

    companion object {
        fun newEmpty(myContext: MyContext): PersistentTimelines {
            return PersistentTimelines(myContext)
        }
    }
}
