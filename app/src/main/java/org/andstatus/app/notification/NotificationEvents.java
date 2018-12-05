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
import android.content.Context;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.timeline.meta.Timeline;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.andstatus.app.data.DbUtils.getLong;

public class NotificationEvents {
    public final static NotificationEvents EMPTY = of(MyContext.EMPTY, Collections.emptyList());
    public final MyContext myContext;
    private final List<NotificationEventType> enabledEvents;
    public final Map<NotificationEventType, NotificationData> map;

    public static NotificationEvents of(@NonNull Context context) {
        return of(MyContextHolder.get(context), Collections.emptyList());
    }

    static NotificationEvents of(MyContext myContext, List<NotificationEventType> enabledEvents) {
        return new NotificationEvents(myContext, enabledEvents, Collections.emptyMap());
    }

    private NotificationEvents(MyContext myContext, List<NotificationEventType> enabledEvents,
                               Map<NotificationEventType, NotificationData> notificationDataMap) {
        this.myContext = myContext;
        this.enabledEvents = enabledEvents;
        map = notificationDataMap;
    }

    /** @return 0 if not found */
    public long getCount(@NonNull NotificationEventType eventType) {
        return map.getOrDefault(eventType, NotificationData.EMPTY).count;
    }

    NotificationEvents clearAll() {
        MyProvider.clearAllNotifications(myContext);
        return load();
    }

    public NotificationEvents clear(@NonNull Timeline timeline) {
        MyProvider.clearNotification(myContext, timeline);
        return load();
    }

    public boolean isEmpty() {
        return map.values().stream().noneMatch(data -> data.count > 0);
    }

    /** When a User clicks on a widget, open a timeline, which has new activities/notes, or a default timeline */
    public PendingIntent getPendingIntent() {
        return map.getOrDefault(getEventTypeWithCount(), NotificationData.EMPTY).getPendingIntent(myContext);
    }

    @NonNull
    private NotificationEventType getEventTypeWithCount() {
        if (isEmpty()) {
            return NotificationEventType.EMPTY;
        } else if (getCount(NotificationEventType.PRIVATE) > 0) {
            return  NotificationEventType.PRIVATE;
        } else if (getCount(NotificationEventType.OUTBOX) > 0) {
            return  NotificationEventType.OUTBOX;
        } else {
            return  map.values().stream().filter(data -> data.count > 0).map(data -> data.event)
                    .findFirst().orElse(NotificationEventType.EMPTY);
        }
    }

    public NotificationEvents load() {
        String sql = "SELECT " + ActivityTable.NEW_NOTIFICATION_EVENT + ", " +
                ActivityTable.NOTIFIED_ACTOR_ID + ", " +
                ActivityTable.UPDATED_DATE +
                " FROM " + ActivityTable.TABLE_NAME +
                " WHERE " + ActivityTable.NEW_NOTIFICATION_EVENT + "!=0";
        Map<NotificationEventType, NotificationData> loadedMap = MyQuery.foldLeft(myContext, sql, new HashMap<>(),
            map1 -> cursor -> foldEvent(
                    map1,
                    NotificationEventType.fromId(getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT)),
                    myContext.accounts().fromActorId(getLong(cursor, ActivityTable.NOTIFIED_ACTOR_ID)),
                    getLong(cursor, ActivityTable.UPDATED_DATE)));
        return new NotificationEvents(myContext, enabledEvents, loadedMap);
    }

    // TODO: event for an Actor, not for an Account
    private HashMap<NotificationEventType, NotificationData> foldEvent(
            HashMap<NotificationEventType, NotificationData> map,
            NotificationEventType eventType, MyAccount myAccount, long updatedDate) {
        NotificationData data = map.get(eventType);
        if (data == null) {
            if (enabledEvents.contains(eventType)) {
                map.put(eventType, new NotificationData(eventType, myAccount, updatedDate));
            }
        } else if( data.myAccount.equals(myAccount)) {
            data.addEventsAt(1, updatedDate);
        } else {
            NotificationData data2 = new NotificationData(eventType, MyAccount.EMPTY, updatedDate);
            data2.addEventsAt(data.count, data.updatedDate);
            map.put(eventType, data2);
        }
        return map;
    }
}
