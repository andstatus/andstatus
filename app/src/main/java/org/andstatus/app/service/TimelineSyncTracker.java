/**
 * Copyright (C) 2013-2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.service;

import org.andstatus.app.net.social.InputTimelinePage;
import org.andstatus.app.net.social.TimelinePosition;
import org.andstatus.app.timeline.meta.Timeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Retrieves and saves information about times and positions in a Timeline of the youngest/oldest downloaded timeline items.
 * The "timeline item" is e.g. a "note" for Twitter and an "Activity" for Pump.Io.
 */
public class TimelineSyncTracker {
    private static final String TAG = TimelineSyncTracker.class.getSimpleName();

    private final Timeline timeline;
    private final boolean isSyncYounger;
    List<TimelinePosition> requestedPositions = new ArrayList<>();
    TimelinePosition firstPosition = TimelinePosition.EMPTY;
    private TimelinePosition nextPosition = TimelinePosition.EMPTY;
    private long downloadedCounter = 0;

    public TimelineSyncTracker(Timeline timeline, boolean syncYounger) {
        this.timeline = timeline;
        this.isSyncYounger = syncYounger;
    }
    
    public TimelinePosition getPreviousPosition() {
        return requestedPositions.isEmpty()
            ? getPreviousTimelinePosition()
            : requestedPositions.get(requestedPositions.size() - 1);
    }

    private TimelinePosition getPreviousTimelinePosition() {
        return TimelinePosition.of(isSyncYounger ? timeline.getYoungestPosition() : timeline.getOldestPosition());
    }

    /**
     * @return Sent Date of the last downloaded note from this timeline
     */
    public long getPreviousItemDate() {
        return isSyncYounger ? timeline.getYoungestItemDate() : timeline.getOldestItemDate();
    }

    /**
     * @return Last date when this timeline was successfully downloaded
     */
    public long getPreviousSyncedDate() {
        return isSyncYounger ? timeline.getYoungestSyncedDate() : timeline.getOldestSyncedDate();
    }

    public void onPositionRequested(TimelinePosition position) {
        requestedPositions.add(position);
        if (position.isEmpty()) {
            clearPosition();
        }
    }

    public void onNewPage(InputTimelinePage page) {
        if (page.firstPosition.nonEmpty()) {
            firstPosition = page.firstPosition;
        }
        nextPosition = isSyncYounger ? page.youngerPosition : page.olderPosition;
    }

    /** A new Timeline Item was downloaded   */
    public void onNewActivity(TimelinePosition timelineItemPosition, long timelineItemDate) {
        downloadedCounter++;
        if (timelineItemPosition != null 
                && timelineItemPosition.nonEmpty()
                && (timelineItemDate > 0)) {
            timeline.onNewMsg(timelineItemDate, timelineItemPosition.getPosition());
        }
    }
    
    public void onTimelineDownloaded() {
        if (isSyncYounger) {
            timeline.setYoungestSyncedDate(System.currentTimeMillis());
        } else {
            timeline.setOldestSyncedDate(System.currentTimeMillis());
        }
    }

    public long getDownloadedCounter() {
        return downloadedCounter;
    }

    public Optional<TimelinePosition> getNextPositionToRequest() {
        TimelinePosition candidate = nextPosition.isEmpty()
            ? getPreviousTimelinePosition()
            : nextPosition;
        if (candidate.nonEmpty() && !requestedPositions.contains(candidate)) return Optional.of(candidate);

        if (downloadedCounter == 0 && firstPosition.nonEmpty() && !requestedPositions.contains(firstPosition)) {
            return Optional.of(firstPosition);
        }

        return Optional.empty();
    }

    public Optional<TimelinePosition> onNotFound() {
        return downloadedCounter > 0 || requestedPositions.contains(TimelinePosition.EMPTY)
                ? Optional.empty()
                : Optional.of(TimelinePosition.EMPTY);
    }

    @Override
    public String toString() {
        return TAG + "[" + timeline.toString()
                    + ", " + timeline.positionsToString()
                    + ", " + downloadedCounter
                    + "]";
    }

    public void clearPosition() {
        timeline.forgetPositionsAndDates();
    }
}
