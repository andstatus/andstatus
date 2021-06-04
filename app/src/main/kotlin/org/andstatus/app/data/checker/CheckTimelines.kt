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
package org.andstatus.app.data.checker

import android.database.Cursor
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.TimelineTable
import org.andstatus.app.timeline.meta.Timeline
import org.andstatus.app.timeline.meta.TimelineSaver
import org.andstatus.app.util.MyLog
import java.util.*
import java.util.function.Consumer

/**
 * @author yvolk@yurivolkov.com
 */
internal class CheckTimelines : DataChecker() {
    override fun fixInternal(): Long {
        return deleteInvalidTimelines() + addDefaultTimelines() + removeDuplicatedTimelines()
    }

    private fun deleteInvalidTimelines(): Long {
        logger.logProgress("Checking if invalid timelines are present")
        val size1 = myContext.timelines.values().size.toLong()
        val toDelete: MutableSet<Timeline> = HashSet()
        var deletedCount: Long = 0
        try {
            MyQuery[myContext, "SELECT * FROM " + TimelineTable.TABLE_NAME,
                    { cursor: Cursor -> Timeline.fromCursor(myContext, cursor) }]
                    .forEach(Consumer { timeline: Timeline ->
                if (!timeline.isValid()) {
                    logger.logProgress("Invalid timeline: $timeline")
                    DbUtils.waitMs(this, 1000)
                    toDelete.add(timeline)
                }
            })
            deletedCount = toDelete.size.toLong()
            if (!countOnly) {
                toDelete.forEach(Consumer { timeline: Timeline -> myContext.timelines.delete(timeline) })
            }
        } catch (e: Exception) {
            val logMsg = "Error: " + e.message
            logger.logProgress(logMsg)
            DbUtils.waitMs(this, 5000)
            MyLog.e(this, logMsg, e)
        }
        myContext.timelines.saveChanged()
        logger.logProgress(if (deletedCount == 0L) "No invalid timelines found"
        else (if (countOnly) "To delete " else "Deleted ") + deletedCount + " invalid timelines. Valid timelines: " + size1)
        DbUtils.waitMs(this, if (deletedCount == 0L) 1000 else 3000)
        return deletedCount
    }

    private fun addDefaultTimelines(): Long {
        logger.logProgress("Checking if all default timelines are present")
        val size1 = myContext.timelines.values().size.toLong()
        try {
            TimelineSaver().addDefaultCombined(myContext)
            for (myAccount in myContext.accounts.get()) {
                TimelineSaver().addDefaultForMyAccount(myContext, myAccount)
            }
        } catch (e: Exception) {
            val logMsg = "Error: " + e.message
            logger.logProgress(logMsg)
            MyLog.e(this, logMsg, e)
        }
        myContext.timelines.saveChanged()
        val size2 = myContext.timelines.values().size
        val addedCount = size2 - size1
        logger.logProgress(if (addedCount == 0L) "No new timelines were added. $size2 timelines" else "Added $addedCount of $size2 timelines")
        DbUtils.waitMs(this, if (addedCount == 0L) 1000 else 3000)
        return addedCount
    }

    private fun removeDuplicatedTimelines(): Long {
        logger.logProgress("Checking if duplicated timelines are present")
        val size1 = myContext.timelines.values().size.toLong()
        val toDelete: MutableSet<Timeline> = HashSet()
        try {
            for (timeline1 in myContext.timelines.values()) {
                for (timeline2 in myContext.timelines.values()) {
                    if (timeline2.duplicates(timeline1)) toDelete.add(timeline2)
                }
            }
            if (!countOnly) {
                toDelete.forEach(Consumer { timeline: Timeline -> myContext.timelines.delete(timeline) })
            }
        } catch (e: Exception) {
            val logMsg = "Error: " + e.message
            logger.logProgress(logMsg)
            MyLog.e(this, logMsg, e)
        }
        myContext.timelines.saveChanged()
        val size2 = myContext.timelines.values().size
        val deletedCount = if (countOnly) toDelete.size else size1 - size2
        logger.logProgress(if (deletedCount == 0L) "No duplicated timelines found. $size2 timelines" else (if (countOnly) "To delete " else "Deleted ") + deletedCount +
                " duplicates of " + size1 + " timelines")
        DbUtils.waitMs(this, if (deletedCount == 0L) 1000 else 3000)
        return deletedCount.toLong()
    }
}
