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

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyContextHolder;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

public class CommandsQueueNotifier {
    private MyContext myContext;
    private boolean mNotificationsEnabled;

    private CommandsQueueNotifier(MyContext myContext) {
        this.myContext = myContext;
        mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false);
    }

    static CommandsQueueNotifier newInstance(MyContext myContext) {
        return new CommandsQueueNotifier(myContext);
    }

    public void update(int mainQueueSize, int retryQueueSize) {
        int count = mainQueueSize + retryQueueSize;
        if (count == 0 ) {
            clearNotifications();
        } else if (mNotificationsEnabled && MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_NOTIFY_OF_COMMANDS_IN_THE_QUEUE, false)) {
            if (mainQueueSize != 0) {
                MyLog.d(this, mainQueueSize + " commands in Main Queue.");
            }
            if (retryQueueSize != 0) {
                MyLog.d(this, retryQueueSize + " commands in Retry Queue.");
            }

            // Set up the notification to display to the user
            Notification notification = new Notification(R.drawable.notification_icon,
                    myContext.context().getText(R.string.notification_title), System.currentTimeMillis());

            int messageTitle;
            String aMessage = "";

            aMessage = I18n.formatQuantityMessage(myContext.context(),
                    R.string.notification_queue_format, count, R.array.notification_queue_patterns,
                    R.array.notification_queue_formats);
            messageTitle = R.string.notification_title_queue;

            // Set up the scrolling message of the notification
            notification.tickerText = aMessage;

            /**
             * This Intent will be sent upon a User tapping the notification
             */
            PendingIntent pi = PendingIntent.getActivity(myContext.context(), 0,
                    new Intent(MyContextHolder.get().context(), QueueViewer.class), 0);
            notification.setLatestEventInfo(myContext.context(),
                    myContext.context().getText(messageTitle), aMessage, pi);

            NotificationManager nM = (NotificationManager) myContext.context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            nM.notify(CommandEnum.NOTIFY_QUEUE.ordinal(), notification);
        }
    }

    private void clearNotifications() {
        NotificationManager nM = (NotificationManager) myContext.context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (nM != null) {
            nM.cancel(CommandEnum.NOTIFY_QUEUE.ordinal());
        }
    }
}
