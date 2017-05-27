/*
 * Copyright (C) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.net.social;

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/ */
public class MbActivity {
    private TimelinePosition timelineItemPosition = TimelinePosition.EMPTY;
    private long timelineItemDate = 0;

    private MbMessage mbMessage = null;
    private MbUser mbUser = null;

    public MbObjectType getObjectType() {
        if (mbMessage != null && !mbMessage.isEmpty()) {
            return MbObjectType.MESSAGE;
        } else if ( mbUser != null && !mbUser.isEmpty()) {
            return MbObjectType.USER;
        } else {
            return MbObjectType.EMPTY;
        }
    }

    public boolean isEmpty() {
        return getObjectType() == MbObjectType.EMPTY;
    }

    public TimelinePosition getTimelineItemPosition() {
        return timelineItemPosition;
    }

    public void setTimelineItemPosition(String strPosition) {
        this.timelineItemPosition = new TimelinePosition(strPosition);
    }

    public long getTimelineItemDate() {
        return timelineItemDate;
    }

    public void setTimelineItemDate(long timelineItemDate) {
        this.timelineItemDate = timelineItemDate;
    }

    public MbMessage getMessage() {
        return mbMessage;
    }

    public void setMessage(MbMessage mbMessage) {
        this.mbMessage = mbMessage;
    }

    public MbUser getUser() {
        return mbUser;
    }

    public void setUser(MbUser mbUser) {
        this.mbUser = mbUser;
    }
}
