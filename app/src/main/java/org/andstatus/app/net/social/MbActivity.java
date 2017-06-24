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

import android.support.annotation.NonNull;

/** Activity in a sense of Activity Streams https://www.w3.org/TR/activitystreams-core/ */
public class MbActivity {
    public static final MbActivity EMPTY = from(MbActivityType.EMPTY);
    private TimelinePosition timelinePosition = TimelinePosition.EMPTY;
    private long timelineDate = 0;

    private MbUser actor = MbUser.EMPTY;
    public final MbActivityType type;

    // Objects of the Activity may be of several types...
    private MbMessage mbMessage = MbMessage.EMPTY;
    private MbUser mbUser = MbUser.EMPTY;
    private MbActivity mbActivity = MbActivity.EMPTY;

    public static MbActivity from(MbActivityType type) {
        return new MbActivity(type);
    }

    private MbActivity(MbActivityType type) {
        this.type = type;
    }

    @NonNull
    public static MbActivity undo(@NonNull MbActivity activity) {
        MbActivity undoActivity = from(MbActivityType.UNDO);
        undoActivity.actor = activity.getActor();
        undoActivity.mbActivity = activity;
        return undoActivity;
    }

    @NonNull
    public MbUser getActor() {
        if (!actor.isEmpty()) {
            return actor;
        }
        switch (getObjectType()) {
            case MESSAGE:
                return mbMessage.getActor();
            case USER:
                return mbUser.actor;
            default:
                return MbUser.EMPTY;
        }
    }

    public void setActor(MbUser actor) {
        this.actor = actor;
    }

    @NonNull
    public MbObjectType getObjectType() {
        if (!mbMessage.isEmpty()) {
            return MbObjectType.MESSAGE;
        } else if (!mbUser.isEmpty()) {
            return MbObjectType.USER;
        } else if (!mbActivity.isEmpty()) {
            return MbObjectType.ACTIVITY;
        } else {
            return MbObjectType.EMPTY;
        }
    }

    public boolean isEmpty() {
        return type == MbActivityType.EMPTY || getObjectType() == MbObjectType.EMPTY;
    }

    public TimelinePosition getTimelinePosition() {
        return timelinePosition;
    }

    public void setTimelinePosition(String strPosition) {
        this.timelinePosition = new TimelinePosition(strPosition);
    }

    public long getTimelineDate() {
        return timelineDate;
    }

    public void setTimelineDate(long timelineDate) {
        this.timelineDate = timelineDate;
    }

    @NonNull
    public MbMessage getMessage() {
        return mbMessage;
    }

    public void setMessage(MbMessage mbMessage) {
        this.mbMessage = mbMessage == null ? MbMessage.EMPTY : mbMessage;
    }

    @NonNull
    public MbUser getUser() {
        return mbUser;
    }

    public void setUser(MbUser mbUser) {
        this.mbUser = mbUser == null ? MbUser.EMPTY : mbUser;
    }

    @NonNull
    public MbActivity getActivity() {
        return mbActivity;
    }
}
