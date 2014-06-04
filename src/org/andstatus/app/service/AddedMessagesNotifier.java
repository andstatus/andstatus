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

package org.andstatus.app.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

public class AddedMessagesNotifier {
    private MyContext myContext;
    private boolean mNotificationsVibrate;

    private AddedMessagesNotifier(MyContext myContext) {
        this.myContext = myContext;
        mNotificationsVibrate = MyPreferences.getDefaultSharedPreferences().getBoolean("vibration", false);
    }

    static AddedMessagesNotifier newInstance(MyContext myContext) {
        return new AddedMessagesNotifier(myContext);
    }

    public void update(CommandResult result) {
        if (!MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false)) {
            return;
        }
        notifyForOneType(result.getMessagesAdded(), CommandEnum.NOTIFY_MENTIONS);
        notifyForOneType(result.getMentionsAdded(), CommandEnum.NOTIFY_DIRECT_MESSAGE);
        notifyForOneType(result.getDirectedAdded(), CommandEnum.NOTIFY_HOME_TIMELINE);
    }
    
    
    private void notifyForOneType(int numMessages, CommandEnum msgType) {
        // If no notifications are enabled, return
        if (numMessages == 0) {
            return;
        }

        MyLog.v(this, "notifyViaNotificationManager n=" + numMessages + "; msgType=" + msgType);
        
        boolean notificationsMessages = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_messages", false);
        boolean notificationsReplies = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_mentions", false);
        boolean notificationsTimeline = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_timeline", false);
        String ringtone = MyPreferences.getDefaultSharedPreferences().getString(MyPreferences.KEY_RINGTONE_PREFERENCE, null);

        // Make sure that notifications haven't been turned off for the
        // message type
        switch (msgType) {
            case NOTIFY_MENTIONS:
                if (!notificationsReplies) {
                    return;
                }
                break;
            case NOTIFY_DIRECT_MESSAGE:
                if (!notificationsMessages) {
                    return;
                }
                break;
            case NOTIFY_HOME_TIMELINE:
                if (!notificationsTimeline) {
                    return;
                }
                break;
            default:
                break;
        }

        // Set up the notification to display to the user
        Notification notification = new Notification(R.drawable.notification_icon,
                myContext.context().getText(R.string.notification_title), System.currentTimeMillis());

        notification.vibrate = null;
        if (mNotificationsVibrate) {
            notification.vibrate = new long[] {
                    200, 300, 200, 300
            };
        }

        notification.flags = Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
        notification.ledOffMS = 1000;
        notification.ledOnMS = 500;
        notification.ledARGB = Color.GREEN;

        if ("".equals(ringtone) || ringtone == null) {
            notification.sound = null;
        } else {
            Uri ringtoneUri = Uri.parse(ringtone);
            notification.sound = ringtoneUri;
        }

        // Set up the pending intent
        PendingIntent contentIntent;

        int messageTitle;
        Intent intent;
        String aMessage = "";

        // Prepare "intent" to launch timeline activities exactly like in
        // org.andstatus.app.TimelineActivity.onOptionsItemSelected
        switch (msgType) {
            case NOTIFY_MENTIONS:
                aMessage = I18n.formatQuantityMessage(myContext.context(),
                        R.string.notification_new_mention_format, numMessages,
                        R.array.notification_mention_patterns,
                        R.array.notification_mention_formats);
                messageTitle = R.string.notification_title_mentions;
                intent = new Intent(myContext.context(), TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.MENTIONS.save());
                contentIntent = PendingIntent.getActivity(myContext.context(), numMessages,
                        intent, 0);
                break;

            case NOTIFY_DIRECT_MESSAGE:
                aMessage = I18n.formatQuantityMessage(myContext.context(),
                        R.string.notification_new_message_format, numMessages,
                        R.array.notification_message_patterns,
                        R.array.notification_message_formats);
                messageTitle = R.string.notification_title_messages;
                intent = new Intent(myContext.context(), TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.DIRECT.save());
                contentIntent = PendingIntent.getActivity(myContext.context(), numMessages,
                        intent, 0);
                break;

            case NOTIFY_HOME_TIMELINE:
            default:
                aMessage = I18n
                        .formatQuantityMessage(myContext.context(),
                                R.string.notification_new_tweet_format, numMessages,
                                R.array.notification_tweet_patterns,
                                R.array.notification_tweet_formats);
                messageTitle = R.string.notification_title;
                intent = new Intent(myContext.context(), TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.HOME.save());
                contentIntent = PendingIntent.getActivity(myContext.context(), numMessages,
                        intent, 0);
                break;
        }

        // Set up the scrolling message of the notification
        notification.tickerText = aMessage;

        // Set the latest event information and send the notification
        notification.setLatestEventInfo(myContext.context(), myContext.context().getText(messageTitle), aMessage,
                contentIntent);
        NotificationManager nM = (NotificationManager) myContext.context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nM.notify(msgType.ordinal(), notification);
    }
}
