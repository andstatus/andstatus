/*
 * Copyright (C) 2017-2018 yvolk (Yuri Volkov), http://yurivolkov.com
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

package org.andstatus.app.notification;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.net.social.Actor;
import org.andstatus.app.origin.Origin;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;

import androidx.annotation.NonNull;

import static org.andstatus.app.util.RelativeTime.DATETIME_MILLIS_NEVER;

public class NotificationData {
    public static final NotificationData EMPTY = new NotificationData(NotificationEventType.EMPTY, Actor.EMPTY,
            DATETIME_MILLIS_NEVER);
    public final NotificationEventType event;
    public final Actor myActor;
    volatile long updatedDate;
    volatile long count;

    public NotificationData(@NonNull NotificationEventType event, @NonNull Actor myActor, long updatedDate) {
        this.event = event;
        this.myActor = myActor;
        this.updatedDate = updatedDate;
        count = updatedDate > DATETIME_MILLIS_NEVER ? 1 : 0;
    }

    synchronized void addEventsAt(long numberOfEvents, long updatedDate) {
        if (numberOfEvents < 1 || updatedDate <= DATETIME_MILLIS_NEVER) return;

        count += numberOfEvents;
        if (this.updatedDate < updatedDate) this.updatedDate = updatedDate;
    }

    PendingIntent getPendingIntent(MyContext myContext) {
        TimelineType timelineType = TimelineType.from(event);
        // When clicking on notifications, always open Combine timeline for Unread notifications
        Timeline timeline = myContext.timelines().get(timelineType,
                timelineType == TimelineType.UNREAD_NOTIFICATIONS ? Actor.EMPTY : myActor, Origin.EMPTY)
                .orElse(myContext.timelines().getDefault());
        Intent intent = new Intent(myContext.context(), FirstActivity.class);
        // "rnd" is necessary to actually bring Extra to the target intent
        // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
        intent.setData(Uri.withAppendedPath(timeline.getUri(),
                "rnd/" + android.os.SystemClock.elapsedRealtime()));
        return PendingIntent.getActivity(myContext.context(), timeline.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    String channelId() {
        return "channel_" + event.id;
    }

    public long getCount() {
        return count;
    }
}
