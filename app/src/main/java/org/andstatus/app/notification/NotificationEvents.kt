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
package org.andstatus.app.notification

import android.app.PendingIntent
import android.database.Cursor
import org.andstatus.app.context.MyContext
import org.andstatus.app.context.MyContextHolder
import org.andstatus.app.data.DbUtils
import org.andstatus.app.data.MyProvider
import org.andstatus.app.data.MyQuery
import org.andstatus.app.database.table.ActivityTable
import org.andstatus.app.net.social.Actor
import org.andstatus.app.timeline.meta.Timeline
import java.util.*
import java.util.function.Function

class NotificationEvents private constructor(val myContext: MyContext?, private val enabledEvents: MutableList<NotificationEventType?>?,
                                             val map: MutableMap<NotificationEventType?, NotificationData?>?) {
    /** @return 0 if not found
     */
    fun getCount(eventType: NotificationEventType): Long {
        return map.getOrDefault(eventType, NotificationData.Companion.EMPTY).count
    }

    fun size(): Long {
        return map.values.stream().mapToLong { obj: NotificationData? -> obj.getCount() }.sum()
    }

    fun clearAll(): NotificationEvents? {
        MyProvider.Companion.clearAllNotifications(myContext)
        return load()
    }

    fun clear(timeline: Timeline): NotificationEvents? {
        MyProvider.Companion.clearNotification(myContext, timeline)
        return load()
    }

    fun isEmpty(): Boolean {
        return map.values.stream().noneMatch { data: NotificationData? -> data.count > 0 }
    }

    /** When a User clicks on a widget, open a timeline, which has new activities/notes, or a default timeline  */
    fun getPendingIntent(): PendingIntent? {
        return map.getOrDefault(getEventTypeWithCount(), NotificationData.Companion.EMPTY).getPendingIntent(myContext)
    }

    private fun getEventTypeWithCount(): NotificationEventType {
        return if (isEmpty()) {
            NotificationEventType.EMPTY
        } else if (getCount(NotificationEventType.PRIVATE) > 0) {
            NotificationEventType.PRIVATE
        } else if (getCount(NotificationEventType.OUTBOX) > 0) {
            NotificationEventType.OUTBOX
        } else {
            map.values.stream().filter { data: NotificationData? -> data.count > 0 }.map { data: NotificationData? -> data.event }
                    .findFirst().orElse(NotificationEventType.EMPTY)
        }
    }

    fun load(): NotificationEvents? {
        val sql = "SELECT " + ActivityTable.NEW_NOTIFICATION_EVENT + ", " +
                ActivityTable.NOTIFIED_ACTOR_ID + ", " +
                ActivityTable.INS_DATE + ", " +
                ActivityTable.UPDATED_DATE +
                " FROM " + ActivityTable.TABLE_NAME +
                " WHERE " + ActivityTable.NEW_NOTIFICATION_EVENT + "!=0"
        val loadedMap: MutableMap<NotificationEventType?, NotificationData?> = MyQuery.foldLeft(myContext, sql, HashMap(),
                Function { map1: HashMap<NotificationEventType?, NotificationData?>? ->
                    Function { cursor: Cursor? ->
                        foldEvent(
                                map1,
                                NotificationEventType.Companion.fromId(DbUtils.getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT)),
                                myContext.users().load(DbUtils.getLong(cursor, ActivityTable.NOTIFIED_ACTOR_ID)),
                                Math.max(DbUtils.getLong(cursor, ActivityTable.INS_DATE), DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE))
                        )
                    }
                })
        return NotificationEvents(myContext, enabledEvents, loadedMap)
    }

    private fun foldEvent(
            map: HashMap<NotificationEventType?, NotificationData?>?,
            eventType: NotificationEventType?, myActor: Actor?, updatedDate: Long): HashMap<NotificationEventType?, NotificationData?>? {
        val data = map.get(eventType)
        if (data == null) {
            if (enabledEvents.contains(eventType)) {
                map[eventType] = NotificationData(eventType, myActor, updatedDate)
            }
        } else if (data.myActor == myActor) {
            data.addEventsAt(1, updatedDate)
        } else {
            val data2 = NotificationData(eventType, Actor.Companion.EMPTY, updatedDate)
            data2.addEventsAt(data.count, data.updatedDate)
            map[eventType] = data2
        }
        return map
    }

    companion object {
        val EMPTY = of(MyContext.Companion.EMPTY, emptyList())
        fun newInstance(): NotificationEvents? {
            return of(MyContextHolder.Companion.myContextHolder.getNow(), emptyList())
        }

        fun of(myContext: MyContext?, enabledEvents: MutableList<NotificationEventType?>?): NotificationEvents? {
            return NotificationEvents(myContext, enabledEvents, emptyMap())
        }
    }
}