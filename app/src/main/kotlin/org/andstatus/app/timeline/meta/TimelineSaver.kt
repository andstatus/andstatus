/*
 * Copyright (c) 2016-2020 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.provider.BaseColumns
import org.andstatus.app.account.MyAccount
import org.andstatus.app.context.MyContext
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.os.MyAsyncTask
import org.andstatus.app.os.NonUiThreadExecutor
import org.andstatus.app.util.TriState
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Save changes to Timelines not on UI thread.
 * Optionally creates default timelines: for all or for one account.
 * @author yvolk@yurivolkov.com
 */
class TimelineSaver {
    private var addDefaults = false
    private var myAccount: MyAccount = MyAccount.EMPTY

    fun setAccount(myAccount: MyAccount): TimelineSaver {
        this.myAccount = myAccount
        return this
    }

    fun setAddDefaults(addDefaults: Boolean): TimelineSaver {
        this.addDefaults = addDefaults
        return this
    }

    fun execute(myContext: MyContext): CompletableFuture<MyContext> {
        return if (MyAsyncTask.isUiThread()) {
            CompletableFuture.supplyAsync({ executeSynchronously(myContext) }, NonUiThreadExecutor.INSTANCE)
        } else {
            CompletableFuture.completedFuture(executeSynchronously(myContext))
        }
    }

    private fun executeSynchronously(myContext: MyContext): MyContext {
        for (count in 30 downTo 1) {
            if (executing.compareAndSet(false, true)) {
                executeSequentially(myContext)
            }
            DbUtils.waitMs(this, 50)
        }
        return myContext
    }

    private fun executeSequentially(myContext: MyContext) {
        try {
            if (addDefaults) {
                if (myAccount === MyAccount.EMPTY) {
                    addDefaultTimelinesIfNoneFound(myContext)
                } else {
                    addDefaultMyAccountTimelinesIfNoneFound(myContext, myAccount)
                }
            }
            for (timeline in myContext.timelines.values()) {
                timeline.save(myContext)
            }
        } finally {
            executing.set(false)
        }
    }

    private fun addDefaultTimelinesIfNoneFound(myContext: MyContext) {
        myContext.accounts.get().forEach(Consumer { ma: MyAccount -> addDefaultMyAccountTimelinesIfNoneFound(myContext, ma) })
    }

    private fun addDefaultMyAccountTimelinesIfNoneFound(myContext: MyContext, ma: MyAccount) {
        if (ma.isValid && myContext.timelines.filter(false, TriState.FALSE,
                        TimelineType.UNKNOWN, ma.actor,  Origin.EMPTY).count() == 0L) {
            addDefaultCombinedTimelinesIfNoneFound(myContext)
            addDefaultOriginTimelinesIfNoneFound(myContext, ma.origin)
            val timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    BaseColumns._ID, TimelineTable.ACTOR_ID + "=" + ma.actorId)
            if (timelineId == 0L) addDefaultForMyAccount(myContext, ma)
        }
    }

    private fun addDefaultCombinedTimelinesIfNoneFound(myContext: MyContext) {
        if (myContext.timelines.filter(false, TriState.TRUE,
                        TimelineType.UNKNOWN, Actor.EMPTY,  Origin.EMPTY).count() == 0L) {
            val timelineId = MyQuery.conditionToLongColumnValue(TimelineTable.TABLE_NAME,
                    BaseColumns._ID, TimelineTable.ACTOR_ID + "=0 AND " + TimelineTable.ORIGIN_ID + "=0")
            if (timelineId == 0L) addDefaultCombined(myContext)
        }
    }

    private fun addDefaultOriginTimelinesIfNoneFound(myContext: MyContext, origin: Origin) {
        if (!origin.isValid()) return
        val timelineId = MyQuery.conditionToLongColumnValue(myContext.database,
                "Any timeline for " + origin.name,
                TimelineTable.TABLE_NAME, BaseColumns._ID,
                TimelineTable.ORIGIN_ID + "=" + origin.id)
        if (timelineId == 0L) addDefaultForOrigin(myContext, origin)
    }

    fun addDefaultForMyAccount(myContext: MyContext, myAccount: MyAccount) {
        for (timelineType in myAccount.actor.getDefaultMyAccountTimelineTypes()) {
            myContext.timelines.forUser(timelineType, myAccount.actor).save(myContext)
        }
    }

    private fun addDefaultForOrigin(myContext: MyContext, origin: Origin) {
        for (timelineType in TimelineType.getDefaultOriginTimelineTypes()) {
            if (origin.originType.isTimelineTypeSyncable(timelineType)
                    || timelineType == TimelineType.EVERYTHING) {
                myContext.timelines[timelineType, Actor.EMPTY, origin].save(myContext)
            }
        }
    }

    fun addDefaultCombined(myContext: MyContext) {
        for (timelineType in TimelineType.values()) {
            if (timelineType.isCombinedRequired()) {
                myContext.timelines[timelineType, Actor.EMPTY,  Origin.EMPTY].save(myContext)
            }
        }
    }

    companion object {
        private val executing: AtomicBoolean = AtomicBoolean(false)
    }
}
