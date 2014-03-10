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
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.Color;
import android.net.Uri;

import org.andstatus.app.IntentExtra;
import org.andstatus.app.R;
import org.andstatus.app.TimelineActivity;
import org.andstatus.app.appwidget.MyAppWidgetProvider;
import org.andstatus.app.context.MyPreferences;
import org.andstatus.app.data.DataPruner;
import org.andstatus.app.data.MyProvider;
import org.andstatus.app.data.TimelineTypeEnum;
import org.andstatus.app.net.ConnectionException;
import org.andstatus.app.util.I18n;
import org.andstatus.app.util.MyLog;

class CommandExecutorLoadTimeline extends CommandExecutorStrategy {

    private boolean mNotificationsEnabled;
    private boolean mNotificationsVibrate;
    
    CommandExecutorLoadTimeline() {
        mNotificationsEnabled = MyPreferences.getDefaultSharedPreferences().getBoolean("notifications_enabled", false);
        mNotificationsVibrate = MyPreferences.getDefaultSharedPreferences().getBoolean("vibration", false);
    }
    
    /* (non-Javadoc)
     * @see org.andstatus.app.service.OneCommandExecutor#execute()
     */
    @Override
    void execute() {
        loadTimelines();
        if (!execContext.getResult().hasError() && execContext.getCommandData().getTimelineType() == TimelineTypeEnum.ALL && !isStopping()) {
            new DataPruner(execContext.getContext()).prune();
        }
        if (!execContext.getResult().hasError()) {
            // Notify all timelines, 
            // see http://stackoverflow.com/questions/6678046/when-contentresolver-notifychange-is-called-for-a-given-uri-are-contentobserv
            execContext.getContext().getContentResolver().notifyChange(MyProvider.TIMELINE_URI, null);
        }
    }

    /**
     * Load Timeline(s) for one MyAccount
     * @return True if everything Succeeded
     */
    private void loadTimelines() {
        for (TimelineTypeEnum timelineType : getTimelines()) {
            if (isStopping()) {
                break;
            }
            execContext.setTimelineType(timelineType);
            loadTimeline();
        }
        if (!execContext.getResult().hasError()) {
            notifyOfUpdatedTimeline(execContext.getResult().getMessagesAdded(), 
                    execContext.getResult().getMentionsAdded(), execContext.getResult().getDirectedAdded());
        }
        String message = (execContext.getResult().hasError() ? "Failed" : "Succeeded")
                + " getting " + execContext.toString();
        MyLog.d(this, message);
    }

    private TimelineTypeEnum[] getTimelines() {
        TimelineTypeEnum[] timelineTypes;
        if (execContext.getCommandData().getTimelineType() == TimelineTypeEnum.ALL) {
            timelineTypes = new TimelineTypeEnum[] {
                    TimelineTypeEnum.HOME, TimelineTypeEnum.MENTIONS,
                    TimelineTypeEnum.DIRECT,
                    TimelineTypeEnum.FOLLOWING_USER
            };
        } else {
            timelineTypes = new TimelineTypeEnum[] {
                    execContext.getCommandData().getTimelineType()
            };
        }
        return timelineTypes;
    }

    private void loadTimeline() {
        boolean ok = false;
        try {
            if (execContext.getMyAccount().getConnection().isApiSupported(execContext.getTimelineType().getConnectionApiRoutine())) {
                long userId = execContext.getCommandData().itemId;
                if (userId == 0) {
                    userId = execContext.getMyAccount().getUserId();
                }
                execContext.setTimelineUserId(userId);
                MyLog.d(this, "Getting " + execContext);
                TimelineDownloader.getStrategy(execContext).download();
            } else {
                MyLog.v(this, execContext.getTimelineType() + " is not supported for "
                        + execContext.getMyAccount().getAccountName());
            }
            ok = true;
        } catch (ConnectionException e) {
            logConnectionException(e, execContext.getTimelineType().toString());
        } catch (SQLiteConstraintException e) {
            MyLog.e(this, execContext.getTimelineType().toString(), e);
        }
        execContext.getResult().setSoftErrorIfNotOk(ok);
    }
    
