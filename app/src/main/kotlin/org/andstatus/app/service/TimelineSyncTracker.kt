/**
 * Copyright (C) 2013-2017 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.andstatus.app.service

import org.andstatus.app.net.social.InputTimelinePage
import org.andstatus.app.net.social.TimelinePosition
import org.andstatus.app.timeline.meta.Timeline
import java.util.*

/**
 * Retrieves and saves information about times and positions in a Timeline of the youngest/oldest downloaded timeline items.
 * The "timeline item" is e.g. a "note" for Twitter and an "Activity" for Pump.Io.
 */
class TimelineSyncTracker(private val timeline: Timeline, private val isSyncYounger: Boolean) {
    var requestedPositions: MutableList<TimelinePosition> = ArrayList()
    var firstPosition: TimelinePosition = TimelinePosition.EMPTY
    private var nextPosition: TimelinePosition = TimelinePosition.EMPTY
    private var downloadedCounter: Long = 0
    fun getPreviousPosition(): TimelinePosition {
        return if (requestedPositions.isEmpty()) getPreviousTimelinePosition() else requestedPositions.get(requestedPositions.size - 1)
    }

    private fun getPreviousTimelinePosition(): TimelinePosition {
        return TimelinePosition.of(if (isSyncYounger) timeline.getYoungestPosition() else timeline.getOldestPosition())
    }

    /**
     * @return Sent Date of the last downloaded note from this timeline
     */
    fun getPreviousItemDate(): Long {
        return if (isSyncYounger) timeline.getYoungestItemDate() else timeline.getOldestItemDate()
    }

    /**
     * @return Last date when this timeline was successfully downloaded
     */
    fun getPreviousSyncedDate(): Long {
        return if (isSyncYounger) timeline.getYoungestSyncedDate() else timeline.getOldestSyncedDate()
    }

    fun onPositionRequested(position: TimelinePosition) {
        requestedPositions.add(position)
        if (position.isEmpty) {
            clearPosition()
        }
    }

    fun onNewPage(page: InputTimelinePage) {
        if (page.firstPosition.nonEmpty) {
            firstPosition = page.firstPosition
        }
        nextPosition = if (isSyncYounger) page.youngerPosition else page.olderPosition
    }

    /** A new Timeline Item was downloaded    */
    fun onNewActivity(timelineItemDate: Long, prevPosition: TimelinePosition?, nextPosition: TimelinePosition?) {
        downloadedCounter++
        timeline.onNewMsg(timelineItemDate,
                if (prevPosition == null) "" else prevPosition.getPosition(),
                if (nextPosition == null) "" else nextPosition.getPosition())
    }

    fun onTimelineDownloaded() {
        if (isSyncYounger) {
            timeline.setYoungestSyncedDate(System.currentTimeMillis())
        } else {
            timeline.setOldestSyncedDate(System.currentTimeMillis())
        }
    }

    fun getDownloadedCounter(): Long {
        return downloadedCounter
    }

    fun getNextPositionToRequest(): Optional<TimelinePosition> {
        val candidate = if (nextPosition.isEmpty) getPreviousTimelinePosition() else nextPosition
        if (candidate.nonEmpty && !requestedPositions.contains(candidate)) return Optional.of(candidate)
        return if (downloadedCounter == 0L && firstPosition.nonEmpty && !requestedPositions.contains(firstPosition)) {
            Optional.of(firstPosition)
        } else Optional.empty()
    }

    fun onNotFound(): Optional<TimelinePosition> {
        return if (downloadedCounter > 0 || requestedPositions.contains(TimelinePosition.EMPTY)) Optional.empty()
        else Optional.of(TimelinePosition.EMPTY)
    }

    override fun toString(): String {
        return (TAG + "[" + timeline.toString()
                + ", " + timeline.positionsToString()
                + ", " + downloadedCounter
                + "]")
    }

    fun clearPosition() {
        timeline.forgetPositionsAndDates()
        timeline.save(timeline.myContext)
    }

    companion object {
        private val TAG: String = TimelineSyncTracker::class.simpleName.toString()
    }
}
