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

package org.andstatus.app.timeline;

import org.andstatus.app.net.social.TimelinePosition;

/**
 * Retrieves and saves information about times and positions in a Timeline of the youngest/oldest downloaded timeline items.
 * The "timeline item" is e.g. a "message" for Twitter and an "Activity" for Pump.Io.
 */
public class TimelineSyncTracker {
    private static final String TAG = TimelineSyncTracker.class.getSimpleName();

    private final Timeline timeline;
    private final boolean isSyncYounger;

    public TimelineSyncTracker(Timeline timeline, boolean syncYounger) {
        this.timeline = timeline;
        this.isSyncYounger = syncYounger;
    }
    
    public TimelinePosition getPreviousPosition() {
        return new TimelinePosition(isSyncYounger ? timeline.getYoungestPosition() : timeline.getOldestPosition());
    }

    /**
     * @return Sent Date of the last downloaded message from this timeline
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

    /** A new Timeline Item was downloaded   */
    public void onNewMsg(TimelinePosition timelineItemPosition, long timelineItemDate) {
        if (timelineItemPosition != null 
                && timelineItemPosition.isPresent()
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
    
    @Override
    public String toString() {
        return TAG + "[" + timeline.toString()
                    + ", " + timeline.positionsToString()
                    + "]";
    }

    public void clearPosition() {
        timeline.clearPosition();
    }
}
