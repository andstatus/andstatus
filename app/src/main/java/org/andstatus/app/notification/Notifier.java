/* 
 * Copyright (c) 2017 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Notifier {
    private static final long[] VIBRATION_PATTERN = {200, 300, 200, 300};
    private static final int LIGHT_COLOR = Color.GREEN;
    private final MyContext myContext;
    private NotificationManager nM = null;
    private boolean notificationArea;
    private boolean vibration;
    private String soundUri;
    private List<NotificationEventType> enabledEvents = Collections.emptyList();
    private volatile NotificationEvents events = NotificationEvents.EMPTY;

    public Notifier(MyContext myContext) {
        this.myContext = myContext;
    }

    public void clearAll() {
        events.clearAll();
        clearAndroidNotifications(Timeline.EMPTY);
        AppWidgets.clearAndUpdateWidgets(events);
    }

    public void clear(Timeline timeline) {
        events.clear(timeline);
        clearAndroidNotifications(timeline);
        AppWidgets.clearAndUpdateWidgets(events);
    }

    private void clearAndroidNotifications(@NonNull Timeline timeline) {
        NotificationEventType.validValues.stream()
                .filter(event -> timeline == Timeline.EMPTY || event.isShownOn(timeline.getTimelineType()))
                .forEach(this::clearAndroidNotification);
    }

    public void clearAndroidNotification(NotificationEventType eventType) {
        if (nM != null) nM.cancel(MyLog.APPTAG, eventType.notificationId());
    }

    public void update() {
        new AppWidgets(events.load()).updateData().updateViews();
        if (notificationArea || vibration || StringUtils.nonEmpty(soundUri)) {
            events.map.values().stream().filter(data -> data.count > 0).forEach(myContext::notify);
        }
    }

    public Notification getAndroidNotification(@NonNull NotificationData data) {
        String noteText = (data.myAccount.isValid() ? data.myAccount.getAccountName() :
                getContext().getText(data.event.titleResId)) + ": " + data.count;
        MyLog.v(this, noteText);

        Notification.Builder builder = new Notification.Builder(getContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(data.channelId());
        } else {
            if (data.event == NotificationEventType.SERVICE_RUNNING) {
                builder.setSound(null);
            } else {
                if (vibration) {
                    builder.setVibrate(VIBRATION_PATTERN);
                }
                builder.setSound(StringUtils.isEmpty(soundUri) ? null : Uri.parse(soundUri));
                builder.setLights(LIGHT_COLOR, 500, 1000);
            }
        }
        if (notificationArea) {
            builder.setSmallIcon(data.event == NotificationEventType.SERVICE_RUNNING
                ? R.drawable.ic_sync_white_24dp
                : SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFICATION_ICON_ALTERNATIVE, false)
                    ? R.drawable.notification_icon_circle
                    : R.drawable.notification_icon)
                .setContentTitle(getContext().getText(data.event.titleResId))
                .setContentText(noteText)
                .setWhen(data.updatedDate)
                .setShowWhen(true);
        }
        builder.setContentIntent(data.getPendingIntent(myContext));
        return builder.build();
    }

    public void notifyAndroid(NotificationData data) {
        if (nM == null) return;
        createNotificationChannel(data);
        nM.notify(MyLog.APPTAG, data.event.notificationId(), getAndroidNotification(data));
    }

    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationData data) {
        if (nM == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        String channelId = data.channelId();
        CharSequence channelName = getContext().getText(data.event.titleResId);
        String description = "AndStatus, " + channelName;
        NotificationChannel channel = new NotificationChannel(channelId, channelName,
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        if (data.event == NotificationEventType.SERVICE_RUNNING) {
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setImportance(NotificationManager.IMPORTANCE_MIN);
            channel.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        } else {
            channel.enableLights(true);
            channel.setLightColor(LIGHT_COLOR);
            channel.enableVibration(vibration);
            if (vibration) {
                channel.setVibrationPattern(VIBRATION_PATTERN);
            }
            channel.setSound(StringUtils.isEmpty(soundUri) ? null : Uri.parse(soundUri),
                    Notification.AUDIO_ATTRIBUTES_DEFAULT);
        }
        nM.createNotificationChannel(channel);
    }

    private Context getContext() {
        return myContext.context();
    }

    public void initialize() {
        if (myContext.isReady()) {
            nM = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (nM == null) {
                MyLog.w(this, "No Notification Service");
            }
        }
        notificationArea = NotificationMethodType.NOTIFICATION_AREA.isEnabled();
        vibration = NotificationMethodType.VIBRATION.isEnabled();
        soundUri = NotificationMethodType.SOUND.getString();
        enabledEvents = NotificationEventType.validValues.stream().filter(NotificationEventType::isEnabled)
                .collect(Collectors.toList());
        events = new NotificationEvents(myContext, enabledEvents).load();
    }

    public boolean isEnabled(NotificationEventType eventType) {
        return enabledEvents.contains(eventType);
    }

    public void onUnsentActivity(long activityId) {
        if (activityId == 0 || !isEnabled(NotificationEventType.OUTBOX)) return;

        MyProvider.setUnsentActivityNotification(myContext, activityId);
        update();
    }

    public NotificationEvents getEvents() {
        return events;
    }
}
