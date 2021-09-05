/*
 * Copyright (C) 2015-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.database.Cursor
import org.andstatus.app.actor.ActorsLoader
import org.andstatus.app.actor.ActorsScreenType
import org.andstatus.app.data.DbUtils
import org.andstatus.app.list.SyncLoader
import org.andstatus.app.timeline.LoadableListActivity.ProgressPublisher
import org.andstatus.app.util.I18n
import org.andstatus.app.util.MyLog
import org.andstatus.app.util.MyStringBuilder
import org.andstatus.app.util.StopWatch
import java.util.*
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
class TimelineLoader<T : ViewItem<T>>(
    private val params: TimelineParameters,
    private val instanceId: Long,
    private val page: TimelinePage<T> = TimelinePage(params, ArrayList())
) : SyncLoader<T>(page.items) {

    override fun load(publisher: ProgressPublisher?): SyncLoader<T> {
        val method = "load"
        val stopWatch: StopWatch = StopWatch.createStarted()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " started; " + params.toSummary())
        }
        params.timeline.save(params.getMyContext())
        if (params.whichPage != WhichPage.EMPTY) {
            filter(loadActors(loadFromCursor(queryDatabase())))
        }
        params.isLoaded = true
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " ended" + ", " + page.items.size + " rows,"
                    + " dates from " + MyLog.formatDateTime(page.params.minDateLoaded)
                    + " to " + MyLog.formatDateTime(page.params.maxDateLoaded)
                    + ", " + stopWatch.time + "ms")
        }
        return this
    }

    private fun queryDatabase(): Cursor? {
        val method = "queryDatabase"
        val stopWatch: StopWatch = StopWatch.createStarted()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "$method started")
        }
        var cursor: Cursor? = null
        for (attempt in 0..2) {
            try {
                cursor = params.queryDatabase()
                break
            } catch (e: IllegalStateException) {
                val message = "Attempt $attempt to prepare cursor"
                MyLog.d(this, "$instanceId $method; $message", e)
                if (DbUtils.waitBetweenRetries(message)) {
                    break
                }
            }
        }
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " ended, " + stopWatch.time + "ms")
        }
        return cursor
    }

    private fun loadFromCursor(cursor: Cursor?): MutableList<T> {
        val method = "loadFromCursor"
        val stopWatch: StopWatch = StopWatch.createStarted()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "$method started")
        }
        val items: MutableList<T> = ArrayList()
        var rowsCount = 0
        if (cursor != null && !cursor.isClosed) {
            try {
                if (cursor.moveToFirst()) {
                    do {
                        rowsCount++
                        val item: T = page.getEmptyItem().fromCursor(params.getMyContext(), cursor)
                        params.rememberItemDateLoaded(item.getDate())
                        items.add(item)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor.close()
            }
        }
        params.rowsLoaded = rowsCount
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, method + " ended; " + rowsCount + " rows, " + stopWatch.time + "ms")
        }
        return items
    }

    private fun loadActors(items: MutableList<T>): MutableList<T> {
        if (items.isEmpty() && !params.timeline.hasActorProfile()) return items
        val loader = ActorsLoader(params.getMyContext(), ActorsScreenType.ACTORS_AT_ORIGIN,
            params.timeline.getOrigin(), 0, "")
        items.forEach(Consumer { item: T -> item.addActorsToLoad(loader) })
        if (params.timeline.timelineType.hasActorProfile()) loader.addActorToList(params.timeline.actor)
        if (loader.getList().isEmpty()) return items
        loader.load(null)
        items.forEach(Consumer { item: T -> item.setLoadedActors(loader) })
        page.setLoadedActor(loader)
        return items
    }

    protected fun filter(items: MutableList<T>) {
        val method = "filter"
        val stopWatch: StopWatch = StopWatch.createStarted()
        if (MyLog.isDebugEnabled()) {
            MyLog.d(this, "$method started")
        }
        val filter = TimelineFilter(params.timeline)
        var rowsCount = 0
        var filteredOutCount = 0
        val reversedOrder = params.isSortOrderAscending()
        for (item in items) {
            rowsCount++
            if (item.matches(filter)) {
                if (reversedOrder) {
                    page.items.add(0, item)
                } else {
                    page.items.add(item)
                }
            } else {
                filteredOutCount++
                if (MyLog.isVerboseEnabled() && filteredOutCount < 6) {
                    MyLog.v(
                        this, filteredOutCount.toString() + " Filtered out: "
                            + I18n.trimTextAt(item.toString(), 200)
                    )
                }
            }
        }
        if (MyLog.isDebugEnabled()) {
            MyLog.d(
                this, method + " ended; Filtered out " + filteredOutCount + " of " + rowsCount
                    + " rows, " + stopWatch.time + "ms"
            )
        }
    }

    override fun toString(): String {
        return MyStringBuilder.formatKeyValue(this, params)
    }

    fun getPage(): TimelinePage<T> {
        return page
    }

}
