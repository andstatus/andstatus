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

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.WhichPage;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.MatchedUri;
import org.andstatus.app.data.TimelineType;
import org.andstatus.app.msg.TimelineActivity;
import org.andstatus.app.service.CommandResult;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

public class AddedMessagesNotifier {
    private MyContext myContext;
    private boolean mNotificationsVibrate;

    private AddedMessagesNotifier(MyContext myContext) {
        this.myContext = myContext;
        mNotificationsVibrate = MyPreferences.getBoolean("vibration", false);
    }

    public static AddedMessagesNotifier newInstance(MyContext myContext) {
        return new AddedMessagesNotifier(myContext);
    }

    public void update(CommandResult result) {
        if (!MyPreferences.getBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, false)) {
            return;
        }
        notifyForOneType(TimelineType.HOME, result.getMessagesAdded());
        notifyForOneType(TimelineType.MENTIONS, result.getMentionsAdded());
        notifyForOneType(TimelineType.DIRECT, result.getDirectedAdded());
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
                return MyPreferences.getBoolean(MyPreferences.KEY_NOTIFY_OF_MENTIONS, false);
            case DIRECT:
                return MyPreferences.getBoolean(MyPreferences.KEY_NOTIFY_OF_DIRECT_MESSAGES, false);
            case HOME:
                return MyPreferences.getBoolean(MyPreferences.KEY_NOTIFY_OF_HOME_TIMELINE, false);
            default:
                return true;
        }
    }

    private void notify(TimelineType timelineType, int messageTitleResId,
            String messageText) {
        String ringtone = MyPreferences.getString(MyPreferences.KEY_RINGTONE_PREFERENCE, null);
        Uri sound = TextUtils.isEmpty(ringtone) ? null : Uri.parse(ringtone);

        Notification.Builder builder =
                new Notification.Builder(myContext.context())
        .setSmallIcon(
                MyPreferences.getBoolean(
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
        Intent intent = new Intent(myContext.context(), TimelineActivity.class);
        intent.setData(Uri.withAppendedPath(
                MatchedUri.getTimelineUri(0, timelineType, myContext.persistentAccounts().size() > 1, 0),
                "rnd/" + android.os.SystemClock.elapsedRealtime()
                ));
        intent.putExtra(IntentExtra.WHICH_PAGE.key, WhichPage.TOP.save());
        PendingIntent pendingIntent = PendingIntent.getActivity(myContext.context(), timelineType.hashCode(),
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        myContext.notify(timelineType, builder.build());
    }
}
