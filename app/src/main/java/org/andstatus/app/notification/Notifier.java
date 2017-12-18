/* 
 * Copyright (c) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.R;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;
import org.andstatus.app.util.StringUtils;

public class Notifier {
    public final NotificationEvents events;
    private boolean notificationArea;
    private boolean vibration;
    private String soundUri;

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
        NotificationManager nM = (NotificationManager) events.myContext.context()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (nM == null) {
            MyLog.w(this, "No Notification Service");
            return;
        }
        NotificationEventType.validValues.stream()
                .filter(event -> timeline == Timeline.EMPTY || event.isShownOn(timeline.getTimelineType()))
                .forEach(event -> nM.cancel(MyLog.APPTAG, event.ordinal()));
    }

    public Notifier(MyContext myContext) {
        events = new NotificationEvents(myContext);
    }

    public void update() {
        events.update();
        notifyViaWidgets(events);
        if (notificationArea || vibration || StringUtils.nonEmpty(soundUri)) events.map.forEach(this::forOneEvent);
    }

    private void notifyViaWidgets(NotificationEvents events) {
        AppWidgets appWidgets = AppWidgets.newInstance(events.myContext);
        appWidgets.updateData();
        appWidgets.updateViews();
    }

    private void forOneEvent(@NonNull NotificationEventType event, NotificationData details) {
        if (details.count == 0 || events.getCount(event) == 0) return;
        String messageText = getContext().getText(event.titleResId) + ": " + details.count;
        MyLog.v(this,  messageText);

        Notification.Builder builder = new Notification.Builder(getContext());
        if (notificationArea) {
            builder.setSmallIcon(
                    SharedPreferencesUtil.getBoolean(
                            MyPreferences.KEY_NOTIFICATION_ICON_ALTERNATIVE, false)
                            ? R.drawable.notification_icon_circle
                            : R.drawable.notification_icon)
                    .setContentTitle(getContext().getText(event.titleResId))
                    .setContentText(messageText);
        }
        if (vibration) {
            builder.setVibrate( new long[] {
                    200, 300, 200, 300
            });
        }
        Uri soundUri = TextUtils.isEmpty(this.soundUri) ? null : Uri.parse(this.soundUri);
        builder.setSound(soundUri);
        builder.setLights(Color.GREEN, 500, 1000);

        // Prepare "intent" to launch timeline activities exactly like in
        // org.andstatus.app.TimelineActivity.onOptionsItemSelected
        Intent intent = new Intent(getContext(), FirstActivity.class);
        // TODO: What timeline to show?!
        intent.setData(Uri.withAppendedPath(
                Timeline.getTimeline(TimelineType.EVERYTHING, null, 0, null).getUri(),
                "rnd/" + android.os.SystemClock.elapsedRealtime()
                ));
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), event.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        events.myContext.notify(event, builder.build());
    }

    public void notifyAndroid(NotificationEventType event, Notification notification) {
        NotificationManager nM = (NotificationManager) events.myContext.context()
                .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (nM == null) {
            MyLog.w(this, "No Notification Service");
            return;
        }
        nM.notify(MyLog.APPTAG, event.ordinal(), notification);
    }

    private Context getContext() {
        return events.myContext.context();
    }

    public void load() {
        notificationArea = NotificationMethodType.NOTIFICATION_AREA.isEnabled();
        vibration = NotificationMethodType.VIBRATION.isEnabled();
        soundUri = NotificationMethodType.SOUND.getString();
        events.load();
    }

    public boolean isEnabled(NotificationEventType eventType) {
        return events.contains(eventType);
    }

    public void onUnsentMessage(long msgId) {
        if (msgId == 0 || !events.contains(NotificationEventType.OUTBOX)) return;
        MyProvider.setUnsentMessageNotification(events.myContext, msgId);
        update();
    }
}
