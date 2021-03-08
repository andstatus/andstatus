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
package org.andstatus.app.notification

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import org.andstatus.app.FirstActivity
import org.andstatus.app.context.MyContext
import org.andstatus.app.net.social.Actor
import org.andstatus.app.origin.Origin
import org.andstatus.app.timeline.meta.TimelineType
import org.andstatus.app.util.RelativeTime

class NotificationData(event: NotificationEventType, myActor: Actor, updatedDate: Long) {
    val event: NotificationEventType
    val myActor: Actor

    @Volatile
    var updatedDate: Long

    @Volatile
    var count: Long
    @Synchronized
    fun addEventsAt(numberOfEvents: Long, updatedDate: Long) {
        if (numberOfEvents < 1 || updatedDate <= RelativeTime.DATETIME_MILLIS_NEVER) return
        count += numberOfEvents
        if (this.updatedDate < updatedDate) this.updatedDate = updatedDate
    }

    fun getPendingIntent(myContext: MyContext?): PendingIntent? {
        val timelineType: TimelineType = TimelineType.Companion.from(event)
        // When clicking on notifications, always open Combine timeline for Unread notifications
        val timeline = myContext.timelines().get(timelineType,
                if (timelineType == TimelineType.UNREAD_NOTIFICATIONS) Actor.Companion.EMPTY else myActor,  Origin.EMPTY)
                .orElse(myContext.timelines().default)
        val intent = Intent(myContext.context(), FirstActivity::class.java)
        // "rnd" is necessary to actually bring Extra to the target intent
        // see http://stackoverflow.com/questions/1198558/how-to-send-parameters-from-a-notification-click-to-an-activity
        intent.data = Uri.withAppendedPath(timeline.uri,
                "rnd/" + SystemClock.elapsedRealtime())
        return PendingIntent.getActivity(myContext.context(), timeline.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun channelId(): String {
        return "channel_" + event.id
    }

    fun getCount(): Long {
        return count
    }

    companion object {
        val EMPTY: NotificationData = NotificationData(NotificationEventType.EMPTY, Actor.Companion.EMPTY,
                RelativeTime.DATETIME_MILLIS_NEVER)
    }

    init {
        this.event = event
        this.myActor = myActor
        this.updatedDate = updatedDate
        count = if (updatedDate > RelativeTime.DATETIME_MILLIS_NEVER) 1 else 0.toLong()
    }
}