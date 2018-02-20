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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;

import org.andstatus.app.account.MyAccount;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.DbUtils;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.database.table.ActivityTable;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 */
public class NotificationEvents {
    public final MyContext myContext;
    public final Map<NotificationEventType, NotificationData> map = new ConcurrentHashMap<>();
    private volatile List<NotificationEventType> enabledEvents = Collections.emptyList();

    public NotificationEvents(MyContext myContext) {
        this.myContext = myContext;
    }

    public void load() {
        enabledEvents = NotificationEventType.validValues.stream().filter(NotificationEventType::isEnabled)
                .collect(Collectors.toList());
        map.clear();
        update();
    }

    public boolean isEnabled(@NonNull NotificationEventType eventType) {
        return enabledEvents.contains(eventType);
    }

    /** @return 0 if not found */
    public long getCount(@NonNull NotificationEventType eventType) {
        return map.getOrDefault(eventType, NotificationData.EMPTY).count;
    }

    void clearAll() {
        map.clear();
        MyProvider.clearNotification(myContext, Timeline.EMPTY);
    }

    public void clear(@NonNull Timeline timeline) {
        MyProvider.clearNotification(myContext, timeline);
        update();
    }

    public boolean isEmpty() {
        return map.values().stream().filter(data -> data.count > 0).count() == 0;
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

    public void update() {
        final String method = "update";
        map.clear();
        SQLiteDatabase db = myContext.getDatabase();
        if (db == null) {
            MyLog.v(this, method + "; Database is null");
            return;
        }
        String sql = "SELECT " + ActivityTable.NEW_NOTIFICATION_EVENT + ", " +
                ActivityTable.ACCOUNT_ID + ", " +
                ActivityTable.UPDATED_DATE +
                " FROM " + ActivityTable.TABLE_NAME +
                " WHERE " + ActivityTable.NEW_NOTIFICATION_EVENT + "!=0";
        try (Cursor cursor = db.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                NotificationEventType eventType = NotificationEventType
                        .fromId(DbUtils.getLong(cursor, ActivityTable.NEW_NOTIFICATION_EVENT));
                MyAccount myAccount = myContext.accounts()
                        .fromActorId(DbUtils.getLong(cursor, ActivityTable.ACCOUNT_ID));
                long updatedDate = DbUtils.getLong(cursor, ActivityTable.UPDATED_DATE);
                onNewEvent(eventType, myAccount, updatedDate);
            }
        } catch (Exception e) {
            MyLog.i(this, method + "; SQL:'" + sql + "'", e);
        }
    }

    // TODO: event for an Actor, not for an Account
    public void onNewEvent(NotificationEventType eventType, MyAccount myAccount, long updatedDate) {
        NotificationData data = map.get(eventType);
        if (data == null) {
            if (isEnabled(eventType)) {
                map.put(eventType, new NotificationData(eventType, myAccount).onEventAt(updatedDate));
            }
        } else if( data.myAccount.equals(myAccount)) {
            data.onEventAt(updatedDate);
        } else {
            NotificationData data2 = new NotificationData(eventType, MyAccount.EMPTY).onEventAt(updatedDate);
            data2.onEventsAt(data.updatedDate, data.count);
            map.put(eventType, data2);
        }
    }
}
