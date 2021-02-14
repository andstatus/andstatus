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
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;

import org.andstatus.app.R;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StopWatch;
import org.andstatus.app.util.UriUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.andstatus.app.notification.NotificationEventType.SERVICE_RUNNING;

public class Notifier {
    private static final long[] VIBRATION_PATTERN = {200, 300, 200, 300};
    private static final int LIGHT_COLOR = Color.GREEN;
    final MyContext myContext;
    private NotificationManager nM = null;
    private boolean notificationArea;
    private boolean vibration;
    private Uri soundUri;
    private List<NotificationEventType> enabledEvents = Collections.emptyList();
    private final AtomicReference<NotificationEvents> refEvents = new AtomicReference<>(NotificationEvents.EMPTY);

    public Notifier(MyContext myContext) {
        this.myContext = myContext;
    }

    public void clearAll() {
        AppWidgets.of(refEvents.updateAndGet(NotificationEvents::clearAll)).clearCounters().updateViews();
        clearAndroidNotifications();
    }

    public void clear(@NonNull Timeline timeline) {
        if (timeline.nonEmpty()) {
            AppWidgets.of(refEvents.updateAndGet(events -> events.clear(timeline))).clearCounters().updateViews();
            clearAndroidNotifications();
        }
    }

    private void clearAndroidNotifications() {
        NotificationEventType.validValues.forEach(this::clearAndroidNotification);
    }

    public void clearAndroidNotification(NotificationEventType eventType) {
        if (nM != null) nM.cancel(MyLog.APPTAG, eventType.notificationId());
    }

    public void update() {
        AppWidgets.of(refEvents.updateAndGet(NotificationEvents::load)).updateData().updateViews();
        if (notificationArea) {
            refEvents.get().map.values().stream().filter(data -> data.count > 0).forEach(myContext::notify);
        }
    }

    public Notification getAndroidNotification(@NonNull NotificationData data) {
        String contentText = (data.myActor.nonEmpty()
                ? data.myActor.getActorNameInTimeline()
                : myContext.context().getText(data.event.titleResId)) + ": " + data.count;
        MyLog.v(this, contentText);

        Notification.Builder builder = new Notification.Builder(myContext.context());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(data.channelId());
        } else {
            if (data.event == SERVICE_RUNNING) {
                builder.setSound(null);
            } else {
                if (vibration) {
                    builder.setVibrate(VIBRATION_PATTERN);
                }
                builder.setSound(UriUtils.isEmpty(soundUri) ? null : soundUri);
                builder.setLights(LIGHT_COLOR, 500, 1000);
            }
        }
        builder.setSmallIcon(data.event == SERVICE_RUNNING
                ? R.drawable.ic_sync_white_24dp
                : SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFICATION_ICON_ALTERNATIVE, false)
                    ? R.drawable.notification_icon_circle
                    : R.drawable.notification_icon)
            .setContentTitle(myContext.context().getText(data.event.titleResId))
            .setContentText(contentText)
            .setWhen(data.updatedDate)
            .setShowWhen(true);
        builder.setContentIntent(data.getPendingIntent(myContext));
        return builder.build();
    }

    public void notifyAndroid(NotificationData data) {
        if (nM == null) return;
        createNotificationChannel(data);
        try {
            nM.notify(MyLog.APPTAG, data.event.notificationId(), getAndroidNotification(data));
        } catch (Exception e) {
            MyLog.w(this, "Notification failed", e);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    public void createNotificationChannel(NotificationData data) {
        if (nM == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        String channelId = data.channelId();
        CharSequence channelName = myContext.context().getText(data.event.titleResId);
        String description = "AndStatus, " + channelName;
        boolean isSilent = data.event == SERVICE_RUNNING || UriUtils.isEmpty(soundUri);
        NotificationChannel channel = new NotificationChannel(channelId, channelName,
                isSilent ? NotificationManager.IMPORTANCE_MIN : NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(description);
        if (data.event == SERVICE_RUNNING) {
            channel.enableLights(false);
            channel.enableVibration(false);
        } else {
            channel.enableLights(true);
            channel.setLightColor(LIGHT_COLOR);
            if (vibration) {
                channel.setVibrationPattern(VIBRATION_PATTERN);
            }
        }
        channel.setSound(isSilent ? null : soundUri, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        nM.createNotificationChannel(channel);
    }

    public void initialize() {
        StopWatch stopWatch = StopWatch.createStarted();
        if (myContext.isReady()) {
            nM = (NotificationManager) myContext.context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (nM == null) {
                MyLog.w(this, "No Notification Service");
            }
        }
        notificationArea = NotificationMethodType.NOTIFICATION_AREA.isEnabled();
        vibration = NotificationMethodType.VIBRATION.isEnabled();
        soundUri = NotificationMethodType.SOUND.getUri();
        enabledEvents = NotificationEventType.validValues.stream().filter(NotificationEventType::isEnabled)
                .collect(Collectors.toList());
        refEvents.set(NotificationEvents.of(myContext, enabledEvents).load());
        MyLog.i(this, "notifierInitializedMs:" + stopWatch.getTime() + "; " + refEvents.get().size() + " events");
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
        return refEvents.get();
    }
}
