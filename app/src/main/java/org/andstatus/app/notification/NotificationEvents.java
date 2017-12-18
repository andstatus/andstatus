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

import android.support.annotation.NonNull;

import org.andstatus.app.context.MyContext;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.MyQuery;
import org.andstatus.app.timeline.meta.Timeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class NotificationEvents {
    public final MyContext myContext;
    public final Map<NotificationEventType, NotificationData> map = new ConcurrentHashMap<>();

    public NotificationEvents(MyContext myContext) {
        this.myContext = myContext;
    }

    public void load() {
        NotificationEventType.validValues.stream().filter(NotificationEventType::isEnabled)
            .forEach(event -> map.put(event, new NotificationData(event, 0)));
        update();
    }

    public void update() {
        map.values().forEach(detail ->
                this.map.put(detail.event,
                        new NotificationData(detail.event, MyQuery.getNumberOfNotificationEvents(detail.event))));
    }

    public boolean contains(@NonNull NotificationEventType eventType) {
        return map.containsKey(eventType);
    }

    /** @return 0 if not found */
    public long getCount(@NonNull NotificationEventType eventType) {
        return map.getOrDefault(eventType, NotificationData.EMPTY).count;
    }

    void clearAll() {
        map.forEach((eventType, data) -> data.count = 0);
        MyProvider.clearNotification(myContext, Timeline.EMPTY);
    }

    public void clear(@NonNull Timeline timeline) {
        MyProvider.clearNotification(myContext, timeline);
        update();
    }

    public boolean isEmpty() {
        return map.values().stream().filter(data -> data.count > 0).count() == 0;
    }
}
