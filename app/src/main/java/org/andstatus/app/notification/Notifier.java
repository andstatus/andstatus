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
import android.text.TextUtils;

import org.andstatus.app.R;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

public class Notifier {
    private static final long[] VIBRATION_PATTERN = {200, 300, 200, 300};
    private static final int LIGHT_COLOR = Color.GREEN;
    public final NotificationEvents events;
    private NotificationManager nM = null;
    private boolean notificationArea;
    private boolean vibration;
    private String soundUri;

    public Notifier(MyContext myContext) {
        events = new NotificationEvents(myContext);
    }

    public void clearAll() {
        events.clearAll();
        clearAndroidNotifications(Timeline.EMPTY);
        AppWidgets.clearAndUpdateWidgets(events.myContext);
    }

    public void clear(Timeline timeline) {
        events.clear(timeline);
        clearAndroidNotifications(timeline);
        AppWidgets.clearAndUpdateWidgets(events.myContext);  // TODO: Clear for this timeline only
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
        events.update();
        notifyViaWidgets(events);
        if (notificationArea || vibration || StringUtils.nonEmpty(soundUri)) events.map.values().stream()
                .filter(data -> data.count > 0).forEach(events.myContext::notify);
    }

    private void notifyViaWidgets(NotificationEvents events) {
        AppWidgets appWidgets = AppWidgets.newInstance(events.myContext);
        appWidgets.updateData();
        appWidgets.updateViews();
    }

    public Notification getAndroidNotification(@NonNull NotificationData data) {
        String noteText = (data.myAccount.isValid() ? data.myAccount.getAccountName() :
                getContext().getText(data.event.titleResId)) + ": " + data.count;
        MyLog.v(this,  noteText);

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
                builder.setSound(TextUtils.isEmpty(soundUri) ? null : Uri.parse(soundUri));
                builder.setLights(LIGHT_COLOR, 500, 1000);
            }
        }
        if (notificationArea) {
            builder.setSmallIcon(
                SharedPreferencesUtil.getBoolean(
                        MyPreferences.KEY_NOTIFICATION_ICON_ALTERNATIVE, false)
                        ? R.drawable.notification_icon_circle
                        : R.drawable.notification_icon)
                .setContentTitle(getContext().getText(data.event.titleResId))
                .setContentText(noteText)
                .setWhen(data.updatedDate)
                .setShowWhen(true);
        }
        builder.setContentIntent(data.getPendingIntent(events.myContext));
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
            channel.setSound(TextUtils.isEmpty(soundUri) ? null : Uri.parse(soundUri),
                    Notification.AUDIO_ATTRIBUTES_DEFAULT);
        }
        nM.createNotificationChannel(channel);
    }

    private Context getContext() {
        return events.myContext.context();
    }

    public void load() {
        if (events.myContext.isReady()) {
            nM = (NotificationManager) getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (nM == null) {
                MyLog.w(this, "No Notification Service");
            }
        }
        notificationArea = NotificationMethodType.NOTIFICATION_AREA.isEnabled();
        vibration = NotificationMethodType.VIBRATION.isEnabled();
        soundUri = NotificationMethodType.SOUND.getString();
        events.load();
    }

    public boolean isEnabled(NotificationEventType eventType) {
        return events.isEnabled(eventType);
    }

    public void onUnsentNote(long noteId) {
        if (noteId == 0 || !events.isEnabled(NotificationEventType.OUTBOX)) return;
        MyProvider.setUnsentNoteNotification(events.myContext, noteId);
        update();
    }
}
