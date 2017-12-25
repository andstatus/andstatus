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

package org.andstatus.app.notification;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;

/**
 *
 */
public class NotificationData {
    public static final NotificationData EMPTY = new NotificationData(NotificationEventType.EMPTY, MyAccount.EMPTY);
    public final NotificationEventType event;
    public final MyAccount myAccount;
    public volatile long updatedDate = 0;
    public volatile long count = 0;

    public NotificationData(@NonNull NotificationEventType event, @NonNull MyAccount myAccount) {
        this.event = event;
        this.myAccount = myAccount;
    }

    public NotificationData onEventAt(long updatedDate) {
        return onEventsAt(updatedDate, 1);
    }

    public NotificationData onEventsAt(long updatedDate, long numberOfEvents) {
        if (numberOfEvents < 1) return this;
        count += numberOfEvents;
        if (this.updatedDate < updatedDate) this.updatedDate = updatedDate;
        return this;
    }

    PendingIntent getPendingIntent(MyContext myContext) {
        TimelineType timeLineType;
        switch (event) {
            case PRIVATE:
                timeLineType = TimelineType.PRIVATE;
                break;
            case OUTBOX:
                timeLineType = TimelineType.OUTBOX;
                break;
            case EMPTY:
                timeLineType = TimelineType.UNKNOWN;
                break;
            default:
                timeLineType = TimelineType.NOTIFICATIONS;
                break;
        }
        Timeline timeline;
        switch(timeLineType) {
            case UNKNOWN:
                timeline = MyContextHolder.get().persistentTimelines().getDefault();
                break;
            default:
                timeline = Timeline.getTimeline(timeLineType, myAccount, 0, null);
                break;
        }

        Intent intent = new Intent(myContext.context(), FirstActivity.class);
        // "rnd" is necessary to actually bring Extra to the target intent
        // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
        intent.setData(Uri.withAppendedPath(timeline.getUri(),
                "rnd/" + android.os.SystemClock.elapsedRealtime()));
        return PendingIntent.getActivity(myContext.context(), timeLineType.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    String channelId() {
        return "channel_" + event.id;
    }
}
