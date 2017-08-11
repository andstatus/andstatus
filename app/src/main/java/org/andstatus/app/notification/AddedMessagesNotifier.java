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
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;

import org.andstatus.app.FirstActivity;
import org.andstatus.app.R;
import org.andstatus.app.appwidget.AppWidgets;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.timeline.meta.Timeline;
import org.andstatus.app.timeline.meta.TimelineType;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.SharedPreferencesUtil;

public class AddedMessagesNotifier {
    private MyContext myContext;
    private boolean mNotificationsVibrate;

    public static void notify(MyContext myContext, CommandResult result) {
        AddedMessagesNotifier notifier = new AddedMessagesNotifier(myContext);
        notifier.update(result);
    }

    private AddedMessagesNotifier(MyContext myContext) {
        this.myContext = myContext;
        mNotificationsVibrate = SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFICATION_VIBRATION, false);
    }

    private void update(CommandResult result) {
        notifyViaWidgets(result);
        if (!SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, false)) {
            return;
        }
        notifyForOneType(TimelineType.HOME, result.getMessagesAdded());
        notifyForOneType(TimelineType.MENTIONS, result.getMentionsAdded());
        notifyForOneType(TimelineType.DIRECT, result.getDirectedAdded());
    }

    private void notifyViaWidgets(CommandResult result) {
        AppWidgets appWidgets = AppWidgets.newInstance(myContext);
        appWidgets.updateData(result);
        appWidgets.updateViews();
    }

    private void notifyForOneType(TimelineType timelineType, int numMessages) {
        if (numMessages == 0 || !areNotificationsEnabled(timelineType)) {
            return;
        }

        MyLog.v(this, "n=" + numMessages + "; timelineType=" + timelineType);

        int messageTitleResId;
        String messageText = "";
        switch (timelineType) {
            case MENTIONS:
                messageTitleResId = R.string.notification_title_mentions;
                messageText = I18n.formatQuantityMessage(myContext.context(),
                        R.string.notification_new_mention_format, numMessages,
                        R.array.notification_mention_patterns,
                        R.array.notification_mention_formats);
                break;

            case DIRECT:
                messageTitleResId = R.string.notification_title_messages;
                messageText = I18n.formatQuantityMessage(myContext.context(),
                        R.string.notification_new_message_format, numMessages,
                        R.array.notification_direct_message_patterns,
                        R.array.notification_direct_message_formats);
                break;

            case HOME:
            default:
                messageTitleResId = R.string.notification_new_home_title;
                messageText = I18n
                        .formatQuantityMessage(myContext.context(),
                                R.string.notification_new_tweet_format, numMessages,
                                R.array.notification_home_patterns,
                                R.array.notification_home_formats);
                break;
        }

        notify(timelineType, messageTitleResId, messageText);
    }

    private boolean areNotificationsEnabled(TimelineType timelineType) {
        switch (timelineType) {
            case MENTIONS:
                return SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFY_OF_MENTIONS, false);
            case DIRECT:
                return SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFY_OF_DIRECT_MESSAGES, false);
            case HOME:
                return SharedPreferencesUtil.getBoolean(MyPreferences.KEY_NOTIFY_OF_HOME_TIMELINE, false);
            default:
                return true;
        }
    }

    private void notify(TimelineType timelineType, int messageTitleResId,
            String messageText) {
        String ringtone = SharedPreferencesUtil.getString(MyPreferences.KEY_NOTIFICATION_RINGTONE, null);
        Uri sound = TextUtils.isEmpty(ringtone) ? null : Uri.parse(ringtone);

        Notification.Builder builder =
                new Notification.Builder(myContext.context())
        .setSmallIcon(
                SharedPreferencesUtil.getBoolean(
                        MyPreferences.KEY_NOTIFICATION_ICON_ALTERNATIVE, false) 
                        ? R.drawable.notification_icon_circle
                                : R.drawable.notification_icon)
                                .setContentTitle(myContext.context().getText(messageTitleResId))
                                .setContentText(messageText)
                                .setSound(sound);

        if (mNotificationsVibrate) {
            builder.setVibrate( new long[] {
                    200, 300, 200, 300
            });
        }
        builder.setLights(Color.GREEN, 500, 1000);	

        // Prepare "intent" to launch timeline activities exactly like in
        // org.andstatus.app.TimelineActivity.onOptionsItemSelected
        Intent intent = new Intent(myContext.context(), FirstActivity.class);
        intent.setData(Uri.withAppendedPath(
                MatchedUri.getTimelineUri(Timeline.getTimeline(timelineType, null, 0, null)),
                "rnd/" + android.os.SystemClock.elapsedRealtime()
                ));
        PendingIntent pendingIntent = PendingIntent.getActivity(myContext.context(), timelineType.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        myContext.notify(timelineType, builder.build());
    }
}
