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
    public static final MbActivity EMPTY = from(MbUser.EMPTY, MbActivityType.EMPTY);
    private TimelinePosition timelinePosition = TimelinePosition.EMPTY;
    private long timelineDate = 0;

    @NonNull
    public final MbUser accountUser;
    private MbUser actor = MbUser.EMPTY;
    @NonNull
    public final MbActivityType type;

    // Objects of the Activity may be of several types...
    @NonNull
    private MbMessage mbMessage = MbMessage.EMPTY;
    @NonNull
    private MbUser mbUser = MbUser.EMPTY;
    private MbActivity mbActivity = null;

    public static MbActivity from(MbUser accountUser, MbActivityType type) {
        return new MbActivity(accountUser, type);
    }

    private MbActivity(MbUser accountUser, MbActivityType type) {
        this.accountUser = accountUser == null ? MbUser.EMPTY : accountUser;
        this.type = type == null ? MbActivityType.EMPTY : type;
    }

    @NonNull
    public MbUser getActor() {
        if (!actor.isEmpty()) {
            return actor;
        }
        switch (getObjectType()) {
            case USER:
                return mbUser;
            case MESSAGE:
                return getMessage().getAuthor();
            default:
                return MbUser.EMPTY;
        }
    }

    public void setActor(MbUser actor) {
        this.actor = actor == null ? MbUser.EMPTY : actor;
    }

    public boolean isAuthorActor() {
        return getActor().oid.equals(getMessage().getAuthor().oid);
    }

    public boolean isActorMe() {
        return getActor().oid.equals(accountUser.oid);
    }

    public boolean isAuthorMe() {
        return getMessage().getAuthor().oid.equals(accountUser.oid);
    }

    @NonNull
    public MbObjectType getObjectType() {
        if (!mbMessage.isEmpty()) {
            return MbObjectType.MESSAGE;
        } else if (!mbUser.isEmpty()) {
            return MbObjectType.USER;
        } else if (mbActivity!= null && !mbActivity.isEmpty()) {
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
        return mbActivity == null ? EMPTY : mbActivity;
    }

    @Override
    public String toString() {
        return "MbActivity{" +
                type +
                ", timelinePosition=" + timelinePosition +
                (timelineDate == 0 ? "" : ", timelineDate=" + timelineDate) +
                (accountUser.isEmpty() ? "" : ", me=" + accountUser.getUserName()) +
                (actor.isEmpty() ? "" : ", actor=" + actor) +
                (mbMessage.isEmpty() ? "" : ", message=" + mbMessage) +
                (getActivity().isEmpty() ? "" : ", activity=" + getActivity()) +
                (mbUser.isEmpty() ? "" : ", user=" + mbUser) +
                '}';
    }
}
