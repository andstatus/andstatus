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
import android.app.TaskStackBuilder;
import android.content.Intent;

import org.andstatus.app.R;
import org.andstatus.app.context.MyContext;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

public class CommandsQueueNotifier {
    private MyContext myContext;
    private boolean mNotificationsEnabled;

    private CommandsQueueNotifier(MyContext myContext) {
        this.myContext = myContext;
        mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean(MyPreferences.KEY_NOTIFICATIONS_ENABLED, false);
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
            createNotification(count);
        }
    }

    /** Based on code from http://developer.android.com/guide/topics/ui/notifiers/notifications.html */
    private void createNotification(int count) {
        CharSequence messageTitle = myContext.context().getText(R.string.notification_title_queue);
        String messageText = I18n.formatQuantityMessage(myContext.context(),
                R.string.notification_queue_format, count, R.array.notification_queue_patterns,
                R.array.notification_queue_formats);

        Notification.Builder mBuilder =
                new Notification.Builder(myContext.context())
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(messageTitle)
                .setContentText(messageText);
        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(myContext.context(), QueueViewer.class);

        // The stack builder object will contain an artificial back stack for the
        // started Activity.
        // This ensures that navigating backward from the Activity leads out of
        // your application to the Home screen.
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(myContext.context());
        // Adds the back stack for the Intent (but not the Intent itself)
        stackBuilder.addParentStack(QueueViewer.class);
        // Adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);
        PendingIntent resultPendingIntent =
                stackBuilder.getPendingIntent(
                    0,
                    PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
        
        NotificationManager nM = (NotificationManager) myContext.context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nM.notify(CommandEnum.NOTIFY_QUEUE.ordinal(), mBuilder.build());
    }

    private void clearNotifications() {
        NotificationManager nM = (NotificationManager) myContext.context().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        if (nM != null) {
            nM.cancel(CommandEnum.NOTIFY_QUEUE.ordinal());
        }
    }
}