    /**
     * TODO: Different notifications for different Accounts
     * @param msgAdded Number of "Tweets" added
     * @param mentionsAdded
     * @param directedAdded
     */
    private void notifyOfUpdatedTimeline(int msgAdded, int mentionsAdded, int directedAdded) {
        boolean notified = false;
        if (mentionsAdded > 0) {
            notifyOfNewTweets(mentionsAdded, CommandEnum.NOTIFY_MENTIONS);
            notified = true;
        }
        if (directedAdded > 0) {
            notifyOfNewTweets(directedAdded, CommandEnum.NOTIFY_DIRECT_MESSAGE);
            notified = true;
        }
        if (msgAdded > 0 || !notified) {
            notifyOfNewTweets(msgAdded, CommandEnum.NOTIFY_HOME_TIMELINE);
            notified = true;
        }
    }
    
    /**
     * Notify the user of new tweets.
     * 
     * @param numHomeTimeline
     */
    private void notifyOfNewTweets(int numTweets, CommandEnum msgType) {
        MyLog.d(this, "notifyOfNewTweets n=" + numTweets + "; msgType=" + msgType);

        if (MyService.UPDATE_WIDGETS_ON_EVERY_UPDATE) {
            // Notify widgets even about the fact, that update occurred
            // even if there was nothing new
            updateWidgets(numTweets, msgType);
        }

        // If no notifications are enabled, return
        if (!mNotificationsEnabled || numTweets == 0) {
            return;
        }

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
                execContext.getContext().getText(R.string.notification_title), System.currentTimeMillis());

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
                aMessage = I18n.formatQuantityMessage(execContext.getContext(),
                        R.string.notification_new_mention_format, numTweets,
                        R.array.notification_mention_patterns,
                        R.array.notification_mention_formats);
                messageTitle = R.string.notification_title_mentions;
                intent = new Intent(execContext.getContext(), TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.MENTIONS.save());
                contentIntent = PendingIntent.getActivity(execContext.getContext(), numTweets,
                        intent, 0);
                break;

            case NOTIFY_DIRECT_MESSAGE:
                aMessage = I18n.formatQuantityMessage(execContext.getContext(),
                        R.string.notification_new_message_format, numTweets,
                        R.array.notification_message_patterns,
                        R.array.notification_message_formats);
                messageTitle = R.string.notification_title_messages;
                intent = new Intent(execContext.getContext(), TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.DIRECT.save());
                contentIntent = PendingIntent.getActivity(execContext.getContext(), numTweets,
                        intent, 0);
                break;

            case NOTIFY_HOME_TIMELINE:
            default:
                aMessage = I18n
                        .formatQuantityMessage(execContext.getContext(),
                                R.string.notification_new_tweet_format, numTweets,
                                R.array.notification_tweet_patterns,
                                R.array.notification_tweet_formats);
                messageTitle = R.string.notification_title;
                intent = new Intent(execContext.getContext(), TimelineActivity.class);
                intent.putExtra(IntentExtra.EXTRA_TIMELINE_TYPE.key,
                        TimelineTypeEnum.HOME.save());
                contentIntent = PendingIntent.getActivity(execContext.getContext(), numTweets,
                        intent, 0);
                break;
        }

        // Set up the scrolling message of the notification
        notification.tickerText = aMessage;

        // Set the latest event information and send the notification
        notification.setLatestEventInfo(execContext.getContext(), execContext.getContext().getText(messageTitle), aMessage,
                contentIntent);
        NotificationManager nM = (NotificationManager) execContext.getContext().getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        nM.notify(msgType.ordinal(), notification);
    }

    /**
     * Send Update intent to AndStatus Widget(s), if there are some
     * installed... (e.g. on the Home screen...)
     * 
     * @see MyAppWidgetProvider
     */
    private void updateWidgets(int numTweets, CommandEnum msgType) {
        Intent intent = new Intent(MyService.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(IntentExtra.EXTRA_MSGTYPE.key, msgType.save());
        intent.putExtra(IntentExtra.EXTRA_NUMTWEETS.key, numTweets);
        execContext.getContext().sendBroadcast(intent);
    }
}
